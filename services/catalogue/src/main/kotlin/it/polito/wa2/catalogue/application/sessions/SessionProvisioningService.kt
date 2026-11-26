package it.polito.wa2.catalogue.application.sessions

import it.polito.wa2.catalogue.application.ports.outbound.InventoryProvisioningPort
import it.polito.wa2.catalogue.application.ports.outbound.OutboxStatus
import it.polito.wa2.catalogue.application.ports.outbound.ProvisioningOutboxPort
import it.polito.wa2.catalogue.application.ports.outbound.ProvisioningOutboxTask
import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.application.ports.outbound.SessionPort
import it.polito.wa2.catalogue.config.CatalogueProperties
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Application service executing session inventory provisioning tasks from the outbox.
 * Remains completely decoupled from scheduling or transport triggers so it can be invoked
 * deterministically in tests or from any background runner.
 *
 * Every database step is its own short transaction; the call to Inventory & Orders runs between them,
 * never inside one (L15, L19). Transactions are programmatic because the steps are internal calls,
 * which @Transactional would silently skip (self-invocation trap).
 */
@Service
class SessionProvisioningService(
    private val sessionPort: SessionPort,
    private val seatingPlanPort: SeatingPlanPort,
    private val outboxPort: ProvisioningOutboxPort,
    private val inventoryProvisioningPort: InventoryProvisioningPort,
    transactionManager: PlatformTransactionManager,
    properties: CatalogueProperties
) {
    private val logger = LoggerFactory.getLogger(SessionProvisioningService::class.java)
    private val maxAttempts = properties.provisioningWorker.maxAttempts
    private val writeTx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Drains all currently due outbox tasks until the queue has no ready items.
     */
    fun processPendingTasks() {
        while (true) {
            val task = writeTx.execute { outboxPort.claimNextPending() } ?: break
            processSingleTask(task)
        }
    }

    /**
     * Processes a single claimed outbox task by performing the remote HTTP call outside
     * any open database transaction and committing either `READY` or retry/failure state.
     */
    fun processSingleTask(task: ProvisioningOutboxTask) {
        val sessionId = task.sessionId
        // Correlation id for the logs of this task and for the outbound call (L13, L25)
        MDC.put(REQUEST_ID_MDC_KEY, task.outboxId)
        try {
            val (session, seats) = readTx.execute { loadConfiguration(task) }!!

            // Remote I/O runs strictly outside any database transaction
            inventoryProvisioningPort.provisionSessionInventory(session, seats)

            writeTx.executeWithoutResult {
                val current = sessionPort.findById(sessionId)
                    ?: error("Session '${sessionId.value}' not found when marking READY")
                sessionPort.save(current.markReady())
                outboxPort.markCompleted(task.outboxId)
            }
            logger.info("Provisioning completed for session '{}', state is now READY", sessionId.value)
        } catch (ex: Exception) {
            logger.warn(
                "Provisioning attempt failed for session '{}' (outbox '{}'): {}",
                sessionId.value,
                task.outboxId,
                ex.message
            )
            handleFailedAttempt(task.outboxId, sessionId, ex.message ?: ex.javaClass.simpleName)
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }

    private fun loadConfiguration(task: ProvisioningOutboxTask): Pair<Session, List<PlanSeat>> {
        val session = sessionPort.findById(task.sessionId)
            ?: error("Session '${task.sessionId.value}' not found for outbox '${task.outboxId}'")
        return session to seatingPlanPort.findSeats(session.planId)
    }

    private fun handleFailedAttempt(outboxId: String, sessionId: SessionId, errorMessage: String) {
        writeTx.executeWithoutResult {
            val outboxStatus = outboxPort.recordFailure(outboxId, errorMessage, maxAttempts)
            if (outboxStatus == OutboxStatus.FAILED) {
                logger.error("Provisioning of session '{}' failed permanently after {} attempts", sessionId.value, maxAttempts)
                val current = sessionPort.findById(sessionId) ?: return@executeWithoutResult
                sessionPort.save(current.markProvisioningFailed())
            }
        }
    }

    private companion object {
        const val REQUEST_ID_MDC_KEY = "requestId"
    }
}

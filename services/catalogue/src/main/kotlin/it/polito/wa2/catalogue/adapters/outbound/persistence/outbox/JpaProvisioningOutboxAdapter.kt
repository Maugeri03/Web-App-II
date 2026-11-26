package it.polito.wa2.catalogue.adapters.outbound.persistence.outbox

import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import it.polito.wa2.catalogue.application.ports.outbound.OutboxStatus
import it.polito.wa2.catalogue.application.ports.outbound.ProvisioningOutboxPort
import it.polito.wa2.catalogue.application.ports.outbound.ProvisioningOutboxTask
import it.polito.wa2.catalogue.config.CatalogueProperties
import it.polito.wa2.catalogue.domain.sessions.SessionId
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * JPA-backed implementation of [ProvisioningOutboxPort] backed by `session_provisioning_outbox`.
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaProvisioningOutboxAdapter(
    private val outboxRepository: SpringDataOutboxRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    properties: CatalogueProperties
) : ProvisioningOutboxPort {

    private val leaseDuration: Duration = properties.provisioningWorker.lease
    private val maxBackoff: Duration = properties.provisioningWorker.maxBackoff

    override fun enqueuePending(sessionId: SessionId) {
        val now = clock.instant()
        val existing = outboxRepository.findBySessionId(sessionId.value)
        if (existing == null) {
            outboxRepository.saveAndFlush(
                SessionProvisioningOutboxEntity(
                    outboxId = idGenerator.newId("out"),
                    sessionId = sessionId.value,
                    status = OutboxStatus.PENDING,
                    attemptCount = 0,
                    nextAttemptAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            // Re-enqueue a previously failed task for a fresh provisioning attempt
            existing.status = OutboxStatus.PENDING
            existing.attemptCount = 0
            existing.lastError = null
            existing.nextAttemptAt = now
            existing.updatedAt = now
            outboxRepository.saveAndFlush(existing)
        }
    }

    override fun claimNextPending(): ProvisioningOutboxTask? {
        val record = outboxRepository.findNextPendingForUpdateSkipLocked() ?: return null
        val now = clock.instant()
        if (record.status == OutboxStatus.IN_PROGRESS) {
            // The previous worker's lease expired without an outcome: count that attempt as lost
            record.attemptCount += 1
            record.lastError = "Lease expired: previous provisioning attempt was interrupted"
        }
        record.status = OutboxStatus.IN_PROGRESS
        // While IN_PROGRESS, next_attempt_at is the lease expiry after which the task can be reclaimed
        record.nextAttemptAt = now.plus(leaseDuration)
        record.updatedAt = now
        val saved = outboxRepository.saveAndFlush(record)
        return ProvisioningOutboxTask(
            outboxId = saved.outboxId,
            sessionId = SessionId(saved.sessionId),
            attemptCount = saved.attemptCount
        )
    }

    override fun markCompleted(outboxId: String) {
        val record = outboxRepository.findById(outboxId).orElse(null) ?: return
        record.status = OutboxStatus.COMPLETED
        record.lastError = null
        record.updatedAt = clock.instant()
        outboxRepository.saveAndFlush(record)
    }

    override fun recordFailure(outboxId: String, errorMessage: String, maxAttempts: Int): OutboxStatus {
        val now = clock.instant()
        val record = outboxRepository.findById(outboxId).orElse(null) ?: return OutboxStatus.FAILED
        val nextAttempt = record.attemptCount + 1
        record.attemptCount = nextAttempt
        record.lastError = errorMessage.take(1000)
        record.updatedAt = now

        val resultingStatus = if (nextAttempt >= maxAttempts) {
            OutboxStatus.FAILED
        } else {
            record.nextAttemptAt = now.plus(backoffWithFullJitter(nextAttempt))
            OutboxStatus.PENDING
        }

        record.status = resultingStatus
        outboxRepository.saveAndFlush(record)
        return resultingStatus
    }

    /**
     * Exponential backoff with full jitter (L13): the delay is drawn uniformly in [0, min(cap, 1s * 2^attempt)],
     * so replicas that failed together do not retry in synchronised waves.
     */
    private fun backoffWithFullJitter(attempt: Int): Duration {
        val exponentialMs = 1000L shl attempt.coerceAtMost(16)
        val boundMs = minOf(exponentialMs, maxBackoff.toMillis())
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(boundMs + 1))
    }
}

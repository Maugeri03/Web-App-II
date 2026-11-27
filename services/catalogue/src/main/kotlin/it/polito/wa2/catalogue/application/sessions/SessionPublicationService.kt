package it.polito.wa2.catalogue.application.sessions

import it.polito.wa2.catalogue.application.exceptions.IdempotencyKeyInProgressException
import it.polito.wa2.catalogue.application.idempotency.IdempotencyRetentionService
import it.polito.wa2.catalogue.application.idempotency.IdempotentCommandRunner
import it.polito.wa2.catalogue.application.idempotency.IdempotentResult
import it.polito.wa2.catalogue.application.ports.outbound.EventPort
import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyPort
import it.polito.wa2.catalogue.application.ports.outbound.ProvisioningOutboxPort
import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.application.ports.outbound.SessionPort
import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.events.EventNotPublishedException
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionNotFoundException
import it.polito.wa2.catalogue.domain.sessions.SessionValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service orchestrating idempotent session publication (`DRAFT -> PROVISIONING`)
 * and enqueuing asynchronous inventory provisioning via dedicated ports.
 */
@Service
class SessionPublicationService(
    private val sessionPort: SessionPort,
    private val eventPort: EventPort,
    private val seatingPlanPort: SeatingPlanPort,
    private val outboxPort: ProvisioningOutboxPort,
    private val idempotencyPort: IdempotencyPort,
    private val idempotencyRetention: IdempotencyRetentionService,
    private val idempotentCommands: IdempotentCommandRunner
) {

    /**
     * Validates `Idempotency-Key`, replays the prior outcome when present, or atomically transitions
     * the session to `PROVISIONING`, enqueues an outbox task, and records the outcome under the key.
     *
     * @param callerId identity of the caller; idempotency keys are scoped to it.
     * @param expectedVersion the version the client expects (from `If-Match`); a mismatch throws
     * [ConcurrentModificationException].
     */
    @Transactional
    fun publishSession(
        callerId: String,
        sessionId: SessionId,
        rawIdempotencyKey: String?,
        expectedVersion: Long
    ): IdempotentResult<Session> {
        val scope = idempotentCommands.scope(
            callerId = callerId,
            operation = "POST:/sessions/${sessionId.value}/publication",
            rawKey = rawIdempotencyKey
        )
        val requestHash = idempotentCommands.fingerprint("publish", sessionId.value)
        val validSince = idempotencyRetention.validSince()

        // A completed identical request is replayed before the If-Match check, as the contract requires
        idempotencyPort.findResult(scope, requestHash, validSince, Session::class)?.let { previous ->
            return IdempotentResult(previous, replayed = true)
        }

        val current = sessionPort.findById(sessionId)
            ?: throw SessionNotFoundException(sessionId)

        if (current.version != expectedVersion) {
            throw ConcurrentModificationException(
                "Session '${sessionId.value}' is at version ${current.version}, but If-Match expected $expectedVersion"
            )
        }
        validatePublishable(current)

        val savedSession = try {
            sessionPort.save(current.startProvisioning())
        } catch (ex: ConcurrentModificationException) {
            // A concurrent request won the race: if it carried the same key, report it as in progress
            // rather than as a stale If-Match. A different payload under the same key is still rejected as reused.
            if (idempotencyPort.findResult(scope, requestHash, validSince, Session::class) != null) {
                throw IdempotencyKeyInProgressException(scope.idempotencyKey)
            }
            throw ex
        }
        outboxPort.enqueuePending(sessionId)
        idempotencyPort.saveResult(scope, requestHash, savedSession, validSince)
        return IdempotentResult(savedSession, replayed = false)
    }

    /**
     * Checks that the parent event is published and that pricing is complete,
     * covering every seat of the session's seating plan exactly.
     */
    private fun validatePublishable(session: Session) {
        val id = session.sessionId.value
        val eventState = eventPort.findPublicationState(session.eventId)
            ?: throw SessionValidationException("Event '${session.eventId.value}' of session '$id' does not exist")
        if (eventState != EventPublicationState.PUBLISHED) {
            throw EventNotPublishedException(session.eventId, eventState)
        }
        if (session.priceTiers.isEmpty() || session.seatPrices.isEmpty()) {
            throw SessionValidationException("Session '$id' must define priceTiers and seatPrices before publication")
        }
        val planSeatIds = seatingPlanPort.findSeats(session.planId).map { it.seatId }.toSet()
        val pricedSeatIds = session.seatPrices.map { it.seatId }.toSet()
        if (planSeatIds != pricedSeatIds) {
            throw SessionValidationException(
                "Session '$id' must assign a price to every seat of plan '${session.planId.value}' and to no other seat"
            )
        }
    }
}

package it.polito.wa2.catalogue.application.sessions

import it.polito.wa2.catalogue.application.idempotency.IdempotentCommandRunner
import it.polito.wa2.catalogue.application.idempotency.IdempotentResult
import it.polito.wa2.catalogue.application.ports.outbound.EventPort
import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.application.ports.outbound.SessionPort
import it.polito.wa2.catalogue.application.ports.outbound.VenuePort
import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.common.DomainValidationException
import it.polito.wa2.catalogue.domain.events.EventNotFoundException
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionConfiguration
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionNotEditableException
import it.polito.wa2.catalogue.domain.sessions.SessionNotFoundException
import it.polito.wa2.catalogue.domain.sessions.SessionStatus
import it.polito.wa2.catalogue.domain.venues.Venue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for draft session commands: creation and replacement of the sales configuration.
 */
@Service
class SessionCommandService(
    private val sessionPort: SessionPort,
    private val eventPort: EventPort,
    private val venuePort: VenuePort,
    private val seatingPlanPort: SeatingPlanPort,
    private val idGenerator: IdGenerator,
    private val idempotentCommands: IdempotentCommandRunner
) {

    /**
     * Creates a `DRAFT` session of [eventId], idempotently under [rawIdempotencyKey].
     * The session belongs to the event's organiser. Throws [EventNotFoundException] if the event does not
     * exist, and `DomainValidationException` if the venue, plan, schedule or pricing are invalid.
     */
    @Transactional
    fun createSession(
        callerId: String,
        eventId: EventId,
        rawIdempotencyKey: String?,
        configuration: SessionConfiguration
    ): IdempotentResult<Session> {
        val scope = idempotentCommands.scope(callerId, "POST:/events/${eventId.value}/sessions", rawIdempotencyKey)
        val requestHash = idempotentCommands.fingerprint(eventId.value, configuration)
        return idempotentCommands.run(scope, requestHash, Session::class) {
            val event = eventPort.findById(eventId) ?: throw EventNotFoundException(eventId)
            val (venue, plan) = loadReferences(configuration)
            sessionPort.create(
                Session.createDraft(
                    sessionId = SessionId(idGenerator.newId("ses")),
                    eventId = eventId,
                    organiserId = event.organiserId,
                    configuration = configuration,
                    venue = venue,
                    plan = plan
                )
            )
        }
    }

    /**
     * Replaces the configuration of the `DRAFT` session [sessionId] if it is still at [expectedVersion]
     * (from `If-Match`). Throws [ConcurrentModificationException] on a version mismatch and
     * `SessionNotEditableException` if the session is no longer a draft.
     */
    @Transactional
    fun replaceSession(sessionId: SessionId, expectedVersion: Long, configuration: SessionConfiguration): Session {
        val current = sessionPort.findById(sessionId) ?: throw SessionNotFoundException(sessionId)
        if (current.version != expectedVersion) {
            throw ConcurrentModificationException(
                "Session '${sessionId.value}' is at version ${current.version}, but If-Match expected $expectedVersion"
            )
        }
        // Report a non-editable session as such, before looking at the references in the body
        if (current.state != SessionStatus.DRAFT) {
            throw SessionNotEditableException(sessionId, current.state)
        }
        val (venue, plan) = loadReferences(configuration)
        return sessionPort.save(current.reconfigure(configuration, venue, plan))
    }

    /** Loads the venue and seating plan referenced by the request body; unknown references are a 422. */
    private fun loadReferences(configuration: SessionConfiguration): Pair<Venue, SeatingPlan> {
        val venue = venuePort.findById(configuration.venueId)
            ?: throw DomainValidationException("Venue '${configuration.venueId.value}' does not exist")
        val plan = seatingPlanPort.findById(configuration.planId)
            ?: throw DomainValidationException("Seating plan '${configuration.planId.value}' does not exist")
        return venue to plan
    }
}

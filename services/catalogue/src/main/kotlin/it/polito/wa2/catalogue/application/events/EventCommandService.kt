package it.polito.wa2.catalogue.application.events

import it.polito.wa2.catalogue.application.idempotency.IdempotentCommandRunner
import it.polito.wa2.catalogue.application.idempotency.IdempotentResult
import it.polito.wa2.catalogue.application.ports.outbound.EventPort
import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventDetails
import it.polito.wa2.catalogue.domain.events.EventNotFoundException
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for event commands: creation and replacement of details and publication state.
 */
@Service
class EventCommandService(
    private val eventPort: EventPort,
    private val idGenerator: IdGenerator,
    private val idempotentCommands: IdempotentCommandRunner
) {

    /**
     * Creates an event owned by [callerId], idempotently under [rawIdempotencyKey].
     */
    @Transactional
    fun createEvent(callerId: String, rawIdempotencyKey: String?, details: EventDetails): IdempotentResult<Event> {
        val scope = idempotentCommands.scope(callerId, "POST:/events", rawIdempotencyKey)
        return idempotentCommands.run(scope, idempotentCommands.fingerprint(details), Event::class) {
            eventPort.create(
                Event.create(
                    eventId = EventId(idGenerator.newId("evt")),
                    organiserId = OrganiserId(callerId),
                    details = details
                )
            )
        }
    }

    /**
     * Replaces the details and publication state of [eventId] if it is still at [expectedVersion]
     * (from `If-Match`); otherwise throws [ConcurrentModificationException].
     */
    @Transactional
    fun replaceEvent(eventId: EventId, expectedVersion: Long, details: EventDetails): Event {
        val current = eventPort.findById(eventId) ?: throw EventNotFoundException(eventId)
        if (current.version != expectedVersion) {
            throw ConcurrentModificationException(
                "Event '${eventId.value}' is at version ${current.version}, but If-Match expected $expectedVersion"
            )
        }
        return eventPort.save(current.replaceDetails(details))
    }
}

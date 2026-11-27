package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId

/**
 * Outbound persistence port for [Event] aggregates.
 */
interface EventPort {

    /** Returns the publication state of [eventId], or `null` if the event does not exist. */
    fun findPublicationState(eventId: EventId): EventPublicationState?

    /** Finds an event by [eventId], or returns `null` if it does not exist. */
    fun findById(eventId: EventId): Event?

    /** Inserts a new event and returns it as stored. */
    fun create(event: Event): Event

    /**
     * Persists the details of an existing event, whose [Event.version] must match the stored one,
     * and returns it with the advanced version. Throws `ConcurrentModificationException` otherwise.
     */
    fun save(event: Event): Event
}

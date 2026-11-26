package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId

/**
 * Outbound persistence port for reading event information needed by session use cases.
 */
interface EventPort {

    /** Returns the publication state of [eventId], or `null` if the event does not exist. */
    fun findPublicationState(eventId: EventId): EventPublicationState?
}

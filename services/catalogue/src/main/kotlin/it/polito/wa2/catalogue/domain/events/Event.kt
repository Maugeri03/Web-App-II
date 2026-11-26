package it.polito.wa2.catalogue.domain.events

import it.polito.wa2.catalogue.domain.sessions.EventId

/** Publication lifecycle of an event. */
enum class EventPublicationState {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}

/**
 * Thrown when a session is published while its parent event is not `PUBLISHED` (HTTP 409).
 */
class EventNotPublishedException(
    eventId: EventId,
    state: EventPublicationState
) : RuntimeException("Event '${eventId.value}' must be PUBLISHED before publishing its sessions (current: $state)")

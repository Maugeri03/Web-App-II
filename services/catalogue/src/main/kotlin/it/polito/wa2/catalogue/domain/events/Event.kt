package it.polito.wa2.catalogue.domain.events

import it.polito.wa2.catalogue.domain.common.ResourceNotFoundException
import it.polito.wa2.catalogue.domain.common.validate
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.OrganiserId

/** Publication lifecycle of an event. */
enum class EventPublicationState {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}

/** Editable details of an event, including its publication state. */
data class EventDetails(
    val title: String,
    val description: String,
    val publicationState: EventPublicationState
) {
    init {
        validate(title.isNotBlank()) { "Event title must not be blank" }
    }
}

/** A catalogue event owned by an organiser; its sessions can be published only while it is `PUBLISHED`. */
data class Event(
    val eventId: EventId,
    val organiserId: OrganiserId,
    val details: EventDetails,
    val version: Long
) {
    init {
        require(version >= 1) { "version must be >= 1" }
    }

    /** Replaces the editable details; the version is advanced by persistence. */
    fun replaceDetails(newDetails: EventDetails): Event = copy(details = newDetails)

    companion object {
        /** Creates a new event at version 1. */
        fun create(eventId: EventId, organiserId: OrganiserId, details: EventDetails): Event =
            Event(eventId, organiserId, details, version = 1)
    }
}

/** Thrown when an event does not exist (HTTP 404). */
class EventNotFoundException(eventId: EventId) : ResourceNotFoundException("Event", eventId.value)

/**
 * Thrown when a session is published while its parent event is not `PUBLISHED` (HTTP 409).
 */
class EventNotPublishedException(
    eventId: EventId,
    state: EventPublicationState
) : RuntimeException("Event '${eventId.value}' must be PUBLISHED before publishing its sessions (current: $state)")

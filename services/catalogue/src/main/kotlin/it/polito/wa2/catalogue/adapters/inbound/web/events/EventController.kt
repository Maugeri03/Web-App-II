package it.polito.wa2.catalogue.adapters.inbound.web.events

import it.polito.wa2.catalogue.adapters.inbound.web.common.ApiHeaders
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions.etagOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.callerIdOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseEnum
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseParameter
import it.polito.wa2.catalogue.application.events.EventCommandService
import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventDetails
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

/** Request body of `POST /events` and `PUT /events/{eventId}` (`EventWriteRequest`). */
data class EventWriteRequestDto(
    val title: String,
    val description: String,
    val publicationState: String
) {
    fun toDomain() = EventDetails(
        title = title,
        description = description,
        publicationState = parseEnum<EventPublicationState>("publicationState", publicationState)
    )
}

/** API representation of an event (`Event`). */
data class EventResponseDto(
    val eventId: String,
    val organiserId: String,
    val title: String,
    val description: String,
    val publicationState: String,
    val version: Long
)

fun Event.toDto() = EventResponseDto(
    eventId = eventId.value,
    organiserId = organiserId.value,
    title = details.title,
    description = details.description,
    publicationState = details.publicationState.name,
    version = version
)

/**
 * REST controller for event management and publication.
 */
@RestController
class EventController(
    private val eventCommandService: EventCommandService
) {

    /**
     * Creates an event owned by the caller. Requires `Idempotency-Key`; a retry of a completed identical
     * request returns the stored `201` response.
     */
    @PostMapping("/events", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createEvent(
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestBody body: EventWriteRequestDto,
        principal: Principal?
    ): ResponseEntity<EventResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val event = eventCommandService.createEvent(callerIdOf(principal), idempotencyKey, body.toDomain()).value
        return ResponseEntity
            .created(URI.create("/events/${event.eventId.value}"))
            .eTag(etagOf(event.version))
            .cacheControl(CacheControl.noStore())
            .body(event.toDto())
    }

    /**
     * Replaces the details and publication state of an event. Requires `If-Match` with the current ETag.
     * Setting `publicationState` to `PUBLISHED` is what allows the event's sessions to be published.
     */
    @PutMapping(
        "/events/{eventId}",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun replaceEvent(
        @PathVariable eventId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody body: EventWriteRequestDto
    ): ResponseEntity<EventResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val expectedVersion = HttpPreconditions.expectedVersion(ifMatch)
        val event = eventCommandService.replaceEvent(
            eventId = parseParameter("eventId") { EventId(eventId) },
            expectedVersion = expectedVersion,
            details = body.toDomain()
        )
        return ResponseEntity
            .ok()
            .eTag(etagOf(event.version))
            .cacheControl(CacheControl.noStore())
            .body(event.toDto())
    }
}

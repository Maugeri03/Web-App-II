package it.polito.wa2.catalogue.adapters.inbound.web.sessions

import it.polito.wa2.catalogue.adapters.inbound.web.common.ApiHeaders
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions.etagOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.callerIdOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseParameter
import it.polito.wa2.catalogue.application.sessions.SessionCommandService
import it.polito.wa2.catalogue.application.sessions.SessionPublicationService
import it.polito.wa2.catalogue.application.sessions.SessionQueryService
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.SessionId
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

/**
 * REST controller exposing endpoints for catalogue sessions.
 */
@RestController
class SessionController(
    private val sessionQueryService: SessionQueryService,
    private val sessionCommandService: SessionCommandService,
    private val sessionPublicationService: SessionPublicationService
) {
    /**
     * Retrieves a single session by its identifier, validating the API version header
     * and supporting conditional GET via `If-None-Match` / `ETag`.
     */
    @GetMapping("/sessions/{sessionId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSessionById(
        @PathVariable sessionId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val session = sessionQueryService.getSession(parseSessionId(sessionId))
        val etagValue = etagOf(session.version)

        if (HttpPreconditions.matchesIfNoneMatch(ifNoneMatch, etagValue)) {
            return ResponseEntity
                .status(HttpStatus.NOT_MODIFIED)
                .eTag(etagValue)
                .build()
        }

        return ResponseEntity
            .ok()
            .eTag(etagValue)
            .cacheControl(CacheControl.noStore())
            .body(session.toDto())
    }

    /**
     * Creates a `DRAFT` session of the event. Requires `Idempotency-Key`; a retry of a completed identical
     * request returns the stored `201` response.
     */
    @PostMapping(
        "/events/{eventId}/sessions",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun createSession(
        @PathVariable eventId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestBody body: SessionWriteRequestDto,
        principal: Principal?
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val session = sessionCommandService.createSession(
            callerId = callerIdOf(principal),
            eventId = parseParameter("eventId") { EventId(eventId) },
            rawIdempotencyKey = idempotencyKey,
            configuration = body.toDomain()
        ).value
        return ResponseEntity
            .created(URI.create("/sessions/${session.sessionId.value}"))
            .eTag(etagOf(session.version))
            .cacheControl(CacheControl.noStore())
            .body(session.toDto())
    }

    /**
     * Replaces the schedule and pricing of a `DRAFT` session. Requires `If-Match` with the current ETag.
     */
    @PutMapping(
        "/sessions/{sessionId}",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun replaceSession(
        @PathVariable sessionId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody body: SessionWriteRequestDto
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val expectedVersion = HttpPreconditions.expectedVersion(ifMatch)
        val session = sessionCommandService.replaceSession(
            sessionId = parseSessionId(sessionId),
            expectedVersion = expectedVersion,
            configuration = body.toDomain()
        )
        return ResponseEntity
            .ok()
            .eTag(etagOf(session.version))
            .cacheControl(CacheControl.noStore())
            .body(session.toDto())
    }

    /**
     * Publishes a draft session (`DRAFT -> PROVISIONING`). Requires `Idempotency-Key` and `If-Match`;
     * returns `202 Accepted` while inventory provisioning proceeds asynchronously.
     */
    @PostMapping("/sessions/{sessionId}/publication", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun publishSession(
        @PathVariable sessionId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        principal: Principal?
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val expectedVersion = HttpPreconditions.expectedVersion(ifMatch)

        val session = sessionPublicationService.publishSession(
            callerId = callerIdOf(principal),
            sessionId = parseSessionId(sessionId),
            rawIdempotencyKey = idempotencyKey,
            expectedVersion = expectedVersion
        ).value

        // Both the first execution and a replay answer 202: the replay returns the recorded outcome
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .location(URI.create("/sessions/${session.sessionId.value}"))
            .eTag(etagOf(session.version))
            .body(session.toDto())
    }

    private fun parseSessionId(raw: String) = parseParameter("sessionId") { SessionId(raw) }
}

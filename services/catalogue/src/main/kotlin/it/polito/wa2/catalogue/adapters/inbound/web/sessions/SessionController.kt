package it.polito.wa2.catalogue.adapters.inbound.web.sessions

import it.polito.wa2.catalogue.adapters.inbound.web.common.ApiHeaders
import it.polito.wa2.catalogue.adapters.inbound.web.common.MissingPreconditionException
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseParameter
import it.polito.wa2.catalogue.application.sessions.SessionPublicationService
import it.polito.wa2.catalogue.application.sessions.SessionQueryService
import it.polito.wa2.catalogue.domain.sessions.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.sessions.SessionId
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

/**
 * REST controller exposing endpoints for catalogue sessions.
 */
@RestController
@RequestMapping("/sessions", produces = [MediaType.APPLICATION_JSON_VALUE])
class SessionController(
    private val sessionQueryService: SessionQueryService,
    private val sessionPublicationService: SessionPublicationService
) {
    /**
     * Retrieves a single session by its identifier, validating the API version header
     * and supporting conditional GET via `If-None-Match` / `ETag`.
     */
    @GetMapping("/{sessionId}")
    fun getSessionById(
        @PathVariable sessionId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val session = sessionQueryService.getSession(parseSessionId(sessionId))
        val etagValue = etagOf(session.version)

        if (matchesIfNoneMatch(ifNoneMatch, etagValue)) {
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
     * Publishes a draft session (`DRAFT -> PROVISIONING`). Requires `Idempotency-Key` and `If-Match`;
     * returns `202 Accepted` while inventory provisioning proceeds asynchronously.
     */
    @PostMapping("/{sessionId}/publication")
    fun publishSession(
        @PathVariable sessionId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        principal: Principal?
    ): ResponseEntity<SessionResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        if (ifMatch.isNullOrBlank()) throw MissingPreconditionException(HttpHeaders.IF_MATCH)
        val expectedVersion = parseVersionEtag(ifMatch)
            ?: throw ConcurrentModificationException("If-Match '$ifMatch' does not match the current session ETag")

        val result = sessionPublicationService.publishSession(
            callerId = callerIdOf(principal),
            sessionId = parseSessionId(sessionId),
            rawIdempotencyKey = idempotencyKey,
            expectedVersion = expectedVersion
        )

        // Both the first execution and a replay answer 202: the replay returns the recorded outcome
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .location(URI.create("/sessions/${result.session.sessionId.value}"))
            .eTag(etagOf(result.session.version))
            .body(result.session.toDto())
    }

    /**
     * Identity used to scope idempotency keys: the authenticated principal's name (the JWT subject once
     * Keycloak is configured), or a shared anonymous identity while authentication is not yet in place.
     */
    private fun callerIdOf(principal: Principal?): String =
        principal?.name?.takeIf { it.isNotBlank() } ?: ANONYMOUS_CALLER

    private fun parseSessionId(raw: String) = parseParameter("sessionId") { SessionId(raw) }

    private fun etagOf(version: Long) = "\"$version\""

    /** Extracts the version from an ETag such as `"3"` or `W/"3"`, or returns `null` if malformed. */
    private fun parseVersionEtag(etag: String): Long? =
        etag.trim().removePrefix("W/").removeSurrounding("\"").toLongOrNull()

    private fun matchesIfNoneMatch(header: String?, currentEtag: String): Boolean {
        if (header.isNullOrBlank()) return false
        return header.split(",")
            .map { it.trim() }
            .any { candidate ->
                candidate == "*" || candidate.removePrefix("W/") == currentEtag
            }
    }

    private companion object {
        const val ANONYMOUS_CALLER = "anonymous"
    }
}

package it.polito.wa2.catalogue.api.sessions

import it.polito.wa2.catalogue.api.common.ApiHeaders
import it.polito.wa2.catalogue.application.sessions.SessionQueryService
import it.polito.wa2.catalogue.entities.sessions.SessionId
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller exposing read endpoints for catalogue sessions.
 */
@RestController
@RequestMapping("/sessions", produces = [MediaType.APPLICATION_JSON_VALUE])
class SessionController(
    private val sessionQueryService: SessionQueryService
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
        val session = sessionQueryService.getSession(SessionId(sessionId))
        val etagValue = "\"${session.version}\""

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

    private fun matchesIfNoneMatch(header: String?, currentEtag: String): Boolean {
        if (header.isNullOrBlank()) return false
        return header.split(",")
            .map { it.trim() }
            .any { candidate ->
                candidate == "*" || candidate.removePrefix("W/") == currentEtag
            }
    }
}
package it.polito.wa2.catalogue.adapters.inbound.web.common

import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import org.springframework.http.HttpHeaders
import java.security.Principal

/**
 * Helpers for version-based strong ETags (`"<version>"`) and the conditional headers built on them.
 */
object HttpPreconditions {

    /** Strong ETag of a representation at [version]. */
    fun etagOf(version: Long): String = "\"$version\""

    /**
     * Returns the version required by [ifMatch]. A missing header is a 428; a malformed ETag can never match
     * the current representation, so it is a 412 like a stale one.
     */
    fun expectedVersion(ifMatch: String?): Long {
        if (ifMatch.isNullOrBlank()) throw MissingPreconditionException(HttpHeaders.IF_MATCH)
        return parseVersionEtag(ifMatch)
            ?: throw ConcurrentModificationException("If-Match '$ifMatch' does not match the current ETag")
    }

    /** Whether [header] (`If-None-Match`) matches [currentEtag], so the response can be a 304. */
    fun matchesIfNoneMatch(header: String?, currentEtag: String): Boolean {
        if (header.isNullOrBlank()) return false
        return header.split(",")
            .map { it.trim() }
            .any { candidate -> candidate == "*" || candidate.removePrefix("W/") == currentEtag }
    }

    /** Extracts the version from an ETag such as `"3"` or `W/"3"`, or returns `null` if malformed. */
    private fun parseVersionEtag(etag: String): Long? =
        etag.trim().removePrefix("W/").removeSurrounding("\"").toLongOrNull()
}

/**
 * Identity of the caller, used to scope idempotency keys and as organiser of the resources it creates:
 * the authenticated principal's name (the JWT subject once Keycloak is configured), or a shared
 * anonymous identity while authentication is not yet in place.
 */
fun callerIdOf(principal: Principal?): String =
    principal?.name?.takeIf { it.isNotBlank() } ?: ANONYMOUS_CALLER

private const val ANONYMOUS_CALLER = "anonymous"

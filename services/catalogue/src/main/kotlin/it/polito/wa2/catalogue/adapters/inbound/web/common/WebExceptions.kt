package it.polito.wa2.catalogue.adapters.inbound.web.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Thrown when the client provides an unsupported or invalid X-API-Version header.
 */
class UnsupportedApiVersionException(
    version: String
) : RuntimeException("Unsupported API version: '$version'. Supported version is '${ApiHeaders.SUPPORTED_VERSION}'")

/**
 * Thrown when the If-Match header is missing on a mutation endpoint requiring optimistic locking.
 */
class MissingPreconditionException(
    headerName: String = "If-Match"
) : RuntimeException("Required precondition header '$headerName' is missing")

/**
 * Thrown when a request parameter cannot be turned into a valid domain value (HTTP 400).
 */
class InvalidRequestParameterException(
    parameter: String,
    reason: String
) : RuntimeException("Invalid parameter '$parameter': $reason")

/**
 * Thrown when a field of a well-formed request body has a value outside its contract (HTTP 422),
 * e.g. a blank identifier, a malformed timestamp or an unknown enum value.
 */
class InvalidRequestFieldException(
    val field: String,
    reason: String
) : RuntimeException("Invalid value for '$field': $reason")

/**
 * Parses a raw request value into a domain value object, turning its validation failure
 * (an [IllegalArgumentException] from `require`) into a client error instead of a 500.
 */
inline fun <T> parseParameter(parameter: String, parse: () -> T): T =
    try {
        parse()
    } catch (ex: IllegalArgumentException) {
        throw InvalidRequestParameterException(parameter, ex.message ?: "invalid value")
    }

/**
 * Same as [parseParameter] for a field of the request body: a failure is a 422.
 */
inline fun <T> parseField(field: String, parse: () -> T): T =
    try {
        parse()
    } catch (ex: IllegalArgumentException) {
        throw InvalidRequestFieldException(field, ex.message ?: "invalid value")
    }

/** Parses an RFC 3339 `date-time` keeping its offset (e.g. `2027-07-10T21:00:00+02:00`). */
fun parseOffsetDateTime(field: String, raw: String): OffsetDateTime =
    try {
        OffsetDateTime.parse(raw)
    } catch (_: DateTimeParseException) {
        throw InvalidRequestFieldException(field, "'$raw' is not a date-time with offset, e.g. 2027-07-10T21:00:00+02:00")
    }

/** Parses an RFC 3339 `date-time` into an instant. */
fun parseInstant(field: String, raw: String): Instant = parseOffsetDateTime(field, raw).toInstant()

/** Parses [raw] as one of the constants of [E], reporting the allowed values otherwise. */
inline fun <reified E : Enum<E>> parseEnum(field: String, raw: String): E =
    enumValues<E>().firstOrNull { it.name == raw }
        ?: throw InvalidRequestFieldException(field, "'$raw' is not one of ${enumValues<E>().joinToString { it.name }}")

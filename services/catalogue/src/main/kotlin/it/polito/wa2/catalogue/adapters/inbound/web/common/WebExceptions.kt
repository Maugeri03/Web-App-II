package it.polito.wa2.catalogue.adapters.inbound.web.common

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
 * Parses a raw request value into a domain value object, turning its validation failure
 * (an [IllegalArgumentException] from `require`) into a client error instead of a 500.
 */
inline fun <T> parseParameter(parameter: String, parse: () -> T): T =
    try {
        parse()
    } catch (ex: IllegalArgumentException) {
        throw InvalidRequestParameterException(parameter, ex.message ?: "invalid value")
    }

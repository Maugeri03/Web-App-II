package it.polito.wa2.catalogue.domain.common

/**
 * Thrown when a resource does not exist or is not visible to the caller (HTTP 404).
 * [resource] is a human-readable name (e.g. `Venue`, `Seating plan`) from which the problem code is derived.
 */
open class ResourceNotFoundException(
    val resource: String,
    id: String
) : RuntimeException("$resource with id '$id' was not found")

/**
 * Thrown when request values or the references they contain break a domain rule (HTTP 422).
 */
open class DomainValidationException(
    override val message: String
) : RuntimeException(message)

/**
 * Thrown when an operation conflicts with the current state of a resource (HTTP 409),
 * e.g. editing a session that is no longer a draft or a concurrent insert of the same unique value.
 */
open class ResourceConflictException(
    override val message: String
) : RuntimeException(message)

/**
 * Thrown when the version expected by the client (from `If-Match`) does not match the current one,
 * or when a concurrent update wins the optimistic lock (HTTP 412).
 */
class ConcurrentModificationException(
    override val message: String
) : RuntimeException(message)

/**
 * Evaluates [condition] and throws [DomainValidationException] with [message] when it is false.
 * Domain rules on request data use this instead of `require`, so a violation is a 422 and not a bug.
 */
inline fun validate(condition: Boolean, message: () -> String) {
    if (!condition) throw DomainValidationException(message())
}

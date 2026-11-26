package it.polito.wa2.catalogue.application.exceptions

/**
 * Thrown when the Idempotency-Key header is missing, blank, or not a valid UUID (HTTP 400).
 */
class MissingOrInvalidIdempotencyKeyException(
    reason: String
) : RuntimeException("Invalid Idempotency-Key header: $reason")

/**
 * Thrown when an Idempotency-Key is reused with different request data (HTTP 422).
 */
class IdempotencyKeyReusedException(
    idempotencyKey: String
) : RuntimeException("Idempotency-Key '$idempotencyKey' was already used with different request data")

/**
 * Thrown when an identical request with the same Idempotency-Key is still being processed (HTTP 409).
 */
class IdempotencyKeyInProgressException(
    idempotencyKey: String
) : RuntimeException("A request with Idempotency-Key '$idempotencyKey' is still being processed")

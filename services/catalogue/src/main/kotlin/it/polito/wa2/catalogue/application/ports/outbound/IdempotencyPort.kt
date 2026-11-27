package it.polito.wa2.catalogue.application.ports.outbound

import java.time.Instant
import kotlin.reflect.KClass

/**
 * Identifies an idempotent operation: keys are scoped to the caller and to the operation.
 */
data class IdempotencyScope(
    val callerId: String,
    val operation: String,
    val idempotencyKey: String
)

/**
 * Outbound persistence port for idempotent command deduplication and result replay.
 * It stores the domain outcome of the command (a `Session`, `Venue`, `Event`, `SeatingPlan`, ...);
 * how it is represented on the wire is up to the web adapter.
 */
interface IdempotencyPort {

    /**
     * Returns the result of type [resultType] recorded for [scope], if one was created after [notBefore]
     * (older records are expired and ignored).
     * Throws `IdempotencyKeyReusedException` if the stored request fingerprint differs from [requestHash].
     */
    fun <T : Any> findResult(scope: IdempotencyScope, requestHash: String, notBefore: Instant, resultType: KClass<T>): T?

    /**
     * Records [result] for [scope], replacing an expired record (created before [notBefore]) if present.
     * Throws `IdempotencyKeyInProgressException` if a concurrent request already recorded the same key.
     */
    fun saveResult(scope: IdempotencyScope, requestHash: String, result: Any, notBefore: Instant)

    /** Deletes all records created before [notBefore] and returns how many were removed. */
    fun purgeExpired(notBefore: Instant): Int
}

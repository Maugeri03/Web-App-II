package it.polito.wa2.catalogue.application.idempotency

import it.polito.wa2.catalogue.application.exceptions.MissingOrInvalidIdempotencyKeyException
import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyPort
import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyScope
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Outcome of an idempotent command: its result, and whether it was replayed from a previous execution
 * with the same Idempotency-Key instead of being executed now.
 */
data class IdempotentResult<T>(
    val value: T,
    val replayed: Boolean
)

/**
 * Runs commands under an `Idempotency-Key`: validates the key, replays the stored outcome of a completed
 * identical request, or executes the command and records its outcome. It never opens a transaction:
 * the command, its effects and the idempotency record must commit together in the caller's transaction.
 */
@Component
class IdempotentCommandRunner(
    private val idempotencyPort: IdempotencyPort,
    private val retention: IdempotencyRetentionService
) {

    /** Builds the scope of [operation] for [callerId], validating that [rawKey] is present and a UUID. */
    fun scope(callerId: String, operation: String, rawKey: String?): IdempotencyScope =
        IdempotencyScope(callerId = callerId, operation = operation, idempotencyKey = validateUuidKey(rawKey))

    /**
     * Fingerprint of the request data: a SHA-256 of [parts]. Parts must have a stable `toString`
     * (strings, numbers, data classes), so the same request always yields the same fingerprint.
     */
    fun fingerprint(vararg parts: Any?): String = sha256(parts.joinToString(separator = "\u0000"))

    /**
     * Replays the result recorded for [scope] or runs [command] and records its result.
     * Must be called inside the caller's transaction.
     */
    fun <T : Any> run(
        scope: IdempotencyScope,
        requestHash: String,
        resultType: KClass<T>,
        command: () -> T
    ): IdempotentResult<T> {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "Idempotent commands must run inside the caller's transaction"
        }
        val validSince = retention.validSince()
        idempotencyPort.findResult(scope, requestHash, validSince, resultType)?.let { previous ->
            return IdempotentResult(previous, replayed = true)
        }
        val result = command()
        idempotencyPort.saveResult(scope, requestHash, result, validSince)
        return IdempotentResult(result, replayed = false)
    }

    private fun validateUuidKey(rawKey: String?): String {
        if (rawKey.isNullOrBlank()) {
            throw MissingOrInvalidIdempotencyKeyException("Header 'Idempotency-Key' is required")
        }
        return try {
            UUID.fromString(rawKey.trim()).toString()
        } catch (_: IllegalArgumentException) {
            throw MissingOrInvalidIdempotencyKeyException("Header 'Idempotency-Key' must be a valid UUID")
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

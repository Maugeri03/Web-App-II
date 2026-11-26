package it.polito.wa2.catalogue.adapters.outbound.persistence.idempotency

import it.polito.wa2.catalogue.application.exceptions.IdempotencyKeyInProgressException
import it.polito.wa2.catalogue.application.exceptions.IdempotencyKeyReusedException
import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyPort
import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyScope
import it.polito.wa2.catalogue.domain.sessions.Session
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant

/**
 * JPA-backed implementation of [IdempotencyPort] backed by `idempotency_records`.
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaIdempotencyRepositoryAdapter(
    private val idempotencyRepository: SpringDataIdempotencyRepository,
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : IdempotencyPort {

    override fun findResult(scope: IdempotencyScope, requestHash: String, notBefore: Instant): Session? {
        val existing = idempotencyRepository.findById(scope.toRecordId()).orElse(null)
            ?.takeIf { !it.createdAt.isBefore(notBefore) }
            ?: return null
        if (existing.requestHash != requestHash) {
            throw IdempotencyKeyReusedException(scope.idempotencyKey)
        }
        return objectMapper.readValue(existing.responseBody, SessionSnapshot::class.java).toDomain()
    }

    override fun saveResult(scope: IdempotencyScope, requestHash: String, result: Session, notBefore: Instant) {
        val recordId = scope.toRecordId()
        // An expired record with the same key must make room for the new one
        idempotencyRepository.findById(recordId).orElse(null)
            ?.takeIf { it.createdAt.isBefore(notBefore) }
            ?.let {
                idempotencyRepository.delete(it)
                idempotencyRepository.flush()
            }
        // persist() forces an INSERT, so a duplicate key fails instead of being merged into an UPDATE
        try {
            entityManager.persist(
                IdempotencyRecordEntity(
                    id = recordId,
                    requestHash = requestHash,
                    responseBody = objectMapper.writeValueAsString(SessionSnapshot.of(result)),
                    createdAt = clock.instant()
                )
            )
            entityManager.flush()
        } catch (_: PersistenceException) {
            throw IdempotencyKeyInProgressException(scope.idempotencyKey)
        }
    }

    override fun purgeExpired(notBefore: Instant): Int =
        idempotencyRepository.deleteCreatedBefore(notBefore)

    private fun IdempotencyScope.toRecordId() =
        IdempotencyRecordId(callerId = callerId, endpoint = operation, idempotencyKey = idempotencyKey)
}

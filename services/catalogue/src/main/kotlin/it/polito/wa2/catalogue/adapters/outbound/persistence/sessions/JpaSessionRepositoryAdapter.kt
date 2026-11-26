package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import it.polito.wa2.catalogue.application.ports.outbound.SessionPort
import it.polito.wa2.catalogue.domain.sessions.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionNotFoundException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository
import java.time.Clock

/**
 * JPA-backed implementation of [SessionPort] for loading and saving [Session] aggregates.
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaSessionRepositoryAdapter(
    private val jpaRepository: SpringDataSessionRepository,
    private val clock: Clock
) : SessionPort {

    /**
     * Loads a [Session] by [sessionId], fetching price tiers and seat prices in two
     * separate queries to avoid a Cartesian product. Must run inside a transaction,
     * since both collections are populated in the same persistence context.
     */
    override fun findById(sessionId: SessionId): Session? {
        val sessionWithTiers = jpaRepository.findWithPriceTiersById(sessionId.value)
            ?: return null
        // Populate the second collection in the persistence context to avoid a Cartesian product
        jpaRepository.findWithSeatPricesById(sessionId.value)
        return sessionWithTiers.toDomain()
    }

    /**
     * Persists changes to an existing [Session] aggregate. The aggregate's [Session.version]
     * must match the stored one, otherwise [ConcurrentModificationException] is thrown.
     */
    override fun save(session: Session): Session {
        val entity = jpaRepository.findById(session.sessionId.value)
            .orElseThrow { SessionNotFoundException(session.sessionId) }
        if (entity.version != session.version) {
            throw concurrentModification(session)
        }
        try {
            entity.state = session.state
            entity.updatedAt = clock.instant()
            jpaRepository.saveAndFlush(entity)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw concurrentModification(session)
        }
        return findById(session.sessionId) ?: throw SessionNotFoundException(session.sessionId)
    }

    private fun concurrentModification(session: Session) =
        ConcurrentModificationException("Session '${session.sessionId.value}' was modified concurrently")
}

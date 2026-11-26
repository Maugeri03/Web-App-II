package it.polito.wa2.catalogue.persistence.sessions

import it.polito.wa2.catalogue.entities.sessions.Session
import it.polito.wa2.catalogue.entities.sessions.SessionId
import it.polito.wa2.catalogue.entities.sessions.SessionPort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * JPA-backed implementation of [SessionPort].
 */
@Repository
class JpaSessionRepositoryAdapter(
    private val jpaRepository: SpringDataSessionRepository
) : SessionPort {

    /**
     * Loads a [Session] by [sessionId], fetching price tiers and seat prices in two
     * separate queries to avoid a Cartesian product.
     */
    @Transactional(readOnly = true)
    override fun findById(sessionId: SessionId): Session? {
        val sessionWithTiers = jpaRepository.findWithPriceTiersById(sessionId.value)
            ?: return null
        // Populate the second collection in the persistence context to avoid a Cartesian product
        jpaRepository.findWithSeatPricesById(sessionId.value)
        return sessionWithTiers.toDomain()
    }
}
package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import it.polito.wa2.catalogue.application.ports.outbound.SessionPort
import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionNotFoundException
import jakarta.persistence.EntityManager
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository
import java.time.Clock

/**
 * JPA-backed implementation of [SessionPort] for loading and saving [Session] aggregates.
 * Joins the transaction of the calling use case: it never opens its own (L19).
 *
 * Price tiers and seat prices are written explicitly rather than through cascades: replacing them is a
 * delete of the old rows (seat prices first, since they reference the tiers) followed by an insert of the
 * new ones, which avoids Hibernate ordering inserts before the deletes of rows with the same key.
 */
@Repository
class JpaSessionRepositoryAdapter(
    private val jpaRepository: SpringDataSessionRepository,
    private val priceTierRepository: SpringDataSessionPriceTierRepository,
    private val seatPriceRepository: SpringDataSessionSeatPriceRepository,
    private val entityManager: EntityManager,
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

    override fun create(session: Session): Session {
        val now = clock.instant()
        // persist() forces an INSERT; merge() would first look for an existing row with the assigned id
        entityManager.persist(
            SessionEntity(
                sessionId = session.sessionId.value,
                eventId = session.eventId.value,
                organiserId = session.organiserId.value,
                venueId = session.venueId.value,
                planId = session.planId.value,
                state = session.state,
                startsAt = session.startsAt,
                endsAt = session.endsAt,
                timeZone = session.timeZone,
                startsLocal = session.startsLocal,
                salesOpenAt = session.salesOpenAt,
                salesCloseAt = session.salesCloseAt,
                version = session.version,
                createdAt = now,
                updatedAt = now
            )
        )
        insertPricing(session)
        return reload(session.sessionId)
    }

    /**
     * Persists changes to an existing [Session] aggregate. The aggregate's [Session.version]
     * must match the stored one, otherwise [ConcurrentModificationException] is thrown.
     * Every save advances the version, so the ETag of the session changes.
     */
    override fun save(session: Session): Session {
        val entity = jpaRepository.findWithPriceTiersById(session.sessionId.value)
            ?: throw SessionNotFoundException(session.sessionId)
        jpaRepository.findWithSeatPricesById(session.sessionId.value)
        if (entity.version != session.version) {
            throw concurrentModification(session)
        }
        val current = entity.toDomain()
        // Seat price rows also store the plan id, so a new plan rewrites them even with the same seat ids
        val pricingChanged = current.priceTiers != session.priceTiers ||
            current.seatPrices != session.seatPrices ||
            current.planId != session.planId
        try {
            entity.venueId = session.venueId.value
            entity.planId = session.planId.value
            entity.state = session.state
            entity.startsAt = session.startsAt
            entity.endsAt = session.endsAt
            entity.timeZone = session.timeZone
            entity.startsLocal = session.startsLocal
            entity.salesOpenAt = session.salesOpenAt
            entity.salesCloseAt = session.salesCloseAt
            entity.updatedAt = clock.instant()
            jpaRepository.saveAndFlush(entity)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw concurrentModification(session)
        }
        if (pricingChanged) {
            seatPriceRepository.deleteAllBySessionId(session.sessionId.value)
            priceTierRepository.deleteAllBySessionId(session.sessionId.value)
            insertPricing(session)
        }
        return reload(session.sessionId)
    }

    private fun insertPricing(session: Session) {
        val sessionId = session.sessionId.value
        // Tiers first: seat prices reference them through a foreign key
        session.priceTiers.forEach { tier ->
            entityManager.persist(
                SessionPriceTierEntity(
                    sessionId = sessionId,
                    priceTierId = tier.priceTierId,
                    name = tier.name,
                    amount = tier.price.formattedAmount(),
                    currency = tier.price.currency
                )
            )
        }
        entityManager.flush()
        session.seatPrices.forEach { seatPrice ->
            entityManager.persist(
                SessionSeatPriceEntity(
                    sessionId = sessionId,
                    seatId = seatPrice.seatId,
                    planId = session.planId.value,
                    priceTierId = seatPrice.priceTierId
                )
            )
        }
        entityManager.flush()
    }

    /** Re-reads the session from the database, so the collections reflect the rows just written. */
    private fun reload(sessionId: SessionId): Session {
        entityManager.flush()
        entityManager.clear()
        return findById(sessionId) ?: throw SessionNotFoundException(sessionId)
    }

    private fun concurrentModification(session: Session) =
        ConcurrentModificationException("Session '${session.sessionId.value}' was modified concurrently")
}

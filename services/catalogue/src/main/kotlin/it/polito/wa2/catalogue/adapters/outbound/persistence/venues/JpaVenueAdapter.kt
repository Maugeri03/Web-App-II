package it.polito.wa2.catalogue.adapters.outbound.persistence.venues

import it.polito.wa2.catalogue.application.ports.outbound.VenuePort
import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.Venue
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

/**
 * JPA-backed implementation of [VenuePort].
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaVenueAdapter(
    private val venueRepository: SpringDataVenueRepository,
    private val entityManager: EntityManager
) : VenuePort {

    override fun findById(venueId: VenueId): Venue? =
        venueRepository.findById(venueId.value).orElse(null)?.toDomain()

    override fun create(venue: Venue): Venue {
        // persist() forces an INSERT; merge() would first look for an existing row with the assigned id
        val entity = VenueEntity.of(venue)
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toDomain()
    }
}

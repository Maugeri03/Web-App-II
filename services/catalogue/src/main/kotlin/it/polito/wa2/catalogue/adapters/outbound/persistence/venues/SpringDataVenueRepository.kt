package it.polito.wa2.catalogue.adapters.outbound.persistence.venues

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data JPA repository for [VenueEntity]. */
interface SpringDataVenueRepository : JpaRepository<VenueEntity, String>

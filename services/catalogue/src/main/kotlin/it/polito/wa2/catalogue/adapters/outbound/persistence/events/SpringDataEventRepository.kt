package it.polito.wa2.catalogue.adapters.outbound.persistence.events

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data JPA repository for [EventEntity]. */
interface SpringDataEventRepository : JpaRepository<EventEntity, String>

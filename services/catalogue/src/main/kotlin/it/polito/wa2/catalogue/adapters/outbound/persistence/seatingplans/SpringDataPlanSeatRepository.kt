package it.polito.wa2.catalogue.adapters.outbound.persistence.seatingplans

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data JPA repository for [PlanSeatEntity]. */
interface SpringDataPlanSeatRepository : JpaRepository<PlanSeatEntity, PlanSeatId> {

    fun findByPlanIdOrderBySeatIdAsc(planId: String): List<PlanSeatEntity>
}

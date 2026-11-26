package it.polito.wa2.catalogue.adapters.outbound.persistence.seatingplans

import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import org.springframework.stereotype.Repository

/** JPA-backed implementation of [SeatingPlanPort] backed by `plan_seats`. */
@Repository
class JpaSeatingPlanAdapter(
    private val planSeatRepository: SpringDataPlanSeatRepository
) : SeatingPlanPort {

    override fun findSeats(planId: PlanId): List<PlanSeat> =
        planSeatRepository.findByPlanIdOrderBySeatIdAsc(planId.value).map { it.toDomain() }
}

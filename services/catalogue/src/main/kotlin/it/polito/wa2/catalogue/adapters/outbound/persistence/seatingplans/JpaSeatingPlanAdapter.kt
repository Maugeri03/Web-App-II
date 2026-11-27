package it.polito.wa2.catalogue.adapters.outbound.persistence.seatingplans

import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.domain.common.ResourceConflictException
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.VenueId
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.springframework.stereotype.Repository

/**
 * JPA-backed implementation of [SeatingPlanPort] backed by `seating_plans` and `plan_seats`.
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaSeatingPlanAdapter(
    private val seatingPlanRepository: SpringDataSeatingPlanRepository,
    private val planSeatRepository: SpringDataPlanSeatRepository,
    private val entityManager: EntityManager
) : SeatingPlanPort {

    override fun findSeats(planId: PlanId): List<PlanSeat> =
        planSeatRepository.findByPlanIdOrderBySeatIdAsc(planId.value).map { it.toDomain() }

    override fun findById(planId: PlanId): SeatingPlan? {
        val plan = seatingPlanRepository.findById(planId.value).orElse(null) ?: return null
        return SeatingPlan(
            planId = PlanId(plan.planId),
            venueId = VenueId(plan.venueId),
            version = plan.version,
            name = plan.name,
            seats = findSeats(planId)
        )
    }

    override fun nextVersion(venueId: VenueId): Long =
        seatingPlanRepository.findMaxVersion(venueId.value) + 1

    override fun create(plan: SeatingPlan): SeatingPlan {
        try {
            entityManager.persist(
                SeatingPlanEntity(
                    planId = plan.planId.value,
                    venueId = plan.venueId.value,
                    version = plan.version,
                    name = plan.name
                )
            )
            plan.seats.forEach { seat ->
                entityManager.persist(
                    PlanSeatEntity(
                        planId = plan.planId.value,
                        seatId = seat.seatId,
                        sector = seat.sector,
                        row = seat.row,
                        number = seat.number
                    )
                )
            }
            entityManager.flush()
        } catch (_: PersistenceException) {
            // The only constraint the domain cannot check is the (venue_id, version) uniqueness
            // against a concurrent creation for the same venue
            throw ResourceConflictException(
                "Another seating plan was created concurrently for venue '${plan.venueId.value}'; retry the request"
            )
        }
        return findById(plan.planId) ?: error("Seating plan '${plan.planId.value}' not found after insert")
    }
}

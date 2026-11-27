package it.polito.wa2.catalogue.domain.seatingplans

import it.polito.wa2.catalogue.domain.common.ResourceNotFoundException
import it.polito.wa2.catalogue.domain.common.validate
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.VenueId

/**
 * Immutable seating layout of a venue. Once created it never changes: a new layout is a new plan
 * with the next version, so sessions keep the plan they were configured with.
 */
data class SeatingPlan(
    val planId: PlanId,
    val venueId: VenueId,
    val version: Long,
    val name: String,
    val seats: List<PlanSeat>
) {
    init {
        require(version >= 1) { "version must be >= 1" }
    }

    companion object {
        /**
         * Validates and creates a new plan: at least one seat, unique seat identifiers,
         * and unique physical labels (sector, row, number) within the plan.
         */
        fun create(planId: PlanId, venueId: VenueId, version: Long, name: String, seats: List<PlanSeat>): SeatingPlan {
            validate(name.isNotBlank()) { "Seating plan name must not be blank" }
            validate(seats.isNotEmpty()) { "A seating plan must contain at least one seat" }
            seats.groupBy { it.seatId }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { duplicate ->
                validate(false) { "Seat id '$duplicate' appears more than once in the plan" }
            }
            seats.groupBy { Triple(it.sector, it.row, it.number) }.filterValues { it.size > 1 }.keys.firstOrNull()
                ?.let { (sector, row, number) ->
                    validate(false) { "Seat label sector '$sector', row '$row', number '$number' appears more than once" }
                }
            return SeatingPlan(planId, venueId, version, name, seats)
        }
    }
}

/** Thrown when a seating plan does not exist (HTTP 404). */
class SeatingPlanNotFoundException(planId: PlanId) : ResourceNotFoundException("Seating plan", planId.value)

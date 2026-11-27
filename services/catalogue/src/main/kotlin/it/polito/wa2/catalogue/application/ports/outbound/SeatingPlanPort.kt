package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.VenueId

/**
 * Outbound persistence port for immutable seating plans and their seats.
 */
interface SeatingPlanPort {

    /** Returns all seats of [planId], ordered by seat identifier. */
    fun findSeats(planId: PlanId): List<PlanSeat>

    /** Finds a seating plan with its seats, or returns `null` if it does not exist. */
    fun findById(planId: PlanId): SeatingPlan?

    /** Returns the version the next plan of [venueId] must take (1 for the first plan). */
    fun nextVersion(venueId: VenueId): Long

    /**
     * Inserts a new plan with its seats and returns it as stored.
     * Throws `ResourceConflictException` if a concurrent request took the same version.
     */
    fun create(plan: SeatingPlan): SeatingPlan
}

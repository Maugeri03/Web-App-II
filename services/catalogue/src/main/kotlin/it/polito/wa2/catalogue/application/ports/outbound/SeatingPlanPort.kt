package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat

/**
 * Outbound persistence port for reading the seats of a seating plan.
 */
interface SeatingPlanPort {

    /** Returns all seats of [planId], ordered by seat identifier. */
    fun findSeats(planId: PlanId): List<PlanSeat>
}

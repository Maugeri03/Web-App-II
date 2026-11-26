package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.Session

/**
 * Outbound port towards Inventory & Orders, used to prepare the seat inventory of a published session.
 */
interface InventoryProvisioningPort {

    /**
     * Sends the frozen [session] configuration, together with the [seats] of its seating plan,
     * to Inventory & Orders. Must be idempotent: retries with the same configuration succeed.
     * Throws on any non-successful outcome.
     */
    fun provisionSessionInventory(session: Session, seats: List<PlanSeat>)
}

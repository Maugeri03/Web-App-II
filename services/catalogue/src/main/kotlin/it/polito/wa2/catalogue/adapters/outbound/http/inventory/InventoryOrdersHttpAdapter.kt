package it.polito.wa2.catalogue.adapters.outbound.http.inventory

import it.polito.wa2.catalogue.application.ports.outbound.InventoryProvisioningPort
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.Session
import org.springframework.stereotype.Component

/**
 * HTTP adapter implementing [InventoryProvisioningPort] through the declarative [InventoryOrdersApi] client.
 * Both `201 Created` and `200 OK` (identical retry) are success; any other status throws, and the
 * outbox worker retries with backoff (PUT is idempotent, so retrying is safe).
 */
@Component
class InventoryOrdersHttpAdapter(
    private val inventoryOrdersApi: InventoryOrdersApi
) : InventoryProvisioningPort {

    override fun provisionSessionInventory(session: Session, seats: List<PlanSeat>) {
        inventoryOrdersApi.provisionSessionInventory(session.sessionId.value, session.toProvisionRequest(seats))
    }

    private fun Session.toProvisionRequest(planSeats: List<PlanSeat>): ProvisionInventoryRequest {
        val tiersById = priceTiers.associateBy { it.priceTierId }
        val seatsById = planSeats.associateBy { it.seatId }
        return ProvisionInventoryRequest(
            configurationVersion = version,
            eventId = eventId.value,
            organiserId = organiserId.value,
            venueId = venueId.value,
            planId = planId.value,
            salesOpenAt = salesOpenAt,
            salesCloseAt = salesCloseAt,
            startsAt = startsAt,
            seats = seatPrices.map { seatPrice ->
                val seat = seatsById[seatPrice.seatId]
                    ?: error("Seat '${seatPrice.seatId}' does not belong to plan '${planId.value}'")
                val tier = tiersById[seatPrice.priceTierId]
                    ?: error("Price tier '${seatPrice.priceTierId}' not found for session '${sessionId.value}'")
                InventorySeatRequest(
                    seatId = seat.seatId,
                    sector = seat.sector,
                    row = seat.row,
                    number = seat.number,
                    priceTierId = tier.priceTierId,
                    unitPrice = MoneyRequest(tier.price.formattedAmount(), tier.price.currency)
                )
            }
        )
    }
}

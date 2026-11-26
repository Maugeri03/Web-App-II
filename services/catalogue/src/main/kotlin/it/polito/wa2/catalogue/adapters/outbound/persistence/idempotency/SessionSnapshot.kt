package it.polito.wa2.catalogue.adapters.outbound.persistence.idempotency

import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.Money
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PriceTier
import it.polito.wa2.catalogue.domain.sessions.SeatPrice
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionStatus
import it.polito.wa2.catalogue.domain.sessions.VenueId
import java.time.Instant

/**
 * Plain, serialization-friendly copy of a [Session], used by the persistence adapter to store the outcome of an idempotent
 * command as JSON and replay it later. Keeps serialisation out of the domain and of the application layer.
 */
data class SessionSnapshot(
    val sessionId: String,
    val eventId: String,
    val organiserId: String,
    val venueId: String,
    val planId: String,
    val state: SessionStatus,
    val startsAt: Instant,
    val endsAt: Instant?,
    val timeZone: String,
    val startsLocal: String,
    val salesOpenAt: Instant,
    val salesCloseAt: Instant,
    val priceTiers: List<PriceTierSnapshot>,
    val seatPrices: List<SeatPriceSnapshot>,
    val version: Long
) {
    data class PriceTierSnapshot(val priceTierId: String, val name: String, val amount: String, val currency: String)
    data class SeatPriceSnapshot(val seatId: String, val priceTierId: String)

    fun toDomain(): Session =
        Session(
            sessionId = SessionId(sessionId),
            eventId = EventId(eventId),
            organiserId = OrganiserId(organiserId),
            venueId = VenueId(venueId),
            planId = PlanId(planId),
            state = state,
            startsAt = startsAt,
            endsAt = endsAt,
            timeZone = timeZone,
            startsLocal = startsLocal,
            salesOpenAt = salesOpenAt,
            salesCloseAt = salesCloseAt,
            priceTiers = priceTiers.map { PriceTier(it.priceTierId, it.name, Money.parseEur(it.amount, it.currency)) },
            seatPrices = seatPrices.map { SeatPrice(it.seatId, it.priceTierId) },
            version = version
        )

    companion object {
        fun of(session: Session): SessionSnapshot =
            SessionSnapshot(
                sessionId = session.sessionId.value,
                eventId = session.eventId.value,
                organiserId = session.organiserId.value,
                venueId = session.venueId.value,
                planId = session.planId.value,
                state = session.state,
                startsAt = session.startsAt,
                endsAt = session.endsAt,
                timeZone = session.timeZone,
                startsLocal = session.startsLocal,
                salesOpenAt = session.salesOpenAt,
                salesCloseAt = session.salesCloseAt,
                priceTiers = session.priceTiers.map {
                    PriceTierSnapshot(it.priceTierId, it.name, it.price.formattedAmount(), it.price.currency)
                },
                seatPrices = session.seatPrices.map { SeatPriceSnapshot(it.seatId, it.priceTierId) },
                version = session.version
            )
    }
}

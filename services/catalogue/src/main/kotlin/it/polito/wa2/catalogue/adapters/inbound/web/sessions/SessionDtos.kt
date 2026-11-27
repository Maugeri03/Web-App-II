package it.polito.wa2.catalogue.adapters.inbound.web.sessions

import it.polito.wa2.catalogue.adapters.inbound.web.common.parseField
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseInstant
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseOffsetDateTime
import it.polito.wa2.catalogue.domain.sessions.Money
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PriceTier
import it.polito.wa2.catalogue.domain.sessions.SeatPrice
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionConfiguration
import it.polito.wa2.catalogue.domain.sessions.VenueId
import java.time.Instant

/**
 * DTO representing a monetary amount in a specific currency.
 */
data class MoneyResponseDto(
    val amount: String,
    val currency: String
)

/**
 * DTO representing a named price tier for a session.
 */
data class PriceTierResponseDto(
    val priceTierId: String,
    val name: String,
    val price: MoneyResponseDto
)

/**
 * DTO representing the assignment of a seat to a price tier.
 */
data class SeatPriceResponseDto(
    val seatId: String,
    val priceTierId: String
)

/**
 * DTO representing a scheduled session with its sales window and pricing configuration.
 */
data class SessionResponseDto(
    val sessionId: String,
    val eventId: String,
    val organiserId: String,
    val venueId: String,
    val planId: String,
    val state: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val timeZone: String,
    val startsLocal: String,
    val salesOpenAt: Instant,
    val salesCloseAt: Instant,
    val priceTiers: List<PriceTierResponseDto>,
    val seatPrices: List<SeatPriceResponseDto>,
    val version: Long
)

/**
 * Maps a domain [Money] value to its API DTO representation.
 */
fun Money.toDto(): MoneyResponseDto =
    MoneyResponseDto(
        amount = formattedAmount(),
        currency = currency
    )

/**
 * Maps a domain [PriceTier] to its API DTO representation.
 */
fun PriceTier.toDto(): PriceTierResponseDto =
    PriceTierResponseDto(
        priceTierId = priceTierId,
        name = name,
        price = price.toDto()
    )

/**
 * Maps a domain [SeatPrice] to its API DTO representation.
 */
fun SeatPrice.toDto(): SeatPriceResponseDto =
    SeatPriceResponseDto(
        seatId = seatId,
        priceTierId = priceTierId
    )

/**
 * Maps a domain [Session] aggregate to its API DTO representation.
 */
fun Session.toDto(): SessionResponseDto =
    SessionResponseDto(
        sessionId = sessionId.value,
        eventId = eventId.value,
        organiserId = organiserId.value,
        venueId = venueId.value,
        planId = planId.value,
        state = state.name,
        startsAt = startsAt,
        endsAt = endsAt,
        timeZone = timeZone,
        startsLocal = startsLocal,
        salesOpenAt = salesOpenAt,
        salesCloseAt = salesCloseAt,
        priceTiers = priceTiers.map { it.toDto() },
        seatPrices = seatPrices.map { it.toDto() },
        version = version
    )

/** Money in a request body (`Money`). */
data class MoneyRequestDto(
    val amount: String,
    val currency: String
)

/** Price tier in a request body (`PriceTier`). */
data class PriceTierRequestDto(
    val priceTierId: String,
    val name: String,
    val price: MoneyRequestDto
)

/** Seat-to-tier assignment in a request body (`SeatPriceAssignment`). */
data class SeatPriceRequestDto(
    val seatId: String,
    val priceTierId: String
)

/**
 * Request body of `POST /events/{eventId}/sessions` and `PUT /sessions/{sessionId}` (`SessionWriteRequest`).
 * Timestamps are read as text so that the offset of `startsAt`, which must match the venue time zone,
 * reaches the domain exactly as the client sent it.
 */
data class SessionWriteRequestDto(
    val venueId: String,
    val planId: String,
    val startsAt: String,
    val endsAt: String? = null,
    val salesOpenAt: String,
    val salesCloseAt: String,
    val priceTiers: List<PriceTierRequestDto>,
    val seatPrices: List<SeatPriceRequestDto>
) {
    fun toDomain(): SessionConfiguration =
        SessionConfiguration(
            venueId = parseField("venueId") { VenueId(venueId) },
            planId = parseField("planId") { PlanId(planId) },
            startsAt = parseOffsetDateTime("startsAt", startsAt),
            endsAt = endsAt?.let { parseOffsetDateTime("endsAt", it) },
            salesOpenAt = parseInstant("salesOpenAt", salesOpenAt),
            salesCloseAt = parseInstant("salesCloseAt", salesCloseAt),
            priceTiers = priceTiers.map {
                PriceTier(it.priceTierId, it.name, Money.parseEur(it.price.amount, it.price.currency))
            },
            seatPrices = seatPrices.map { SeatPrice(it.seatId, it.priceTierId) }
        )
}

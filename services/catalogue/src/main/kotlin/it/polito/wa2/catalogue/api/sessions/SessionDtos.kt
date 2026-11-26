package it.polito.wa2.catalogue.api.sessions

import it.polito.wa2.catalogue.entities.sessions.Money
import it.polito.wa2.catalogue.entities.sessions.PriceTier
import it.polito.wa2.catalogue.entities.sessions.SeatPrice
import it.polito.wa2.catalogue.entities.sessions.Session
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
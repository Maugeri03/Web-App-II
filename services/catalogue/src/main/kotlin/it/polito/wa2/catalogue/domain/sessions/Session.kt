package it.polito.wa2.catalogue.domain.sessions

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Strongly-typed identifier for a session. */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "sessionId must not be blank" }
    }
}

/** Strongly-typed identifier for an event. */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "eventId must not be blank" }
    }
}

/** Strongly-typed identifier for an organiser. */
@JvmInline
value class OrganiserId(val value: String) {
    init {
        require(value.isNotBlank()) { "organiserId must not be blank" }
    }
}

/** Strongly-typed identifier for a venue. */
@JvmInline
value class VenueId(val value: String) {
    init {
        require(value.isNotBlank()) { "venueId must not be blank" }
    }
}

/** Strongly-typed identifier for a seating plan. */
@JvmInline
value class PlanId(val value: String) {
    init {
        require(value.isNotBlank()) { "planId must not be blank" }
    }
}

/** Lifecycle state of a scheduled event session. */
enum class SessionStatus {
    DRAFT,
    PROVISIONING,
    READY,
    FAILED,
    CANCELLED
}

/**
 * Non-negative monetary value in EUR with exact two-decimal precision.
 */
data class Money(
    val amount: BigDecimal,
    val currency: String = "EUR"
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Money amount must be non-negative" }
        require(currency == "EUR") { "Unsupported currency: $currency" }
    }

    /** Returns the amount formatted as a plain decimal string with two fractional digits. */
    fun formattedAmount(): String =
        amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString()

    companion object {
        /** Parses a decimal string matching `^(0|[1-9][0-9]*)\.[0-9]{2}$` into a [Money] instance. */
        fun parseEur(rawAmount: String, currency: String = "EUR"): Money {
            val regex = Regex("^(0|[1-9][0-9]*)\\.[0-9]{2}$")
            require(regex.matches(rawAmount)) { "Invalid monetary amount format: $rawAmount" }
            return Money(BigDecimal(rawAmount), currency)
        }
    }
}

/** Named price tier within a session. */
data class PriceTier(
    val priceTierId: String,
    val name: String,
    val price: Money
) {
    init {
        require(priceTierId.isNotBlank()) { "priceTierId must not be blank" }
        require(name.isNotBlank()) { "Price tier name must not be blank" }
    }
}

/** Assignment of a physical seat to a price tier for a session. */
data class SeatPrice(
    val seatId: String,
    val priceTierId: String
) {
    init {
        require(seatId.isNotBlank()) { "seatId must not be blank" }
        require(priceTierId.isNotBlank()) { "priceTierId must not be blank" }
    }
}

/** Domain aggregate representing a scheduled event session and its pricing configuration. */
data class Session(
    val sessionId: SessionId,
    val eventId: EventId,
    val organiserId: OrganiserId,
    val venueId: VenueId,
    val planId: PlanId,
    val state: SessionStatus,
    val startsAt: Instant,
    val endsAt: Instant?,
    val timeZone: String,
    val startsLocal: String,
    val salesOpenAt: Instant,
    val salesCloseAt: Instant,
    val priceTiers: List<PriceTier>,
    val seatPrices: List<SeatPrice>,
    val version: Long
) {
    init {
        require(version >= 1) { "version must be >= 1" }
    }

    /**
     * Transitions the session from `DRAFT` (or a previous `FAILED` attempt)
     * into `PROVISIONING` so inventory initialization can proceed asynchronously.
     */
    fun startProvisioning(): Session {
        if (state != SessionStatus.DRAFT && state != SessionStatus.FAILED) {
            throw InvalidSessionStateTransitionException(sessionId, state, SessionStatus.PROVISIONING)
        }
        return copy(state = SessionStatus.PROVISIONING)
    }

    /**
     * Marks a `PROVISIONING` session as `READY` once Inventory & Orders has initialized its seats.
     */
    fun markReady(): Session {
        if (state != SessionStatus.PROVISIONING) {
            throw InvalidSessionStateTransitionException(sessionId, state, SessionStatus.READY)
        }
        return copy(state = SessionStatus.READY)
    }

    /**
     * Marks a `PROVISIONING` session as `FAILED` after exhausting provisioning retries.
     */
    fun markProvisioningFailed(): Session {
        if (state != SessionStatus.PROVISIONING) {
            throw InvalidSessionStateTransitionException(sessionId, state, SessionStatus.FAILED)
        }
        return copy(state = SessionStatus.FAILED)
    }
}
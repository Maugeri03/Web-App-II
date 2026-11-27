package it.polito.wa2.catalogue.domain.sessions

import it.polito.wa2.catalogue.domain.common.validate
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.venues.Venue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

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
        validate(amount >= BigDecimal.ZERO) { "Money amount must be non-negative" }
        validate(currency == "EUR") { "Unsupported currency: $currency" }
    }

    /** Returns the amount formatted as a plain decimal string with two fractional digits. */
    fun formattedAmount(): String =
        amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString()

    companion object {
        /** Parses a decimal string matching `^(0|[1-9][0-9]*)\.[0-9]{2}$` into a [Money] instance. */
        fun parseEur(rawAmount: String, currency: String = "EUR"): Money {
            val regex = Regex("^(0|[1-9][0-9]*)\\.[0-9]{2}$")
            validate(regex.matches(rawAmount)) { "Invalid monetary amount format: $rawAmount" }
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
        validate(priceTierId.isNotBlank()) { "priceTierId must not be blank" }
        validate(name.isNotBlank()) { "Price tier name must not be blank" }
    }
}

/** Assignment of a physical seat to a price tier for a session. */
data class SeatPrice(
    val seatId: String,
    val priceTierId: String
) {
    init {
        validate(seatId.isNotBlank()) { "seatId must not be blank" }
        validate(priceTierId.isNotBlank()) { "priceTierId must not be blank" }
    }
}

/**
 * Editable configuration of a draft session, as supplied by the organiser.
 * [startsAt] keeps the offset sent by the client, which must match the venue time zone.
 */
data class SessionConfiguration(
    val venueId: VenueId,
    val planId: PlanId,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime?,
    val salesOpenAt: Instant,
    val salesCloseAt: Instant,
    val priceTiers: List<PriceTier>,
    val seatPrices: List<SeatPrice>
) {
    /**
     * Checks the configuration against the selected [venue] and [plan]: the plan belongs to the venue,
     * the schedule and sales window are consistent, and every seat of the plan has exactly one existing tier.
     * Throws `DomainValidationException` (HTTP 422) on the first violation.
     */
    fun validateAgainst(venue: Venue, plan: SeatingPlan) {
        validate(venue.venueId == venueId) { "Venue '${venue.venueId.value}' does not match '${venueId.value}'" }
        validate(plan.planId == planId) { "Seating plan '${plan.planId.value}' does not match '${planId.value}'" }
        validate(plan.venueId == venueId) {
            "Seating plan '${planId.value}' does not belong to venue '${venueId.value}'"
        }

        val expectedOffset = venue.zoneId.rules.getOffset(startsAt.toInstant())
        validate(startsAt.offset == expectedOffset) {
            "startsAt offset ${startsAt.offset} does not match time zone ${venue.details.timeZone} " +
                "($expectedOffset) at the session start"
        }
        validate(endsAt == null || endsAt.toInstant().isAfter(startsAt.toInstant())) {
            "endsAt must be later than startsAt"
        }
        validate(salesOpenAt.isBefore(salesCloseAt)) { "salesOpenAt must be earlier than salesCloseAt" }
        validate(!salesCloseAt.isAfter(startsAt.toInstant())) { "salesCloseAt must not be later than startsAt" }

        validate(priceTiers.isNotEmpty()) { "At least one price tier is required" }
        firstDuplicate(priceTiers.map { it.priceTierId })?.let { duplicate ->
            validate(false) { "Price tier id '$duplicate' appears more than once" }
        }
        validate(seatPrices.isNotEmpty()) { "At least one seat price assignment is required" }
        firstDuplicate(seatPrices.map { it.seatId })?.let { duplicate ->
            validate(false) { "Seat '$duplicate' is assigned more than once" }
        }

        val tierIds = priceTiers.map { it.priceTierId }.toSet()
        seatPrices.firstOrNull { it.priceTierId !in tierIds }?.let { assignment ->
            validate(false) {
                "Seat '${assignment.seatId}' references unknown price tier '${assignment.priceTierId}'"
            }
        }

        val planSeatIds = plan.seats.map { it.seatId }.toSet()
        val assignedSeatIds = seatPrices.map { it.seatId }.toSet()
        (assignedSeatIds - planSeatIds).firstOrNull()?.let { unknown ->
            validate(false) { "Seat '$unknown' does not belong to seating plan '${planId.value}'" }
        }
        (planSeatIds - assignedSeatIds).firstOrNull()?.let { missing ->
            validate(false) { "Seat '$missing' of seating plan '${planId.value}' has no price tier" }
        }
    }

    private fun firstDuplicate(values: List<String>): String? =
        values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
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
     * Replaces the configuration of a `DRAFT` session after validating it against [venue] and [plan].
     * Published configuration is immutable, so any other state throws [SessionNotEditableException].
     */
    fun reconfigure(configuration: SessionConfiguration, venue: Venue, plan: SeatingPlan): Session {
        if (state != SessionStatus.DRAFT) {
            throw SessionNotEditableException(sessionId, state)
        }
        configuration.validateAgainst(venue, plan)
        return applying(configuration, venue)
    }

    private fun applying(configuration: SessionConfiguration, venue: Venue): Session =
        copy(
            venueId = configuration.venueId,
            planId = configuration.planId,
            startsAt = configuration.startsAt.toInstant(),
            endsAt = configuration.endsAt?.toInstant(),
            timeZone = venue.details.timeZone,
            startsLocal = localStart(configuration, venue),
            salesOpenAt = configuration.salesOpenAt,
            salesCloseAt = configuration.salesCloseAt,
            priceTiers = configuration.priceTiers.sortedBy { it.priceTierId },
            seatPrices = configuration.seatPrices.sortedBy { it.seatId }
        )

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

    companion object {
        /** Local wall-clock start, without offset (e.g. `2027-07-10T21:00:00`), always with seconds. */
        private val LOCAL_START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

        private fun localStart(configuration: SessionConfiguration, venue: Venue): String =
            configuration.startsAt.atZoneSameInstant(venue.zoneId).toLocalDateTime().format(LOCAL_START_FORMAT)

        /**
         * Creates a `DRAFT` session of [eventId] at version 1, after validating [configuration]
         * against the selected [venue] and [plan]. The time zone and local start are derived from the venue.
         */
        fun createDraft(
            sessionId: SessionId,
            eventId: EventId,
            organiserId: OrganiserId,
            configuration: SessionConfiguration,
            venue: Venue,
            plan: SeatingPlan
        ): Session {
            configuration.validateAgainst(venue, plan)
            val startsAt = configuration.startsAt.toInstant()
            return Session(
                sessionId = sessionId,
                eventId = eventId,
                organiserId = organiserId,
                venueId = configuration.venueId,
                planId = configuration.planId,
                state = SessionStatus.DRAFT,
                startsAt = startsAt,
                endsAt = configuration.endsAt?.toInstant(),
                timeZone = venue.details.timeZone,
                startsLocal = localStart(configuration, venue),
                salesOpenAt = configuration.salesOpenAt,
                salesCloseAt = configuration.salesCloseAt,
                priceTiers = configuration.priceTiers.sortedBy { it.priceTierId },
                seatPrices = configuration.seatPrices.sortedBy { it.seatId },
                version = 1
            )
        }
    }
}
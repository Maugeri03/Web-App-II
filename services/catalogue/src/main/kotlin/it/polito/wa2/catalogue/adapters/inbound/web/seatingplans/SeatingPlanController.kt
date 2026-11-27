package it.polito.wa2.catalogue.adapters.inbound.web.seatingplans

import it.polito.wa2.catalogue.adapters.inbound.web.common.ApiHeaders
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions.etagOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.callerIdOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.parseParameter
import it.polito.wa2.catalogue.application.seatingplans.SeatingPlanCommandService
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.VenueId
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

/** A numbered seat (`Seat`), used both in requests and in responses. */
data class SeatDto(
    val seatId: String,
    val sector: String,
    val row: String,
    val number: String
)

/** Request body of `POST /venues/{venueId}/seating-plans` (`SeatingPlanCreateRequest`). */
data class SeatingPlanCreateRequestDto(
    val name: String,
    val seats: List<SeatDto>
)

/** API representation of a seating plan (`SeatingPlan`). */
data class SeatingPlanResponseDto(
    val planId: String,
    val venueId: String,
    val version: Long,
    val name: String,
    val seats: List<SeatDto>
)

fun SeatingPlan.toDto() = SeatingPlanResponseDto(
    planId = planId.value,
    venueId = venueId.value,
    version = version,
    name = name,
    seats = seats.map { SeatDto(it.seatId, it.sector, it.row, it.number) }
)

/**
 * REST controller for seating plan management.
 */
@RestController
class SeatingPlanController(
    private val seatingPlanCommandService: SeatingPlanCommandService
) {

    /**
     * Creates an immutable seating plan for the venue, with the next version of that venue.
     * Requires `Idempotency-Key`; a retry of a completed identical request returns the stored `201` response.
     */
    @PostMapping(
        "/venues/{venueId}/seating-plans",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun createSeatingPlan(
        @PathVariable venueId: String,
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestBody body: SeatingPlanCreateRequestDto,
        principal: Principal?
    ): ResponseEntity<SeatingPlanResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val plan = seatingPlanCommandService.createSeatingPlan(
            callerId = callerIdOf(principal),
            venueId = parseParameter("venueId") { VenueId(venueId) },
            rawIdempotencyKey = idempotencyKey,
            name = body.name,
            seats = body.seats.map { PlanSeat(it.seatId, it.sector, it.row, it.number) }
        ).value
        return ResponseEntity
            .created(URI.create("/seating-plans/${plan.planId.value}"))
            .eTag(etagOf(plan.version))
            .cacheControl(CacheControl.noStore())
            .body(plan.toDto())
    }
}

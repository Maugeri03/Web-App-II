package it.polito.wa2.catalogue.adapters.inbound.web.venues

import it.polito.wa2.catalogue.adapters.inbound.web.common.ApiHeaders
import it.polito.wa2.catalogue.adapters.inbound.web.common.HttpPreconditions.etagOf
import it.polito.wa2.catalogue.adapters.inbound.web.common.callerIdOf
import it.polito.wa2.catalogue.application.venues.VenueCommandService
import it.polito.wa2.catalogue.domain.venues.Venue
import it.polito.wa2.catalogue.domain.venues.VenueDetails
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.Principal

/** Request body of `POST /venues` (`VenueWriteRequest`). */
data class VenueWriteRequestDto(
    val name: String,
    val address: String,
    val timeZone: String
) {
    fun toDomain() = VenueDetails(name = name, address = address, timeZone = timeZone)
}

/** API representation of a venue (`Venue`). */
data class VenueResponseDto(
    val venueId: String,
    val organiserId: String,
    val name: String,
    val address: String,
    val timeZone: String,
    val version: Long
)

fun Venue.toDto() = VenueResponseDto(
    venueId = venueId.value,
    organiserId = organiserId.value,
    name = details.name,
    address = details.address,
    timeZone = details.timeZone,
    version = version
)

/**
 * REST controller for venue management.
 */
@RestController
class VenueController(
    private val venueCommandService: VenueCommandService
) {

    /**
     * Creates a venue owned by the caller. Requires `Idempotency-Key`; a retry of a completed identical
     * request returns the stored `201` response.
     */
    @PostMapping("/venues", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createVenue(
        @RequestHeader(name = ApiHeaders.X_API_VERSION, required = false) apiVersion: String?,
        @RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
        @RequestBody body: VenueWriteRequestDto,
        principal: Principal?
    ): ResponseEntity<VenueResponseDto> {
        ApiHeaders.validateVersion(apiVersion)
        val venue = venueCommandService.createVenue(callerIdOf(principal), idempotencyKey, body.toDomain()).value
        return ResponseEntity
            .created(URI.create("/venues/${venue.venueId.value}"))
            .eTag(etagOf(venue.version))
            .cacheControl(CacheControl.noStore())
            .body(venue.toDto())
    }
}

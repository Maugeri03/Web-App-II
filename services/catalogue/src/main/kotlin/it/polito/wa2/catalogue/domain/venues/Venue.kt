package it.polito.wa2.catalogue.domain.venues

import it.polito.wa2.catalogue.domain.common.ResourceNotFoundException
import it.polito.wa2.catalogue.domain.common.validate
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.VenueId
import java.time.DateTimeException
import java.time.ZoneId

/** Editable details of a venue, as supplied on creation. */
data class VenueDetails(
    val name: String,
    val address: String,
    val timeZone: String
) {
    init {
        validate(name.isNotBlank()) { "Venue name must not be blank" }
        validate(address.isNotBlank()) { "Venue address must not be blank" }
        validate(isValidZone(timeZone)) { "Venue time zone '$timeZone' is not a valid IANA time zone" }
    }

    private companion object {
        /** Accepts region ids such as `Europe/Rome` only, not raw offsets like `+02:00`. */
        fun isValidZone(raw: String): Boolean =
            try {
                raw.contains('/') && ZoneId.of(raw).id == raw
            } catch (_: DateTimeException) {
                false
            }
    }
}

/** A venue managed by an organiser; sessions take their time zone from it. */
data class Venue(
    val venueId: VenueId,
    val organiserId: OrganiserId,
    val details: VenueDetails,
    val version: Long
) {
    init {
        require(version >= 1) { "version must be >= 1" }
    }

    val zoneId: ZoneId get() = ZoneId.of(details.timeZone)

    companion object {
        /** Creates a new venue at version 1. */
        fun create(venueId: VenueId, organiserId: OrganiserId, details: VenueDetails): Venue =
            Venue(venueId, organiserId, details, version = 1)
    }
}

/** Thrown when a venue does not exist (HTTP 404). */
class VenueNotFoundException(venueId: VenueId) : ResourceNotFoundException("Venue", venueId.value)

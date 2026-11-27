package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.Venue

/**
 * Outbound persistence port for [Venue] aggregates.
 */
interface VenuePort {

    /** Finds a venue by [venueId], or returns `null` if it does not exist. */
    fun findById(venueId: VenueId): Venue?

    /** Inserts a new venue and returns it as stored. */
    fun create(venue: Venue): Venue
}

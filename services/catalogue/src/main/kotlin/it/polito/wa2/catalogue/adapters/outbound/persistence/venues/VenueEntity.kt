package it.polito.wa2.catalogue.adapters.outbound.persistence.venues

import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.Venue
import it.polito.wa2.catalogue.domain.venues.VenueDetails
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/** JPA entity mapped to the `venues` table. */
@Entity
@Table(name = "venues")
class VenueEntity(
    @Id
    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: String,

    @Column(name = "organiser_id", nullable = false, updatable = false)
    val organiserId: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "address", nullable = false)
    var address: String,

    @Column(name = "time_zone", nullable = false)
    var timeZone: String,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 1
) {
    fun toDomain(): Venue =
        Venue(
            venueId = VenueId(venueId),
            organiserId = OrganiserId(organiserId),
            details = VenueDetails(name = name, address = address, timeZone = timeZone),
            version = version
        )

    override fun equals(other: Any?): Boolean = this === other || (other is VenueEntity && venueId == other.venueId)

    override fun hashCode(): Int = venueId.hashCode()

    companion object {
        fun of(venue: Venue): VenueEntity =
            VenueEntity(
                venueId = venue.venueId.value,
                organiserId = venue.organiserId.value,
                name = venue.details.name,
                address = venue.details.address,
                timeZone = venue.details.timeZone,
                version = venue.version
            )
    }
}

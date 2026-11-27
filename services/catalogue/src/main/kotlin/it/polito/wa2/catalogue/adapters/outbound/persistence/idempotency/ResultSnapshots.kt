package it.polito.wa2.catalogue.adapters.outbound.persistence.idempotency

import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventDetails
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.Venue
import it.polito.wa2.catalogue.domain.venues.VenueDetails
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KClass

/** Serialization-friendly copy of a [Venue]. */
data class VenueSnapshot(
    val venueId: String,
    val organiserId: String,
    val name: String,
    val address: String,
    val timeZone: String,
    val version: Long
) {
    fun toDomain() = Venue(VenueId(venueId), OrganiserId(organiserId), VenueDetails(name, address, timeZone), version)

    companion object {
        fun of(venue: Venue) = VenueSnapshot(
            venueId = venue.venueId.value,
            organiserId = venue.organiserId.value,
            name = venue.details.name,
            address = venue.details.address,
            timeZone = venue.details.timeZone,
            version = venue.version
        )
    }
}

/** Serialization-friendly copy of an [Event]. */
data class EventSnapshot(
    val eventId: String,
    val organiserId: String,
    val title: String,
    val description: String,
    val publicationState: EventPublicationState,
    val version: Long
) {
    fun toDomain() =
        Event(EventId(eventId), OrganiserId(organiserId), EventDetails(title, description, publicationState), version)

    companion object {
        fun of(event: Event) = EventSnapshot(
            eventId = event.eventId.value,
            organiserId = event.organiserId.value,
            title = event.details.title,
            description = event.details.description,
            publicationState = event.details.publicationState,
            version = event.version
        )
    }
}

/** Serialization-friendly copy of a [SeatingPlan]. */
data class SeatingPlanSnapshot(
    val planId: String,
    val venueId: String,
    val version: Long,
    val name: String,
    val seats: List<SeatSnapshot>
) {
    data class SeatSnapshot(val seatId: String, val sector: String, val row: String, val number: String)

    fun toDomain() = SeatingPlan(
        planId = PlanId(planId),
        venueId = VenueId(venueId),
        version = version,
        name = name,
        seats = seats.map { PlanSeat(it.seatId, it.sector, it.row, it.number) }
    )

    companion object {
        fun of(plan: SeatingPlan) = SeatingPlanSnapshot(
            planId = plan.planId.value,
            venueId = plan.venueId.value,
            version = plan.version,
            name = plan.name,
            seats = plan.seats.map { SeatSnapshot(it.seatId, it.sector, it.row, it.number) }
        )
    }
}

/**
 * Converts the domain results of idempotent commands to and from the JSON stored in `idempotency_records`.
 * Snapshots keep serialization out of the domain, whose value classes are not meant for Jackson.
 */
internal object ResultSnapshots {

    fun toJson(objectMapper: ObjectMapper, result: Any): String {
        val snapshot: Any = when (result) {
            is Session -> SessionSnapshot.of(result)
            is Venue -> VenueSnapshot.of(result)
            is Event -> EventSnapshot.of(result)
            is SeatingPlan -> SeatingPlanSnapshot.of(result)
            else -> throw IllegalArgumentException("Unsupported idempotent result type ${result.javaClass.name}")
        }
        return objectMapper.writeValueAsString(snapshot)
    }

    fun <T : Any> fromJson(objectMapper: ObjectMapper, json: String, resultType: KClass<T>): T {
        val result: Any = when (resultType) {
            Session::class -> objectMapper.readValue(json, SessionSnapshot::class.java).toDomain()
            Venue::class -> objectMapper.readValue(json, VenueSnapshot::class.java).toDomain()
            Event::class -> objectMapper.readValue(json, EventSnapshot::class.java).toDomain()
            SeatingPlan::class -> objectMapper.readValue(json, SeatingPlanSnapshot::class.java).toDomain()
            else -> throw IllegalArgumentException("Unsupported idempotent result type ${resultType.qualifiedName}")
        }
        return resultType.java.cast(result)
    }
}

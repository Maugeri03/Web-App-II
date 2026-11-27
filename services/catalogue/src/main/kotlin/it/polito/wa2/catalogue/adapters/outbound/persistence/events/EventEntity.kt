package it.polito.wa2.catalogue.adapters.outbound.persistence.events

import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventDetails
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/** JPA entity mapped to the `events` table. */
@Entity
@Table(name = "events")
class EventEntity(
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: String,

    @Column(name = "organiser_id", nullable = false, updatable = false)
    val organiserId: String,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "description", nullable = false)
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false)
    var publicationState: EventPublicationState,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 1
) {
    fun toDomain(): Event =
        Event(
            eventId = EventId(eventId),
            organiserId = OrganiserId(organiserId),
            details = EventDetails(title = title, description = description, publicationState = publicationState),
            version = version
        )

    override fun equals(other: Any?): Boolean = this === other || (other is EventEntity && eventId == other.eventId)

    override fun hashCode(): Int = eventId.hashCode()

    companion object {
        fun of(event: Event): EventEntity =
            EventEntity(
                eventId = event.eventId.value,
                organiserId = event.organiserId.value,
                title = event.details.title,
                description = event.details.description,
                publicationState = event.details.publicationState,
                version = event.version
            )
    }
}

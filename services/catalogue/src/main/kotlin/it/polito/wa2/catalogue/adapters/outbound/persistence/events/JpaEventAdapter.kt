package it.polito.wa2.catalogue.adapters.outbound.persistence.events

import it.polito.wa2.catalogue.application.ports.outbound.EventPort
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Read-only JPA entity mapped to the `events` table (only the columns needed so far). */
@Entity
@Table(name = "events")
class EventEntity(
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false)
    val publicationState: EventPublicationState
)

/** Spring Data JPA repository for [EventEntity]. */
interface SpringDataEventRepository : JpaRepository<EventEntity, String>

/** JPA-backed implementation of [EventPort]. */
@Repository
class JpaEventAdapter(
    private val eventRepository: SpringDataEventRepository
) : EventPort {

    override fun findPublicationState(eventId: EventId): EventPublicationState? =
        eventRepository.findById(eventId.value).orElse(null)?.publicationState
}

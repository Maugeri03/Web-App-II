package it.polito.wa2.catalogue.adapters.outbound.persistence.events

import it.polito.wa2.catalogue.application.ports.outbound.EventPort
import it.polito.wa2.catalogue.domain.common.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.events.Event
import it.polito.wa2.catalogue.domain.events.EventNotFoundException
import it.polito.wa2.catalogue.domain.events.EventPublicationState
import it.polito.wa2.catalogue.domain.sessions.EventId
import jakarta.persistence.EntityManager
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Repository

/**
 * JPA-backed implementation of [EventPort].
 * Joins the transaction of the calling use case: it never opens its own (L19).
 */
@Repository
class JpaEventAdapter(
    private val eventRepository: SpringDataEventRepository,
    private val entityManager: EntityManager
) : EventPort {

    override fun findPublicationState(eventId: EventId): EventPublicationState? =
        eventRepository.findById(eventId.value).orElse(null)?.publicationState

    override fun findById(eventId: EventId): Event? =
        eventRepository.findById(eventId.value).orElse(null)?.toDomain()

    override fun create(event: Event): Event {
        // persist() forces an INSERT; merge() would first look for an existing row with the assigned id
        val entity = EventEntity.of(event)
        entityManager.persist(entity)
        entityManager.flush()
        return entity.toDomain()
    }

    override fun save(event: Event): Event {
        val entity = eventRepository.findById(event.eventId.value)
            .orElseThrow { EventNotFoundException(event.eventId) }
        if (entity.version != event.version) {
            throw concurrentModification(event)
        }
        entity.title = event.details.title
        entity.description = event.details.description
        entity.publicationState = event.details.publicationState
        try {
            // An unchanged representation keeps its version (and ETag); a change advances it
            return eventRepository.saveAndFlush(entity).toDomain()
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw concurrentModification(event)
        }
    }

    private fun concurrentModification(event: Event) =
        ConcurrentModificationException("Event '${event.eventId.value}' was modified concurrently")
}

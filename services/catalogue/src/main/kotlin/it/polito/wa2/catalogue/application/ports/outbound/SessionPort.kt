package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId

/**
 * Outbound persistence port focused on querying and persisting [Session] aggregates.
 */
interface SessionPort {

    /** Finds a [Session] by its [SessionId], or returns `null` if not found. */
    fun findById(sessionId: SessionId): Session?

    /** Inserts a new [Session] with its price tiers and seat prices, and returns it as stored. */
    fun create(session: Session): Session

    /**
     * Persists an updated [Session] aggregate (state, schedule and pricing) and returns the saved domain state.
     * The aggregate's version must match the stored one, otherwise `ConcurrentModificationException` is thrown.
     */
    fun save(session: Session): Session
}

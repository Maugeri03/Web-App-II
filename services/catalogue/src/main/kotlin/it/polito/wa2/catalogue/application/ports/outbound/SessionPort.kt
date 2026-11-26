package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId

/**
 * Outbound persistence port focused on querying and persisting [Session] aggregates.
 */
interface SessionPort {

    /** Finds a [Session] by its [SessionId], or returns `null` if not found. */
    fun findById(sessionId: SessionId): Session?

    /** Persists an updated [Session] aggregate and returns the saved domain state. */
    fun save(session: Session): Session
}
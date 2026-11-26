package it.polito.wa2.catalogue.entities.sessions

/**
 * Outbound persistence port for querying [Session] aggregates.
 */
interface SessionPort {
    /**
     * Finds a [Session] by its [SessionId], or returns `null` if not found.
     */
    fun findById(sessionId: SessionId): Session?
}
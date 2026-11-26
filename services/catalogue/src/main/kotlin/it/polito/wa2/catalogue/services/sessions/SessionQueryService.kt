package it.polito.wa2.catalogue.application.sessions

import it.polito.wa2.catalogue.entities.sessions.Session
import it.polito.wa2.catalogue.entities.sessions.SessionId
import it.polito.wa2.catalogue.exceptions.SessionNotFoundException
import it.polito.wa2.catalogue.entities.sessions.SessionPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service handling read-only queries for sessions.
 */
@Service
class SessionQueryService(
    private val sessionPort: SessionPort
) {
    /**
     * Retrieves a [Session] by [sessionId] or throws [SessionNotFoundException] if missing.
     */
    @Transactional(readOnly = true)
    fun getSession(sessionId: SessionId): Session =
        sessionPort.findById(sessionId) ?: throw SessionNotFoundException(sessionId)
}
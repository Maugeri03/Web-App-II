package it.polito.wa2.catalogue.exceptions

import it.polito.wa2.catalogue.entities.sessions.SessionId

/**
 * Thrown when a requested session does not exist.
 */
class SessionNotFoundException(sessionId: SessionId) :
    RuntimeException("Session '${sessionId.value}' was not found")

/**
 * Thrown when the `X-API-Version` request header specifies an unsupported version.
 */
class UnsupportedApiVersionException(version: String) :
    RuntimeException("Unsupported X-API-Version '$version'. Supported version is 1.0")
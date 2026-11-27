package it.polito.wa2.catalogue.domain.sessions

import it.polito.wa2.catalogue.domain.common.DomainValidationException
import it.polito.wa2.catalogue.domain.common.ResourceConflictException
import it.polito.wa2.catalogue.domain.common.ResourceNotFoundException

/**
 * Thrown when a requested session cannot be found in the repository.
 */
class SessionNotFoundException(
    sessionId: SessionId
) : ResourceNotFoundException("Session", sessionId.value)

/**
 * Thrown when a state transition is not permitted from the current session status.
 */
class InvalidSessionStateTransitionException(
    sessionId: SessionId,
    currentStatus: SessionStatus,
    targetStatus: SessionStatus
) : RuntimeException(
    "Cannot move session '${sessionId.value}' from status '$currentStatus' to '$targetStatus'"
)

/**
 * Thrown when the configuration of a session that is no longer a `DRAFT` is modified (HTTP 409):
 * published configuration is immutable.
 */
class SessionNotEditableException(
    sessionId: SessionId,
    currentStatus: SessionStatus
) : ResourceConflictException(
    "Session '${sessionId.value}' is $currentStatus: only DRAFT sessions can be modified"
)

/**
 * Thrown when domain validation rules fail before creating or publishing a session.
 */
class SessionValidationException(
    message: String
) : DomainValidationException(message)

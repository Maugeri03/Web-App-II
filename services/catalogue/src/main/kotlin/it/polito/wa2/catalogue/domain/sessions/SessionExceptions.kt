package it.polito.wa2.catalogue.domain.sessions

/**
 * Thrown when a requested session cannot be found in the repository.
 */
class SessionNotFoundException(
    sessionId: SessionId
) : RuntimeException("Session with id '${sessionId.value}' was not found")

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
 * Thrown when domain validation rules fail before creating or publishing a session.
 */
class SessionValidationException(
    override val message: String
) : RuntimeException(message)

/**
 * Thrown when the expected session version (e.g. from `If-Match`) does not match the current one,
 * or when a concurrent update wins the optimistic lock.
 */
class ConcurrentModificationException(
    message: String
) : RuntimeException(message)

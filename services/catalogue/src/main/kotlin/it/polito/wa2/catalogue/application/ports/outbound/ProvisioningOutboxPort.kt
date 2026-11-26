package it.polito.wa2.catalogue.application.ports.outbound

import it.polito.wa2.catalogue.domain.sessions.SessionId

/** Processing lifecycle status of a transactional outbox entry. */
enum class OutboxStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Claimed outbox work item handed off to the background provisioning worker.
 */
data class ProvisioningOutboxTask(
    val outboxId: String,
    val sessionId: SessionId,
    val attemptCount: Int
)

/**
 * Outbound persistence port for enqueuing, claiming, and updating session provisioning outbox tasks.
 */
interface ProvisioningOutboxPort {

    /** Enqueues or resets a `PENDING` provisioning outbox task for [sessionId]. */
    fun enqueuePending(sessionId: SessionId)

    /**
     * Claims the next due task using `FOR UPDATE SKIP LOCKED` and transitions it to `IN_PROGRESS`
     * under a time-limited lease. Due tasks are `PENDING` ones whose retry time has come and
     * `IN_PROGRESS` ones whose lease expired (their worker was interrupted), so no task is ever stuck.
     */
    fun claimNextPending(): ProvisioningOutboxTask?

    /** Marks the outbox task [outboxId] as `COMPLETED`. */
    fun markCompleted(outboxId: String)

    /**
     * Records a failed provisioning attempt for [outboxId], scheduling a retry with backoff
     * or marking the task `FAILED` once [maxAttempts] is reached.
     */
    fun recordFailure(outboxId: String, errorMessage: String, maxAttempts: Int): OutboxStatus
}
package it.polito.wa2.catalogue.adapters.outbound.persistence.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Spring Data JPA repository for polling and updating session provisioning outbox tasks.
 */
interface SpringDataOutboxRepository : JpaRepository<SessionProvisioningOutboxEntity, String> {

    /** Finds the (unique) outbox task associated with a session. */
    fun findBySessionId(sessionId: String): SessionProvisioningOutboxEntity?

    /**
     * Atomically selects the next eligible task using PostgreSQL row-level locking with SKIP LOCKED
     * to safely support concurrent worker instances. Eligible tasks are PENDING tasks whose retry time
     * has come, and IN_PROGRESS tasks whose lease (stored in `next_attempt_at`) has expired because
     * the worker that claimed them crashed or was stopped.
     */
    @Query(
        value = """
            SELECT *
            FROM session_provisioning_outbox
            WHERE status IN ('PENDING', 'IN_PROGRESS')
              AND next_attempt_at <= now()
            ORDER BY next_attempt_at ASC, created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findNextPendingForUpdateSkipLocked(): SessionProvisioningOutboxEntity?
}

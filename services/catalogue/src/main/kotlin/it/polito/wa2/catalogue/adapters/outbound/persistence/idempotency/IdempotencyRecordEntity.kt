package it.polito.wa2.catalogue.adapters.outbound.persistence.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * Composite primary key for idempotency records scoped by caller, endpoint and client key.
 */
@Embeddable
data class IdempotencyRecordId(
    @Column(name = "caller_id", nullable = false)
    val callerId: String = "",

    @Column(name = "endpoint", nullable = false)
    val endpoint: String = "",

    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String = ""
) : Serializable

/**
 * JPA entity mapping the `idempotency_records` table, storing the serialised result of a command.
 */
@Entity
@Table(name = "idempotency_records")
class IdempotencyRecordEntity(
    @EmbeddedId
    val id: IdempotencyRecordId,

    @Column(name = "request_hash", nullable = false)
    val requestHash: String,

    @Column(name = "response_body", nullable = false)
    val responseBody: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant
)

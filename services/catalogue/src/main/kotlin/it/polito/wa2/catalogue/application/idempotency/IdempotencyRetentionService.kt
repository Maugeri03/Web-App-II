package it.polito.wa2.catalogue.application.idempotency

import it.polito.wa2.catalogue.application.ports.outbound.IdempotencyPort
import it.polito.wa2.catalogue.config.CatalogueProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Defines how long idempotency keys are retained (24 hours by default, as stated by the API contract)
 * and purges the expired ones.
 */
@Service
class IdempotencyRetentionService(
    private val idempotencyPort: IdempotencyPort,
    private val clock: Clock,
    properties: CatalogueProperties
) {
    private val logger = LoggerFactory.getLogger(IdempotencyRetentionService::class.java)
    private val retention: Duration = properties.idempotency.retention

    /** Oldest creation time a record may have to still be valid now. */
    fun validSince(): Instant = clock.instant().minus(retention)

    /** Deletes every expired idempotency record, in a single transaction. */
    @Transactional
    fun purgeExpired() {
        val removed = idempotencyPort.purgeExpired(validSince())
        if (removed > 0) {
            logger.info("Purged {} expired idempotency records", removed)
        }
    }
}

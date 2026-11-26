package it.polito.wa2.catalogue.adapters.inbound.scheduling

import it.polito.wa2.catalogue.application.idempotency.IdempotencyRetentionService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled trigger adapter that periodically removes expired idempotency records.
 */
@Component
class IdempotencyPurgeScheduler(
    private val retentionService: IdempotencyRetentionService
) {
    @Scheduled(fixedDelayString = "\${catalogue.idempotency.purge-fixed-delay-ms:3600000}")
    fun triggerPurge() {
        retentionService.purgeExpired()
    }
}

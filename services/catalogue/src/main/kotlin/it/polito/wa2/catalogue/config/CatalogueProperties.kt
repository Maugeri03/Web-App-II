package it.polito.wa2.catalogue.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Typed, validated configuration of the catalogue service (`catalogue.*`).
 * Invalid values make the application fail at start-up instead of at first use.
 */
@Validated
@ConfigurationProperties(prefix = "catalogue")
data class CatalogueProperties(
    @field:Valid val inventoryOrders: InventoryOrders,
    @field:Valid val provisioningWorker: ProvisioningWorker = ProvisioningWorker(),
    @field:Valid val idempotency: Idempotency = Idempotency()
) {
    /** Outbound client towards Inventory & Orders. Plain numbers are read as milliseconds. */
    data class InventoryOrders(
        @field:NotBlank val baseUrl: String,
        @DurationUnit(ChronoUnit.MILLIS) val connectTimeout: Duration = Duration.ofSeconds(2),
        @DurationUnit(ChronoUnit.MILLIS) val readTimeout: Duration = Duration.ofSeconds(5)
    )

    /** Background worker draining the provisioning outbox. */
    data class ProvisioningWorker(
        @field:Positive val fixedDelayMs: Long = 1000,
        @field:Positive val maxAttempts: Int = 5,
        /** Lease of a claimed task; must exceed the Inventory & Orders connect + read timeouts. */
        @DurationUnit(ChronoUnit.MILLIS) val lease: Duration = Duration.ofSeconds(30),
        /** Upper bound of the exponential backoff between attempts (full jitter is applied below it). */
        @DurationUnit(ChronoUnit.MILLIS) val maxBackoff: Duration = Duration.ofSeconds(60)
    )

    /** Retention of idempotency keys (24 hours by contract) and purge frequency. */
    data class Idempotency(
        val retention: Duration = Duration.ofHours(24),
        @field:Positive val purgeFixedDelayMs: Long = 3_600_000
    )
}

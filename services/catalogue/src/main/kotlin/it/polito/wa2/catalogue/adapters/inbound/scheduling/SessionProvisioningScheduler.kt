package it.polito.wa2.catalogue.adapters.inbound.scheduling

import it.polito.wa2.catalogue.application.sessions.SessionProvisioningService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled trigger adapter that periodically invokes [SessionProvisioningService]
 * to drain pending session provisioning tasks from the outbox.
 */
@Component
class SessionProvisioningScheduler(
    private val provisioningService: SessionProvisioningService
) {
    /** Triggers a polling pass over due outbox provisioning entries. */
    @Scheduled(fixedDelayString = "\${catalogue.provisioning-worker.fixed-delay-ms:1000}")
    fun triggerProvisioningPoll() {
        provisioningService.processPendingTasks()
    }
}
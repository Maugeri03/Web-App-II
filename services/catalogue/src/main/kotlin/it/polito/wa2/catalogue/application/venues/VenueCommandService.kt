package it.polito.wa2.catalogue.application.venues

import it.polito.wa2.catalogue.application.idempotency.IdempotentCommandRunner
import it.polito.wa2.catalogue.application.idempotency.IdempotentResult
import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import it.polito.wa2.catalogue.application.ports.outbound.VenuePort
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.Venue
import it.polito.wa2.catalogue.domain.venues.VenueDetails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for venue commands.
 */
@Service
class VenueCommandService(
    private val venuePort: VenuePort,
    private val idGenerator: IdGenerator,
    private val idempotentCommands: IdempotentCommandRunner
) {

    /**
     * Creates a venue owned by [callerId], idempotently under [rawIdempotencyKey].
     * The organiser is derived from the caller, never from the request body.
     */
    @Transactional
    fun createVenue(callerId: String, rawIdempotencyKey: String?, details: VenueDetails): IdempotentResult<Venue> {
        val scope = idempotentCommands.scope(callerId, "POST:/venues", rawIdempotencyKey)
        return idempotentCommands.run(scope, idempotentCommands.fingerprint(details), Venue::class) {
            venuePort.create(
                Venue.create(
                    venueId = VenueId(idGenerator.newId("ven")),
                    organiserId = OrganiserId(callerId),
                    details = details
                )
            )
        }
    }
}

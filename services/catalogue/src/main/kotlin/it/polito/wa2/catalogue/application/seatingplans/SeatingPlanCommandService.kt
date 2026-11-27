package it.polito.wa2.catalogue.application.seatingplans

import it.polito.wa2.catalogue.application.idempotency.IdempotentCommandRunner
import it.polito.wa2.catalogue.application.idempotency.IdempotentResult
import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import it.polito.wa2.catalogue.application.ports.outbound.SeatingPlanPort
import it.polito.wa2.catalogue.application.ports.outbound.VenuePort
import it.polito.wa2.catalogue.domain.seatingplans.SeatingPlan
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import it.polito.wa2.catalogue.domain.sessions.VenueId
import it.polito.wa2.catalogue.domain.venues.VenueNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service for seating plan commands.
 */
@Service
class SeatingPlanCommandService(
    private val venuePort: VenuePort,
    private val seatingPlanPort: SeatingPlanPort,
    private val idGenerator: IdGenerator,
    private val idempotentCommands: IdempotentCommandRunner
) {

    /**
     * Creates an immutable seating plan for [venueId] with the next version of that venue,
     * idempotently under [rawIdempotencyKey]. Throws [VenueNotFoundException] if the venue does not exist.
     */
    @Transactional
    fun createSeatingPlan(
        callerId: String,
        venueId: VenueId,
        rawIdempotencyKey: String?,
        name: String,
        seats: List<PlanSeat>
    ): IdempotentResult<SeatingPlan> {
        val scope = idempotentCommands.scope(callerId, "POST:/venues/${venueId.value}/seating-plans", rawIdempotencyKey)
        val requestHash = idempotentCommands.fingerprint(venueId.value, name, seats)
        return idempotentCommands.run(scope, requestHash, SeatingPlan::class) {
            venuePort.findById(venueId) ?: throw VenueNotFoundException(venueId)
            seatingPlanPort.create(
                SeatingPlan.create(
                    planId = PlanId(idGenerator.newId("plan")),
                    venueId = venueId,
                    version = seatingPlanPort.nextVersion(venueId),
                    name = name,
                    seats = seats
                )
            )
        }
    }
}

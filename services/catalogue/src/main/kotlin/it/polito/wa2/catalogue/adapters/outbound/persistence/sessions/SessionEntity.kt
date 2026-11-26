package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import it.polito.wa2.catalogue.domain.sessions.EventId
import it.polito.wa2.catalogue.domain.sessions.OrganiserId
import it.polito.wa2.catalogue.domain.sessions.PlanId
import it.polito.wa2.catalogue.domain.sessions.Session
import it.polito.wa2.catalogue.domain.sessions.SessionId
import it.polito.wa2.catalogue.domain.sessions.SessionStatus
import it.polito.wa2.catalogue.domain.sessions.VenueId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * JPA entity mapped to the `sessions` table.
 */
@Entity
@Table(name = "sessions")
class SessionEntity(
    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    val sessionId: String,

    @Column(name = "event_id", nullable = false)
    val eventId: String,

    @Column(name = "organiser_id", nullable = false)
    val organiserId: String,

    @Column(name = "venue_id", nullable = false)
    val venueId: String,

    @Column(name = "plan_id", nullable = false)
    val planId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    var state: SessionStatus,

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = true)
    val endsAt: Instant?,

    @Column(name = "time_zone", nullable = false)
    val timeZone: String,

    @Column(name = "starts_local", nullable = false)
    val startsLocal: String,

    @Column(name = "sales_open_at", nullable = false)
    val salesOpenAt: Instant,

    @Column(name = "sales_close_at", nullable = false)
    val salesCloseAt: Instant,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("priceTierId ASC")
    val priceTiers: MutableList<SessionPriceTierEntity> = mutableListOf(),

    @OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("seatId ASC")
    val seatPrices: MutableList<SessionSeatPriceEntity> = mutableListOf()
) {
    /** Converts this JPA entity into a [Session] domain aggregate. */
    fun toDomain(): Session =
        Session(
            sessionId = SessionId(sessionId),
            eventId = EventId(eventId),
            organiserId = OrganiserId(organiserId),
            venueId = VenueId(venueId),
            planId = PlanId(planId),
            state = state,
            startsAt = startsAt,
            endsAt = endsAt,
            timeZone = timeZone,
            startsLocal = startsLocal,
            salesOpenAt = salesOpenAt,
            salesCloseAt = salesCloseAt,
            priceTiers = priceTiers.map { it.toDomain() },
            seatPrices = seatPrices.map { it.toDomain() },
            version = version
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionEntity) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()
}
package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import it.polito.wa2.catalogue.domain.sessions.SeatPrice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

/** Composite primary key for [SessionSeatPriceEntity]. */
data class SessionSeatPriceId(
    val sessionId: String = "",
    val seatId: String = ""
) : Serializable

/** JPA entity mapped to the `session_seat_prices` table. */
@Entity
@Table(name = "session_seat_prices")
@IdClass(SessionSeatPriceId::class)
class SessionSeatPriceEntity(
    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    val sessionId: String,

    @Id
    @Column(name = "seat_id", nullable = false, updatable = false)
    val seatId: String,

    @Column(name = "plan_id", nullable = false)
    val planId: String,

    @Column(name = "price_tier_id", nullable = false)
    val priceTierId: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    val session: SessionEntity? = null
) {
    /** Converts this JPA entity into a [SeatPrice] domain value object. */
    fun toDomain(): SeatPrice =
        SeatPrice(
            seatId = seatId,
            priceTierId = priceTierId
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionSeatPriceEntity) return false
        return sessionId == other.sessionId && seatId == other.seatId
    }

    override fun hashCode(): Int = 31 * sessionId.hashCode() + seatId.hashCode()
}
package it.polito.wa2.catalogue.adapters.outbound.persistence.seatingplans

import it.polito.wa2.catalogue.domain.sessions.PlanSeat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/** Composite primary key for [PlanSeatEntity]. */
data class PlanSeatId(
    val planId: String = "",
    val seatId: String = ""
) : Serializable

/** Read-only JPA entity mapped to the `plan_seats` table. */
@Entity
@Table(name = "plan_seats")
@IdClass(PlanSeatId::class)
class PlanSeatEntity(
    @Id
    @Column(name = "plan_id", nullable = false, updatable = false)
    val planId: String,

    @Id
    @Column(name = "seat_id", nullable = false, updatable = false)
    val seatId: String,

    @Column(name = "sector", nullable = false)
    val sector: String,

    // "row" is a reserved word in PostgreSQL, so the column name must be quoted
    @Column(name = "\"row\"", nullable = false)
    val row: String,

    @Column(name = "number", nullable = false)
    val number: String
) {
    /** Converts this JPA entity into a [PlanSeat] domain value object. */
    fun toDomain(): PlanSeat = PlanSeat(seatId = seatId, sector = sector, row = row, number = number)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlanSeatEntity) return false
        return planId == other.planId && seatId == other.seatId
    }

    override fun hashCode(): Int = 31 * planId.hashCode() + seatId.hashCode()
}

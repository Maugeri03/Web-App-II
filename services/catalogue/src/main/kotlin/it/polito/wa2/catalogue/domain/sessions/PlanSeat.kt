package it.polito.wa2.catalogue.domain.sessions

import it.polito.wa2.catalogue.domain.common.validate

/** Physical seat of a seating plan, identified by its location inside the venue. */
data class PlanSeat(
    val seatId: String,
    val sector: String,
    val row: String,
    val number: String
) {
    init {
        validate(seatId.isNotBlank()) { "seatId must not be blank" }
        validate(sector.isNotBlank()) { "Seat '$seatId' must have a non-blank sector" }
        validate(row.isNotBlank()) { "Seat '$seatId' must have a non-blank row" }
        validate(number.isNotBlank()) { "Seat '$seatId' must have a non-blank number" }
    }
}

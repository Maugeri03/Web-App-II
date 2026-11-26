package it.polito.wa2.catalogue.domain.sessions

/** Physical seat of a seating plan, identified by its location inside the venue. */
data class PlanSeat(
    val seatId: String,
    val sector: String,
    val row: String,
    val number: String
) {
    init {
        require(seatId.isNotBlank()) { "seatId must not be blank" }
    }
}

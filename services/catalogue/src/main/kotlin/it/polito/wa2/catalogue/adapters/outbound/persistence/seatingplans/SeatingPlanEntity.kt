package it.polito.wa2.catalogue.adapters.outbound.persistence.seatingplans

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA entity mapped to the `seating_plans` table. Plans are immutable: [version] is the plan's
 * sequence number within its venue, assigned once on creation, not an optimistic-locking counter.
 */
@Entity
@Table(name = "seating_plans")
class SeatingPlanEntity(
    @Id
    @Column(name = "plan_id", nullable = false, updatable = false)
    val planId: String,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: String,

    @Column(name = "version", nullable = false, updatable = false)
    val version: Long,

    @Column(name = "name", nullable = false, updatable = false)
    val name: String
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is SeatingPlanEntity && planId == other.planId)

    override fun hashCode(): Int = planId.hashCode()
}

/** Spring Data JPA repository for [SeatingPlanEntity]. */
interface SpringDataSeatingPlanRepository : JpaRepository<SeatingPlanEntity, String> {

    /** Highest plan version of [venueId], or 0 if the venue has no plans yet. */
    @Query("select coalesce(max(p.version), 0) from SeatingPlanEntity p where p.venueId = :venueId")
    fun findMaxVersion(@Param("venueId") venueId: String): Long
}

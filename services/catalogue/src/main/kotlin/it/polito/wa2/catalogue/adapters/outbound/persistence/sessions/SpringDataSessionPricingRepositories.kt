package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Spring Data JPA repository for [SessionPriceTierEntity]. */
interface SpringDataSessionPriceTierRepository : JpaRepository<SessionPriceTierEntity, SessionPriceTierId> {

    /**
     * Bulk-deletes the price tiers of [sessionId]. Seat prices referencing them must be deleted first.
     * Pending changes are flushed before, and the persistence context is cleared after.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SessionPriceTierEntity t where t.sessionId = :sessionId")
    fun deleteAllBySessionId(@Param("sessionId") sessionId: String): Int
}

/** Spring Data JPA repository for [SessionSeatPriceEntity]. */
interface SpringDataSessionSeatPriceRepository : JpaRepository<SessionSeatPriceEntity, SessionSeatPriceId> {

    /**
     * Bulk-deletes the seat prices of [sessionId].
     * Pending changes are flushed before, and the persistence context is cleared after.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SessionSeatPriceEntity p where p.sessionId = :sessionId")
    fun deleteAllBySessionId(@Param("sessionId") sessionId: String): Int
}

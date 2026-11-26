package it.polito.wa2.catalogue.adapters.outbound.persistence.sessions

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Spring Data JPA repository for [SessionEntity].
 */
interface SpringDataSessionRepository : JpaRepository<SessionEntity, String> {

    /** Fetches a session along with its `priceTiers` collection. */
    @Query(
        """
        select distinct s
        from SessionEntity s
        left join fetch s.priceTiers
        where s.sessionId = :sessionId
        """
    )
    fun findWithPriceTiersById(@Param("sessionId") sessionId: String): SessionEntity?

    /** Fetches a session along with its `seatPrices` collection. */
    @Query(
        """
        select distinct s
        from SessionEntity s
        left join fetch s.seatPrices
        where s.sessionId = :sessionId
        """
    )
    fun findWithSeatPricesById(@Param("sessionId") sessionId: String): SessionEntity?
}
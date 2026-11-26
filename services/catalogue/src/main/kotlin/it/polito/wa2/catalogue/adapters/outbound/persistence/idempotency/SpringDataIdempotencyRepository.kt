package it.polito.wa2.catalogue.adapters.outbound.persistence.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Spring Data JPA repository for [IdempotencyRecordEntity].
 */
interface SpringDataIdempotencyRepository : JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    /** Bulk-deletes every record created before [notBefore]. */
    @Modifying
    @Query("delete from IdempotencyRecordEntity r where r.createdAt < :notBefore")
    fun deleteCreatedBefore(@Param("notBefore") notBefore: Instant): Int
}

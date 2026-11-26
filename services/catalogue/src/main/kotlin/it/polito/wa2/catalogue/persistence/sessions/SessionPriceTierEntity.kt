package it.polito.wa2.catalogue.persistence.sessions

import it.polito.wa2.catalogue.entities.sessions.Money
import it.polito.wa2.catalogue.entities.sessions.PriceTier
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

/** Composite primary key for [SessionPriceTierEntity]. */
data class SessionPriceTierId(
    val sessionId: String = "",
    val priceTierId: String = ""
) : Serializable

/** JPA entity mapped to the `session_price_tiers` table. */
@Entity
@Table(name = "session_price_tiers")
@IdClass(SessionPriceTierId::class)
class SessionPriceTierEntity(
    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    val sessionId: String,

    @Id
    @Column(name = "price_tier_id", nullable = false, updatable = false)
    val priceTierId: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "amount", nullable = false)
    val amount: String,

    @Column(name = "currency", nullable = false)
    val currency: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    val session: SessionEntity? = null
) {
    /** Converts this JPA entity into a [PriceTier] domain value object. */
    fun toDomain(): PriceTier =
        PriceTier(
            priceTierId = priceTierId,
            name = name,
            price = Money.parseEur(amount, currency)
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionPriceTierEntity) return false
        return sessionId == other.sessionId && priceTierId == other.priceTierId
    }

    override fun hashCode(): Int = 31 * sessionId.hashCode() + priceTierId.hashCode()
}
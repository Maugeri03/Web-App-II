package it.polito.wa2.catalogue.adapters.outbound.ids

import it.polito.wa2.catalogue.application.ports.outbound.IdGenerator
import org.springframework.stereotype.Component
import java.util.UUID

/** [IdGenerator] backed by random UUIDs. */
@Component
class UuidIdGenerator : IdGenerator {

    override fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
}

package it.polito.wa2.catalogue.application.ports.outbound

/**
 * Outbound port generating unique identifiers, injectable so tests can use predictable ones.
 */
fun interface IdGenerator {

    /** Returns a new unique identifier prefixed with [prefix] (e.g. `out_...`). */
    fun newId(prefix: String): String
}

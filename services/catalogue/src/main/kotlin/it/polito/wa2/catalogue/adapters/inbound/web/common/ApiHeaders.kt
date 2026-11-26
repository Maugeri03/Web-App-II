package it.polito.wa2.catalogue.adapters.inbound.web.common


/**
 * Constants and helpers for API-level HTTP headers.
 */
object ApiHeaders {
    const val X_API_VERSION = "X-API-Version"
    const val SUPPORTED_VERSION = "1.0"
    const val IDEMPOTENCY_KEY = "Idempotency-Key"

    /**
     * Validates that the incoming `X-API-Version` header matches [SUPPORTED_VERSION].
     * Defaults to [SUPPORTED_VERSION] when the header is omitted.
     */
    fun validateVersion(rawVersion: String?) {
        val version = rawVersion ?: SUPPORTED_VERSION
        if (version != SUPPORTED_VERSION) {
            throw UnsupportedApiVersionException(version)
        }
    }
}
package it.polito.wa2.catalogue.adapters.inbound.web.common

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Assigns a correlation identifier to every request: it reuses the caller's `X-Request-Id`
 * when valid, or generates one, exposes it in the MDC for every log line of the request,
 * and returns it in the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && it.all(::isSafeChar) }
            ?: UUID.randomUUID().toString()
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }

    /** Rejects caller-provided ids that could inject content into logs or headers. */
    private fun isSafeChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_' || c == '.'

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_MDC_KEY = "requestId"
        private const val MAX_LENGTH = 128
    }
}

package it.polito.wa2.catalogue.adapters.outbound.http.inventory

import it.polito.wa2.catalogue.config.CatalogueProperties
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.http.HttpClient

/**
 * Builds the [InventoryOrdersApi] client once: base URL and connect/read timeouts from typed configuration,
 * API version header, and propagation of the correlation id (L07, L13).
 */
@Configuration
class InventoryOrdersClientConfig {

    @Bean
    fun inventoryOrdersApi(properties: CatalogueProperties): InventoryOrdersApi {
        val config = properties.inventoryOrders
        val requestFactory = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(config.connectTimeout).build()
        ).apply { setReadTimeout(config.readTimeout) }

        val restClient = RestClient.builder()
            .baseUrl(config.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(API_VERSION_HEADER, API_VERSION)
            .requestInterceptor(correlationIdInterceptor())
            .build()

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(InventoryOrdersApi::class.java)
    }

    /** Copies the correlation id of the current request or task onto the outbound call. */
    private fun correlationIdInterceptor() = ClientHttpRequestInterceptor { request, body, execution ->
        MDC.get(REQUEST_ID_MDC_KEY)?.let { request.headers.set(REQUEST_ID_HEADER, it) }
        execution.execute(request, body)
    }

    private companion object {
        const val API_VERSION_HEADER = "X-API-Version"
        const val API_VERSION = "1.0"
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_MDC_KEY = "requestId"
    }
}

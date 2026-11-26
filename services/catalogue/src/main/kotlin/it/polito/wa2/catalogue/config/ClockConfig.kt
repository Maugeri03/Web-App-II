package it.polito.wa2.catalogue.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Provides the system clock as a bean, so every component reads time through an injectable
 * [Clock] that tests can replace with a fixed one.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

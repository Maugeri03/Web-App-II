package it.polito.wa2.catalogue

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<CatalogueApplication>().with(TestcontainersConfiguration::class).run(*args)
}

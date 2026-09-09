package br.com.tavaressan.cafey

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CafeyBackendApplication

fun main(args: Array<String>) {
	runApplication<CafeyBackendApplication>(*args)
}

package br.com.tavaressan.cafey

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<CafeyBackendApplication>().with(TestcontainersConfiguration::class).run(*args)
}

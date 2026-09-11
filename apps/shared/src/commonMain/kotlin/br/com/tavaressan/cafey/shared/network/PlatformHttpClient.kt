package br.com.tavaressan.cafey.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** Engine HTTP por plataforma: OkHttp no Android, CIO no Desktop, `Js` no Web (wasmJs). */
expect fun createPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

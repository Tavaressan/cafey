package br.com.tavaressan.cafey.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Origens autorizadas a fazer requisições cross-origin (CORS) à API — usadas pelo app Web (APP-08),
 * servido em uma origem distinta do backend (dev server do Kotlin/Wasm em desenvolvimento, domínio
 * publicado em produção).
 *
 * Lista explícita, nunca wildcard: a especificação de CORS proíbe combinar
 * `Access-Control-Allow-Origin: *` com `Access-Control-Allow-Credentials: true`, e o Spring rejeita
 * essa combinação em runtime. Configurável via `CAFEY_CORS_ALLOWED_ORIGINS` (lista separada por
 * vírgula) para que produção não dependa do valor de desenvolvimento hardcoded aqui.
 */
@ConfigurationProperties(prefix = "cafey.cors")
class CorsProperties(
    var allowedOrigins: List<String> = listOf("http://localhost:8081")
)

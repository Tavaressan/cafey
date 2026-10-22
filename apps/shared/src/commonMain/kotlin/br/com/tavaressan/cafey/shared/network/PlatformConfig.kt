package br.com.tavaressan.cafey.shared.network

/**
 * URL padrão do backend, por plataforma. No Android é a produção (`https://cafey-backend.duckdns.org`),
 * porque o app roda em aparelho físico; para o emulador, passe `backendBaseUrl=http://10.0.2.2:8080`
 * (ver `androidApp/build.gradle.kts`). Desktop e Web rodam no mesmo host do backend em dev, então
 * `localhost` funciona direto.
 */
expect val platformDefaultBaseUrl: String

package br.com.tavaressan.cafey.notification.fcm

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Campos relevantes do JSON da conta de serviço do Firebase (#362).
 * Lidos uma única vez na inicialização; a chave privada fica apenas neste objeto e nunca vai para logs.
 */
internal data class FcmCredenciais(
    @JsonProperty("project_id") val projectId: String,
    @JsonProperty("client_email") val clientEmail: String,
    /** Chave privada RSA PKCS#8 em formato PEM (`-----BEGIN PRIVATE KEY-----`). */
    @JsonProperty("private_key") val privateKey: String,
    @JsonProperty("token_uri") val tokenUri: String
)

package br.com.tavaressan.cafey.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Chaves RSA externas para assinatura/validação de access tokens JWT (RS256).
 *
 * Valores esperados em Base64: [privateKey] no formato PKCS#8 e [publicKey] no formato X.509.
 * Cabeçalhos PEM (`-----BEGIN...-----`/`-----END...-----`) são tolerados e removidos antes da
 * decodificação. Se ambas forem omitidas, [br.com.tavaressan.cafey.security.JwtTokenService] gera
 * um par efêmero (não sobrevive a restart). Se apenas uma for informada, o boot falha.
 */
@ConfigurationProperties(prefix = "cafey.jwt")
class JwtKeyProperties(
    var privateKey: String? = null,
    var publicKey: String? = null
)

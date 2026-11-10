package br.com.tavaressan.cafey.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuração de 2FA/TOTP (issue #260).
 *
 * [encryptionKey] é a chave AES-256 (Base64, 32 bytes decodificados) usada para cifrar o segredo
 * TOTP persistido em `usuarios.mfa_secret`. É deliberadamente separada do par RSA usado para JWT
 * ([JwtKeyProperties]): reutilizar o segredo do JWT como chave de cifra exigiria justificativa
 * explícita e mistura propósitos criptográficos distintos.
 *
 * Se omitida fora do perfil `prod`, [br.com.tavaressan.cafey.security.MfaCryptoService] gera uma
 * chave efêmera (segredos cifrados não sobrevivem a restart) — apenas para desenvolvimento/testes.
 * Em `prod`, a ausência falha o boot.
 */
@ConfigurationProperties(prefix = "cafey.mfa")
class MfaProperties(
    var encryptionKey: String? = null
)

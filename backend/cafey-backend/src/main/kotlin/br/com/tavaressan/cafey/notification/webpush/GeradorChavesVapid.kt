package br.com.tavaressan.cafey.notification.webpush

import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

/**
 * Utilitário para gerar o par VAPID (P-256) usado pelo Web Push (#363).
 * Imprime as chaves em Base64URL cru (sem padding), formato esperado por CAFEY_PUSH_VAPID_*.
 */
object GeradorChavesVapid {
    @JvmStatic
    fun main(args: Array<String>) {
        val par = KeyPairGenerator.getInstance("EC").apply { initialize(WebPushChaves.params) }.genKeyPair()
        val publica = WebPushChaves.publicaNaoComprimida(par.public as ECPublicKey)
        val privada = (par.private as ECPrivateKey).s.toByteArray().ajustar32()
        println("CAFEY_PUSH_VAPID_PUBLIC_KEY=${WebPushChaves.codificarBase64Url(publica)}")
        println("CAFEY_PUSH_VAPID_PRIVATE_KEY=${WebPushChaves.codificarBase64Url(privada)}")
        println("CAFEY_PUSH_VAPID_SUBJECT=mailto:ops@exemplo.com")
    }

    private fun ByteArray.ajustar32(): ByteArray = when {
        size == 32 -> this
        size == 33 && this[0] == 0.toByte() -> copyOfRange(1, 33)
        size < 32 -> ByteArray(32 - size) + this
        else -> takeLast(32).toByteArray()
    }
}

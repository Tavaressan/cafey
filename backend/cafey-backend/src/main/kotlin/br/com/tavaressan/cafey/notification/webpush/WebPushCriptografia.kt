package br.com.tavaressan.cafey.notification.webpush

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object WebPushCriptografia {
    private const val RS: Int = 4096
    private val RANDOM = SecureRandom()
    private val INFO_WEBPUSH = "WebPush: info\u0000".toByteArray(StandardCharsets.US_ASCII)
    private val CEK_INFO = "Content-Encoding: aes128gcm\u0000".toByteArray(StandardCharsets.US_ASCII)
    private val NONCE_INFO = "Content-Encoding: nonce\u0000".toByteArray(StandardCharsets.US_ASCII)

    data class EntradaCriptografia(
        val uaPublic: ByteArray,   // 65 bytes
        val authSecret: ByteArray,  // 16 bytes
        val plaintext: ByteArray,
        val salt: ByteArray? = null,
        val parEfemero: KeyPair? = null
    )

    fun criptografar(entrada: EntradaCriptografia): ByteArray {
        val salt = entrada.salt ?: ByteArray(16).also { RANDOM.nextBytes(it) }
        val asPair = entrada.parEfemero ?: gerarParEfemero()
        val asPublic = WebPushChaves.publicaNaoComprimida(asPair.public as ECPublicKey)

        val ikm = derivarIkm(entrada.authSecret, entrada.uaPublic, asPublic, asPair)
        val prk = hkdfExtract(salt, ikm)
        val cek = hkdfExpand(prk, CEK_INFO, 16)
        val nonce = hkdfExpand(prk, NONCE_INFO, 12)

        val padded = entrada.plaintext + byteArrayOf(0x02)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(padded)

        val header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.size)
            .put(salt)
            .putInt(RS)
            .put(asPublic.size.toByte())
            .put(asPublic)
            .array()
        return header + ciphertext
    }

    fun decifrar(uaPrivate: java.security.PrivateKey, uaPublic: ByteArray, authSecret: ByteArray, corpo: ByteArray): ByteArray {
        require(corpo.size > 86 + 16) { "Corpo aes128gcm inválido" }
        val buf = ByteBuffer.wrap(corpo)
        val salt = ByteArray(16).also { buf.get(it) }
        val rs = buf.int
        require(rs >= corpo.size) { "rs inválido para registro único" }
        val idlen = buf.get().toInt() and 0xFF
        val asPublic = ByteArray(idlen).also { buf.get(it) }
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val ikm = derivarIkmRecebedor(authSecret, uaPublic, asPublic, uaPrivate)
        val prk = hkdfExtract(salt, ikm)
        val cek = hkdfExpand(prk, CEK_INFO, 16)
        val nonce = hkdfExpand(prk, NONCE_INFO, 12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
        val padded = cipher.doFinal(ciphertext)
        val idx = padded.lastIndexOf(0x02.toByte())
        require(idx >= 0) { "Delimitador de padding ausente" }
        return padded.copyOfRange(0, idx)
    }

    private fun derivarIkm(authSecret: ByteArray, uaPublic: ByteArray, asPublic: ByteArray, asPair: KeyPair): ByteArray {
        val ecdh = ecdh(asPair.private, WebPushChaves.publicaDeBytes(uaPublic))
        val prkKey = hkdfExtract(authSecret, ecdh)
        val keyInfo = INFO_WEBPUSH + uaPublic + asPublic
        return hkdfExpand(prkKey, keyInfo, 32)
    }

    private fun derivarIkmRecebedor(authSecret: ByteArray, uaPublic: ByteArray, asPublic: ByteArray, uaPrivate: java.security.PrivateKey): ByteArray {
        val ecdh = ecdh(uaPrivate, WebPushChaves.publicaDeBytes(asPublic))
        val prkKey = hkdfExtract(authSecret, ecdh)
        val keyInfo = INFO_WEBPUSH + uaPublic + asPublic
        return hkdfExpand(prkKey, keyInfo, 32)
    }

    fun ecdh(privada: java.security.PrivateKey, publica: java.security.PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privada)
        ka.doPhase(publica, true)
        return ka.generateSecret()
    }

    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray = hmac(salt, ikm)

    fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val out = ByteArray(len)
        var t = ByteArray(0)
        var pos = 0
        var i = 1
        while (pos < len) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copiar = minOf(t.size, len - pos)
            System.arraycopy(t, 0, out, pos, copiar)
            pos += copiar
            i++
        }
        return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun gerarParEfemero(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(WebPushChaves.params, RANDOM) }.genKeyPair()
}

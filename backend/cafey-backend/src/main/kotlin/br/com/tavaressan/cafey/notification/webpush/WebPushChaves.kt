package br.com.tavaressan.cafey.notification.webpush

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64

object WebPushChaves {
    val params: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }
    private val keyFactory: KeyFactory = KeyFactory.getInstance("EC")

    fun decodificarBase64Url(valor: String): ByteArray =
        Base64.getUrlDecoder().decode(valor.trim().trimEnd('='))

    fun codificarBase64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun publicaNaoComprimida(chave: ECPublicKey): ByteArray = pontoParaBytes(chave.w)

    fun pontoParaBytes(ponto: ECPoint): ByteArray {
        val x = ajustar(ponto.affineX)
        val y = ajustar(ponto.affineY)
        val out = ByteArray(65)
        out[0] = 0x04
        System.arraycopy(x, 0, out, 1, 32)
        System.arraycopy(y, 0, out, 33, 32)
        return out
    }

    fun bytesParaPonto(bytes: ByteArray): ECPoint {
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "Ponto P-256 não comprimido inválido" }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        return ECPoint(x, y)
    }

    fun publicaDeBytes(bytes: ByteArray): ECPublicKey =
        keyFactory.generatePublic(ECPublicKeySpec(bytesParaPonto(bytes), params)) as ECPublicKey

    fun privadaDeBytes(bytes: ByteArray): ECPrivateKey {
        require(bytes.size == 32) { "Escalar P-256 inválido" }
        return keyFactory.generatePrivate(ECPrivateKeySpec(BigInteger(1, bytes), params)) as ECPrivateKey
    }

    fun parVapid(publicaB64: String, privadaB64: String): KeyPair? = runCatching {
        val pubBytes = decodificarBase64Url(publicaB64)
        val privBytes = decodificarBase64Url(privadaB64)
        if (pubBytes.size != 65 || pubBytes[0] != 0x04.toByte() || privBytes.size != 32) return null
        val publica = publicaDeBytes(pubBytes)
        val privada = privadaDeBytes(privBytes)
        if (!correspondem(privada, publica)) return null
        KeyPair(publica, privada)
    }.getOrNull()

    private fun ajustar(v: BigInteger): ByteArray {
        val bytes = v.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size == 33 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, 33)
            bytes.size < 32 -> ByteArray(32 - bytes.size) + bytes
            else -> bytes.takeLast(32).toByteArray()
        }
    }

    fun correspondem(privada: ECPrivateKey, publica: ECPublicKey): Boolean = runCatching {
        val sig = Signature.getInstance("SHA256withECDSAinP1363Format")
        val dados = "cafey-vapid-check".toByteArray()
        sig.initSign(privada)
        sig.update(dados)
        val assinatura = sig.sign()
        val ver = Signature.getInstance("SHA256withECDSAinP1363Format")
        ver.initVerify(publica)
        ver.update(dados)
        ver.verify(assinatura)
    }.getOrDefault(false)
}

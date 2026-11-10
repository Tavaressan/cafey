package br.com.tavaressan.cafey.security

import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP (RFC 6238) via JCE, sem dependência externa: HMAC-SHA1, 6 dígitos, passo de 30s, janela ±1.
 * Segredo gerado com 20 bytes (160 bits) e codificado em Base32 (padrão de apps autenticadores).
 */
@Service
class MfaTotpService(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    private val timeStepSeconds: Long = 30
    private val digits: Int = 6
    private val window: Int = 1

    fun generateSecretBase32(): String {
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return Base32.encode(bytes)
    }

    fun buildOtpAuthUri(secretBase32: String, email: String, issuer: String = "Cafey"): String {
        val label = URLEncoder.encode("$issuer:$email", Charsets.UTF_8)
        val encIssuer = URLEncoder.encode(issuer, Charsets.UTF_8)
        return "otpauth://totp/$label?secret=$secretBase32&issuer=$encIssuer&algorithm=SHA1&digits=$digits&period=$timeStepSeconds"
    }

    fun verifyCode(secretBase32: String, code: String, epochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val normalized = code.trim()
        if (!normalized.matches(Regex("^\\d{$digits}$"))) return false
        val key = Base32.decode(secretBase32)
        val counter = epochSeconds / timeStepSeconds
        for (offset in -window..window) {
            if (generateTotp(key, counter + offset) == normalized) return true
        }
        return false
    }

    fun generateCode(secretBase32: String, epochSeconds: Long = System.currentTimeMillis() / 1000): String {
        val key = Base32.decode(secretBase32)
        val counter = epochSeconds / timeStepSeconds
        return generateTotp(key, counter)
    }

    private fun generateTotp(key: ByteArray, counter: Long): String {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }
}

internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val output = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1f
                output.append(ALPHABET[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1f
            output.append(ALPHABET[index])
        }
        return output.toString()
    }

    fun decode(value: String): ByteArray {
        val cleaned = value.uppercase().replace("=", "").replace("\\s".toRegex(), "")
        if (cleaned.isEmpty()) return ByteArray(0)
        val output = ArrayList<Byte>(cleaned.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (c in cleaned) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "Caractere Base32 inválido: $c" }
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                val b = (buffer shr (bitsLeft - 8)) and 0xff
                output.add(b.toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }
}

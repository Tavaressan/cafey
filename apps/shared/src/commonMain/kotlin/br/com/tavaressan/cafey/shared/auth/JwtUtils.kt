package br.com.tavaressan.cafey.shared.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val jwtJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalEncodingApi::class)
fun jwtSubject(token: String?): String? {
    if (token.isNullOrBlank()) return null
    val parts = token.split('.')
    if (parts.size < 2) return null
    return try {
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1])
        val element = jwtJson.parseToJsonElement(payload.decodeToString())
        element.jsonObject["sub"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

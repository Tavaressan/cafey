package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Round-trip de (de)serialização dos modelos de domínio contra o shape real do backend. */
class SerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun authResponse_roundTrip() {
        val original = AuthResponse(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            tokenType = "Bearer",
            expiresIn = 900,
        )
        val encoded = json.encodeToString(AuthResponse.serializer(), original)
        val decoded = json.decodeFromString(AuthResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun authResponse_decodesBackendShape() {
        // Shape real de br.com.tavaressan.cafey.auth.AuthDto.AuthResponse.
        val backendJson = """
            {"accessToken":"a","refreshToken":"r","tokenType":"Bearer","expiresIn":900}
        """.trimIndent()
        val decoded = json.decodeFromString(AuthResponse.serializer(), backendJson)
        assertEquals("a", decoded.accessToken)
        assertEquals("r", decoded.refreshToken)
    }

    @Test
    fun dispositivoResponse_decodesBackendShape() {
        // Shape real de br.com.tavaressan.cafey.device.DeviceDto.DispositivoResponse.
        val backendJson = """
            {
              "id":"11111111-1111-1111-1111-111111111111",
              "nome":"Base da cozinha",
              "timezone":"America/Sao_Paulo",
              "estado":"OCIOSO",
              "online":true,
              "ultimoVisto":"2026-09-09T10:00:00Z",
              "versaoAgendamentos":1,
              "duracaoPreparoS":300,
              "limiarDescalcificacao":200,
              "contadorPreparos":34,
              "papel":"PROPRIETARIO",
              "criadoEm":"2026-01-01T00:00:00Z",
              "atualizadoEm":"2026-09-09T10:00:00Z"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(DispositivoResponse.serializer(), backendJson)
        assertEquals("Base da cozinha", decoded.nome)
        assertEquals(PapelDispositivo.PROPRIETARIO, decoded.papel)
        assertEquals(DeviceState.Idle, DeviceState.from(decoded.estado))
    }

    @Test
    fun deviceState_normalizesUnknownStrings() {
        assertEquals(DeviceState.Idle, DeviceState.from("desligado"))
        assertEquals(DeviceState.Brewing, DeviceState.from("PREPARANDO"))
        assertEquals(DeviceState.Error, DeviceState.from("erro"))
        assertEquals(DeviceState.Unknown("MANUTENCAO"), DeviceState.from("MANUTENCAO"))
    }
}

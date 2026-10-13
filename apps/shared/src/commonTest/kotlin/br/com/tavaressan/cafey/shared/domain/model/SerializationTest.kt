package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // Issue #189 — o JSON do app e o do backend não falham alto se o nome do campo divergir
    // (`ignoreUnknownKeys` aqui, Jackson ignora chaves desconhecidas lá): o campo só some.
    @Test
    fun criarAgendamentoRequest_encodesDuracaoPreparoSKey() {
        val request = CriarAgendamentoRequest(hora = "07:30", diasSemana = 62, duracaoPreparoS = 480)
        val encoded = json.encodeToString(CriarAgendamentoRequest.serializer(), request)
        assertTrue(""""duracaoPreparoS":480""" in encoded, encoded)
    }

    @Test
    fun atualizarAgendamentoRequest_encodesDuracaoPreparoSKey() {
        val request = AtualizarAgendamentoRequest(duracaoPreparoS = 360)
        val encoded = json.encodeToString(AtualizarAgendamentoRequest.serializer(), request)
        assertTrue(""""duracaoPreparoS":360""" in encoded, encoded)
    }

    @Test
    fun agendamentoResponse_decodesBackendShape() {
        // Shape real de br.com.tavaressan.cafey.schedule.AgendamentoResponse (ScheduleDto.kt).
        val comDuracao = """
            {
              "id":"22222222-2222-2222-2222-222222222222",
              "dispositivoId":"11111111-1111-1111-1111-111111111111",
              "hora":"07:00",
              "diasSemana":62,
              "ativo":true,
              "duracaoPreparoS":480,
              "criadoEm":"2026-09-09T10:00:00Z",
              "atualizadoEm":"2026-09-09T10:00:00Z"
            }
        """.trimIndent()
        assertEquals(480, json.decodeFromString(AgendamentoResponse.serializer(), comDuracao).duracaoPreparoS)

        // Agendamento antigo (V4) sem duração: o backend devolve null explícito.
        val semDuracao = comDuracao.replace(""""duracaoPreparoS":480""", """"duracaoPreparoS":null""")
        assertNull(json.decodeFromString(AgendamentoResponse.serializer(), semDuracao).duracaoPreparoS)
    }

    @Test
    fun deviceState_normalizesUnknownStrings() {
        assertEquals(DeviceState.Idle, DeviceState.from("desligado"))
        assertEquals(DeviceState.Brewing, DeviceState.from("PREPARANDO"))
        assertEquals(DeviceState.Error, DeviceState.from("erro"))
        assertEquals(DeviceState.Unknown("MANUTENCAO"), DeviceState.from("MANUTENCAO"))
    }
}

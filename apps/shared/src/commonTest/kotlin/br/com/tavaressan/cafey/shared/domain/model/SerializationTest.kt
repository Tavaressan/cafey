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

    @Test
    fun cuidadosResponse_decodesBackendShape() {
        // Shape de br.com.tavaressan.cafey.event.CuidadosResponse (GET /dispositivos/{id}/cuidados), conferido
        // contra CuidadosIntegrationTest (issue #190). Json estrito: nomes de campo, tipos e valores do enum
        // atuais precisam decodificar sem `ignoreUnknownKeys`. O literal é escrito à mão, então um campo novo
        // no backend não quebra este teste — ele trava só o contrato de hoje.
        val backendJson = """
            {
              "descalcificacao":{
                "contadorPreparos":60,
                "limiarDescalcificacao":400,
                "precisaDescalcificar":false,
                "percentualUso":15.0
              },
              "enxague":{
                "contadorPreparos":34,
                "limiarPreparos":40,
                "precisaAtencao":false,
                "percentualUso":85.0
              },
              "filtro":{
                "contadorPreparos":210,
                "limiarPreparos":300,
                "precisaAtencao":false,
                "percentualUso":70.0
              },
              "destaque":"ENXAGUE"
            }
        """.trimIndent()
        val decoded = Json.decodeFromString(CuidadosResponse.serializer(), backendJson)
        assertEquals(34, decoded.enxague.contadorPreparos)
        assertEquals(40, decoded.enxague.limiarPreparos)
        assertEquals(300, decoded.filtro.limiarPreparos)
        assertEquals(400, decoded.descalcificacao.limiarDescalcificacao)
        assertEquals(TipoCuidado.ENXAGUE, decoded.destaque)
    }

    @Test
    fun statusCuidadoResponse_decodesBaixaShape() {
        // Shape real de StatusCuidadoResponse (POST /dispositivos/{id}/enxague|filtro/baixa).
        val backendJson = """
            {"contadorPreparos":0,"limiarPreparos":300,"precisaAtencao":false,"percentualUso":0.0}
        """.trimIndent()
        val decoded = Json.decodeFromString(StatusCuidadoResponse.serializer(), backendJson)
        assertEquals(StatusCuidadoResponse(0, 300, false, 0.0), decoded)
    }
}

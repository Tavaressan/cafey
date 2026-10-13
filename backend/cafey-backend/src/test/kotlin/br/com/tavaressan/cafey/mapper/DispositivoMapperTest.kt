package br.com.tavaressan.cafey.mapper

import br.com.tavaressan.cafey.device.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.device.CriarDispositivoRequest
import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.PapelDispositivo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DispositivoMapperTest {

    private val mapper = DispositivoMapper()

    @Test
    fun `toEntity aplica os padroes quando o request traz so o nome`() {
        val entity = mapper.toEntity(CriarDispositivoRequest(nome = "Cafeteira"))

        assertEquals("Cafeteira", entity.nome)
        assertEquals("America/Sao_Paulo", entity.timezone)
        assertEquals(300, entity.duracaoPreparoS)
        assertEquals(200, entity.limiarDescalcificacao)
    }

    @Test
    fun `toEntity remove espacos das pontas de nome e timezone`() {
        val entity = mapper.toEntity(CriarDispositivoRequest(nome = "  Cafeteira  ", timezone = " America/Manaus "))

        assertEquals("Cafeteira", entity.nome)
        assertEquals("America/Manaus", entity.timezone)
    }

    @Test
    fun `toEntity usa duracao e limiar informados`() {
        val entity = mapper.toEntity(
            CriarDispositivoRequest(nome = "Cafeteira", duracaoPreparoS = 240, limiarDescalcificacao = 150)
        )

        assertEquals(240, entity.duracaoPreparoS)
        assertEquals(150, entity.limiarDescalcificacao)
    }

    @Test
    fun `atualizar nao altera os campos nulos do request`() {
        val entity = Dispositivo(
            nome = "Original", timezone = "America/Manaus", duracaoPreparoS = 240, limiarDescalcificacao = 150
        )
        val atualizadoEmAntes = Instant.parse("2020-01-01T00:00:00Z")
        entity.atualizadoEm = atualizadoEmAntes

        mapper.atualizar(entity, AtualizarDispositivoRequest())

        assertEquals("Original", entity.nome)
        assertEquals("America/Manaus", entity.timezone)
        assertEquals(240, entity.duracaoPreparoS)
        assertEquals(150, entity.limiarDescalcificacao)
        assertTrue(entity.atualizadoEm.isAfter(atualizadoEmAntes))
    }

    @Test
    fun `atualizar aplica so os campos informados e remove espacos`() {
        val entity = Dispositivo(nome = "Original", limiarDescalcificacao = 150)

        mapper.atualizar(entity, AtualizarDispositivoRequest(nome = "  Novo nome  ", timezone = " America/Manaus "))

        assertEquals("Novo nome", entity.nome)
        assertEquals("America/Manaus", entity.timezone)
        assertEquals(300, entity.duracaoPreparoS)
        assertEquals(150, entity.limiarDescalcificacao)
    }

    @Test
    fun `atualizar aplica duracao e limiar quando informados`() {
        val entity = Dispositivo(nome = "Original")

        mapper.atualizar(entity, AtualizarDispositivoRequest(duracaoPreparoS = 240, limiarDescalcificacao = 100))

        assertEquals("Original", entity.nome)
        assertEquals(240, entity.duracaoPreparoS)
        assertEquals(100, entity.limiarDescalcificacao)
    }

    @Test
    fun `toResponse preenche todos os campos`() {
        val id = UUID.randomUUID()
        val ultimoVisto = Instant.parse("2026-10-13T10:00:00Z")
        val criadoEm = Instant.parse("2026-10-01T10:00:00Z")
        val atualizadoEm = Instant.parse("2026-10-12T10:00:00Z")
        val entity = Dispositivo(
            id = id,
            nome = "Cafeteira",
            timezone = "America/Manaus",
            estado = "PREPARANDO",
            online = true,
            ultimoVisto = ultimoVisto,
            versaoAgendamentos = 4,
            duracaoPreparoS = 240,
            limiarDescalcificacao = 150,
            contadorPreparos = 17,
            criadoEm = criadoEm,
            atualizadoEm = atualizadoEm
        )

        val response = mapper.toResponse(entity, PapelDispositivo.CONVIDADO)

        assertEquals(id, response.id)
        assertEquals("Cafeteira", response.nome)
        assertEquals("America/Manaus", response.timezone)
        assertEquals("PREPARANDO", response.estado)
        assertTrue(response.online)
        assertEquals(ultimoVisto, response.ultimoVisto)
        assertEquals(4, response.versaoAgendamentos)
        assertEquals(240, response.duracaoPreparoS)
        assertEquals(150, response.limiarDescalcificacao)
        assertEquals(17, response.contadorPreparos)
        assertEquals(PapelDispositivo.CONVIDADO, response.papel)
        assertEquals(criadoEm, response.criadoEm)
        assertEquals(atualizadoEm, response.atualizadoEm)
    }

    @Test
    fun `toResponse mantem ultimoVisto nulo`() {
        val entity = Dispositivo(id = UUID.randomUUID(), nome = "Cafeteira")

        assertNull(mapper.toResponse(entity, PapelDispositivo.PROPRIETARIO).ultimoVisto)
    }
}

package br.com.tavaressan.cafey.mapper

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.schedule.Agendamento
import br.com.tavaressan.cafey.schedule.AtualizarAgendamentoRequest
import br.com.tavaressan.cafey.schedule.CriarAgendamentoRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class AgendamentoMapperTest {

    private val mapper = AgendamentoMapper()
    private val dispositivo = Dispositivo(id = UUID.randomUUID(), nome = "Cafeteira")

    @Test
    fun `toEntity converte o formato HH mm e vincula ao dispositivo`() {
        val entity = mapper.toEntity(
            CriarAgendamentoRequest(hora = "07:30", diasSemana = 62, ativo = false, duracaoPreparoS = 480),
            dispositivo
        )

        assertSame(dispositivo, entity.dispositivo)
        assertEquals(LocalTime.of(7, 30), entity.hora)
        assertEquals(62.toShort(), entity.diasSemana)
        assertFalse(entity.ativo)
        assertEquals(480, entity.duracaoPreparoS)
    }

    @Test
    fun `toEntity usa os padroes quando ativo e duracao sao omitidos`() {
        val entity = mapper.toEntity(CriarAgendamentoRequest(hora = "07:05", diasSemana = 1), dispositivo)

        assertEquals(LocalTime.of(7, 5), entity.hora)
        assertTrue(entity.ativo)
        assertNull(entity.duracaoPreparoS)
    }

    @Test
    fun `formato HH mm vai e volta sem perda`() {
        val entity = mapper.toEntity(CriarAgendamentoRequest(hora = "07:30", diasSemana = 62), dispositivo)
        entity.id = UUID.randomUUID()

        assertEquals("07:30", mapper.toResponse(entity).hora)
    }

    @Test
    fun `atualizar nao altera os campos nulos do request`() {
        val entity = Agendamento(
            dispositivo = dispositivo, hora = LocalTime.of(7, 0), diasSemana = 62, ativo = true, duracaoPreparoS = 360
        )
        val atualizadoEmAntes = Instant.parse("2020-01-01T00:00:00Z")
        entity.atualizadoEm = atualizadoEmAntes

        mapper.atualizar(entity, AtualizarAgendamentoRequest())

        assertEquals(LocalTime.of(7, 0), entity.hora)
        assertEquals(62.toShort(), entity.diasSemana)
        assertTrue(entity.ativo)
        assertEquals(360, entity.duracaoPreparoS)
        assertTrue(entity.atualizadoEm.isAfter(atualizadoEmAntes))
    }

    @Test
    fun `atualizar aplica so os campos informados`() {
        val entity = Agendamento(
            dispositivo = dispositivo, hora = LocalTime.of(7, 0), diasSemana = 62, ativo = true, duracaoPreparoS = 360
        )

        mapper.atualizar(entity, AtualizarAgendamentoRequest(hora = "08:15", ativo = false))

        assertEquals(LocalTime.of(8, 15), entity.hora)
        assertFalse(entity.ativo)
        assertEquals(62.toShort(), entity.diasSemana)
        assertEquals(360, entity.duracaoPreparoS)
    }

    @Test
    fun `atualizar aplica dias da semana e duracao quando informados`() {
        val entity = Agendamento(dispositivo = dispositivo, hora = LocalTime.of(7, 0), diasSemana = 62)

        mapper.atualizar(entity, AtualizarAgendamentoRequest(diasSemana = 127, duracaoPreparoS = 600))

        assertEquals(127.toShort(), entity.diasSemana)
        assertEquals(600, entity.duracaoPreparoS)
        assertEquals(LocalTime.of(7, 0), entity.hora)
    }

    @Test
    fun `toResponse preenche todos os campos`() {
        val id = UUID.randomUUID()
        val criadoEm = Instant.parse("2026-10-01T10:00:00Z")
        val atualizadoEm = Instant.parse("2026-10-12T10:00:00Z")
        val entity = Agendamento(
            id = id,
            dispositivo = dispositivo,
            hora = LocalTime.of(6, 45),
            diasSemana = 31,
            ativo = false,
            duracaoPreparoS = 480,
            criadoEm = criadoEm,
            atualizadoEm = atualizadoEm
        )

        val response = mapper.toResponse(entity)

        assertEquals(id, response.id)
        assertEquals(dispositivo.id, response.dispositivoId)
        assertEquals("06:45", response.hora)
        assertEquals(31.toShort(), response.diasSemana)
        assertFalse(response.ativo)
        assertEquals(480, response.duracaoPreparoS)
        assertEquals(criadoEm, response.criadoEm)
        assertEquals(atualizadoEm, response.atualizadoEm)
    }

    @Test
    fun `toResponse mantem duracao nula`() {
        val entity = Agendamento(
            id = UUID.randomUUID(), dispositivo = dispositivo, hora = LocalTime.of(7, 0), diasSemana = 62
        )

        assertNull(mapper.toResponse(entity).duracaoPreparoS)
    }
}

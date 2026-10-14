package br.com.tavaressan.cafey.schedule

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalTime
import java.util.UUID

@SpringBootTest
class AgendamentoRepositoryBuscaTest @Autowired constructor(
    private val agendamentoRepository: AgendamentoRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository
) {
    private lateinit var usuario: Usuario
    private lateinit var dispositivo: Dispositivo
    private lateinit var outroDispositivo: Dispositivo
    private lateinit var cafeDaManha: Agendamento
    private lateinit var cafeDaManhaTarde: Agendamento
    private lateinit var cafeDaManhaOutro: Agendamento
    private lateinit var semDescricao: Agendamento
    private lateinit var cafeDaManhaComAcento: Agendamento

    @BeforeEach
    fun setUp() {
        usuario = usuarioRepository.save(
            Usuario(nome = "Busca", email = "agenda-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(Dispositivo(nome = "Busca agenda"))
        outroDispositivo = dispositivoRepository.save(Dispositivo(nome = "Outra agenda"))
        usuarioDispositivoRepository.save(UsuarioDispositivo(usuario = usuario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO))
        usuarioDispositivoRepository.save(UsuarioDispositivo(usuario = usuario, dispositivo = outroDispositivo, papel = PapelDispositivo.PROPRIETARIO))
        cafeDaManha = salvar(dispositivo, "Cafe da manha", "07:00")
        cafeDaManhaTarde = salvar(dispositivo, "Cafe da manha", "08:00")
        cafeDaManhaOutro = salvar(outroDispositivo, "Cafe da manha", "06:00")
        semDescricao = salvar(dispositivo, null, "09:00")
        cafeDaManhaComAcento = salvar(dispositivo, "Cafe da manhã", "10:00")
    }

    @AfterEach
    fun tearDown() {
        listOf(dispositivo, outroDispositivo).forEach { disp ->
            agendamentoRepository.deleteAll(agendamentoRepository.findByDispositivoId(disp.id!!))
            usuarioDispositivoRepository.findByDispositivoId(disp.id!!).forEach(usuarioDispositivoRepository::delete)
            dispositivoRepository.delete(disp)
        }
        usuarioRepository.delete(usuario)
    }

    @Test
    fun `busca descricao parcial ignora caixa restringe dispositivo exclui nulos e ordena por hora`() {
        val resultado = agendamentoRepository.buscarPorDescricao(dispositivo.id!!, "Manha")
        assertEquals(listOf(cafeDaManha.id, cafeDaManhaTarde.id), resultado.map { it.id })
        assertTrue(resultado.none { it.id == cafeDaManhaOutro.id || it.id == semDescricao.id || it.id == cafeDaManhaComAcento.id })
        assertTrue(agendamentoRepository.buscarPorDescricao(dispositivo.id!!, "manha").none { it.id == cafeDaManhaComAcento.id })
    }

    private fun salvar(dispositivo: Dispositivo, descricao: String?, hora: String) = agendamentoRepository.save(
        Agendamento(dispositivo = dispositivo, hora = LocalTime.parse(hora), diasSemana = 127, descricao = descricao)
    )
}

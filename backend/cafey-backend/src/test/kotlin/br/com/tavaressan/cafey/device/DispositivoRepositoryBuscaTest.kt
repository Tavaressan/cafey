package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class DispositivoRepositoryBuscaTest @Autowired constructor(
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository
) {
    private lateinit var usuarioA: Usuario
    private lateinit var usuarioB: Usuario
    private lateinit var cozinha: Dispositivo
    private lateinit var escritorio: Dispositivo
    private lateinit var cozinhaB: Dispositivo

    @BeforeEach
    fun setUp() {
        usuarioA = criarUsuario("busca-a")
        usuarioB = criarUsuario("busca-b")
        cozinha = dispositivoRepository.save(Dispositivo(nome = "Cafeteira da Cozinha"))
        escritorio = dispositivoRepository.save(Dispositivo(nome = "Cafeteira do Escritório"))
        cozinhaB = dispositivoRepository.save(Dispositivo(nome = "Cafeteira da Cozinha 2"))
        vincular(usuarioA, cozinha, PapelDispositivo.PROPRIETARIO)
        vincular(usuarioA, escritorio, PapelDispositivo.PROPRIETARIO)
        vincular(usuarioB, cozinha, PapelDispositivo.CONVIDADO)
        vincular(usuarioB, cozinhaB, PapelDispositivo.PROPRIETARIO)
    }

    @AfterEach
    fun tearDown() {
        listOf(cozinha, escritorio, cozinhaB).forEach { dispositivo ->
            usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach(usuarioDispositivoRepository::delete)
            dispositivoRepository.delete(dispositivo)
        }
        usuarioRepository.delete(usuarioA)
        usuarioRepository.delete(usuarioB)
    }

    @Test
    fun `busca parcial ignora caixa respeita vinculos e ordena por nome`() {
        val resultadoA = dispositivoRepository.buscarPorNomeDoUsuario(usuarioA.id!!, "coz")
        val resultadoB = dispositivoRepository.buscarPorNomeDoUsuario(usuarioB.id!!, "COZ")

        assertEquals(listOf("Cafeteira da Cozinha"), resultadoA.map { it.nome })
        assertEquals(listOf("Cafeteira da Cozinha", "Cafeteira da Cozinha 2"), resultadoB.map { it.nome })
        assertEquals(
            listOf("Cafeteira da Cozinha", "Cafeteira da Cozinha 2", "Cafeteira do Escritório"),
            dispositivoRepository.buscarPorNomeDoUsuario(usuarioA.id!!, "Cafeteira").map { it.nome }
        )
        assertTrue(dispositivoRepository.buscarPorNomeDoUsuario(usuarioA.id!!, "sem resultado").isEmpty())
    }

    private fun criarUsuario(prefixo: String) = usuarioRepository.save(
        Usuario(nome = prefixo, email = "$prefixo-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
    )

    private fun vincular(usuario: Usuario, dispositivo: Dispositivo, papel: PapelDispositivo) {
        usuarioDispositivoRepository.save(UsuarioDispositivo(usuario = usuario, dispositivo = dispositivo, papel = papel))
    }
}

package br.com.cafey.device

import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.exception.ResourceNotFoundException
import br.com.cafey.user.Usuario
import br.com.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DispositivoServiceTest {

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private lateinit var service: DispositivoService

    private val ownerId = UUID.randomUUID()
    private val guestId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private lateinit var ownerUser: Usuario
    private lateinit var guestUser: Usuario
    private lateinit var device: Dispositivo
    private lateinit var ownerLink: UsuarioDispositivo
    private lateinit var guestLink: UsuarioDispositivo

    @BeforeEach
    fun setUp() {
        service = DispositivoService(dispositivoRepository, usuarioDispositivoRepository, usuarioRepository)

        ownerUser = Usuario(id = ownerId, nome = "Owner", email = "owner@cafey.com", senhaHash = "hash")
        guestUser = Usuario(id = guestId, nome = "Guest", email = "guest@cafey.com", senhaHash = "hash")
        device = Dispositivo(id = deviceId, nome = "Cafeteira Britânia", duracaoPreparoS = 300)

        ownerLink = UsuarioDispositivo(usuario = ownerUser, dispositivo = device, papel = PapelDispositivo.PROPRIETARIO)
        guestLink = UsuarioDispositivo(usuario = guestUser, dispositivo = device, papel = PapelDispositivo.CONVIDADO)
    }

    @Test
    fun `should create device as owner`() {
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer {
            val d = it.getArgument<Dispositivo>(0)
            d.id = deviceId
            d
        }
        `when`(usuarioDispositivoRepository.save(any(UsuarioDispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = CriarDispositivoRequest(nome = "Minha Cafeteira")
        val res = service.criar(req, ownerId)

        assertEquals("Minha Cafeteira", res.nome)
        assertEquals(PapelDispositivo.PROPRIETARIO, res.papel)
        verify(usuarioDispositivoRepository).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should list devices for user`() {
        `when`(usuarioDispositivoRepository.findByUsuarioId(ownerId)).thenReturn(listOf(ownerLink))

        val list = service.listarDoUsuario(ownerId)
        assertEquals(1, list.size)
        assertEquals(deviceId, list[0].id)
    }

    @Test
    fun `should update device when owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = AtualizarDispositivoRequest(nome = "Cafeteira Atualizada", duracaoPreparoS = 240)
        val res = service.atualizar(deviceId, req, ownerId)

        assertEquals("Cafeteira Atualizada", res.nome)
        assertEquals(240, res.duracaoPreparoS)
    }

    @Test
    fun `should reject update from guest`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        val req = AtualizarDispositivoRequest(nome = "Nome Inválido")
        assertThrows<BadCredentialsException> {
            service.atualizar(deviceId, req, guestId)
        }
    }

    @Test
    fun `should share device with guest by email`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioRepository.findByEmail("guest@cafey.com")).thenReturn(guestUser)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(null)
        `when`(usuarioDispositivoRepository.save(any(UsuarioDispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = CompartilharDispositivoRequest(email = "guest@cafey.com")
        val res = service.compartilhar(deviceId, req, ownerId)

        assertEquals(guestId, res.usuarioId)
        assertEquals(PapelDispositivo.CONVIDADO, res.papel)
    }

    @Test
    fun `should reject sharing with non-existing user`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioRepository.findByEmail("unknown@cafey.com")).thenReturn(null)

        val req = CompartilharDispositivoRequest(email = "unknown@cafey.com")
        assertThrows<ResourceNotFoundException> {
            service.compartilhar(deviceId, req, ownerId)
        }
    }

    @Test
    fun `should delete device when owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)

        service.excluir(deviceId, ownerId)
        verify(dispositivoRepository).delete(device)
    }

    @Test
    fun `should remove guest sharing`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        service.removerCompartilhamento(deviceId, guestId, ownerId)
        verify(usuarioDispositivoRepository).delete(guestLink)
    }
}

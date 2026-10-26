package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CompartilhamentoServiceTest {

    @Mock
    private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private lateinit var service: CompartilhamentoService

    private val ownerId = UUID.randomUUID()
    private val guestId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private lateinit var ownerUser: Usuario
    private lateinit var guestUser: Usuario
    private lateinit var otherUser: Usuario
    private lateinit var device: Dispositivo
    private lateinit var ownerLink: UsuarioDispositivo
    private lateinit var guestLink: UsuarioDispositivo

    @BeforeEach
    fun setUp() {
        service = CompartilhamentoService(usuarioDispositivoRepository, usuarioRepository)

        ownerUser = Usuario(id = ownerId, nome = "Owner", email = "owner@cafey.com", senhaHash = "hash")
        guestUser = Usuario(id = guestId, nome = "Guest", email = "guest@cafey.com", senhaHash = "hash")
        otherUser = Usuario(id = otherId, nome = "Other", email = "other@cafey.com", senhaHash = "hash")
        device = Dispositivo(id = deviceId, nome = "Cafeteira Britânia", duracaoPreparoS = 300)

        ownerLink = UsuarioDispositivo(usuario = ownerUser, dispositivo = device, papel = PapelDispositivo.PROPRIETARIO)
        guestLink = UsuarioDispositivo(usuario = guestUser, dispositivo = device, papel = PapelDispositivo.CONVIDADO)
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
        assertEquals("Guest", res.nome)
        assertEquals("guest@cafey.com", res.email)
        assertEquals(PapelDispositivo.CONVIDADO, res.papel)
        verify(usuarioDispositivoRepository).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should reject sharing when caller has no access`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(null)

        val req = CompartilharDispositivoRequest(email = "guest@cafey.com")
        assertThrows<ResourceNotFoundException> {
            service.compartilhar(deviceId, req, ownerId)
        }
    }

    @Test
    fun `should reject sharing when caller is not owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        val req = CompartilharDispositivoRequest(email = "other@cafey.com")
        assertThrows<BadCredentialsException> {
            service.compartilhar(deviceId, req, guestId)
        }
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
    fun `should reject sharing with oneself`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioRepository.findByEmail("owner@cafey.com")).thenReturn(ownerUser)

        val req = CompartilharDispositivoRequest(email = "owner@cafey.com")
        assertThrows<BadCredentialsException> {
            service.compartilhar(deviceId, req, ownerId)
        }
    }

    @Test
    fun `should reject sharing when user already has access`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioRepository.findByEmail("guest@cafey.com")).thenReturn(guestUser)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        val req = CompartilharDispositivoRequest(email = "guest@cafey.com")
        assertThrows<BadCredentialsException> {
            service.compartilhar(deviceId, req, ownerId)
        }
    }

    @Test
    fun `should list device shares when owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(listOf(ownerLink, guestLink))

        val result = service.listarCompartilhamentos(deviceId, ownerId)

        assertEquals(2, result.size)
        assertEquals(ownerId, result[0].usuarioId)
        assertEquals(guestId, result[1].usuarioId)
    }

    @Test
    fun `should reject listing shares when caller has no access`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.listarCompartilhamentos(deviceId, ownerId)
        }
    }

    @Test
    fun `should reject listing shares when caller is not owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        assertThrows<BadCredentialsException> {
            service.listarCompartilhamentos(deviceId, guestId)
        }
    }

    @Test
    fun `should remove guest sharing when caller is owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        service.removerCompartilhamento(deviceId, guestId, ownerId)
        verify(usuarioDispositivoRepository).delete(guestLink)
    }

    @Test
    fun `should allow guest to remove own sharing`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        service.removerCompartilhamento(deviceId, guestId, guestId)
        verify(usuarioDispositivoRepository).delete(guestLink)
    }

    @Test
    fun `should reject removing sharing when operator has no access`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(otherId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.removerCompartilhamento(deviceId, guestId, otherId)
        }
    }

    @Test
    fun `should reject removing sharing when operator is not owner and not the guest`() {
        val otherLink = UsuarioDispositivo(usuario = otherUser, dispositivo = device, papel = PapelDispositivo.CONVIDADO)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(otherId, deviceId)).thenReturn(otherLink)

        assertThrows<BadCredentialsException> {
            service.removerCompartilhamento(deviceId, guestId, otherId)
        }
    }

    @Test
    fun `should reject removing sharing when target sharing does not exist`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(otherId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.removerCompartilhamento(deviceId, otherId, ownerId)
        }
    }

    @Test
    fun `should reject removing owner link`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)

        assertThrows<BadCredentialsException> {
            service.removerCompartilhamento(deviceId, ownerId, ownerId)
        }
    }
}

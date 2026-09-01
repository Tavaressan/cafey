package br.com.cafey.device

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/dispositivos")
class DispositivoController(
    private val dispositivoService: DispositivoService
) {

    @GetMapping
    fun listar(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<DispositivoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivos = dispositivoService.listarDoUsuario(usuarioId)
        return ResponseEntity.ok(dispositivos)
    }

    @PostMapping
    fun criar(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CriarDispositivoRequest
    ): ResponseEntity<DispositivoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivo = dispositivoService.criar(request, usuarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(dispositivo)
    }

    @GetMapping("/{id}")
    fun obter(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<DispositivoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivo = dispositivoService.obterPorId(id, usuarioId)
        return ResponseEntity.ok(dispositivo)
    }

    @PutMapping("/{id}")
    fun atualizar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AtualizarDispositivoRequest
    ): ResponseEntity<DispositivoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivo = dispositivoService.atualizar(id, request, usuarioId)
        return ResponseEntity.ok(dispositivo)
    }

    @DeleteMapping("/{id}")
    fun excluir(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val usuarioId = UUID.fromString(jwt.subject)
        dispositivoService.excluir(id, usuarioId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/compartilhar")
    fun compartilhar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CompartilharDispositivoRequest
    ): ResponseEntity<CompartilhamentoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val compartilhamento = dispositivoService.compartilhar(id, request, usuarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(compartilhamento)
    }

    @GetMapping("/{id}/compartilhamentos")
    fun listarCompartilhamentos(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<List<CompartilhamentoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val compartilhamentos = dispositivoService.listarCompartilhamentos(id, usuarioId)
        return ResponseEntity.ok(compartilhamentos)
    }

    @DeleteMapping("/{id}/compartilhar/{convidadoId}")
    fun removerCompartilhamento(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @PathVariable convidadoId: UUID
    ): ResponseEntity<Void> {
        val usuarioId = UUID.fromString(jwt.subject)
        dispositivoService.removerCompartilhamento(id, convidadoId, usuarioId)
        return ResponseEntity.noContent().build()
    }
}

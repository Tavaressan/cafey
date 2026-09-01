package br.com.cafey.schedule

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/dispositivos/{dispositivoId}/agendamentos")
class AgendamentoController(
    private val agendamentoService: AgendamentoService
) {

    @GetMapping
    fun listar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<List<AgendamentoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val list = agendamentoService.listar(dispositivoId, usuarioId)
        return ResponseEntity.ok(list)
    }

    @PostMapping
    fun criar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID,
        @Valid @RequestBody request: CriarAgendamentoRequest
    ): ResponseEntity<AgendamentoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val salvo = agendamentoService.criar(dispositivoId, request, usuarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo)
    }

    @PutMapping("/{agendamentoId}")
    fun atualizar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID,
        @PathVariable agendamentoId: UUID,
        @Valid @RequestBody request: AtualizarAgendamentoRequest
    ): ResponseEntity<AgendamentoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val atualizado = agendamentoService.atualizar(dispositivoId, agendamentoId, request, usuarioId)
        return ResponseEntity.ok(atualizado)
    }

    @DeleteMapping("/{agendamentoId}")
    fun excluir(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID,
        @PathVariable agendamentoId: UUID
    ): ResponseEntity<Void> {
        val usuarioId = UUID.fromString(jwt.subject)
        agendamentoService.excluir(dispositivoId, agendamentoId, usuarioId)
        return ResponseEntity.noContent().build()
    }
}

package br.com.tavaressan.cafey.schedule

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Agendamentos", description = "Agendamentos recorrentes de preparo por dispositivo")
@ApiResponse(
    responseCode = "401",
    description = "Token JWT ausente ou inválido",
    content = [Content(schema = Schema(implementation = ProblemDetail::class))]
)
@ApiResponse(
    responseCode = "404",
    description = "Dispositivo ou agendamento não encontrado",
    content = [Content(schema = Schema(implementation = ProblemDetail::class))]
)
@RestController
@RequestMapping("/dispositivos/{dispositivoId}/agendamentos")
class AgendamentoController(
    private val agendamentoService: AgendamentoService
) {

    @Operation(summary = "Lista os agendamentos de um dispositivo")
    @GetMapping
    fun listar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<List<AgendamentoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val list = agendamentoService.listar(dispositivoId, usuarioId)
        return ResponseEntity.ok(list)
    }

    @Operation(summary = "Cria um agendamento para um dispositivo")
    @ApiResponse(responseCode = "201", description = "Agendamento criado com sucesso")
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

    @Operation(summary = "Atualiza um agendamento existente")
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

    @Operation(summary = "Exclui um agendamento")
    @ApiResponse(responseCode = "204", description = "Agendamento excluído com sucesso")
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

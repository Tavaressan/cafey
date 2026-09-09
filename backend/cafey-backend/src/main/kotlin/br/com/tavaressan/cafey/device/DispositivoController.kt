package br.com.tavaressan.cafey.device

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

@Tag(name = "Dispositivos", description = "Gerenciamento de dispositivos e seus compartilhamentos")
@ApiResponse(
    responseCode = "401",
    description = "Token JWT ausente ou inválido",
    content = [Content(schema = Schema(implementation = ProblemDetail::class))]
)
@RestController
@RequestMapping("/dispositivos")
class DispositivoController(
    private val dispositivoService: DispositivoService
) {

    @Operation(summary = "Lista os dispositivos do usuário autenticado")
    @GetMapping
    fun listar(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<DispositivoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivos = dispositivoService.listarDoUsuario(usuarioId)
        return ResponseEntity.ok(dispositivos)
    }

    @Operation(summary = "Cria um novo dispositivo para o usuário autenticado")
    @ApiResponse(responseCode = "201", description = "Dispositivo criado com sucesso")
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping
    fun criar(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CriarDispositivoRequest
    ): ResponseEntity<DispositivoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivo = dispositivoService.criar(request, usuarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(dispositivo)
    }

    @Operation(summary = "Obtém um dispositivo pelo ID")
    @ApiResponse(
        responseCode = "404",
        description = "Dispositivo não encontrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @GetMapping("/{id}")
    fun obter(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<DispositivoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val dispositivo = dispositivoService.obterPorId(id, usuarioId)
        return ResponseEntity.ok(dispositivo)
    }

    @Operation(summary = "Atualiza dados de um dispositivo")
    @ApiResponse(
        responseCode = "404",
        description = "Dispositivo não encontrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
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

    @Operation(summary = "Exclui um dispositivo")
    @ApiResponse(responseCode = "204", description = "Dispositivo excluído com sucesso")
    @ApiResponse(
        responseCode = "404",
        description = "Dispositivo não encontrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @DeleteMapping("/{id}")
    fun excluir(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val usuarioId = UUID.fromString(jwt.subject)
        dispositivoService.excluir(id, usuarioId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Compartilha um dispositivo com outro usuário por email")
    @ApiResponse(responseCode = "201", description = "Compartilhamento criado com sucesso")
    @ApiResponse(
        responseCode = "404",
        description = "Dispositivo ou usuário convidado não encontrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
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

    @Operation(summary = "Lista os compartilhamentos de um dispositivo")
    @GetMapping("/{id}/compartilhamentos")
    fun listarCompartilhamentos(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<List<CompartilhamentoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val compartilhamentos = dispositivoService.listarCompartilhamentos(id, usuarioId)
        return ResponseEntity.ok(compartilhamentos)
    }

    @Operation(summary = "Remove o compartilhamento de um dispositivo com um usuário convidado")
    @ApiResponse(responseCode = "204", description = "Compartilhamento removido com sucesso")
    @ApiResponse(
        responseCode = "404",
        description = "Dispositivo, convidado ou compartilhamento não encontrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
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

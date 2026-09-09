package br.com.tavaressan.cafey.event

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@Tag(name = "Eventos", description = "Eventos de consumo, estatísticas e descalcificação por dispositivo")
@ApiResponse(
    responseCode = "401",
    description = "Token JWT ausente ou inválido",
    content = [Content(schema = Schema(implementation = ProblemDetail::class))]
)
@ApiResponse(
    responseCode = "404",
    description = "Dispositivo não encontrado",
    content = [Content(schema = Schema(implementation = ProblemDetail::class))]
)
@RestController
@RequestMapping("/dispositivos/{dispositivoId}")
class EventoController(
    private val eventoService: EventoService
) {

    @Operation(summary = "Lista os eventos de consumo do dispositivo, com filtros e paginação")
    @GetMapping("/eventos")
    fun listar(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID,
        @RequestParam(required = false) resultado: String?,
        @RequestParam(required = false) inicio: Instant?,
        @RequestParam(required = false) fim: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<EventoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val result = eventoService.listar(dispositivoId, usuarioId, resultado, inicio, fim, page, size)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "Obtém as estatísticas de consumo do dispositivo")
    @GetMapping("/estatisticas")
    fun obterEstatisticas(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<EstatisticasConsumoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val stats = eventoService.obterEstatisticas(dispositivoId, usuarioId)
        return ResponseEntity.ok(stats)
    }

    @Operation(summary = "Obtém o status atual de descalcificação do dispositivo")
    @GetMapping("/descalcificacao")
    fun obterStatusDescalcificacao(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<StatusDescalcificacaoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val status = eventoService.obterStatusDescalcificacao(dispositivoId, usuarioId)
        return ResponseEntity.ok(status)
    }

    @Operation(summary = "Registra a baixa (reset) da descalcificação do dispositivo")
    @PostMapping("/descalcificacao/baixa")
    fun darBaixaDescalcificacao(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<StatusDescalcificacaoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val status = eventoService.darBaixaDescalcificacao(dispositivoId, usuarioId)
        return ResponseEntity.ok(status)
    }

    @Operation(summary = "Processa um lote de eventos recebidos via proxy BLE do dispositivo")
    @ApiResponse(responseCode = "201", description = "Eventos processados com sucesso")
    @PostMapping("/eventos/proxy-ble")
    fun processarProxyBle(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID,
        @Valid @RequestBody request: ProxyBleEventosRequest
    ): ResponseEntity<List<EventoResponse>> {
        val usuarioId = UUID.fromString(jwt.subject)
        val resultado = eventoService.processarProxyBle(dispositivoId, request, usuarioId)
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado)
    }
}

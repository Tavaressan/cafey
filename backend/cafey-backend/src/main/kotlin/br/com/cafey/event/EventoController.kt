package br.com.cafey.event

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/dispositivos/{dispositivoId}")
class EventoController(
    private val eventoService: EventoService
) {

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

    @GetMapping("/estatisticas")
    fun obterEstatisticas(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<EstatisticasConsumoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val stats = eventoService.obterEstatisticas(dispositivoId, usuarioId)
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/descalcificacao")
    fun obterStatusDescalcificacao(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<StatusDescalcificacaoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val status = eventoService.obterStatusDescalcificacao(dispositivoId, usuarioId)
        return ResponseEntity.ok(status)
    }

    @PostMapping("/descalcificacao/baixa")
    fun darBaixaDescalcificacao(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable dispositivoId: UUID
    ): ResponseEntity<StatusDescalcificacaoResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        val status = eventoService.darBaixaDescalcificacao(dispositivoId, usuarioId)
        return ResponseEntity.ok(status)
    }

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

package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
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

@RestController
@RequestMapping("/notificacoes")
@Tag(name = "Notificações", description = "Registro de aparelhos e navegadores para push (#318)")
class NotificacaoController(
    private val registroPushService: RegistroPushService,
    private val notificacaoService: NotificacaoService,
    private val properties: NotificacaoProperties
) {

    @Operation(summary = "Registra (ou renova) o token FCM ou a assinatura Web Push deste aparelho")
    @ApiResponse(
        responseCode = "400",
        description = "Campos incoerentes com a plataforma ou endpoint de push não suportado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/registros")
    fun registrar(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: RegistrarPushRequest
    ): ResponseEntity<RegistroPushResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(registroPushService.registrar(UUID.fromString(jwt.subject), request))

    @Operation(summary = "Lista os destinos de push do usuário")
    @GetMapping("/registros")
    fun listar(@AuthenticationPrincipal jwt: Jwt): List<RegistroPushResponse> =
        registroPushService.listar(UUID.fromString(jwt.subject))

    @Operation(summary = "Remove um destino de push do usuário")
    @ApiResponse(
        responseCode = "404",
        description = "Registro inexistente ou de outro usuário",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @DeleteMapping("/registros/{id}")
    fun remover(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID): ResponseEntity<Void> {
        registroPushService.remover(UUID.fromString(jwt.subject), id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Remove o destino pelo token/endpoint (logout); idempotente")
    @PostMapping("/registros/remover")
    fun removerPorDestino(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: RemoverPushRequest
    ): ResponseEntity<Void> {
        registroPushService.removerPorDestino(UUID.fromString(jwt.subject), request.destino)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Chave pública VAPID para o navegador assinar o Web Push")
    @ApiResponse(
        responseCode = "503",
        description = "Web Push não configurado neste servidor",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @GetMapping("/vapid-public-key")
    fun vapidPublicKey(): VapidPublicKeyResponse {
        val chave = properties.vapidPublicKey.trim()
        if (chave.isEmpty()) throw ServicoIndisponivelException("Web Push não configurado")
        return VapidPublicKeyResponse(chave)
    }

    @Operation(summary = "Envia uma notificação de teste aos destinos do próprio usuário")
    @PostMapping("/teste")
    fun teste(@AuthenticationPrincipal jwt: Jwt): TesteNotificacaoResponse =
        notificacaoService.enviarTeste(UUID.fromString(jwt.subject))
}

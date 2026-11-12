package br.com.tavaressan.cafey.notification

enum class ResultadoEnvio {
    ENTREGUE,
    /** O serviço de push disse que o destino não existe mais: o registro é apagado. */
    DESTINO_INVALIDO,
    /** Erro que pode passar sozinho (rede, 429, 5xx): entra na retentativa. */
    FALHA_TRANSITORIA,
    /** Erro que não muda com retentativa, mas não prova que o destino morreu. */
    FALHA_PERMANENTE
}

/** Um provedor de push (#362 FCM, #363 Web Push). Implementações não lançam exceção: devolvem o resultado. */
interface CanalPush {
    val plataforma: PlataformaPush

    /** Falso quando falta credencial: o despacho ignora o canal sem erro. */
    val habilitado: Boolean

    fun enviar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio
}

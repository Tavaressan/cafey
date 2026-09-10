package br.com.tavaressan.cafey.shared.network

/** Erro de domínio da camada de rede — a UI trata `ApiError`, nunca exceções cruas do Ktor. */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Resposta HTTP de erro (4xx/5xx) com corpo Problem Details (RFC 9457, BE-06). */
    class Http(val status: Int, val problem: ProblemDetail?) : ApiError(
        message = problem?.detail ?: problem?.title ?: "Erro HTTP $status"
    )

    /** Falha de conectividade — sem resposta do servidor. */
    class Network(cause: Throwable) : ApiError("Falha de conexão com o servidor", cause)

    /** Resposta recebida mas não deserializável no shape esperado. */
    class Serialization(cause: Throwable) : ApiError("Resposta inesperada do servidor", cause)

    /** Sessão expirada — refresh token inválido/revogado; a UI deve levar ao login. */
    data object SessionExpired : ApiError("Sessão expirada, faça login novamente")
}

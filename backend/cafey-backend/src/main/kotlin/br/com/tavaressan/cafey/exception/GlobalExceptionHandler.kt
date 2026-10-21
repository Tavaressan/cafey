package br.com.tavaressan.cafey.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
        problemDetail.title = "Resource Not Found"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Bad credentials")
        problemDetail.title = "Unauthorized"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    /**
     * Falha de autenticação do Spring Security (o signin passa pelo `AuthenticationManager`). A
     * mensagem é genérica e fixa: não repete a da exceção, para nunca revelar se o e-mail existe.
     * Atenção ao nome: a [BadCredentialsException] própria do projeto (mesmo pacote) é outra
     * classe, tratada acima, e não passa por aqui.
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos")
        problemDetail.title = "Unauthorized"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    /**
     * Erro de infraestrutura durante a autenticação (por exemplo, banco fora do ar ao carregar o
     * usuário; o `DaoAuthenticationProvider` o embrulha em `InternalAuthenticationServiceException`,
     * subclasse desta). É erro do servidor, nunca "senha errada". Por ser mais específico que
     * [AuthenticationException], este handler tem precedência sobre o de 401.
     */
    @ExceptionHandler(AuthenticationServiceException::class)
    fun handleAuthenticationService(ex: AuthenticationServiceException, request: WebRequest): ProblemDetail =
        handleAllExceptions(ex, request)

    @ExceptionHandler(RequisicaoInvalidaException::class)
    fun handleRequisicaoInvalida(ex: RequisicaoInvalidaException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")
        problemDetail.title = "Bad Request"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    @ExceptionHandler(ServicoIndisponivelException::class)
    fun handleServicoIndisponivel(ex: ServicoIndisponivelException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.message ?: "Service unavailable")
        problemDetail.title = "Service Unavailable"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException, request: WebRequest): ProblemDetail {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed: $errors")
        problemDetail.title = "Bad Request"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }

    /**
     * Issue #252 — loga a exceção com stack trace: o Spring não loga o que um `@ExceptionHandler`
     * resolve, e sem isto um 500 não deixava rastro algum. A resposta continua genérica.
     */
    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: WebRequest): ProblemDetail {
        logger.error("Erro não tratado em {}", request.getDescription(false), ex)
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        problemDetail.title = "Internal Server Error"
        problemDetail.type = URI.create("about:blank")
        return problemDetail
    }
}

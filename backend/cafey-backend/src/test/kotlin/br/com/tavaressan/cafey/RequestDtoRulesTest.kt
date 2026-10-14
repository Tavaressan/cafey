package br.com.tavaressan.cafey

import br.com.tavaressan.cafey.auth.LoginRequest
import br.com.tavaressan.cafey.auth.RedefinirSenhaRequest
import br.com.tavaressan.cafey.auth.RefreshRequest
import br.com.tavaressan.cafey.auth.RegisterRequest
import br.com.tavaressan.cafey.auth.SolicitarRecuperacaoSenhaRequest
import br.com.tavaressan.cafey.device.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.device.CompartilharDispositivoRequest
import br.com.tavaressan.cafey.device.ComandoRequest
import br.com.tavaressan.cafey.device.CriarDispositivoRequest
import br.com.tavaressan.cafey.event.IngestaoEventoRequest
import br.com.tavaressan.cafey.event.ProxyBleEventosRequest
import br.com.tavaressan.cafey.schedule.AtualizarAgendamentoRequest
import br.com.tavaressan.cafey.schedule.CriarAgendamentoRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

/**
 * Item h da Atividade Integrada: todo DTO de request tem ao menos duas regras de validação diferentes.
 * A lista é mantida à mão; um DTO de request novo entra aqui. O `TestDto` (andaime de desenvolvimento)
 * fica de fora.
 */
class RequestDtoRulesTest {

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeFactory() = factory.close()

        @JvmStatic
        fun requestDtos(): List<KClass<*>> = listOf(
            RegisterRequest::class,
            LoginRequest::class,
            RefreshRequest::class,
            SolicitarRecuperacaoSenhaRequest::class,
            RedefinirSenhaRequest::class,
            CriarDispositivoRequest::class,
            AtualizarDispositivoRequest::class,
            CompartilharDispositivoRequest::class,
            ComandoRequest::class,
            CriarAgendamentoRequest::class,
            AtualizarAgendamentoRequest::class,
            IngestaoEventoRequest::class,
            ProxyBleEventosRequest::class
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestDtos")
    fun `should declare at least two different validation rules`(dto: KClass<*>) {
        val regras = validator.getConstraintsForClass(dto.java).constrainedProperties
            .flatMap { it.constraintDescriptors }
            .map { it.annotation.annotationClass }
            .toSet()

        assertTrue(regras.size >= 2, "${dto.simpleName} tem só ${regras.map { it.simpleName }}")
    }
}

package br.com.tavaressan.cafey.config

import br.com.tavaressan.cafey.security.RateLimitingFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import tools.jackson.databind.ObjectMapper

/**
 * Registra o [RateLimitingFilter] apenas nas rotas sensíveis de autenticação (BE-24), antes de
 * qualquer outro filtro (incluindo o filtro de segurança).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfig {

    @Bean
    fun rateLimitingFilterRegistration(
        properties: RateLimitProperties,
        objectMapper: ObjectMapper
    ): FilterRegistrationBean<RateLimitingFilter> {
        val registration = FilterRegistrationBean(RateLimitingFilter(properties, objectMapper))
        registration.setUrlPatterns(listOf("/auth/login", "/auth/registrar", "/auth/recuperar-senha"))
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}

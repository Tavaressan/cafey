package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.CorsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
    private val jwtTokenService: JwtTokenService,
    private val corsProperties: CorsProperties,
    private val environment: Environment
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(12)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return jwtTokenService.decoder
    }

    /**
     * Origens do app Web autorizadas (APP-08): sem credenciais (a autenticação viaja no header
     * `Authorization: Bearer`, nunca em cookie), por isso `allowCredentials = false` — o que
     * também mantém a combinação com `*` fora de cogitação, já vedada pela spec de CORS quando
     * há credenciais.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf(
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS
            ).map { it.name() }
            allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
            allowCredentials = false
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // "/test/**" (scaffolding de dev para exercitar o GlobalExceptionHandler, ver
        // TestController) só é liberado fora do perfil `prod` (issue #185) — em produção,
        // cai em `anyRequest().authenticated()` e responde 401 sem token, mesma guarda de
        // JwtTokenService para as chaves RSA.
        val isProd = environment.activeProfiles.contains("prod")

        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/auth/**").permitAll()
                auth.requestMatchers("/actuator/**").permitAll()
                if (!isProd) {
                    auth.requestMatchers("/test/**").permitAll()
                }
                auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder())
                }
            }

        return http.build()
    }
}

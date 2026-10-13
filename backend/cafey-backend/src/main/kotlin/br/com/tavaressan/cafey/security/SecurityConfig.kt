package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.CorsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

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

    /**
     * `AuthenticationManager` do signin (RF-18): `DaoAuthenticationProvider` com o
     * [UsuarioDetailsService] e o [PasswordEncoder] do projeto. Formato da documentação do Spring
     * Security 7 ("Publish an AuthenticationManager bean"); no 7 o provider recebe o
     * `UserDetailsService` pelo construtor.
     */
    @Bean
    fun authenticationManager(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder
    ): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return ProviderManager(provider)
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
    fun securityFilterChain(http: HttpSecurity, objectMapper: ObjectMapper): SecurityFilterChain {
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
                // LISTA FECHADA de rotas públicas, uma a uma (sem o curinga "/auth/**"): uma rota nova
                // sob /auth/ nasce protegida e só vira pública se for acrescentada aqui. Todas as
                // públicas funcionam com e sem o cabeçalho Authorization, inclusive com token inválido
                // ou expirado nele (o JwtAuthenticationFilter só autentica; quem decide é esta regra).
                //
                // Cadastro e login (nomes oficiais e aliases mantidos por compatibilidade com os
                // clientes já publicados): ainda não há token quando são chamados.
                auth.requestMatchers(
                    HttpMethod.POST, "/auth/signin", "/auth/signup", "/auth/login", "/auth/registrar"
                ).permitAll()
                // Renovação do access token e recuperação/redefinição de senha: fluxos que, por
                // natureza, são usados sem um access token válido (expirado ou esquecido).
                auth.requestMatchers(
                    HttpMethod.POST, "/auth/refresh", "/auth/recuperar-senha", "/auth/redefinir-senha"
                ).permitAll()
                // Página estática de redefinição de senha (issue #194), aberta pelo link do e-mail.
                auth.requestMatchers("/redefinir-senha.html").permitAll()
                // Swagger UI e documentação OpenAPI: a demonstração da API começa por elas (o botão
                // "Authorize" é que leva o token).
                auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Scaffolding oculto do Swagger, usado pelo GlobalExceptionHandlerTest; fora do perfil prod.
                if (!isProd) {
                    auth.requestMatchers("/test/**").permitAll()
                }
                // Todo o resto exige token. Não há permitAll para "/actuator/**": o Actuator não é
                // dependência do projeto. Se for adicionado, liberar só "/actuator/health".
                auth.anyRequest().authenticated()
            }
            // Sem este entry point, ao sair o .oauth2ResourceServer o Spring Security cairia no
            // Http403ForbiddenEntryPoint e responderia 403 a quem não mandou token.
            .exceptionHandling { it.authenticationEntryPoint(ProblemDetailAuthenticationEntryPoint(objectMapper)) }
            // Filtro próprio no lugar do BearerTokenAuthenticationFilter do resource server. A âncora
            // só define a posição (funciona sem formLogin): fica antes do AnonymousAuthenticationFilter,
            // do ExceptionTranslationFilter e do AuthorizationFilter. Instanciado aqui, e não como
            // bean, para o Spring Boot não registrá-lo também no servlet container (rodaria duas vezes).
            .addFilterBefore(JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

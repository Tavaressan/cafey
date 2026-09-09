package br.com.tavaressan.cafey.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// SecurityScheme "bearerAuth" habilita o botão Authorize do Swagger UI para JWT Bearer.
@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Cafey API")
                    .version("v1")
                    .description(
                        "API REST do Cafey para gerenciamento de dispositivos, agendamentos e " +
                            "eventos de consumo de água, consumida pelos clientes Kotlin Multiplataforma."
                    )
            )
            // Requisito de segurança global: sobrescrito com @SecurityRequirements vazio nos endpoints públicos.
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }
}

package br.com.tavaressan.cafey.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Limites de requisições por IP/rota nos endpoints sensíveis de autenticação.
 *
 * Cada rota tem uma capacidade (número de requisições) por janela de tempo (em segundos).
 * Estrutura plana (sem objetos aninhados) para manter o binding simples e previsível, no mesmo
 * espírito de [AwsIotProperties].
 */
@ConfigurationProperties(prefix = "cafey.rate-limit")
class RateLimitProperties(
    var enabled: Boolean = true,
    var loginCapacity: Long = 5,
    var loginPeriodSeconds: Long = 60,
    var registrarCapacity: Long = 10,
    var registrarPeriodSeconds: Long = 60,
    var recuperarSenhaCapacity: Long = 5,
    var recuperarSenhaPeriodSeconds: Long = 60
)

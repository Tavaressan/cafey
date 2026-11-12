package br.com.tavaressan.cafey.notification

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificacaoProperties::class)
class NotificacaoConfig {

    companion object {
        const val EXECUTOR = "notificacaoExecutor"
        const val HTTP_CLIENT = "notificacaoHttpClient"
    }

    /** Pool pequeno e fila limitada: entrega de push nunca compete com a ingestão nem cresce sem teto. */
    @Bean(EXECUTOR)
    fun notificacaoExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 200
        setThreadNamePrefix("push-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(10)
        initialize()
    }

    /** Cliente HTTP compartilhado pelos canais (#362, #363); sem redirecionamento, timeouts curtos. */
    @Bean(HTTP_CLIENT)
    fun notificacaoHttpClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
}

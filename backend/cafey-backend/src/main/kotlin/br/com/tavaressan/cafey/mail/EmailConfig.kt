package br.com.tavaressan.cafey.mail

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EmailProperties::class)
class EmailConfig

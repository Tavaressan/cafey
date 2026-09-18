package br.com.tavaressan.cafey.mail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cafey.mail")
class EmailProperties(
    var from: String = "Cafey <nao-responda@cafey.local>",
    var resetPasswordUrl: String = "http://localhost:8081/redefinir-senha"
)

package br.com.tavaressan.cafey.exception

/** Dependência externa (ex.: conexão MQTT) indisponível no momento da requisição (HTTP 503). */
class ServicoIndisponivelException(message: String) : RuntimeException(message)

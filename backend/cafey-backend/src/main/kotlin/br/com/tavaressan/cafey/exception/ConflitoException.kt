package br.com.tavaressan.cafey.exception

/** Requisição válida que conflita com o estado atual do recurso (HTTP 409). */
class ConflitoException(message: String) : RuntimeException(message)

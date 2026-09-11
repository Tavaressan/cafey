package br.com.tavaressan.cafey.exception

/** Requisição sintaticamente válida, mas com valor semanticamente inaceitável (HTTP 400). */
class RequisicaoInvalidaException(message: String) : RuntimeException(message)

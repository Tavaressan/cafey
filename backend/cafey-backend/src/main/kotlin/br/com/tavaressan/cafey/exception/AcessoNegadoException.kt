package br.com.tavaressan.cafey.exception

/** Usuário autenticado, mas sem vínculo com o recurso solicitado (HTTP 403). */
class AcessoNegadoException(message: String) : RuntimeException(message)

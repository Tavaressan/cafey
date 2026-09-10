package br.com.tavaressan.cafey.shared.network

import kotlinx.serialization.Serializable

/**
 * Corpo de erro RFC 9457 (Problem Details) que o backend devolve em respostas de erro (BE-06).
 * Campos opcionais porque nem toda implementação preenche todos.
 */
@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)

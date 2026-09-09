package br.com.tavaressan.cafey.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import kotlinx.serialization.SerializationException

/**
 * Executa uma chamada HTTP e mapeia o resultado para [ApiError] em vez de deixar vazar exceções
 * cruas do Ktor — a UI só precisa conhecer `ApiError`.
 */
suspend inline fun <reified T> HttpClient.apiRequest(block: HttpRequestBuilder.() -> Unit): T {
    try {
        return request(block).body()
    } catch (e: ResponseException) {
        val problem = try {
            e.response.body<ProblemDetail>()
        } catch (_: Exception) {
            null
        }
        throw ApiError.Http(e.response.status.value, problem)
    } catch (e: SerializationException) {
        throw ApiError.Serialization(e)
    } catch (e: ApiError) {
        throw e
    } catch (e: Exception) {
        throw ApiError.Network(e)
    }
}

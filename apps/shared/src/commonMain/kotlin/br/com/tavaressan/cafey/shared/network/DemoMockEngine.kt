package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

/**
 * MockEngine completo para o Modo Demo da Caféy (Issue #184).
 *
 * Fornece respostas realistas para todos os endpoints utilizados pelos ViewModels
 * do app KMP (Home, Agendamentos, Histórico, Cuidados, Autenticação e Comandos),
 * permitindo demonstração sem dependência de hardware físico ou de infraestrutura de nuvem.
 */
fun createDemoMockEngine(): MockEngine = MockEngine { request ->
    val path = request.url.encodedPath
    val method = request.method.value

    when {
        // --- AUTENTICAÇÃO ---
        path == "/auth/login" || path == "/auth/registrar" || path == "/auth/refresh" -> {
            respond(
                content = """
                    {
                        "accessToken": "demo-jwt-token-access",
                        "refreshToken": "demo-jwt-token-refresh",
                        "tokenType": "Bearer",
                        "expiresIn": 86400
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path == "/auth/recuperar-senha" -> {
            respond(
                content = """{"mensagem":"Instruções de recuperação enviadas para o email informado."}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path == "/auth/redefinir-senha" -> {
            respond(content = "", status = HttpStatusCode.OK)
        }

        // --- DISPOSITIVOS ---
        path == "/dispositivos" && method == "GET" -> {
            respond(
                content = """
                    [
                        {
                            "id": "cafey-demo-cp30",
                            "nome": "Caféy Britânia CP30",
                            "timezone": "America/Sao_Paulo",
                            "estado": "PRONTO",
                            "online": true,
                            "ultimoVisto": "2026-10-15T21:00:00Z",
                            "versaoAgendamentos": 1,
                            "duracaoPreparoS": 480,
                            "limiarDescalcificacao": 100,
                            "contadorPreparos": 42,
                            "papel": "PROPRIETARIO",
                            "criadoEm": "2026-09-01T10:00:00Z",
                            "atualizadoEm": "2026-10-15T21:00:00Z"
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path == "/dispositivos" && method == "POST" -> {
            respond(
                content = """
                    {
                        "id": "cafey-demo-novo",
                        "nome": "Nova Caféy",
                        "timezone": "America/Sao_Paulo",
                        "estado": "PRONTO",
                        "online": true,
                        "ultimoVisto": "2026-10-15T21:00:00Z",
                        "versaoAgendamentos": 1,
                        "duracaoPreparoS": 480,
                        "limiarDescalcificacao": 100,
                        "contadorPreparos": 0,
                        "papel": "PROPRIETARIO",
                        "criadoEm": "2026-10-15T21:00:00Z",
                        "atualizadoEm": "2026-10-15T21:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        path.matches(Regex("/dispositivos/[^/]+")) && method == "GET" -> {
            respond(
                content = """
                    {
                        "id": "cafey-demo-cp30",
                        "nome": "Caféy Britânia CP30",
                        "timezone": "America/Sao_Paulo",
                        "estado": "PRONTO",
                        "online": true,
                        "ultimoVisto": "2026-10-15T21:00:00Z",
                        "versaoAgendamentos": 1,
                        "duracaoPreparoS": 480,
                        "limiarDescalcificacao": 100,
                        "contadorPreparos": 42,
                        "papel": "PROPRIETARIO",
                        "criadoEm": "2026-09-01T10:00:00Z",
                        "atualizadoEm": "2026-10-15T21:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.matches(Regex("/dispositivos/[^/]+")) && (method == "PUT" || method == "DELETE") -> {
            if (method == "DELETE") {
                respond(content = "", status = HttpStatusCode.NoContent)
            } else {
                respond(
                    content = """
                        {
                            "id": "cafey-demo-cp30",
                            "nome": "Caféy Britânia CP30",
                            "timezone": "America/Sao_Paulo",
                            "estado": "PRONTO",
                            "online": true,
                            "ultimoVisto": "2026-10-15T21:00:00Z",
                            "versaoAgendamentos": 1,
                            "duracaoPreparoS": 480,
                            "limiarDescalcificacao": 100,
                            "contadorPreparos": 42,
                            "papel": "PROPRIETARIO",
                            "criadoEm": "2026-09-01T10:00:00Z",
                            "atualizadoEm": "2026-10-15T21:00:00Z"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        }

        // --- COMANDOS ---
        path.endsWith("/comando") && method == "POST" -> {
            respond(
                content = """
                    {
                        "comandoId": "cmd-demo-001",
                        "acao": "LIGAR",
                        "duracaoS": 480,
                        "emitidoEm": "2026-10-15T21:30:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Accepted,
                headers = jsonHeaders,
            )
        }

        // --- AGENDAMENTOS ---
        path.endsWith("/agendamentos") && method == "GET" -> {
            respond(
                content = """
                    [
                        {
                            "id": "ag-demo-01",
                            "dispositivoId": "cafey-demo-cp30",
                            "hora": "07:00",
                            "diasSemana": 62,
                            "ativo": true,
                            "duracaoPreparoS": 480,
                            "criadoEm": "2026-09-01T10:00:00Z",
                            "atualizadoEm": "2026-10-15T10:00:00Z"
                        },
                        {
                            "id": "ag-demo-02",
                            "dispositivoId": "cafey-demo-cp30",
                            "hora": "15:30",
                            "diasSemana": 65,
                            "ativo": false,
                            "duracaoPreparoS": 360,
                            "criadoEm": "2026-09-10T14:00:00Z",
                            "atualizadoEm": "2026-10-14T15:00:00Z"
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/agendamentos") && method == "POST" -> {
            respond(
                content = """
                    {
                        "id": "ag-demo-novo",
                        "dispositivoId": "cafey-demo-cp30",
                        "hora": "08:00",
                        "diasSemana": 62,
                        "ativo": true,
                        "duracaoPreparoS": 480,
                        "criadoEm": "2026-10-15T21:00:00Z",
                        "atualizadoEm": "2026-10-15T21:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        path.contains("/agendamentos/") && (method == "PUT" || method == "DELETE") -> {
            if (method == "DELETE") {
                respond(content = "", status = HttpStatusCode.NoContent)
            } else {
                respond(
                    content = """
                        {
                            "id": "ag-demo-01",
                            "dispositivoId": "cafey-demo-cp30",
                            "hora": "07:15",
                            "diasSemana": 62,
                            "ativo": true,
                            "duracaoPreparoS": 480,
                            "criadoEm": "2026-09-01T10:00:00Z",
                            "atualizadoEm": "2026-10-15T21:00:00Z"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }
        }

        // --- EVENTOS E HISTÓRICO ---
        path.endsWith("/eventos") && method == "GET" -> {
            respond(
                content = """
                    {
                        "content": [
                            {
                                "id": "ev-demo-01",
                                "eventoId": "evt-01",
                                "tipo": "PREPAROU",
                                "resultado": "CONCLUIDO",
                                "origem": "APP",
                                "duracaoS": 480,
                                "timestamp": "2026-10-15T07:08:00Z",
                                "detalheErro": null,
                                "criadoEm": "2026-10-15T07:08:01Z"
                            },
                            {
                                "id": "ev-demo-02",
                                "eventoId": "evt-02",
                                "tipo": "PREPAROU",
                                "resultado": "CONCLUIDO",
                                "origem": "AGENDAMENTO",
                                "duracaoS": 480,
                                "timestamp": "2026-10-14T07:08:00Z",
                                "detalheErro": null,
                                "criadoEm": "2026-10-14T07:08:01Z"
                            },
                            {
                                "id": "ev-demo-03",
                                "eventoId": "evt-03",
                                "tipo": "PREPAROU",
                                "resultado": "CONCLUIDO",
                                "origem": "BOTAO",
                                "duracaoS": 360,
                                "timestamp": "2026-10-13T16:06:00Z",
                                "detalheErro": null,
                                "criadoEm": "2026-10-13T16:06:01Z"
                            }
                        ],
                        "totalElements": 3,
                        "totalPages": 1,
                        "number": 0,
                        "size": 20,
                        "last": true
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/estatisticas") && method == "GET" -> {
            respond(
                content = """
                    {
                        "totalPreparosConcluidos": 42,
                        "porOrigem": {
                            "APP": 22,
                            "AGENDAMENTO": 14,
                            "BOTAO": 6
                        },
                        "tempoTotalPreparoSegundos": 20160,
                        "sequenciaManhasDias": 5
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        // --- CUIDADOS DA CAFETEIRA ---
        path.endsWith("/cuidados") && method == "GET" -> {
            respond(
                content = """
                    {
                        "descalcificacao": {
                            "contadorPreparos": 42,
                            "limiarDescalcificacao": 100,
                            "precisaDescalcificar": false,
                            "percentualUso": 42.0
                        },
                        "enxague": {
                            "contadorPreparos": 8,
                            "limiarPreparos": 10,
                            "precisaAtencao": false,
                            "percentualUso": 80.0
                        },
                        "filtro": {
                            "contadorPreparos": 25,
                            "limiarPreparos": 60,
                            "precisaAtencao": false,
                            "percentualUso": 41.66
                        },
                        "destaque": "ENXAGUE"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/descalcificacao") && method == "GET" -> {
            respond(
                content = """
                    {
                        "contadorPreparos": 42,
                        "limiarDescalcificacao": 100,
                        "precisaDescalcificar": false,
                        "percentualUso": 42.0
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/descalcificacao/baixa") && method == "POST" -> {
            respond(
                content = """
                    {
                        "contadorPreparos": 0,
                        "limiarDescalcificacao": 100,
                        "precisaDescalcificar": false,
                        "percentualUso": 0.0
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/enxague/baixa") && method == "POST" -> {
            respond(
                content = """
                    {
                        "contadorPreparos": 0,
                        "limiarPreparos": 10,
                        "precisaAtencao": false,
                        "percentualUso": 0.0
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/filtro/baixa") && method == "POST" -> {
            respond(
                content = """
                    {
                        "contadorPreparos": 0,
                        "limiarPreparos": 60,
                        "precisaAtencao": false,
                        "percentualUso": 0.0
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        // --- COMPARTILHAMENTOS ---
        path.endsWith("/compartilhamentos") && method == "GET" -> {
            respond(
                content = """
                    [
                        {
                            "usuarioId": "usr-convidado-1",
                            "nome": "Convidado Caféy",
                            "email": "convidado@cafey.com",
                            "papel": "CONVIDADO",
                            "compartilhadoEm": "2026-10-15T21:00:00Z"
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        path.endsWith("/compartilhar") && method == "POST" -> {
            respond(
                content = """
                    {
                        "usuarioId": "usr-convidado-2",
                        "nome": "Novo Convidado",
                        "email": "convidado2@cafey.com",
                        "papel": "CONVIDADO",
                        "compartilhadoEm": "2026-10-15T21:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }

        path.contains("/compartilhar/") && method == "DELETE" -> {
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        // Fallback seguro para rotas não mapeadas em demo
        else -> {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
    }
}

/**
 * Cria um [HttpClient] pré-configurado com [MockEngine] para rodar o app 100% offline em Modo Demo.
 */
fun createDemoHttpClient(baseUrl: String, tokenStorage: TokenStorage): HttpClient =
    HttpClient(createDemoMockEngine(), sharedHttpClientConfig(baseUrl, tokenStorage))

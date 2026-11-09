# 2FA / TOTP (RFC 6238) — issue #260

Autenticação de dois fatores baseada em tempo (TOTP, compatível com Google Authenticator, Authy etc.), implementada no backend sem dependência externa (JCE HmacSHA1, 6 dígitos, passo 30s, janela ±1).

## Fluxo

1. **Configurar** (autenticado): `POST /auth/2fa/configurar` gera um segredo TOTP, armazena-o **cifrado** (AES-256-GCM, chave `cafey.mfa.encryption-key`, separada do JWT) e retorna `otpauthUri` + `segredo` (Base32). O 2FA **não** é ativado nesta etapa.
2. **Ativar** (autenticado): `POST /auth/2fa/ativar` com `{"codigo":"123456"}` valida o primeiro código e define `mfa_habilitado=true`.
3. **Login com 2FA ativo**: `POST /auth/signin` (ou `/auth/login`) retorna **HTTP 200** com `{"mfaRequerido":true,"desafioToken":"...","tokenType":"Bearer","expiresIn":300}` — sem `accessToken`/`refreshToken`. O token de desafio é um JWT de curta duração (5 min, claim `purpose=mfa_challenge`) e **não** pode ser usado como Bearer em rotas protegidas.
4. **Validar desafio** (público): `POST /auth/2fa/validar` com `{"desafioToken":"...","codigo":"123456"}` emite os tokens finais (`accessToken`, `refreshToken`).

## Compatibilidade (clientes sem 2FA)

Para usuários com `mfa_habilitado=false`, o `/auth/signin` mantém a resposta **idêntica** à anterior: `accessToken`, `refreshToken`, `tokenType`, `expiresIn` (sem `mfaRequerido`/`desafioToken`).

## Endpoints

| Método | Rota | Auth | Corpo (req) | Resposta (200) |
|---|---|---|---|---|
| POST | `/auth/2fa/configurar` | Bearer | — | `{"otpauthUri":"otpauth://totp/...","segredo":"BASE32"}` |
| POST | `/auth/2fa/ativar` | Bearer | `{"codigo":"dddddd"}` | `{"mensagem":"2FA ativado com sucesso"}` |
| POST | `/auth/2fa/validar` | público | `{"desafioToken":"...","codigo":"dddddd"}` | `AuthResponse` com tokens (`accessToken`,`refreshToken`) |

Erros relevantes: código TOTP inválido ou desafio inválido/expirado → **401**; ativar sem configurar → **400**.

## Configuração

- `CAFEY_MFA_ENCRYPTION_KEY` / `cafey.mfa.encryption-key`: Base64 de 32 bytes (AES-256). Obrigatória em `prod`; fora de `prod`, ausência gera chave efêmera (segredos não sobrevivem a restart).
- Rate limit: `/auth/2fa/validar` limitado por IP (padrão 10 req/60s), junto aos demais endpoints sensíveis de auth.

## Documentação OpenAPI

Os endpoints estão anotados no Swagger/OpenAPI (`/swagger-ui.html`, `/v3/api-docs`): `/auth/2fa/configurar` e `/auth/2fa/ativar` exigem `bearerAuth`; `/auth/2fa/validar` é público.

# 2FA / TOTP (RFC 6238) — issue #260

Autenticação de dois fatores baseada em tempo (TOTP, compatível com Google Authenticator, Authy etc.), implementada no backend sem dependência externa (JCE HmacSHA1, 6 dígitos, passo 30s, janela ±1).

## Fluxo

1. **Configurar** (autenticado): `POST /auth/2fa/configurar` gera um segredo TOTP e o guarda **cifrado** (AES-256-GCM, chave `cafey.mfa.encryption-key`, separada do JWT) como **pendente** (`usuarios.mfa_secret_pendente`). Retorna `otpauthUri` (o app mostra como QR code) + `segredo` (Base32). Nada muda no login ainda: se o 2FA já estiver ativo, o segredo em uso continua valendo.
2. **Ativar** (autenticado): `POST /auth/2fa/ativar` com `{"codigo":"123456"}` valida o código contra o segredo pendente. Se bater, o pendente vira o segredo em uso, `mfa_habilitado=true`, e a resposta traz **10 códigos de recuperação** novos (os anteriores deixam de valer). Os códigos aparecem só nessa resposta.
3. **Login com 2FA ativo**: `POST /auth/signin` (ou `/auth/login`) retorna **HTTP 200** com `{"mfaRequerido":true,"desafioToken":"...","tokenType":"Bearer","expiresIn":300}` — sem `accessToken`/`refreshToken`. O token de desafio é um JWT de curta duração (5 min, claim `purpose=mfa_challenge`) e **não** pode ser usado como Bearer em rotas protegidas.
4. **Validar desafio** (público): `POST /auth/2fa/validar` com `{"desafioToken":"...","codigo":"..."}` emite os tokens finais. `codigo` é o TOTP de 6 dígitos **ou** um código de recuperação (`XXXXX-XXXXX`, aceita minúsculas e sem hífen), que é consumido no uso.
5. **Desativar** (autenticado): `POST /auth/2fa/desativar` com `{"codigo":"..."}` (TOTP ou código de recuperação) apaga o segredo e os códigos de recuperação. Pedir o código impede que um token de acesso vazado baste para remover o segundo fator.

### Reconfigurar

Reconfigurar é repetir os passos 1 e 2 com o 2FA ativo. Até o passo 2 dar certo, o autenticador antigo e os códigos de recuperação antigos continuam valendo; desistir no meio não tranca o usuário fora da conta.

### Códigos de recuperação

10 códigos de 10 caracteres (alfabeto sem 0/O, 1/I/L, ~49 bits cada), exibidos como `XXXXX-XXXXX`. Só o hash SHA-256 fica no banco (`mfa_codigos_recuperacao`). `GET /auth/2fa/status` informa quantos restam (`codigosRecuperacaoRestantes`). Para gerar um conjunto novo, basta reconfigurar.

## Compatibilidade (clientes sem 2FA)

Para usuários com `mfa_habilitado=false`, o `/auth/signin` mantém a resposta **idêntica** à anterior: `accessToken`, `refreshToken`, `tokenType`, `expiresIn` (sem `mfaRequerido`/`desafioToken`).

## Endpoints

| Método | Rota | Auth | Corpo (req) | Resposta (200) |
|---|---|---|---|---|
| POST | `/auth/2fa/configurar` | Bearer | — | `{"otpauthUri":"otpauth://totp/...","segredo":"BASE32"}` |
| POST | `/auth/2fa/ativar` | Bearer | `{"codigo":"dddddd"}` | `{"mensagem":"2FA ativado com sucesso","codigosRecuperacao":["ABCDE-FGHJK", ...]}` |
| POST | `/auth/2fa/validar` | público | `{"desafioToken":"...","codigo":"dddddd ou XXXXX-XXXXX"}` | `AuthResponse` com tokens (`accessToken`,`refreshToken`) |
| POST | `/auth/2fa/desativar` | Bearer | `{"codigo":"dddddd ou XXXXX-XXXXX"}` | `{"mensagem":"2FA desativado"}` |
| GET | `/auth/2fa/status` | Bearer | — | `{"mfaHabilitado":true,"codigosRecuperacaoRestantes":10}` |

Erros relevantes: código inválido ou desafio inválido/expirado → **401**; ativar sem configurar e desativar sem 2FA ativo → **400**; chave MFA ausente em `prod` → **503**.

## Configuração

- `CAFEY_MFA_ENCRYPTION_KEY` / `cafey.mfa.encryption-key`: Base64 de 32 bytes (AES-256) usada para cifrar `usuarios.mfa_secret`. **Separada** das chaves JWT.
  - **Gerar:** `openssl rand -base64 32`
  - **Onde colocar (produção):** no `.env` da instância (o mesmo usado pelo `backend/compose.prod.yaml`), como `CAFEY_MFA_ENCRYPTION_KEY=<valor>`. O compose repassa a variável ao container do `app`.
  - **Trocar a chave invalida os segredos TOTP já cadastrados** (não é possível decifrá-los). Usuários com 2FA ativo precisarão reconfigurar o 2FA.
  - **Ausente em `prod`:** o app **sobe normalmente** (não derruba o boot) e registra `ERROR` avisando. Nesse estado degradado: `POST /auth/2fa/configurar` e `POST /auth/2fa/ativar` respondem **503** com mensagem clara; login de usuário **sem** 2FA segue idêntico; login de usuário **com** 2FA ativo **falha fechado** (não emite JWT sem o segundo fator) com erro claro (503).
  - **Ausente fora de `prod`:** chave efêmera com `WARN` (segredos não sobrevivem a restart); apenas dev/testes.
- Rate limit: `/auth/2fa/validar`, `/auth/2fa/ativar` e `/auth/2fa/desativar` dividem um balde por IP (padrão 10 req/60s), para que tentar códigos de recuperação por uma rota não contorne o limite da outra.

## Documentação OpenAPI

Os endpoints estão anotados no Swagger/OpenAPI (`/swagger-ui.html`, `/v3/api-docs`): `/auth/2fa/configurar` e `/auth/2fa/ativar` exigem `bearerAuth`; `/auth/2fa/validar` é público.

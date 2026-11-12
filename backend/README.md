# cafey-backend

API do Cafey (Spring Boot / Kotlin). Há dois modos de rodar em desenvolvimento.

No `Cafey-backend.zip` da entrega, esta pasta (`backend/` no repositório) se chama `cafey-backend/`: troque `cd backend` por `cd cafey-backend` nos comandos abaixo.

## Modo 1 — banco no Compose, API no host (`bootRun`)

Modo original, útil para depurar/rodar a API direto na IDE.

```bash
cd backend
docker compose up -d db
cd cafey-backend
./gradlew bootRun
```

A API sobe em `http://localhost:8080`, conectando ao Postgres publicado em `localhost:5432`
(credenciais padrão em `application.yml`: `cafey_user` / `cafey_password` / banco `cafey_db`).

## Modo 2 — stack completa via Compose (banco + API)

```bash
cd backend
docker compose up
```

Sobe o Postgres (`db`) e a API (`app`), construída a partir do `Dockerfile` em `cafey-backend/`
(multi-stage: `eclipse-temurin:21-jdk` para `./gradlew bootJar`, depois `eclipse-temurin:21-jre`
para rodar o jar). Na primeira execução o Compose constrói a imagem automaticamente — não é preciso
rodar `docker build` à parte. Para forçar rebuild após alterar código: `docker compose up --build`.

O serviço `app` espera o `db` ficar saudável (`depends_on: condition: service_healthy`) antes de
subir; o Flyway aplica as migrations no boot (`ddl-auto: validate`).

### Por que Dockerfile e não `bootBuildImage`

O plugin do Spring Boot inclui `bootBuildImage` (Cloud Native Buildpacks, sem Dockerfile). Foi
avaliado primeiro, mas Buildpacks não é um backend de build suportado nativamente pelo `docker
compose build`/`up --build`: seria preciso rodar `./gradlew bootBuildImage` manualmente antes de
`docker compose up`, o que reintroduz o passo manual no host que a issue #149 pedia para eliminar.
Um `Dockerfile` multi-stage permite que `docker compose up` construa a imagem sozinho.

### Variáveis de ambiente

Nenhuma credencial ou chave fica embutida na imagem — todas entram via variável de ambiente no
`compose.yaml` (com defaults de desenvolvimento quando ausentes):

| Variável | Uso | Default (dev) |
|---|---|---|
| `CAFEY_DB_USERNAME` / `CAFEY_DB_PASSWORD` / `CAFEY_DB_NAME` | credenciais do Postgres (`db` e `app`) | `cafey_user` / `cafey_password` / `cafey_db` |
| `CAFEY_JWT_PRIVATE_KEY` / `CAFEY_JWT_PUBLIC_KEY` | par de chaves RSA (PKCS#8/X.509, Base64 ou PEM) para assinar os JWT emitidos em `/auth` | vazio — gera par efêmero (dev/teste); **obrigatório com perfil `prod` ativo**, senão o boot falha |
| `CAFEY_CORS_ALLOWED_ORIGINS` | origem(ns), separadas por vírgula, autorizadas a chamar a API a partir do app Web | `http://localhost:8081` |
| `CAFEY_MAIL_FROM` / `CAFEY_MAIL_RESET_PASSWORD_URL` | remetente e URL da página de redefinição de senha (`/redefinir-senha.html`, servida pelo backend) usados no e-mail de recuperação | `Cafey <nao-responda@cafey.local>` / `http://localhost:8080/redefinir-senha.html` |
| `CAFEY_MAIL_SMTP_HOST` / `CAFEY_MAIL_SMTP_PORT` / `CAFEY_MAIL_SMTP_USERNAME` / `CAFEY_MAIL_SMTP_PASSWORD` | SMTP do AWS SES (`email-smtp.<região>.amazonaws.com`, credenciais SMTP do SES) — **só perfil `prod`**, propagadas por `compose.prod.yaml` | vazio / `587` / vazio / vazio — fora de `prod` o e-mail só é logado (`LogEmailSenderService`) |

Para sobrescrever localmente sem editar `compose.yaml`, crie um `backend/.env` (não versionado) com
as variáveis acima — o Compose o carrega automaticamente.

> **Resolvido em 2026-09-14 (issue #157):** o app Web publicado no Vercel
> (`https://cafey-web.vercel.app`) aponta para o backend remoto (`BACKEND_BASE_URL` no workflow
> de deploy) e `CAFEY_CORS_ALLOWED_ORIGINS` em produção já inclui essa origem — integração ponta a
> ponta validada (CORS, deploy do webApp e chamadas à API).

Certificados do AWS IoT Core (`aws.iot.certificate-path` / `private-key-path` / `root-ca-path`) são
montados via volume em produção (`backend/compose.prod.yaml`, bind mount de
`/opt/cafey/secrets:/run/secrets:ro`) com as properties apontadas pelas variáveis
`AWS_IOT_CERTIFICATE_PATH`/`AWS_IOT_PRIVATE_KEY_PATH`/`AWS_IOT_ROOT_CA_PATH` — o bean
correspondente só é ativado (`@ConditionalOnProperty`) se esses paths estiverem configurados; em
dev, a ausência não impede o boot (MQTT fica desabilitado). Ativado em produção desde 2026-09-14 —
ver `docs/docs_arquitetura/aws-iot-core-provisioning.md`.

## Notificações push (épico #318)

O backend envia notificações push para os dispositivos dos usuários quando eventos relevantes
acontecem (ex.: café pronto, falha no preparo, cafeteira offline). Os canais são independentes e
desligados por padrão: sem credencial configurada, o boot segue normal e o envio apenas registra um
aviso em log (adequado para desenvolvimento e testes).

### Android — Firebase Cloud Messaging (HTTP v1, #362)

1. **Criar o projeto Firebase** (somente Cloud Messaging): acesse o
   [console do Firebase](https://console.firebase.google.com/), crie/selecione o projeto do app e
   habilite apenas o Cloud Messaging. Não é necessário configurar outros produtos.
2. **Criar a conta de serviço** com papel mínimo de envio: em
   *Configurações do projeto → Contas de serviço → Gerenciar contas de serviço* (Google Cloud),
   crie uma conta de serviço e conceda a ela o papel **Firebase Cloud Messaging API Admin**
   (`roles/cloudmessaging.admin`, equivalente mínimo para envio de mensagens via API HTTP v1).
   Gere uma chave JSON para essa conta.
3. **Montar a credencial na instância** (nunca versionar o arquivo): copie o JSON para
   `/opt/cafey/secrets/firebase-service-account.json` na instância. Em produção o
   `backend/compose.prod.yaml` já monta `/opt/cafey/secrets` como `/run/secrets:ro` dentro do
   container, e a variável `CAFEY_PUSH_FCM_CREDENTIALS_PATH` aponta por padrão para
   `/run/secrets/firebase-service-account.json`.
4. **Testar ponta a ponta**: com o app Android registrado (`POST /notificacoes/registrar`) e o
   backend configurado, chame `POST /notificacoes/teste` autenticado como o usuário dono do
   registro. A resposta informa quantos destinos foram considerados e quantas entregas o FCM
   confirmou.

Configuração (variáveis já declaradas em `application.yml`, `.env.example` e `compose.prod.yaml`):

| Variável | Uso | Default |
|---|---|---|
| `CAFEY_PUSH_FCM_CREDENTIALS_PATH` | Caminho do JSON da conta de serviço do Firebase (Android/FCM) | vazio — canal Android desligado |

**Rotação da credencial:** gere uma nova chave JSON para a mesma conta de serviço no console,
substitua o arquivo em `/opt/cafey/secrets/firebase-service-account.json` e reinicie o serviço do
backend para recarregar o arquivo. Após validar o envio, revogue a chave antiga no console. O
conteúdo da credencial (chave privada, tokens) nunca é registrado em log.

> Web Push (#363) usa variáveis próprias (`CAFEY_PUSH_VAPID_PUBLIC_KEY`,
> `CAFEY_PUSH_VAPID_PRIVATE_KEY`, `CAFEY_PUSH_VAPID_SUBJECT`); consulte a ADR e o `.env.example`.

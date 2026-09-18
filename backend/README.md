# cafey-backend

API do Cafey (Spring Boot / Kotlin). Há dois modos de rodar em desenvolvimento.

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

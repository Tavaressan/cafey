# Especificação Técnica — Backend Cafey

**Projeto Integrador — 6º Semestre**
**Versão:** 1.1
**Data:** 01/09/2026
**Complementa:** `spec-cafeteira-conectada.md` §7 (Backend e Modelo de Dados)

> **Revisão 1.1.** Fecha o contrato do evento de preparo, que a v1.0 deixara em
> suspenso. A restrição que motiva a maior parte das mudanças: o dispositivo
> apenas chaveia energia, sem sensor de nível, corrente ou temperatura. O
> sistema não observa a conclusão da extração — ele a temporiza.

---

## 1. Decisões de stack

| Item | Decisão | Alternativa descartada |
|---|---|---|
| Linguagem | Kotlin | — |
| Framework | Spring Boot linha 4.1.x | 3.5.x (EOL desde 30/06/2026) |
| JDK | 25 (LTS) | 17 (baseline mínimo do Boot 4.1) |
| Build | Gradle Kotlin DSL | Maven (o resto do monorepo é Gradle) |
| Persistência | Spring Data JPA | Spring Data JDBC, jOOQ |
| Migrações | Flyway | `ddl-auto`, Liquibase |
| Autenticação | JWT auto-emitido | Sessão + cookie, token opaco |
| MQTT | Cliente persistente no AWS IoT Core, X.509 | Regras IoT → HTTPS; Mosquitto local |
| Fim do preparo | Temporizador local no dispositivo | Detecção por sensor (corrente, temperatura, nível) |

A alternativa Regras IoT → HTTPS eliminaria o CRT nativo, o certificado do
backend e o tratamento de reconexão, mas exige endpoint HTTPS público
alcançável pela AWS — custo pior numa apresentação ao vivo, em que o backend
roda na máquina do autor.

### 1.1 Starters do Spring Boot 4

O Boot 4.0 renomeou starters. Os nomes antigos ainda resolvem, mas estão
depreciados e serão removidos. Usar os novos:

| Depreciado | Correto no 4.x |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |

No Boot 4 o Flyway não é mais autoconfigurado pela simples presença do jar: é
preciso o starter `spring-boot-starter-flyway`. Cada starter também passou a ter
um companheiro de teste (`spring-boot-starter-webmvc-test`,
`spring-boot-starter-data-jpa-test`), que o Initializr não adiciona
automaticamente.

Esse é o modo de falha mais traiçoeiro da modularização: com apenas
`flyway-core` no classpath, a aplicação sobe normalmente e nunca executa
migração alguma. Conferir a tabela de histórico do Flyway após a primeira
execução.

### 1.2 Bootstrap

```
https://start.spring.io/#!type=gradle-project-kotlin&language=kotlin
  &packaging=jar&jvmVersion=25
  &groupId=br.com.tavaressan&artifactId=cafey-backend
  &name=cafey-backend&packageName=br.com.tavaressan.cafey
  &dependencies=web,security,oauth2-resource-server,data-jpa,postgresql,
                flyway,validation,actuator,docker-compose,testcontainers
```

Conferir na interface os rótulos: Spring Web, Spring Security, OAuth2 Resource
Server, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation,
Spring Boot Actuator, Docker Compose Support, Testcontainers.

O ZIP traz wrapper e `settings.gradle.kts` próprios. Descompactar em `backend/`
do monorepo. Não conflita com `apps/` desde que os builds fiquem independentes.

> Docker Desktop disponível no ambiente de desenvolvimento, em versão antiga
> (sem os patches mais recentes). Se `testcontainers` apresentar
> incompatibilidade com essa versão, a contingência é Postgres local e corte da
> dependência — sem impacto no marco de 04/09, que não exige testes de
> integração.

### 1.3 Dependência fora do Initializr

O SDK de dispositivo da AWS não está no catálogo do Initializr:

```kotlin
implementation("software.amazon.awssdk.iotdevicesdk:aws-iot-device-sdk:<versão>")
```

Fixar a versão consultando `github.com/aws/aws-iot-device-sdk-java-v2/releases`
no momento da instalação. O SDK antigo (`com.amazonaws:aws-iot-device-sdk-java`)
está em modo de manutenção e não recebe funcionalidades novas.

Esse SDK carrega o AWS CRT, que é binário nativo. Se isso atrapalhar em
container ou em CI, a alternativa é Eclipse Paho MQTTv5 com `SSLContext`
montado à mão a partir do certificado — mais código, sem dependência nativa.

---

## 2. Premissas assumidas

Nenhuma destas foi decidida explicitamente. Todas são reversíveis; estão aqui
para serem contestadas antes de virarem código.

| # | Premissa | Impacto se estiver errada |
|---|---|---|
| P1 | Group `br.com.tavaressan`, pacote `br.com.tavaressan.cafey` | Renomear pacote |
| P2 | Um único dispositivo no protótipo; certificado X.509 gravado manualmente no NVS ao flashar | UC-04 ganharia provisionamento de frota |
| P3 | O fuso do agendamento é atributo do dispositivo, não do usuário | Coluna muda de tabela |
| P4 | Access token de 15 min + refresh token opaco de 30 dias | Ver §4.3 |
| P5 | `duracao` não é coluna; é calculada (`fim - inicio`) | Reintroduzir coluna |
| P6 | O backend tem sua própria "thing" e certificado no AWS IoT, distinto do dispositivo | Política IAM diferente |
| P7 | O dispositivo apenas chaveia energia; a conclusão do preparo é temporizada, não observada | Entra estado terminal novo em `resultado` e telemetria em `saude` |
| P8 | `evento_id` no formato `bootId:seq`, com `seq` persistido em NVS | Chave de deduplicação repete entre boots |
| P9 | `duracao_preparo_s` padrão de 300 s, ajustável pelo dono | Muda apenas o default da coluna |

P9 é estimativa não confirmada — ver pendência 7.

---

## 3. Modelo de dados

Revisão de `spec-cafeteira-conectada.md` §7.2. As mudanças estão marcadas.

### usuarios

| Campo | Tipo | Observação |
|---|---|---|
| id | uuid | PK |
| nome | text | |
| email | citext | Único |
| senha_hash | text | BCrypt (`BCryptPasswordEncoder`, força 12) |
| criado_em | timestamptz | `default now()` |

### dispositivos

| Campo | Tipo | Observação |
|---|---|---|
| id | uuid | PK |
| serial | text | Único; identificador do ESP32 |
| apelido | text | |
| firmware | text | |
| timezone | text | **Novo.** IANA, ex. `America/Sao_Paulo` |
| estado | text | **Novo.** Espelho do tópico `estado` |
| ultimo_visto | timestamptz | Do tópico `saude` |
| ciclos_desde_descalcificacao | int | `default 0` |
| limiar_descalcificacao | int | **Novo.** `default 75` |
| versao_agendamentos | bigint | **Novo.** Contador monotônico; ver §5.3 |
| duracao_preparo_s | int | **Novo.** `default 300`. Temporizador aplicado pelo dispositivo ao ligar |

`timezone` fecha a lacuna do §7.2 original: `agendamentos.hora` era `time` sem
referência de fuso, e o ESP32 sincroniza NTP em UTC. Sem esse campo, "07:00" não
tem significado definido — o dispositivo precisa de uma referência explícita
para converter. Guardar o identificador IANA em vez de um deslocamento fixo
mantém a informação estável se o aparelho mudar de região ou se as regras de
fuso do país forem alteradas: o identificador continua válido, apenas a base de
dados de fusos é atualizada.

`limiar_descalcificacao` resolve a pendência 6 do §13 da especificação, com
default no meio da faixa de 60 a 90 ciclos citada no §7.3.

### usuario_dispositivo

| Campo | Tipo | Observação |
|---|---|---|
| usuario_id | uuid | FK, PK composta |
| dispositivo_id | uuid | FK, PK composta |
| papel | text | `DONO` \| `CONVIDADO` |
| criado_em | timestamptz | |

### agendamentos

| Campo | Tipo | Observação |
|---|---|---|
| id | uuid | PK |
| dispositivo_id | uuid | FK |
| hora | time | Interpretada no `timezone` do dispositivo |
| dias_semana | smallint | **Mudou.** Bitmask; bit 0 = domingo … bit 6 = sábado |
| ativo | boolean | |
| criado_por | uuid | FK usuarios |

Bitmask em vez de conjunto porque JPA não mapeia tipo conjunto do Postgres sem
conversor customizado. Seg–sex = `0b0111110` = 62. A leitura fica menos óbvia
em SQL cru; o ganho é não escrever `AttributeConverter`.

### eventos_preparo

| Campo | Tipo | Observação |
|---|---|---|
| id | uuid | PK |
| dispositivo_id | uuid | FK |
| evento_id | text | **Novo.** `bootId:seq` gerado no dispositivo |
| inicio | timestamptz | |
| fim | timestamptz | **Mudou.** Não nulo: instante do corte de energia |
| resultado | text | **Novo.** `CONCLUIDO` \| `CANCELADO` |
| origem | text | `APP` \| `AGENDAMENTO` \| `BOTAO` |
| recebido_em | timestamptz | Distinguir evento atrasado de evento ao vivo |

```sql
CONSTRAINT uq_evento_dedupe UNIQUE (dispositivo_id, evento_id)
```

A deduplicação usa `evento_id`, não `inicio`. O preparo pode ocorrer antes da
sincronização NTP — justamente o cenário offline que motiva o proxy BLE — e
nesse caso o timestamp é revisado pelo próprio dispositivo depois. Chave de
identidade não pode depender de dado que a origem corrige. Dois preparos
anteriores à sincronização também receberiam o mesmo instante de época e
colidiriam sob a restrição antiga.

O evento é publicado uma única vez, em estado terminal, e por isso `fim` é não
nulo. `CONCLUIDO` é o corte pelo temporizador local; `CANCELADO` é o corte por
comando (UC-09). Sem sensor, não existe terceiro desfecho detectável: queda de
energia no meio do preparo não gera evento, e isso é limitação declarada, não
lacuna de implementação.

A ingestão usa `ON CONFLICT (dispositivo_id, evento_id) DO NOTHING`. `duracao`
continua calculada (`fim - inicio`), agora sempre definida.

### refresh_tokens

**Nova tabela.** Ver §4.3.

| Campo | Tipo | Observação |
|---|---|---|
| id | uuid | PK |
| usuario_id | uuid | FK |
| familia_id | uuid | Agrupa a cadeia de rotações de uma sessão |
| token_hash | text | SHA-256 do token; nunca o valor em claro |
| expira_em | timestamptz | |
| revogado_em | timestamptz | Nulo enquanto válido |

---

## 4. Autenticação e autorização

### 4.1 Mecanismo

JWT assinado em RS256, emitido pelo próprio backend. A validação usa o suporte
a OAuth2 Resource Server do Spring Security, que aceita qualquer Bearer token,
inclusive JWT próprio, bastando um bean `JwtDecoder`.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          public-key-location: classpath:jwt/public.pem
```

A chave privada fica fora do repositório, injetada por variável de ambiente.
Gerar o par com `openssl genrsa` / `openssl rsa -pubout`. A chave pública pode
ser versionada; a privada, nunca.

### 4.2 Claims

| Claim | Conteúdo |
|---|---|
| `sub` | `usuarios.id` |
| `iss` | `cafey-backend` |
| `exp` | Emissão + 15 min |
| `iat` | Emissão |

Sem claim de papel: o papel (`DONO` / `CONVIDADO`) é por dispositivo, não por
usuário, e colocá-lo no token o tornaria obsoleto a cada compartilhamento. A
autorização consulta `usuario_dispositivo` na requisição.

### 4.3 Janela de revogação

O JWT stateless não é revogável. Isso colide com o UC-05: ao remover um
convidado, o token dele continua aceito até expirar. O par 15 min / 30 dias
limita a janela a 15 minutos e mantém a sessão viva em mobile sem login
repetido. Refresh é rotativo — cada uso revoga o anterior.

*Alternativa mais simples, se o prazo apertar:* access token de 7 dias sem
refresh token, aceitando janela de revogação de 7 dias e descartando a tabela
`refresh_tokens`. Documentar a escolha para a banca em qualquer um dos casos.

A rotação de refresh token exige detecção de reuso: ao receber um token já
revogado, revogar toda a família (`familia_id`) do usuário. Sem isso, um token
furtado permanece utilizável até a próxima rotação legítima, e a rotação deixa
de ter efeito de segurança.

### 4.4 Regras de acesso

| Recurso | Regra |
|---|---|
| `POST /auth/**` | Público |
| Demais rotas | Autenticado |
| Operar / agendar em um dispositivo | `DONO` ou `CONVIDADO` do dispositivo |
| Compartilhar, renomear, remover dispositivo | Apenas `DONO` |
| `POST /dispositivos/{id}/eventos` (proxy BLE) | `DONO` ou `CONVIDADO`; rejeitar `inicio` no futuro ou anterior a N dias; `origem` é valor declarado pelo cliente, não verificado |
| `/actuator/health` | Público; demais endpoints do Actuator, autenticado |

CSRF desabilitado (não há cookie de sessão). CORS liberado apenas para as
origens dos clientes, configurado por propriedade.

---

## 5. API REST

Prefixo `/api/v1`. Erros no formato Problem Details (RFC 9457, que obsoleta a
RFC 7807).

### 5.1 Endpoints

| Método | Rota | UC |
|---|---|---|
| POST | `/auth/registrar` | UC-01 |
| POST | `/auth/login` | UC-02 |
| POST | `/auth/refresh` | — |
| POST | `/auth/logout` | — |
| POST | `/auth/senha/esqueci` | UC-03 |
| POST | `/auth/senha/redefinir` | UC-03 |
| GET | `/dispositivos` | — |
| POST | `/dispositivos` | UC-04 |
| GET | `/dispositivos/{id}` | UC-08 |
| PATCH | `/dispositivos/{id}` | — |
| POST | `/dispositivos/{id}/compartilhamentos` | UC-05 |
| DELETE | `/dispositivos/{id}/compartilhamentos/{usuarioId}` | UC-05 |
| POST | `/dispositivos/{id}/comandos` | UC-06, 07, 09 |
| GET | `/dispositivos/{id}/agendamentos` | — |
| POST | `/dispositivos/{id}/agendamentos` | UC-10 |
| PUT | `/dispositivos/{id}/agendamentos/{agId}` | UC-11, UC-13 |
| DELETE | `/dispositivos/{id}/agendamentos/{agId}` | UC-12 |
| GET | `/dispositivos/{id}/eventos` | UC-14 |
| POST | `/dispositivos/{id}/eventos` | Proxy BLE (§6.5 da spec) |
| GET | `/dispositivos/{id}/estatisticas` | UC-15 |
| GET | `/dispositivos/{id}/manutencao` | UC-16 |
| POST | `/dispositivos/{id}/manutencao/baixa` | UC-17 |

UC-06, 07 e 09 compartilham a mesma rota porque são a mesma operação com ação
diferente no corpo — `{"acao": "LIGAR" | "DESLIGAR" | "CANCELAR"}`.

`POST /dispositivos/{id}/eventos` é a porta do proxy BLE: o app envia eventos
em nome do dispositivo, e a restrição de deduplicação absorve reenvios.

`POST /dispositivos/{id}/comandos` responde `202` com `comandoId`. O desfecho
não vem na resposta: o cliente o obtém por `GET /dispositivos/{id}` (campo
`estado`) ou pelo histórico de eventos.

### 5.2 Ordem de corte

Seguindo §2.4 da especificação, na ordem: compartilhamentos (UC-05), depois
`/estatisticas` (mantendo `/eventos` em lista), depois `/auth/senha/*` (UC-03).

### 5.3 Efeito colateral obrigatório

Toda escrita em agendamento (`POST`, `PUT`, `DELETE`) incrementa
`dispositivos.versao_agendamentos` **no banco**
(`UPDATE ... SET versao_agendamentos = versao_agendamentos + 1 ... RETURNING`) e
republica a **lista completa** no tópico `agendamentos` **após o commit**, via
`@TransactionalEventListener(AFTER_COMMIT)`.

Não existe transação abrangendo Postgres e MQTT. Publicar antes do commit pode
entregar ao dispositivo um agendamento que sofreu rollback; publicar depois pode
falhar em silêncio — e é este o caso aceito, porque a publicação é auto-curável:
a lista é completa e a versão é monotônica, bastando republicar quando o
dispositivo reaparecer em `saude` com `online: true`. Publicar delta violaria a
idempotência exigida pelo §6.3 da especificação.

O incremento precisa ocorrer no banco. Um read-modify-write pelo JPA perde
atualização concorrente e publica duas listas com a mesma versão — o firmware
ignoraria a segunda pela regra de `<=`, divergindo em silêncio.

---

## 6. Integração MQTT

### 6.1 Cliente

Um único cliente MQTT compartilhado por toda a aplicação, autenticado por
certificado X.509 próprio do backend (premissa P6), conectado na porta 8883.
Reconexão automática com backoff exponencial. Assinaturas em QoS 1:

```
dispositivos/+/estado
dispositivos/+/eventos
dispositivos/+/saude
```

A política IAM do backend precisa cobrir `iot:Connect`, `iot:Subscribe`,
`iot:Receive` nesses filtros e `iot:Publish` mais `iot:RetainPublish` em
`comando` e `agendamentos`.

A política do **dispositivo** precisa, além disso, de uma `Condition` com
`iot:ConnectAttributes: LastWill` no statement de `iot:Connect`, para que o Will
possa ser publicado com retain.

### 6.2 Payloads

A especificação define os tópicos (§6.2) mas não o formato das mensagens. O que
segue é proposta, e precisa ser fechada em conjunto com o firmware — é contrato
compartilhado, versionado junto de ambos os lados (§9 da spec, manutenibilidade).

**`dispositivos/{id}/comando`** — descendente, QoS 1, sem retain

```json
{
  "comandoId": "uuid",
  "acao": "LIGAR",
  "duracaoS": 300,
  "emitidoEm": "2026-09-01T13:00:00Z"
}
```

`comandoId` permite ao firmware descartar reentrega de QoS 1.

**`dispositivos/{id}/agendamentos`** — descendente, QoS 1, retain

```json
{
  "versao": 7,
  "timezone": "America/Sao_Paulo",
  "duracaoS": 300,
  "agendamentos": [
    { "id": "uuid", "hora": "07:00", "diasSemana": 62, "ativo": true }
  ]
}
```

`versao` é monotônico. O firmware ignora mensagem com versão menor ou igual à
que já tem gravada em NVS — protege contra retain antigo entregue na reconexão.
`duracaoS` viaja também aqui porque o disparo agendado ocorre offline, sem
comando que a carregue.

**`dispositivos/{id}/estado`** — ascendente, QoS 1, retain

```json
{ "estado": "PREPARANDO", "desde": "2026-09-01T13:00:04Z", "firmware": "1.0.0" }
```

**`dispositivos/{id}/eventos`** — ascendente, QoS 1, sem retain

Publicação única, em estado terminal:

```json
{
  "eventoId": "a3f10c42:7",
  "inicio": "2026-09-01T13:00:04Z",
  "fim": "2026-09-01T13:05:04Z",
  "resultado": "CONCLUIDO",
  "origem": "BOTAO"
}
```

O mesmo corpo vale para `POST /dispositivos/{id}/eventos`, mantendo uma
serialização só nos dois caminhos.

**`dispositivos/{id}/saude`** — ascendente, QoS 1, Last Will

```json
{ "online": true, "uptimeS": 3600, "rssi": -62 }
```

Last Will: `{ "online": false }`. É o que permite detectar queda do dispositivo
sem polling.

### 6.3 Retain e Last Will

`iot:RetainPublish` é exigida para publicar com retain e precisa ser concedida
junto de `iot:Publish`. Will message retido exige `Condition` com
`iot:ConnectAttributes: LastWill` no `iot:Connect` da política do dispositivo.

Limitação relevante do AWS IoT Core: mensagem retida não é entregue a quem
assina com wildcard — o filtro precisa casar exatamente com o tópico. O backend,
assinando `dispositivos/+/estado`, não recebe o estado retido ao reconectar. O
sentido descendente permanece correto, porque o ESP32 assina o próprio tópico
exato, e é dele que depende a ressincronização após reboot descrita no §6.3 da
especificação. Para o estado, o banco é a fonte de verdade e `ultimo_visto`
indica o frescor.

---

## 7. Estrutura de pacotes

```
br.com.tavaressan.cafey
├── CafeyApplication.kt
├── config/          CORS, Jackson, propriedades
├── security/        JwtEncoder, filtros, SecurityFilterChain
├── user/            entidade, repositório, serviço, controller
├── device/          idem, incluindo compartilhamento
├── schedule/        idem, incluindo republicação MQTT
├── event/           ingestão, histórico, estatísticas
├── maintenance/     contador e alerta de descalcificação
└── mqtt/            cliente, assinaturas, serialização de payloads
```

Fatiado por domínio, não por camada. Cada pacote tem entidade, repositório,
serviço e controller próprios.

---

## 8. Migrações Flyway

| Arquivo | Conteúdo |
|---|---|
| `V1__usuarios.sql` | `usuarios`, extensão `citext` |
| `V2__auth.sql` | `refresh_tokens`, incluindo `familia_id` |
| `V3__dispositivos.sql` | `dispositivos` (com `duracao_preparo_s int NOT NULL DEFAULT 300`), `usuario_dispositivo` |
| `V4__agendamentos.sql` | `agendamentos` |
| `V5__eventos.sql` | `eventos_preparo`, restrições e índice |

```sql
-- V5__eventos.sql
CREATE TABLE eventos_preparo (
  id             uuid        PRIMARY KEY,
  dispositivo_id uuid        NOT NULL REFERENCES dispositivos (id),
  evento_id      text        NOT NULL,
  inicio         timestamptz NOT NULL,
  fim            timestamptz NOT NULL,
  resultado      text        NOT NULL,
  origem         text        NOT NULL,
  recebido_em    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_evento_dedupe UNIQUE (dispositivo_id, evento_id),
  CONSTRAINT ck_evento_resultado CHECK (resultado IN ('CONCLUIDO', 'CANCELADO')),
  CONSTRAINT ck_evento_origem    CHECK (origem IN ('APP', 'AGENDAMENTO', 'BOTAO')),
  CONSTRAINT ck_evento_intervalo CHECK (fim >= inicio)
);

CREATE INDEX ix_eventos_dispositivo_inicio
  ON eventos_preparo (dispositivo_id, inicio DESC);
```

`spring.jpa.hibernate.ddl-auto=validate`. O Flyway é a única autoridade sobre o
esquema; o Hibernate apenas confere se as entidades batem.

---

## 9. Divergências em relação à especificação principal

Requerem atualização de `spec-cafeteira-conectada.md` antes da banca:

1. `agendamentos.dias_semana`: conjunto → `smallint` bitmask
2. `eventos_preparo.duracao`: removida, passa a ser calculada
3. `eventos_preparo`: adicionadas `recebido_em` e restrição de deduplicação
4. `dispositivos`: adicionadas `timezone`, `estado`, `limiar_descalcificacao`, `versao_agendamentos`
5. Nova tabela `refresh_tokens`
6. `eventos_preparo`: identidade passa a ser `evento_id` gerado no dispositivo; `fim` não nulo; nova coluna `resultado`
7. `dispositivos`: adicionada `duracao_preparo_s` — o fim do preparo é temporizado, não observado

Registrado também na especificação principal:

- §7.3: o contador de descalcificação incrementa apenas em `CONCLUIDO`; preparo cancelado não completa o ciclo de água
- §14 (novo): o sistema reporta **acionamento**, não conclusão de extração. Sem sensor, queda de energia durante o preparo não gera evento

Somam-se às duas divergências já registradas em `STATUS.md` (alimentação
resolvida em §4.4; isolamento galvânico incorreto em §4.5 e §9).

---

## 10. Ordem de execução

Marco de 04/09 pede "backend autentica ponta a ponta". Só os itens 1 a 4
pertencem a esse marco; MQTT é Fase 2.

| # | Item | Fase |
|---|---|---|
| 1 | Esqueleto do Initializr, Postgres via Docker Compose | 1 |
| 2 | `V1`, `V2` e domínio de usuário | 1 |
| 3 | Emissão e validação de JWT, `SecurityFilterChain` | 1 |
| 4 | `/auth/registrar`, `/auth/login`, `/auth/refresh` | 1 |
| 5 | `V3`, CRUD de dispositivos e vínculo | 2 |
| 6 | Cliente MQTT, assinaturas, ingestão de estado e saúde | 2 |
| 7 | `V4`, agendamentos com republicação de lista completa | 2 |
| 8 | `V5`, ingestão de eventos e histórico | 3 |
| 9 | Estatísticas e contador de descalcificação | 3 |
| 10 | Rota do proxy BLE | 4 |

---

## 11. Pendências

| # | Item | Estado |
|---|---|---|
| 1 | Confirmar as premissas P1 a P9 (§2) | Aberta |
| 2 | Fechar payloads MQTT com o firmware | **Reduzida.** Resta o formato de `bootId:seq` e o comportamento quando o preparo termina antes da sincronização NTP |
| 3 | `iot:RetainPublish` | **Fechada** (§6.3) |
| 4 | Criar thing e certificado do backend no AWS IoT Core | Aberta |
| 5 | Fixar a versão do `aws-iot-device-sdk` | Aberta |
| 6 | Decidir se `/auth/senha/*` entra ou é cortado | Aberta. Se entrar, falta tabela de token de redefinição e uma `V6` |
| 7 | Confirmar `duracao_preparo_s` com o tempo real de extração da Britânia CP30 | **Nova.** Bloqueia `V3` |

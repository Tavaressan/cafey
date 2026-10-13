---
id: spec:atividade-integrada-backend-parte-1
type: spec
project: cafey
status: draft
created: 2026-10-13
updated: 2026-10-13
---
# Atividade Integrada — Parte 1 do backend (BE-28 a BE-35)

## Summary

Fecha as pendências do backend (Kotlin, Spring Boot, Spring Security) para cumprir a Parte 1 da Atividade Integrada sobre as entidades `Dispositivo` e `Agendamento`: tamanho das colunas String, campo `descricao`, busca parcial (LIKE), leitura de agendamento por id, camada mapper, Bean Validation, rotas `/auth/signin` e `/auth/signup` com `AuthenticationManager` e filtro JWT próprio. Consolida as issues #210 a #217 do épico #209 com rastreabilidade (cada RF cita a issue de origem).

## Context

Estado de referência: `main` em `700d3a8` (13/10/2026). O épico #209 pedia integrar antes as PRs #203 (migration V7, duração por agendamento) e #204 (cuidados). As duas e a #221 já estão na `main`, então a V8 da #211 está liberada. O texto das issues #210 a #217 é de 10/10/2026, anterior a esses merges, e esta Spec já os incorpora:

- `V7__agendamentos_duracao_preparo.sql` e `Agendamento.duracaoPreparoS: Int?` existem. O `PostgresIntegrationTest` hoje espera as versões V1 a V7.
- A V7 já foi implantada em produção: o workflow `Deploy Backend` do commit `9522d5f` (que traz a V7) terminou com sucesso em 13/10/2026 (execução 38434495054), e as execuções dos commits `a97c42f` e `700d3a8`, posteriores, também (verificado com `gh run list`). Além disso, em 13/10/2026 o `GET https://cafey-backend.duckdns.org/v3/api-docs` de produção respondeu 200. Ele expõe `duracaoPreparoS` em `AgendamentoResponse` e as rotas `/cuidados`, `/enxague/baixa` e `/filtro/baixa`, ou seja, a produção roda pelo menos o `a97c42f`. Com `ddl-auto: validate`, essa versão só sobe se a coluna da V7 existir, então a V7 está aplicada. O `flyway_schema_history` não foi lido diretamente.
- `ScheduleDtoValidationTest`, `CuidadosIntegrationTest`, `OpenApiDocsTest` e o teste de `AgendamentoServiceTest` que captura o `AgendamentosPayload` existem.
- `DispositivoService.atualizar` (commit `700d3a8`) compara timezone (após `trim`) e duração antes de atribuir, para incrementar `versaoAgendamentos` uma única vez e publicar `AgendamentosAlteradosEvent`. A issue #214 não descreve isso.
- Números de linha citados nas issues estão defasados em `DispositivoService` (`criar`, `atualizar` e `toResponse` estão hoje nas linhas 70, 94 e 220).
- `SecurityConfig.kt:74` usa `permitAll` no curinga `/auth/**`, e `SecurityConfig.kt:77` libera `/actuator/**`. O `AuthController` tem hoje exatamente 5 rotas, todas `POST` e públicas: `/auth/registrar`, `/auth/login`, `/auth/refresh`, `/auth/recuperar-senha` e `/auth/redefinir-senha`. Não existe rota `/auth/*` que exija token (o `logout` aparece só na tabela da §5.1 da spec v1.2, não no código).

Fontes lidas: épico #209; issues #210 a #217; código em `backend/cafey-backend/src/main/kotlin/br/com/tavaressan/cafey/` (`device/`, `schedule/`, `auth/`, `security/`, `config/`, `exception/`, `event/EventoDto.kt`, `event/EventoService.kt`, `mqtt/MqttPayloads.kt`); migrations V1 a V7; testes citados; `apps/shared/.../network/AuthApi.kt` e `ApiClient.kt`; `.github/workflows/deploy-backend.yml` e `deploy-webapp.yml`; `docs/documentacao_backend_pi_2_6/spec-backend-v1.2.md` (§4.4 e §5.1); `firmware/main/storage/event_queue_store.hpp`.

Mapa dos itens do enunciado para os componentes:

| Item | Exigência | Componente | Origem |
|---|---|---|---|
| a | Entidades, id e tamanho das Strings | 1 | #210, #211 |
| b | Repositórios com JPQL e LIKE | 4 | #212 (depende de #211) |
| c, d | Serviços e controllers com CRUD, GET por id e GET de listagem | 3 | #213 |
| e | `/signin` e `/signup` com Spring Security e JWT | 6 | #216 |
| f | Utilitário JWT, filtro, `SecurityFilterChain`, rotas públicas e protegidas | 7 | #217 |
| g | DTOs e camada mapper | 2 | #214 |
| h | Bean Validation, 2 regras diferentes por DTO | 5 | #215 |

Componentes:

1. Modelo de dados das entidades da atividade (#210, #211).
2. Camada mapper (#214).
3. Consulta de agendamento por id (#213).
4. Busca parcial (LIKE) nas listagens (#212).
5. Validação de entrada (#215).
6. Autenticação por credenciais: `/auth/signin` e `/auth/signup` (#216).
7. Autenticação por token JWT e autorização de rotas (#217).

Ordem de execução do épico: #210, #214, #213, #211, #212, #215, #216, #217; DOC-07 (#218) por último.

Premissas (assumidas, não confirmadas):

- P1: o prefixo `/auth` é aceito pelo professor, ficando `/auth/signin` e `/auth/signup`. Se for exigida a rota na raiz (`/signin`), basta mapear também os caminhos sem prefixo e incluí-los na lista de rotas públicas e no rate limit (épico #209 e #216).
- P2: "listar todos os elementos" significa todos os que o usuário pode ver. `GET /dispositivos` e `GET /dispositivos/{dispositivoId}/agendamentos` ficam restritos ao dono ou aos vinculados; não existe listagem global entre usuários (épico #209 e #213).
- P3: a camada mapper fica em pasta e pacote próprios, `mapper/` (`br.com.tavaressan.cafey.mapper`) (épico #209 e #214).
- P4: o texto do enunciado da Atividade Integrada não está no repositório. As exigências (JPQL com LIKE, `AuthenticationManager`, filtro JWT próprio, camada mapper) são conhecidas apenas pelas citações nas issues e são tratadas como restrições externas, o que justifica citar tecnologia em alguns requisitos.
- P5: as duas entidades da atividade são `Dispositivo` e `Agendamento`. `Usuario`, `EventoPreparo` e as entidades de token ficam de fora (épico #209).

Afirmações das issues não verificadas nesta Spec (vêm de "documentação e experimentos" segundo o épico): o `ddl-auto: validate` do Hibernate 7.4 compara só o tipo da coluna, não o tamanho; os construtores do `DaoAuthenticationProvider` no Spring Security 7.1; o `BearerTokenAuthenticationFilter` responde 401 mesmo em rota `permitAll`; o decoder aceita até 60 s de diferença de relógio; o cliente Ktor envia o token guardado em todas as requisições (a documentação do Ktor consultada descreve como padrão enviar só após um 401, então isto permanece não confirmado e não altera nenhum critério abaixo).

## Goals

1. Cumprir os itens a a h da Parte 1 da Atividade Integrada no backend, para `Dispositivo` e `Agendamento`, demonstráveis pelo Swagger UI.
2. Não quebrar clientes publicados (web, Android e Desktop) nem o contrato MQTT com o firmware.
3. Manter a autenticação de ponta a ponta funcionando (emissão, validação e leitura do usuário do token) com código do projeto visível para a correção.
4. Fechar as lacunas de validação que hoje deixam entrar dados que o banco não suporta (500 por `timezone` longo, duração e limiar fora de faixa).

## Non-Goals

- README da raiz (DOC-07, #218) e convite do professor como colaborador (DOC-08, #219).
- Parte 2 (frontend): entidades no app, formulários "Gravar" e "Limpar Campos", e exibir ou editar `descricao` no app.
- Validar se `timezone` é um fuso IANA válido. Um fuso inválido continua sendo gravado e quebra `GET /dispositivos/{id}/estatisticas` com 500 (`EventoService` chama `ZoneId.of`). Fica para outra issue.
- Escapar `%` e `_` digitados no termo de busca (viram curinga do LIKE; o valor vai como parâmetro, então não há injeção, só resultado mais amplo; limitação conhecida) e paginar a busca.
- Busca tolerante a acento: `manha` não encontra "manhã". Registrado como não objetivo.
- Remover os aliases `/auth/login` e `/auth/registrar`.
- Listagem global entre usuários.
- Mudar o payload MQTT de agendamentos.
- Alterar o conjunto de durações permitidas do agendamento (240, 360, 480 e 600 s), já em vigor.
- Conversões de outras features no mapper (eventos, compartilhamentos, autenticação, payloads MQTT).
- Validação do `TestDto` (andaime oculto em `controller/TestController.kt`, `@Profile("!prod")`).
- Adicionar o Spring Boot Actuator ao projeto.
- Rate limit em rotas além das já limitadas (`/auth/refresh` e `/auth/redefinir-senha` seguem sem limite).
- Verificar no banco, a cada requisição, se o usuário do token ainda existe (o comportamento atual é mantido, ver Security / Privacy).

## Users / Personas

- Avaliador (professor): confere o código-fonte contra o enunciado e a demonstração pelo Swagger.
- Desenvolvedor (único integrante do projeto): implementa e demonstra.
- Usuário final do app: dono ou convidado de um dispositivo, que lista, busca e agenda.
- Clientes publicados (app web, builds Android e Desktop): consomem a API e não podem quebrar.
- Firmware do ESP32: consome o payload retido de agendamentos e o proxy BLE alimenta a ingestão de eventos.

## Functional Requirements

Prioridades: Must = exigido por um item da Parte 1 ou protege comportamento já publicado; Should = pode ser adiado sem comprometer a nota.

### Componente 1 - Modelo de dados das entidades da atividade (origem: #210, #211)

### RF-01 - Tamanho declarado das colunas String de Dispositivo

**Priority:** Must

**Description:**

A entidade `Dispositivo` declara o tamanho dos seus três campos String com os mesmos valores da migration V3, para a entidade descrever o banco real. Nenhuma migration nova. (origem: #210)

**Acceptance Criteria:**

- [ ] `nome` declara tamanho 255; `timezone` e `estado` declaram tamanho 50 (iguais a `varchar(255)`, `varchar(50)` e `varchar(50)` da V3). Conferido na revisão de código, porque o `validate` do Hibernate não compara tamanho (afirmado pela issue).
- [ ] Nenhuma migration nova é criada por este requisito.
- [ ] `./gradlew test` passa, incluindo `PostgresIntegrationTest` (Flyway com `ddl-auto: validate`), verificado no CI Backend.

### RF-02 - Campo descricao do Agendamento: persistência

**Priority:** Must

**Description:**

O `Agendamento` ganha um campo String opcional `descricao`, rótulo livre de até 100 caracteres (ex.: "Café da manhã"), criado pela migration V8. Agendamentos existentes ficam sem descrição (`NULL`). Com o campo, o `Agendamento` passa a ter 4 tipos da lista do professor (String, LocalTime, Boolean, Int). (origem: #211)

**Acceptance Criteria:**

- [ ] A migration `V8__agendamentos_descricao.sql` adiciona `agendamentos.descricao` como `character varying(100)`, aceitando nulo.
- [ ] `PostgresIntegrationTest` passa a esperar o histórico Flyway exatamente V1 a V8 (hoje espera V1 a V7; nome do teste e KDoc atualizados).
- [ ] É acrescentado ao `PostgresIntegrationTest` um teste que lê `information_schema.columns` e confere, para `agendamentos.descricao`, `data_type = 'character varying'`, `character_maximum_length = 100` e `is_nullable = 'YES'`. O teste existente sobre `agendamentos` cobre apenas `hora`, `criado_em` e `atualizado_em`, e não serve para isso.
- [ ] `Agendamento.descricao` é mapeado com tamanho 100, o mesmo da V8.
- [ ] Agendamentos antigos (descrição nula) continuam listando normalmente.

### RF-03 - Campo descricao do Agendamento: API

**Priority:** Must

**Description:**

Criar (POST) e atualizar (PUT) agendamento aceitam `descricao`, e as respostas de POST, PUT e GET devolvem o campo. A descrição é normalizada antes de gravar: `trim` e, se ficar vazia, vira `null`. No PUT, ausente ou `null` significa não alterar (mesma semântica dos outros campos); enviar `""` ou só espaços limpa a descrição. (origem: #211)

**Acceptance Criteria:**

- [ ] POST e PUT aceitam `descricao`; as respostas de POST, PUT e GET (lista e por id) devolvem o campo.
- [ ] `descricao` com 101 ou mais caracteres resulta em 400 Problem Details com a mensagem "Descrição deve ter no máximo 100 caracteres" e o nome do campo.
- [ ] PUT sem `descricao` (ausente ou `null`) não altera o valor salvo.
- [ ] PUT com `""` ou só espaços grava `null`; POST com `""` ou só espaços também grava `null`.
- [ ] Espaços nas pontas são removidos antes de gravar.
- [ ] Testes de serviço e de validação do DTO cobrem os casos acima.

### RF-04 - Payload MQTT de agendamentos não recebe a descrição

**Priority:** Must

**Description:**

O firmware não precisa da descrição, e o payload retido de agendamentos mantém o mesmo conteúdo e tamanho. (origem: #211)

**Acceptance Criteria:**

- [ ] No `AgendamentoServiceTest`, o teste que captura o `AgendamentosPayload` publicado pelo `AgendamentosMqttSyncListener` usa um agendamento com `descricao` preenchida e verifica que o JSON serializado do payload não contém `"descricao"`.
- [ ] `AgendamentoItemPayload` não ganha campo novo.

### Componente 2 - Camada mapper (origem: #214)

### RF-05 - Camada mapper para as duas entidades

**Priority:** Must

**Description:**

As conversões entre DTO de request, entidade e DTO de response de `Dispositivo` e `Agendamento` ficam numa camada própria, o pacote `br.com.tavaressan.cafey.mapper` (P3), com `DispositivoMapper` e `AgendamentoMapper` injetados nos serviços. O `AgendamentoMapper` trata `duracaoPreparoS` e `descricao` (com a normalização de RF-03). A decisão de negócio de republicar os agendamentos não pertence ao mapper. (origem: #214; campos de #203 e #211)

**Acceptance Criteria:**

- [ ] O pacote `br.com.tavaressan.cafey.mapper` contém `DispositivoMapper` e `AgendamentoMapper`.
- [ ] As conversões ficam nos mappers: request para entidade (com os padrões atuais e `trim`), aplicação parcial de update (só campos não nulos), entidade para response e o formato `"HH:mm"`. Os serviços não mantêm `toResponse` privado.
- [ ] A detecção de mudança efetiva de timezone ou duração, o incremento único de `versaoAgendamentos` e a publicação de `AgendamentosAlteradosEvent` ficam no serviço.
- [ ] O comentário que explica por que ler `agendamento.dispositivo.id` não inicializa o proxy lazy do Hibernate é mantido no `AgendamentoMapper`.

### RF-06 - Refatoração sem mudança de comportamento

**Priority:** Must

**Description:**

Introduzir os mappers não altera nenhum comportamento da API. As regras de negócio (checagem de vínculo e papel, incremento de `versaoAgendamentos`, publicação de `AgendamentosAlteradosEvent` e a regra de republicação por mudança de timezone ou duração do commit `700d3a8`) continuam no serviço. (origem: #214)

**Acceptance Criteria:**

- [ ] Os testes que já existem passam sem mudar as asserções; só a montagem dos serviços nos testes unitários muda, usando instâncias reais dos mappers (`DispositivoMapper()` e `AgendamentoMapper()`), não `@Mock`.
- [ ] Os testes de republicação de `DispositivoServiceTest` (timezone igual após `trim`, bump único de versão quando timezone e duração mudam juntos, só o nome muda, duração igual) passam sem mudar asserções.
- [ ] Os JSON das respostas existentes não mudam (o campo `descricao` de RF-03 é o único acréscimo).

### RF-07 - Testes unitários dos mappers

**Priority:** Must

**Description:**

Os mappers têm testes unitários próprios, sem contexto Spring. (origem: #214)

**Acceptance Criteria:**

- [ ] Teste dos padrões (timezone `America/Sao_Paulo`, duração 300 s, limiar 200) e do `trim` no `toEntity` de dispositivo.
- [ ] Teste de atualização parcial no `DispositivoMapper`: campo `null` não altera o valor (ao menos `nome` e `limiarDescalcificacao`).
- [ ] Teste de atualização parcial no `AgendamentoMapper`: `descricao` `null` preserva o valor existente e `""` (ou só espaços) limpa, conforme RF-03; `duracaoPreparoS` `null` preserva.
- [ ] Teste do formato `"HH:mm"` de ida e volta no agendamento.
- [ ] Teste de que o `toResponse` preenche todos os campos, incluindo `duracaoPreparoS` e `descricao`.

### Componente 3 - Consulta de agendamento por id (origem: #213)

### RF-08 - Buscar agendamento por id

**Priority:** Must

**Description:**

Passa a existir `AgendamentoService.obterPorId(dispositivoId, agendamentoId, usuarioId)` e a rota `GET /dispositivos/{dispositivoId}/agendamentos/{agendamentoId}`, completando o CRUD (criar, listar, buscar por id, atualizar, remover) das duas entidades. A checagem "o agendamento existe e pertence ao dispositivo da rota" tem o mesmo resultado em obter, atualizar e excluir. (origem: #213)

**Acceptance Criteria:**

- [ ] Testes de serviço (`AgendamentoService.obterPorId`): sucesso devolve o `AgendamentoResponse` (com `duracaoPreparoS` e `descricao`); `agendamentoId` inexistente lança "Agendamento não encontrado"; agendamento de outro dispositivo lança "Agendamento não pertence a este dispositivo", mesmo que o usuário tenha acesso aos dois dispositivos; usuário sem vínculo lança a mesma exceção de acesso das outras rotas, sem revelar se o dispositivo existe.
- [ ] Testes de controller (HTTP): sucesso responde 200 com o corpo; inexistente, outro dispositivo e sem vínculo respondem 404 em Problem Details; sem token responde 401.
- [ ] A rota aparece na documentação OpenAPI (Swagger UI) com a resposta 404 documentada como Problem Details.
- [ ] `atualizar` e `excluir` mantêm as mesmas respostas 404 de hoje.

### Componente 4 - Busca parcial (LIKE) nas listagens (origem: #212)

### RF-09 - Busca de dispositivos por nome

**Priority:** Must

**Description:**

`GET /dispositivos?nome=<termo>` devolve apenas os dispositivos vinculados ao usuário do token (dono ou convidado) cujo nome contém o termo, sem diferenciar maiúsculas de minúsculas, ordenados por nome. O repositório tem ao menos uma consulta JPQL com LIKE sobre `nome` (item b do enunciado). (origem: #212)

**Acceptance Criteria:**

- [ ] `DispositivoRepository` tem pelo menos 1 método `@Query` em JPQL com `LIKE` sobre `nome`.
- [ ] A busca é parcial e ignora maiúsculas e minúsculas: `nome=COZ` encontra "Cafeteira da Cozinha".
- [ ] Termo sem correspondência: 200 com lista vazia.
- [ ] Teste com 2 usuários e nomes parecidos: cada um só recebe os dispositivos vinculados a ele.
- [ ] O `papel` de cada item da busca está correto e é montado sem uma consulta por item (sem N+1).
- [ ] Testes de repositório (casamento parcial, maiúsculas, escopo) e de controller.

### RF-10 - Busca de agendamentos por descrição

**Priority:** Must

**Description:**

`GET /dispositivos/{dispositivoId}/agendamentos?descricao=<termo>` devolve apenas os agendamentos daquele dispositivo cuja `descricao` contém o termo, sem diferenciar maiúsculas de minúsculas, ordenados por hora. Agendamentos com descrição nula não casam. O repositório tem ao menos uma consulta JPQL com LIKE sobre `descricao`. A rota exige o mesmo vínculo das demais rotas do dispositivo. (origem: #212; depende de #211)

**Acceptance Criteria:**

- [ ] `AgendamentoRepository` tem pelo menos 1 método `@Query` em JPQL com `LIKE` sobre `descricao`.
- [ ] A busca é parcial e ignora maiúsculas e minúsculas: `descricao=Manha` encontra "Cafe da manha".
- [ ] A busca não tolera acento: `descricao=manha` NÃO encontra "manhã" (coerente com o Non-Goal).
- [ ] Termo sem correspondência: 200 com lista vazia.
- [ ] A busca só devolve agendamentos do dispositivo da rota.
- [ ] Usuário sem vínculo com o dispositivo: 404, como nas outras rotas.
- [ ] Testes de repositório (casamento parcial, maiúsculas, acento, escopo, descrição nula) e de controller.

### RF-11 - Filtro opcional: ausência, branco e termo

**Priority:** Must

**Description:**

O parâmetro é opcional nas duas listagens, que já existem. Ausente ou em branco, a rota devolve a lista completa, exatamente como hoje (a rota de "listar todos" do item d continua existindo). O termo informado é usado sem os espaços das pontas (`trim`). (origem: #212)

**Acceptance Criteria:**

- [ ] Sem o parâmetro, ou com ele vazio ou só espaços, `GET /dispositivos` e `GET /dispositivos/{id}/agendamentos` devolvem o mesmo resultado de hoje.
- [ ] Um termo com espaços nas pontas (ex.: `" coz "`) é aplicado sem eles e encontra o mesmo que `coz`.
- [ ] Os parâmetros `nome` e `descricao` aparecem documentados no Swagger UI com descrição.
- [ ] Testes de controller cobrem filtro, ausência de filtro, parâmetro em branco e termo com espaços.

### Componente 5 - Validação de entrada (origem: #215)

### RF-12 - Validação de nome, duração e limiar do dispositivo

**Priority:** Must

**Description:**

`CriarDispositivoRequest` e `AtualizarDispositivoRequest` recusam, com 400 e mensagem em português, os valores que hoje passam e que o banco ou a regra de negócio não suportam. No PUT, `null` significa não alterar, então as regras tratam `null` como válido. (origem: #215)

Regras (nome, duração e limiar; as de `timezone` estão em RF-13):

| Campo | POST | PUT |
|---|---|---|
| `nome` | obrigatório, não em branco, no máximo 255 caracteres | se vier, não pode ser só espaços (quebra de linha é aceita) e no máximo 255 |
| `duracaoPreparoS` | de 30 a 900 | de 30 a 900 |
| `limiarDescalcificacao` | maior que zero | maior que zero |

O teto de 900 s é o mesmo de `ComandoRequest`. Os tamanhos 255 e 50 são os das colunas da V3 (RF-01).

**Acceptance Criteria:**

- [ ] PUT com `duracaoPreparoS` igual a 10: 400; igual a 901: 400; igual a 300: 200.
- [ ] POST com `duracaoPreparoS` igual a 901: 400.
- [ ] POST ou PUT com `limiarDescalcificacao` igual a 0: 400.
- [ ] PUT com `nome` igual a `"   "`: 400; com 256 caracteres: 400; com `"Cafeteira\nCozinha"`: 200; sem `nome`: 200 e o nome não muda.
- [ ] Todo 400 vem em Problem Details com o campo e a mensagem, no formato atual do `GlobalExceptionHandler`.
- [ ] Teste de controller para o 400 do PUT de dispositivo.

### RF-13 - Validação de timezone do dispositivo

**Priority:** Must

**Description:**

`timezone` tem no máximo 50 caracteres (o tamanho da coluna) e, se informado, não pode ser vazio nem só espaços, no POST e no PUT. Sem `timezone`, o POST usa `America/Sao_Paulo` e o PUT não altera. Motivo da recusa de fuso em branco: desde o commit `700d3a8` (PR #221), mudar o timezone republica na hora o payload retido de agendamentos, então um fuso em branco chegaria ao firmware. A validação de fuso IANA continua fora do escopo (Non-Goals). (origem: #215)

**Acceptance Criteria:**

- [ ] POST ou PUT com `timezone` de 51 caracteres: 400 em Problem Details (antes era 500, porque a exceção do banco caía no tratamento genérico).
- [ ] POST ou PUT com `timezone` igual a `""` ou só espaços: 400.
- [ ] POST sem `timezone` grava `America/Sao_Paulo`; PUT sem `timezone` não altera o valor.
- [ ] Testes de validação do DTO (`jakarta.validation.Validator`) cobrem 50 caracteres (válido), 51 (inválido), vazio e só espaços.

### RF-14 - Segunda regra em RefreshRequest

**Priority:** Must

**Description:**

`RefreshRequest` ganha um limite de tamanho (máximo 100 caracteres, mensagem "RefreshToken inválido") além do `@NotBlank` atual. O refresh token gerado pelo `JwtTokenService` tem 72 caracteres (duas vezes um UUID textual de 36). (origem: #215)

**Acceptance Criteria:**

- [ ] `POST /auth/refresh` com `refreshToken` de 101 caracteres: 400 em Problem Details.
- [ ] `POST /auth/refresh` com um refresh token real (72 caracteres) continua funcionando.
- [ ] `RefreshRequest` tem pelo menos 2 regras diferentes (teste de validação do DTO).

### RF-15 - Validação do lote do proxy BLE

**Priority:** Must

**Description:**

`ProxyBleEventosRequest` (recebido por `POST /dispositivos/{dispositivoId}/eventos/proxy-ble`) hoje não tem nenhuma regra, e as regras de `IngestaoEventoRequest` nunca são executadas porque a lista `eventos` não valida os elementos em cascata. Passa a: validar cada elemento da lista (as regras de `IngestaoEventoRequest` passam a rodar) e ter duas regras próprias na lista: não vazia e no máximo 100 eventos. Justificativa do 100: a fila offline do firmware comporta 32 eventos (`EventQueueStore::kCapacity`), o app envia a fila de uma vez, sem dividir, e 100 dá folga para um aumento futuro da fila. É uma mudança de comportamento intencional (ver Rollout e a decisão D-08). (origem: #215)

**Acceptance Criteria:**

- [ ] Lista `eventos` vazia: 400 em Problem Details.
- [ ] Lote com exatamente 100 eventos válidos: aceito (201).
- [ ] Lote com 101 eventos: 400.
- [ ] Elemento com `eventoId`, `resultado` ou `origem` em branco: 400, com o campo do elemento na mensagem (ex.: `eventos[0].eventoId`).
- [ ] Lote válido (1 a 100 eventos, elementos válidos) continua respondendo 201 como hoje.
- [ ] Testes de validação do DTO e de controller cobrem lista vazia, lote de 100, lote de 101, elemento inválido e lote válido.

### RF-16 - Cobertura de todos os DTOs de request

**Priority:** Must

**Description:**

Todos os DTOs de request do projeto, exceto o andaime `TestDto`, têm pelo menos 2 regras de validação diferentes. Hoje atendem sem mudança: `CriarAgendamentoRequest`, `AtualizarAgendamentoRequest`, `RegisterRequest`, `LoginRequest`, `SolicitarRecuperacaoSenhaRequest`, `RedefinirSenhaRequest`, `CompartilharDispositivoRequest`, `ComandoRequest` e `IngestaoEventoRequest` (esta, ao ser validada em cascata). Passam a atender por este componente: `CriarDispositivoRequest`, `AtualizarDispositivoRequest`, `RefreshRequest` e `ProxyBleEventosRequest`. (origem: #215; escopo ampliado pela decisão D-08)

**Acceptance Criteria:**

- [ ] Todos os DTOs de request do projeto, exceto o andaime `TestDto`, têm ao menos 2 regras diferentes; a lista DTO por regras vai na descrição da PR.
- [ ] Os testes de validação usam `jakarta.validation.Validator`, no estilo do `ScheduleDtoValidationTest`, para os DTOs alterados.
- [ ] Todo 400 de validação continua no formato Problem Details atual do `GlobalExceptionHandler` (`Validation failed: campo: mensagem`).

### Componente 6 - Autenticação por credenciais: /auth/signin e /auth/signup (origem: #216)

### RF-17 - Rotas /auth/signin e /auth/signup

**Priority:** Must

**Description:**

`POST /auth/signup` cadastra e `POST /auth/signin` autentica, ambos públicos e devolvendo `AuthResponse` (token JWT de acesso e refresh token). São os nomes oficiais (premissa P1). `/auth/registrar` e `/auth/login` continuam funcionando como aliases, porque o app web publicado e builds Android e Desktop já instalados chamam os caminhos atuais. Os aliases têm o mesmo status, o mesmo contrato de request e response e as mesmas mensagens dos nomes oficiais; eles compartilham o balde de rate limit com o nome oficial (RF-20). (origem: #216)

**Acceptance Criteria:**

- [ ] `POST /auth/signup` com dados válidos: 201 com `AuthResponse` (`accessToken` JWT e `refreshToken`).
- [ ] `POST /auth/signin` com credenciais válidas: 200 com `accessToken`, aceito numa rota protegida (`GET /dispositivos` responde 200).
- [ ] `/auth/signin` e `/auth/signup` são públicas (sem token).
- [ ] `/auth/login` e `/auth/registrar` continuam funcionando e devolvem o mesmo status, o mesmo formato de request e response e as mesmas mensagens de erro que `/auth/signin` e `/auth/signup` (um teste compara os dois nomes para o mesmo caso).
- [ ] O Swagger UI mostra `/auth/signin` e `/auth/signup`, e a documentação das operações deixa claro que esses são os nomes oficiais e que `/auth/login` e `/auth/registrar` são aliases mantidos por compatibilidade.
- [ ] No documento OpenAPI (`/v3/api-docs`), `/auth/signin` e `/auth/signup` declaram segurança vazia (públicos), como o `/auth/login` já verificado pelo `OpenApiDocsTest`.

### RF-18 - Verificação das credenciais pelo Spring Security

**Priority:** Must

**Description:**

No signin, as credenciais são autenticadas pelos mecanismos do Spring Security (restrição do enunciado, P4): `AuthenticationManager` com `DaoAuthenticationProvider`, `UserDetailsService` que carrega o usuário pelo e-mail normalizado (`trim` e minúsculas, como hoje) e o `PasswordEncoder` existente. Se válidas, a aplicação gera e devolve o token JWT. A autenticação não pode alterar a senha persistida do usuário. (origem: #216)

**Acceptance Criteria:**

- [ ] As credenciais do signin são verificadas por `AuthenticationManager.authenticate(...)`; `AuthService` não chama mais `passwordEncoder.matches`.
- [ ] O e-mail é normalizado (`trim` e minúsculas) antes de carregar o usuário.
- [ ] Após um signin bem-sucedido, o `senhaHash` do usuário no banco permanece o mesmo (a limpeza de credenciais do principal feita pelo Spring não vai para a entidade).
- [ ] O signin bem-sucedido continua emitindo `accessToken` e `refreshToken` (família de refresh nova, como hoje).
- [ ] `AuthControllerTest` é adaptado ao novo conjunto de colaboradores do `AuthService` (hoje monta o serviço à mão).

### RF-19 - Erros do signin

**Priority:** Must

**Description:**

Falhas de autenticação do Spring Security viram 401 Problem Details com mensagem genérica que não revela se o e-mail existe. Falha de infraestrutura ao carregar o usuário (ex.: banco fora do ar) é erro do servidor (500), nunca "senha errada". A `BadCredentialsException` própria do projeto continua respondendo 401 como hoje. (origem: #216)

**Acceptance Criteria:**

- [ ] Senha errada: 401 Problem Details com `detail` "Email ou senha inválidos".
- [ ] E-mail inexistente: o mesmo 401, com a mesma mensagem (nem 404, nem 500).
- [ ] Teste separado: a `BadCredentialsException` do Spring Security (`org.springframework.security.authentication`) resulta em 401 com "Email ou senha inválidos".
- [ ] Teste separado: a `AuthenticationServiceException` (e a subclasse `InternalAuthenticationServiceException`, produzida quando o `UserDetailsService` falha, ex.: `UsuarioRepository` lançando exceção) resulta em 500 com o mesmo corpo do tratamento genérico, e não em 401.
- [ ] Teste separado: a `BadCredentialsException` do projeto (`br.com.tavaressan.cafey.exception`) mantém o comportamento atual, 401 com a mensagem que o chamador definiu (ex.: refresh token inválido, convidado sem permissão).

### RF-20 - Rate limit compartilhado entre nome novo e antigo

**Priority:** Must

**Description:**

`/auth/signin` e `/auth/signup` entram no rate limit com os mesmos limites de hoje (login: 5 requisições por 60 s por IP; registrar: 10 por 60 s, configuráveis em `cafey.rate-limit.*`). O balde é identificado por uma chave canônica: IP mais rota canônica. `signin` e `login` têm a mesma rota canônica, e `signup` e `registrar` têm outra, de modo que alias e nome oficial compartilham o balde sem perder a separação por IP. Sem isso, alternar entre o nome novo e o antigo dobraria as tentativas por minuto. (origem: #216)

**Acceptance Criteria:**

- [ ] O rate limit vale para `/auth/signin` e `/auth/signup` com os mesmos limites de hoje.
- [ ] Alternar `/auth/login` e `/auth/signin` a partir do mesmo IP resulta em 429 na 6ª tentativa do minuto; para `/auth/signup` e `/auth/registrar`, na 11ª.
- [ ] IPs diferentes têm baldes separados: esgotar o balde de um IP não limita outro IP.
- [ ] Os limites continuam configuráveis em `cafey.rate-limit.*`, e `enabled=false` continua desligando o rate limit, como hoje.
- [ ] `/auth/recuperar-senha` mantém o seu balde; rotas hoje sem limite continuam sem limite.
- [ ] `RateLimitingFilterTest` cobre o balde compartilhado e a separação por IP.

### RF-21 - App passa a chamar as rotas novas (2ª entrega)

**Priority:** Should

**Description:**

Numa entrega separada e posterior ao deploy do backend com as rotas novas (decisão D-10), `AuthApi.kt` passa a chamar `auth/signup` e `auth/signin`. Os aliases mantêm o app funcionando enquanto isso não acontece, por isso a prioridade é Should. A 2ª entrega é rastreada por uma issue de acompanhamento, aberta quando a 1ª for mergeada. (origem: #216)

**Acceptance Criteria:**

- [ ] A entrega só ocorre quando as duas condições são verdadeiras: (1) o workflow `Deploy Backend` da entrega 1 concluiu com sucesso; (2) `POST /auth/signin` responde 200 com credenciais válidas no ambiente publicado.
- [ ] `AuthApi.kt` usa `auth/signup` e `auth/signin`.
- [ ] CI Apps verde.
- [ ] Verificação manual: cadastro e login no app web ou Desktop funcionam contra o backend.

### Componente 7 - Autenticação por token JWT e autorização de rotas (origem: #217)

### RF-22 - Utilitário JWT: validar token e ler o usuário

**Priority:** Must

**Description:**

`JwtTokenService` (nome mantido, apresentado no README como a biblioteca de utilitários JWT) passa a oferecer, além de gerar o token, validar um token e ler o id do usuário do token validado. Toda validação passa pelo decoder existente; não há leitura manual do token. (origem: #217)

**Acceptance Criteria:**

- [ ] Validar token devolve o `Jwt` quando assinatura e expiração estão corretas; token inválido, expirado ou malformado lança `JwtException`.
- [ ] Extrair o id do usuário devolve o `sub` como UUID. Existe também a forma que recebe o token em texto, que valida e delega.
- [ ] Teste: token com `sub` ausente lança `JwtException`.
- [ ] Teste: token com `sub` que não é UUID válido (malformado) lança `JwtException`.
- [ ] Testes cobrem: token válido; expirado (com `exp` pelo menos 61 s no passado, usando `agora - 5 min`, por causa da tolerância de 60 s do decoder, afirmada pela issue); assinado por outra chave; malformado; id extraído igual ao `sub`.

### RF-23 - Filtro de autenticação JWT próprio

**Priority:** Must

**Description:**

Um filtro do projeto, `JwtAuthenticationFilter` (`OncePerRequestFilter`), autentica as requisições com `Authorization: Bearer <token>` usando o utilitário (validar e depois extrair o usuário) e substitui o filtro interno do resource server do Spring (`.oauth2ResourceServer` removido). O filtro valida assinatura, expiração e o formato UUID do `sub`, sem consultar o banco, o mesmo comportamento do resource server atual quanto à consulta ao banco. Quem decide o resultado é a regra de autorização. (origem: #217)

**Acceptance Criteria:**

- [ ] A classe `JwtAuthenticationFilter` existe, está registrada na `SecurityFilterChain`, e usa `validarToken` e `extrairUsuarioId` do utilitário. O `.oauth2ResourceServer` foi removido.
- [ ] Com token válido, a `Authentication` no contexto tem `isAuthenticated == true` e o principal é o `Jwt`; os controllers continuam com `@AuthenticationPrincipal jwt: Jwt` sem alteração.
- [ ] O filtro não faz nenhuma consulta ao banco para autenticar (nenhum repositório é injetado nele).
- [ ] Sem o cabeçalho `Authorization: Bearer ...`, a requisição segue sem autenticação.
- [ ] Com token inválido ou expirado, o contexto é limpo e a requisição segue; rota protegida responde 401, rota pública é atendida.
- [ ] O filtro roda uma única vez por requisição (não é registrado de novo como filtro do servlet).

### RF-24 - 401 para requisições não autenticadas em rota protegida

**Priority:** Must

**Description:**

Sem autenticação válida, uma rota protegida responde sempre 401 (nunca 403), em Problem Details, com o cabeçalho `WWW-Authenticate: Bearer` e `detail` "Token JWT ausente ou inválido". Em rota pública, esses mesmos casos são atendidos normalmente. (origem: #217)

**Acceptance Criteria:**

- [ ] Rota protegida, cabeçalho `Authorization` ausente: 401.
- [ ] Rota protegida, esquema diferente de Bearer (ex.: `Authorization: Basic <usuario:senha em base64>`, montado em tempo de execução no teste): 401.
- [ ] Rota protegida, `Authorization: Bearer ` com token vazio: 401.
- [ ] Rota protegida, Bearer malformado (ex.: `Bearer abc`): 401.
- [ ] Rota protegida, Bearer com assinatura inválida (adulterado, ou assinado por outra chave): 401.
- [ ] Rota protegida, Bearer expirado: 401.
- [ ] Rota protegida, Bearer válido: 200.
- [ ] A falta de autenticação nunca responde 403.
- [ ] A resposta 401 tem status 401, `Content-Type: application/problem+json`, cabeçalho `WWW-Authenticate: Bearer` e corpo com `status`, `title` e `detail` igual a "Token JWT ausente ou inválido".
- [ ] Os casos acima são testados por requisição HTTP (MockMvc) contra a cadeia de segurança, para garantir que quem responde é o entry point do Security e não o handler de exceções do MVC.

### RF-25 - Rotas públicas: lista fechada por matchers explícitos

**Priority:** Must

**Description:**

As únicas rotas públicas são as de uma lista fechada, declarada com um matcher explícito por rota (caminho e, quando faz sentido, método HTTP), e não com o curinga `/auth/**` (hoje em `SecurityConfig.kt:74`). Cada rota da lista é comentada no `SecurityConfig` com o motivo:

- `POST` em `/auth/signin`, `/auth/signup` e nos aliases `/auth/login` e `/auth/registrar`;
- `POST` em `/auth/refresh`, `/auth/recuperar-senha` e `/auth/redefinir-senha` (fluxos que por natureza não têm access token);
- `/redefinir-senha.html`;
- o Swagger (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`);
- `/test/**`, apenas fora do perfil `prod`.

Conferido no `AuthController`: as 5 rotas `/auth/*` de hoje (`registrar`, `login`, `refresh`, `recuperar-senha`, `redefinir-senha`) mais `signin` e `signup` são todas as rotas `/auth/*` e todas públicas, então nenhuma rota `/auth/*` passa a ser protegida pela troca do curinga. O `permitAll` de `/actuator/**` é removido (curinga público sem uso: o Actuator não é dependência do projeto). Todas as demais rotas são protegidas. As rotas públicas funcionam com e sem o cabeçalho `Authorization`, inclusive com token inválido ou expirado nele (hoje respondem 401 nesse caso). (origem: #217)

**Acceptance Criteria:**

- [ ] O `SecurityConfig` declara as rotas públicas por matchers explícitos, sem `/auth/**`, cada uma com comentário do motivo.
- [ ] As 7 rotas públicas de `/auth` respondem sem token (um teste por rota).
- [ ] Uma rota nova criada sob `/auth/` fora da lista não fica pública automaticamente: sem token, responde 401 (teste com uma rota de teste sob `/auth/`).
- [ ] `POST /auth/login` com `Authorization: Bearer invalido` e credenciais válidas: 200 (hoje 401).
- [ ] Não há `permitAll` para `/actuator/**`: `GET /actuator/health` sem token responde 401.
- [ ] As demais rotas exigem autenticação.

### RF-26 - Não regressão da segurança

**Priority:** Must

**Description:**

Trocar o filtro do Spring por um filtro próprio não pode alterar o que já funciona. (origem: #217)

**Acceptance Criteria:**

- [ ] Continuam verdes, sem mudar asserções: `ComandoControllerTest` (401 sem token e 202 no caso feliz com token real), `OpenInViewIntegrationTest`, `CorsConfigurationTest` (preflight 200 e 403 e requisição real 401), `TestControllerProdProfileTest`, `GlobalExceptionHandlerTest` e `CuidadosIntegrationTest`.
- [ ] O botão "Authorize" do Swagger UI continua funcionando.
- [ ] Verificação manual: o app Desktop ou Web faz login e lista dispositivos contra o backend local.

## Non-Functional Requirements

### RNF-01 - Entregas verificadas por CI

**Priority:** Must

**Description:**

Cada entrega mantém o projeto compilando e testado. (origem: todas as issues)

**Acceptance Criteria:**

- [ ] Toda entrega que mexe em `backend/**` passa em `./gradlew test` e tem o CI Backend verde.
- [ ] O CI Apps é exigido apenas na 2ª entrega da #216 (RF-21), a única que mexe em `apps/`.
- [ ] Os testes que dependem do Postgres real (`PostgresIntegrationTest`) são verificados no CI; uma falha local por Docker (ambiente) não é falha da entrega.

### RNF-02 - Demonstrabilidade pelo Swagger UI

**Priority:** Must

**Description:**

O critério 3d do enunciado só aceita como cumprido o que dá para demonstrar rodando. (origem: épico #209, #212, #213, #216)

**Acceptance Criteria:**

- [ ] Fluxo demonstrável no Swagger UI: `POST /auth/signup`, `POST /auth/signin`, "Authorize" com o `accessToken`, `GET /dispositivos`.
- [ ] Criar dois dispositivos ("Cafeteira da Cozinha" e "Cafeteira do Escritório"), chamar `GET /dispositivos?nome=coz` e ver só um.
- [ ] Criar um agendamento, copiar o `id` e chamar o GET por id.
- [ ] As rotas novas e os parâmetros `nome` e `descricao` aparecem no Swagger UI.

### RNF-03 - Compatibilidade com clientes publicados e com o firmware

**Priority:** Must

**Description:**

Nada que um cliente publicado usa hoje deixa de funcionar, exceto os endurecimentos de validação listados em Rollout. (origem: #211, #214, #216, #217)

**Acceptance Criteria:**

- [ ] As respostas existentes mantêm todos os campos atuais; o único acréscimo é `descricao` em `AgendamentoResponse`. Fato verificado: `ApiClient.kt:40` do app usa `Json { ignoreUnknownKeys = true }`, então o campo novo não quebra os clientes publicados.
- [ ] `/auth/login` e `/auth/registrar` seguem aceitos com o mesmo contrato.
- [ ] O payload MQTT de agendamentos não muda (RF-04).

### RNF-04 - Histórico de migrations íntegro

**Priority:** Must

**Description:**

Estado atual: V1 a V7, com a V7 já implantada em produção (ver Context). Estado esperado depois da implementação: V1 a V8. Como a V7 já está aplicada, a V8 segue o fluxo normal. Uma V8 aplicada antes da V7 deixaria a V7 como "ignorada" e o backend não subiria (afirmado pela issue #211), situação que não ocorre mais. (origem: #211)

**Acceptance Criteria:**

- [ ] O histórico do Flyway esperado pelo `PostgresIntegrationTest` passa de V1 a V7 (estado atual) para V1 a V8 (após a implementação), contínuo e na ordem.
- [ ] Nenhuma migration com versão menor que a última aplicada é adicionada por este trabalho.

### RNF-05 - Isolamento de dados e não enumeração

**Priority:** Must

**Description:**

Busca, listagem e leitura por id nunca expõem dados de usuário sem vínculo, e as respostas de autenticação não revelam se um e-mail existe. (origem: #212, #213, #216)

**Acceptance Criteria:**

- [ ] Busca de dispositivos só devolve dispositivos vinculados ao usuário do token (RF-09).
- [ ] Sem vínculo com o dispositivo, as rotas de agendamento respondem 404, sem revelar se o dispositivo existe (RF-08, RF-10).
- [ ] Senha errada e e-mail inexistente no signin respondem o mesmo 401 com a mesma mensagem (RF-19).

## Behavior / States

Tratamento de uma requisição, por estado do cabeçalho `Authorization` e tipo de rota:

| Cabeçalho `Authorization` | Rota protegida | Rota pública |
|---|---|---|
| Ausente | 401 | atendida |
| Esquema diferente de Bearer (ex.: `Basic`) | 401 | atendida |
| `Bearer ` com token vazio | 401 | atendida |
| Bearer malformado | 401 | atendida (hoje: 401) |
| Bearer com assinatura inválida | 401 | atendida (hoje: 401) |
| Bearer expirado | 401 | atendida (hoje: 401) |
| Bearer válido | atendida (autenticada) | atendida |

Fluxo de autenticação em duas camadas: (1) `signin` verifica as credenciais pelo `AuthenticationManager` e emite o JWT; (2) em cada requisição seguinte, `JwtAuthenticationFilter` valida o token e lê o usuário.

Filtro opcional de listagem: parâmetro ausente ou em branco = lista completa; parâmetro informado = lista filtrada pelo vínculo do usuário.

## Data

- `agendamentos.descricao`: `varchar(100)`, nulo permitido (V8). Sem outra mudança de schema.
- Histórico do Flyway: estado atual V1 a V7; esperado após a implementação V1 a V8.
- `Dispositivo`: `nome` 255, `timezone` 50 e `estado` 50 declarados na entidade, iguais à V3 (sem migration).
- `AgendamentoResponse` ganha `descricao: String?`. `AgendamentoItemPayload` (MQTT) não muda.
- Estado de referência dos tipos: `Dispositivo` tem String, Boolean e Int; `Agendamento` passa a ter String, LocalTime, Boolean e Int.

## Integrations / Dependencies

- Dependências entre componentes: #214 antes de #211, #212 e #213 (o código novo já nasce usando os mappers); #211 antes de #212; #216 antes de #217 (as duas mexem no `SecurityConfig`).
- Stack: Spring Boot 4.1.1 (Spring Security 7.1.1, Hibernate 7.4, Jackson 3), Flyway, PostgreSQL, Kotlin 2.3.21 (`build.gradle.kts`).
- Clientes: o app (`apps/shared`) usa `AuthApi.kt` (só RF-21 mexe no app), com Ktor e `ignoreUnknownKeys = true`.
- Deploys independentes e em paralelo na `main`: `deploy-backend.yml` (paths `backend/**`) e `deploy-webapp.yml` (paths `apps/shared/**` entre outros), sem ordem entre eles.
- Documentação a jusante: DOC-07 (#218) descreverá a camada mapper, a lista de rotas públicas e as premissas P1 e P2.

## Edge Cases

- Entrada inválida (DTOs): `descricao` com 101 caracteres; `nome` só com espaços no PUT; `nome` com quebra de linha no PUT (aceito); `nome` com 256 caracteres; `timezone` com 51 caracteres ou em branco; `refreshToken` com 101 caracteres; lote do proxy BLE vazio, com 101 eventos ou com elemento em branco. Em todos: 400 Problem Details com campo e mensagem.
- Tamanho antes do `trim`: a validação de tamanho roda sobre o valor bruto. Um `nome` de 256 caracteres é recusado mesmo que o `trim` o reduzisse a 255 (inferência a partir de como o Bean Validation funciona; não testado).
- `UUID` malformado em `agendamentoId`: não coberto pelas issues. Inferência (não verificada): como nas demais rotas com UUID, o handler genérico responderia 500. ⚠️ ABERTO.
- Ausência de dados: busca sem resultado devolve 200 com lista vazia; sem vínculo devolve 404. Agendamentos antigos com `descricao` nula listam normalmente e não casam com o LIKE.
- Termo de busca: `%` e `_` funcionam como curinga (limitação conhecida); acento não é ignorado; espaços nas pontas são descartados.
- Autenticação expirada: token expirado há menos de 60 s ainda é aceito pela tolerância do decoder (afirmado pela issue #217); em rota protegida com token realmente expirado, 401; em rota pública, a requisição é atendida. Os demais casos de cabeçalho estão na tabela de Behavior / States.
- Método HTTP fora do matcher: com matchers por método, um `GET /auth/login` sem token passa a responder 401 em vez do 405 de hoje (inferência, não testada).
- Dependência indisponível: falha de infraestrutura ao carregar o usuário no signin devolve 500, não 401.
- Duplicidade: signup com e-mail já cadastrado mantém o comportamento atual (lido no código: `BadCredentialsException` do projeto, resultando em 401), apesar de a anotação do Swagger citar 400.
- Estado inconsistente: entrega do app (RF-21) antes do backend com as rotas novas resulta em 404 no login do web novo (por isso as duas entregas, D-10).
- Concorrência e retry: nenhum caso específico introduzido por este trabalho.

## Error Handling

| Situação | Resposta |
|---|---|
| Validação de DTO falha | 400 Problem Details, `detail` "Validation failed: campo: mensagem" |
| Dispositivo ou agendamento inexistente, de outro dispositivo, ou sem vínculo | 404 Problem Details |
| Sem token, token inválido, expirado, malformado ou adulterado em rota protegida | 401 `application/problem+json` com `WWW-Authenticate: Bearer` e `detail` "Token JWT ausente ou inválido" (nunca 403) |
| Signin com senha errada ou e-mail inexistente | 401 Problem Details "Email ou senha inválidos" |
| Falha de infraestrutura ao carregar o usuário no signin | 500 Problem Details genérico |
| Rate limit excedido | 429 com `Retry-After` (comportamento atual do `RateLimitingFilter`) |

## Security / Privacy

- Buscas e leituras por id respeitam o vínculo do usuário (RNF-05).
- O signin não revela se o e-mail existe (mensagem e status iguais para senha errada e e-mail inexistente).
- O `senhaHash` persistido não pode ser alterado pelo processo de autenticação (RF-18).
- A validação de assinatura e expiração do token continua no decoder; o filtro próprio só decide se autentica. Os testes de token adulterado e expirado são a garantia (RF-22 a RF-24).
- Comportamento mantido, não mudança: a autenticação do token não consulta o banco, tanto no resource server atual quanto no filtro novo. Um token de usuário removido, ou de convidado cujo acesso foi revogado, continua válido até expirar (access token de 15 min), conforme a limitação já registrada na §4.3 da spec v1.2. Pequeno deslocamento: o formato UUID do `sub` passa a ser verificado no filtro; hoje só é verificado quando o controller chama `UUID.fromString`. Os tokens emitidos pelo próprio backend sempre têm `sub` em UUID, então não afeta os clientes.
- Lista fechada de rotas públicas por matchers explícitos e remoção do `permitAll` de `/actuator/**` (RF-25).
- Rate limit compartilhado entre nome novo e antigo para não ampliar a janela de força bruta (RF-20).

## Rollout

- Ordem de implementação (épico #209): #210, #214, #213, #211, #212, #215, #216, #217; DOC-07 (#218) por último.
- O backend faz deploy automático a cada push na `main` com mudanças em `backend/**`. A V7 já está implantada em produção (workflow `Deploy Backend` do commit `9522d5f` concluído com sucesso em 13/10/2026), então a V8 segue o fluxo normal; não há mais o risco de a V8 chegar antes da V7.
- #216 em duas entregas (D-10): (1) backend com `/auth/signin` e `/auth/signup` e os aliases; (2) outra entrega troca o `AuthApi.kt` para as rotas novas (RF-21). Motivo: os deploys de backend e webapp são independentes e paralelos, e o web novo daria 404 no login se publicasse antes do backend. A 2ª entrega é liberada quando (a) o workflow `Deploy Backend` da entrega 1 concluir com sucesso e (b) `POST /auth/signin` responder 200 com credenciais válidas no ambiente publicado. Uma issue de acompanhamento rastreia a 2ª entrega e é aberta quando a 1ª for mergeada.
- Mudanças de comportamento intencionais, para registrar nas PRs:
  - rota pública com token inválido ou expirado deixa de responder 401 (RF-25);
  - `timezone` com mais de 50 caracteres passa de 500 para 400 (RF-13);
  - `timezone` em branco passa a ser recusado com 400 (RF-13);
  - PUT de dispositivo com duração fora de 30 a 900 ou limiar menor ou igual a zero passa de aceito para 400 (RF-12). No app, `AtualizarDispositivoRequest` é declarado mas não construído em nenhum lugar, e o cadastro envia só o `nome` (leitura de `apps/shared`);
  - o proxy BLE passa a responder 400 onde hoje processa: lote vazio, lote acima de 100 eventos e elemento com `eventoId`, `resultado` ou `origem` em branco (RF-15). O app não envia lote vazio (`BleEventProxy.sincronizar` retorna antes quando não há pendentes) e envia a fila de uma vez, sem dividir; a fila do firmware tem no máximo 32 eventos (`EventQueueStore::kCapacity`), então o lote legítimo cabe nos 100, que dão folga para um aumento futuro da fila;
  - `GET /actuator/health` sem token responde 401 (hoje o `permitAll` deixa a requisição passar e, por inferência não testada, ela cai em 404, porque o Actuator não é dependência).
- Verificação: CI Backend verde nas entregas que mexem em `backend/**`; CI Apps só na 2ª entrega da #216; demonstração pelo Swagger (RNF-02).

## Open Questions

- ⚠️ ABERTO: por quanto tempo os aliases `/auth/login` e `/auth/registrar` ficam e quando removê-los (hoje, Non-Goal).
- ⚠️ ABERTO: o §5.1 de `docs/documentacao_backend_pi_2_6/spec-backend-v1.2.md` (tabela de rotas) não é atualizado por nenhuma issue deste épico (faltam `signin`, `signup`, GET de agendamento por id e os filtros; já diverge do código em prefixo `/api/v1`, `PATCH` x `PUT` e `/auth/senha/*` x `/auth/recuperar-senha`). A §4.4 diz `/actuator/health` público e `POST /auth/**` público, o que diverge de RF-25.
- ⚠️ ABERTO: as consultas LIKE (RF-09, RF-10) são testadas só no H2 dos testes de repositório ou também contra PostgreSQL real? O `PostgresIntegrationTest` é o único que usa Postgres e só roda no CI.
- ⚠️ ABERTO: comportamento de `agendamentoId` com formato de UUID inválido (ver Edge Cases).

## Decisions

Decisões do épico #209 (já tomadas):

- D-01: entidades da atividade = `Dispositivo` e `Agendamento` (as únicas com CRUD completo ou quase; `Usuario` e `EventoPreparo` não têm 3 tipos da lista nem CRUD).
- D-02: LIKE do `Agendamento` por um campo próprio `descricao`, não pelo nome do dispositivo (atende o item b sem margem de interpretação).
- D-03: filtro JWT próprio e métodos de validação no utilitário (o enunciado os pede explicitamente; o filtro interno do resource server não aparece no código do projeto).
- D-04: rotas `/auth/signin` e `/auth/signup`, mantendo `/auth/login` e `/auth/registrar` (premissa P1).
- D-05: listagens restritas ao dono (premissa P2).
- D-06: camada mapper em `mapper/` (premissa P3).

Decisões tomadas na elaboração desta Spec (candidatas a ADR):

- D-07: remover o `permitAll` de `/actuator/**` mesmo divergindo da §4.4 da spec v1.2; se o Actuator for adicionado, liberar só `/actuator/health`. Nada em compose, Dockerfile ou workflows consulta esse endpoint.
- D-08: escopo da validação (#215) = todos os DTOs de request do projeto, exceto o andaime `TestDto`. Inclui o `ProxyBleEventosRequest` (lista não vazia, no máximo 100, validação em cascata) e a recusa de `timezone` em branco, justificada pelo `700d3a8`.
- D-09: um token inválido ou expirado em rota pública não bloqueia mais a requisição; quem decide é a regra de autorização (comportamento mudado de propósito).
- D-10: a #216 é entregue em duas etapas (backend com aliases primeiro; `AuthApi.kt` depois do deploy), por causa dos deploys independentes e paralelos.
- D-11: como o serviço detecta a mudança efetiva de timezone e duração depois de o `DispositivoMapper.atualizar` aplicar os campos (para o bump único de `versaoAgendamentos` e o evento, preservados pelos testes existentes). É escolha de implementação e não bloqueia a Spec. Técnicas possíveis: (a) o mapper devolve um indicador de que algo que afeta o payload de agendamentos mudou, e o serviço decide o bump e o evento; (b) o serviço tira um instantâneo de timezone e duração antes e compara depois do mapper; (c) o serviço continua atribuindo diretamente timezone e duração (a própria detecção), e o mapper cobre os demais campos. Qualquer uma das três satisfaz RF-05 e RF-06; na (c), as atribuições diretas de timezone e duração no serviço são aceitas como parte da detecção de mudança.

## References

- Épico #209 e issues #210 (BE-28), #211 (BE-29), #212 (BE-30), #213 (BE-31), #214 (BE-32), #215 (BE-33), #216 (BE-34), #217 (BE-35); DOC-07 (#218) e DOC-08 (#219).
- PRs #203 (V7), #204 (cuidados) e #221; commit `700d3a8`; commit `9522d5f` (traz a V7).
- `docs/documentacao_backend_pi_2_6/spec-backend-v1.2.md` (§4.3 e §4.4 regras de acesso e JWT, §5.1 endpoints).
- Código: `backend/cafey-backend/src/main/kotlin/br/com/tavaressan/cafey/` (`device/`, `schedule/`, `auth/`, `security/SecurityConfig.kt`, `security/JwtTokenService.kt`, `security/RateLimitingFilter.kt`, `config/RateLimitConfig.kt`, `exception/GlobalExceptionHandler.kt`, `event/EventoDto.kt`); `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/network/AuthApi.kt` e `ApiClient.kt`; `firmware/main/storage/event_queue_store.hpp`; `.github/workflows/deploy-backend.yml` e `deploy-webapp.yml`.
- Documentação citada pela #216 (não verificada aqui): Spring Security 7.1, "Publish an AuthenticationManager bean", https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/passwords/index.html.
- Documentação do Ktor (client-bearer-auth.md e client-auth.md, consultada via Context7): seleção do provider e padrão de envio de credenciais.

## Revision History

| Data | Autor | Mudança |
|------|-------|---------|
| 2026-10-13 | /vetor:spec | rascunho inicial gerado por /vetor:spec (versão enxuta, decomposição em 7 componentes confirmada pelo usuário) |

- Relacionado: documentacao_backend_pi_2_6/spec-backend-v1.2.md

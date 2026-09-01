# Backlog — Cafey

Derivado de `docs/spec-cafeteira-conectada.md` (v1.1), `docs/spec-backend.md`
(v1.1), `STATUS.md` e `README.md`. Posição de referência: 01/09/2026, Fase 1.

## Convenções

| Campo | Significado |
|---|---|
| ID | Prefixo por trilha: `HW`, `FW`, `BE`, `APP`, `ART`, `DOC`, `INFRA` |
| Fase | 1 (24/08–04/09), 2 (07/09–25/09), 3 (28/09–09/10), 4 (12/10–16/10) |
| Prio | `crítico` = nunca cortar (§2.4); `corte N` = ordem de corte |

Ordem de corte da §2.4, do primeiro ao último: BLE e proxy BLE, gráficos de
estatística, compartilhamento com convidado, cliente desktop, recuperação de
senha. Nunca cortar: agendamento offline, botão físico, aterramento e
isolamento elétrico.

## Marcos

| Data | Critério | Itens que o compõem |
|---|---|---|
| 04/09 | Relé aciona carga por comando do ESP32; backend autentica ponta a ponta | HW-05, BE-04 |
| 20/09 | Artigo submetido ao EnGeTec | ART-12 |
| 25/09 | Comando do mobile chega à base via AWS IoT | APP-04, BE-08, FW-10 |
| 02/10 | Montagem elétrica validada por profissional | HW-16 |
| 09/10 | Agendamento dispara offline; histórico com eventos reais | FW-14, BE-12 |
| 16/10 | Projeto pronto para a banca | DOC-06 |

---

## Hardware

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| HW-01 | Comprar componentes: cafeteira Britânia CP30, módulo de relé de gatilho alto, HLK-PM01, fusível 8 A, tomada fêmea e cabo NBR 14136, botão, LED RGB cátodo comum | 1 | — | crítico |
| HW-02 | Conferir serigrafia do DevKit pino a pino contra o símbolo `ESP32_30pin` | 1 | HW-01 | |
| HW-03 | Conferir ordem VCC/GND/IN e NC/COM/NO na serigrafia do módulo de relé (trocar COM com NO inverte a lógica) | 1 | HW-01 | |
| HW-04 | Identificar as pernas físicas do LED RGB com multímetro em teste de diodo | 1 | HW-01 | |
| HW-05 | Protótipo do relé em bancada acionado pelo ESP32 — **marco 04/09** | 1 | HW-02, HW-03 | crítico |
| HW-06 | Medir o tempo real de extração da cafeteira; fecha a pendência 7 do backend e fixa `duracao_preparo_s` | 2 | HW-01 | |
| HW-07 | Atualizar o Valor de F1 no esquemático se o fusível comprado for 8 A | 2 | HW-01 | |
| HW-08 | Atribuir footprints a todos os componentes no KiCad — pré-requisito do PCB | 2 | HW-01 | |
| HW-09 | Registrar as exclusões de ERC: `power_out ↔ power_out` em `N` e `Earth_Protective`; `pin_to_pin` entre U1.GND e U2.-Vout | 2 | — | |
| HW-10 | Levantar dimensões físicas de ESP32, módulo de relé, HLK-PM01 e tomada fêmea | 2 | HW-01 | |
| HW-11 | Planificação da chapa: recortes de tomada, cabo, botão e LED | 2 | HW-10 | |
| HW-12 | Definir separação física entre região de rede e região de baixa tensão, sem cabos de alta e baixa em paralelo | 2 | HW-11 | crítico |
| HW-13 | Resolver posicionamento da antena: janela plástica ou antena externa (a chapa atenua Wi-Fi e BLE) | 2 | HW-11 | |
| HW-14 | Prever fixação do terminal olhal de aterramento (J3) na chapa | 2 | HW-11 | crítico |
| HW-15 | Solicitar dobra da chapa à empresa | 2 | HW-11, HW-12, HW-13, HW-14 | |
| HW-16 | Montagem: fiação de rede em bornes com tampa, fixação dos módulos | 3 | HW-15 | crítico |
| HW-17 | Validação da montagem elétrica por profissional habilitado — **marco 02/10** | 3 | HW-16 | crítico |
| HW-18 | Corrigir o símbolo `ESP32_30pin`: pino 16 (3V3) declarado como entrada de energia, e remover o `PWR_FLAG` que compensa isso | 4 | — | |

---

## Firmware

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| FW-01 | Esqueleto ESP-IDF: CMake, `idf.py`, menuconfig, particionamento de flash, build no DevKit | 1 | — | |
| FW-02 | Estrutura de Active Objects sobre FreeRTOS: Cafeteira, Conectividade, Agendador, cada um com fila própria | 2 | FW-01 | |
| FW-03 | Driver do relé em GPIO26 com gatilho alto e pull-down; verificar ausência de acionamento espúrio no boot | 1 | FW-01, HW-03 | crítico |
| FW-04 | Botão em GPIO27: toque curto liga/desliga, toque durante preparo cancela; evento com origem `BOTAO` | 2 | FW-02 | crítico |
| FW-05 | LED RGB conforme §5.3: cor indica estado da cafeteira, piscar indica problema de rede | 2 | FW-02, HW-04 | |
| FW-06 | Persistência em NVS: agendamentos e fila circular de eventos, com mutex no acesso | 2 | FW-02 | crítico |
| FW-07 | Temporizador local de preparo aplicando `duracaoS`; corte de energia gera resultado `CONCLUIDO` | 2 | FW-03, HW-06 | crítico |
| FW-08 | Geração de `evento_id` no formato `bootId:seq`, com `seq` persistido em NVS | 2 | FW-06 | |
| FW-09 | Wi-Fi: conexão, reconexão e provisionamento de credenciais (UC-04) | 2 | FW-02 | |
| FW-10 | MQTT sobre TLS com certificado X.509; conexão ao AWS IoT Core em QoS 1 — **marco 25/09** | 2 | FW-09 | |
| FW-11 | Assinar os tópicos descendentes `comando` e `agendamentos` pelo nome exato (retain não é entregue a filtro com wildcard) | 2 | FW-10 | crítico |
| FW-12 | Publicar `estado` com retain, `eventos` sem retain, e `saude` com Last Will retido | 2 | FW-10 | |
| FW-13 | Regra de versão monotônica: ignorar lista de agendamentos com `versao` menor ou igual à gravada em NVS | 2 | FW-06, FW-11 | crítico |
| FW-14 | NTP e agendador local disparando por relógio interno, sem depender da nuvem — **marco 09/10** | 3 | FW-06 | crítico |
| FW-15 | Definir comportamento quando o preparo termina antes da sincronização NTP; fecha o restante da pendência 2 do backend | 3 | FW-08, FW-14 | |
| FW-16 | Fila de eventos pendentes: enfileirar offline, drenar ao reconectar | 3 | FW-06, FW-10 | crítico |
| FW-17 | Serviço BLE com características de comando, estado e agendamentos | 4 | FW-02 | corte 1 |
| FW-18 | Proxy BLE: expor a fila de eventos pendentes e limpá-la após confirmação | 4 | FW-16, FW-17 | corte 1 |

---

## Backend

Segue a ordem de execução do §10 da especificação do backend.

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| BE-01 | Esqueleto do Initializr (Boot 4.1.x, JDK 25, Gradle KTS) e Postgres via Docker Compose | 1 | — | |
| BE-02 | Migrações `V1` e `V2`; domínio de usuário com hash BCrypt força 12 | 1 | BE-01 | |
| BE-03 | Emissão de JWT RS256, `JwtDecoder` por chave pública, `SecurityFilterChain` | 1 | BE-02 | |
| BE-04 | `/auth/registrar`, `/auth/login`, `/auth/refresh` — **marco 04/09** | 1 | BE-03 | crítico |
| BE-05 | Rotação de refresh token com detecção de reuso: token revogado derruba a família inteira | 1 | BE-04 | |
| BE-06 | Tratamento de erros em Problem Details (RFC 9457) | 1 | BE-01 | |
| BE-07 | Verificar se o Flyway executa de fato: conferir a tabela de histórico após a primeira subida | 1 | BE-02 | crítico |
| BE-08 | Criar thing e certificado do backend no AWS IoT Core, com política cobrindo `iot:Publish` e `iot:RetainPublish` | 2 | — | |
| BE-09 | Fixar a versão do `aws-iot-device-sdk` consultando os releases do repositório | 2 | — | |
| BE-10 | `V3`, CRUD de dispositivos e vínculo `usuario_dispositivo` | 2 | BE-04 | |
| BE-11 | Cliente MQTT persistente com reconexão em backoff exponencial e assinaturas em QoS 1 — **marco 25/09** | 2 | BE-08, BE-09 | |
| BE-12 | Ingestão de `estado` e `saude`; atualizar `dispositivos.estado` e `ultimo_visto` | 2 | BE-11 | |
| BE-13 | `V4`, agendamentos com incremento de `versao_agendamentos` no banco e republicação da lista completa em `AFTER_COMMIT` | 2 | BE-10, BE-11 | crítico |
| BE-14 | Republicar a lista de agendamentos quando o dispositivo reaparecer em `saude` com `online: true` | 2 | BE-12, BE-13 | crítico |
| BE-15 | `V5` e ingestão de eventos com `ON CONFLICT (dispositivo_id, evento_id) DO NOTHING` | 3 | BE-11 | |
| BE-16 | Histórico de preparos (UC-14) | 3 | BE-15 | |
| BE-17 | Contador de descalcificação incrementando apenas em `CONCLUIDO`, alerta e baixa (UC-16, UC-17) | 3 | BE-15 | |
| BE-18 | Estatísticas de consumo por `origem` (UC-15) | 3 | BE-16 | corte 2 |
| BE-19 | Compartilhamento de dispositivo com convidado (UC-05) | 3 | BE-10 | corte 3 |
| BE-20 | `POST /dispositivos/{id}/eventos` para o proxy BLE, rejeitando `inicio` no futuro ou antigo demais | 4 | BE-15 | corte 1 |
| BE-21 | Recuperação de senha (UC-03) e migração `V6` para o token de redefinição | 4 | BE-04 | corte 5 |
| BE-22 | Gerenciamento e injeção segura de chaves RSA (RS256) via variáveis de ambiente / Secrets Manager | 2 | BE-03 | |
| BE-23 | Provedor de envio de e-mail (AWS SES / SMTP) para recuperação de senha com perfis dev/prod | 4 | BE-21 | |
| BE-24 | Rate Limiting e proteção contra força bruta nos endpoints de autenticação (Bucket4j) | 2 | BE-04 | |
| BE-25 | Documentação interativa de API via OpenAPI 3 e Swagger UI (SpringDoc) | 2 | BE-04 | |
| BE-26 | Desativar Open Session In View (`spring.jpa.open-in-view: false`) e validar queries JPA | 2 | BE-01 | |

---

## Aplicativos (Kotlin Multiplataforma)

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| APP-01 | Projeto KMP com módulo `shared` e targets mobile, web e desktop | 2 | — | |
| APP-02 | `shared`: modelos, cliente HTTP, autenticação e armazenamento seguro do token | 2 | APP-01, BE-04 | |
| APP-03 | Mobile: cadastro e login | 2 | APP-02 | |
| APP-04 | Mobile: ligar, desligar, cancelar e estado em tempo real — **marco 25/09** | 2 | APP-03, BE-11 | crítico |
| APP-05 | Mobile: criar, editar, excluir e ativar agendamentos | 3 | APP-04, BE-13 | crítico |
| APP-06 | Mobile: histórico de preparos em lista | 3 | APP-04, BE-16 | |
| APP-07 | Mobile: alerta de descalcificação e baixa do contador | 3 | APP-04, BE-17 | |
| APP-08 | Web no nível essencial: login, operação, agendamento e histórico | 3 | APP-05, APP-06 | |
| APP-09 | Desktop no nível essencial, reaproveitando composables | 3 | APP-08 | corte 4 |
| APP-10 | Gráficos de estatística | 4 | APP-06, BE-18 | corte 2 |
| APP-11 | Cliente BLE: fallback de comando e proxy de eventos para a nuvem | 4 | APP-04, FW-18 | corte 1 |

---

## Artigo — EnGeTec 2026

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| ART-01 | Cadastro no site do evento para receber as notificações de avaliação | 1 | — | crítico |
| ART-02 | Confirmar no Teams a data de entrega (a especificação registra 20/09) | 1 | — | crítico |
| ART-03 | Resolver com o professor as duas dúvidas do template: primeira página aparentemente ausente e uso de livros nas referências | 1 | — | crítico |
| ART-04 | Preâmbulo: título, resumo e palavras-chave em português, inglês e espanhol, com no máximo 180 palavras por resumo | 2 | ART-03 | crítico |
| ART-05 | Seção 1 — Introdução (sem subtítulos, conforme o template) | 2 | ART-03 | crítico |
| ART-06 | Seção 2 — Fundamentação Teórica, subseções 2.1 a 2.5 | 2 | ART-05 | crítico |
| ART-07 | Seção 3 — Materiais e Métodos, subseções 3.1 a 3.6 | 2 | ART-06 | crítico |
| ART-08 | Seção 4 — Resultados e Discussões, subseções 4.1 a 4.4 | 2 | HW-05, ART-07 | crítico |
| ART-09 | Seção 5 — Considerações Finais | 2 | ART-08 | crítico |
| ART-10 | Referências em norma única (ABNT ou APA) mantida em todo o texto | 2 | ART-09 | crítico |
| ART-11 | Declaração de responsabilidade e declaração de uso (ou não uso) de IA; apagar o parágrafo de fundo amarelo | 2 | ART-10 | crítico |
| ART-12 | Revisão de formatação contra o template: fontes, espaçamentos, margens, figuras e tabelas | 2 | ART-11 | crítico |
| ART-13 | Submissão — **marco 20/09** | 2 | ART-12 | crítico |
| ART-14 | Apresentação no evento (requisito para o certificado e para os anais) | 4 | ART-13 | |

---

## Documentação

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| DOC-01 | Corrigir §4.5 e §9 da especificação: com o jumper JD-VCC no lugar não há isolamento galvânico por optoacoplador; a separação vem da rigidez dielétrica do contato do relé | 2 | — | crítico |
| DOC-02 | Atualizar §4.4: a alimentação deixou de ser pendente e está resolvida com o HLK-PM01 embutido | 2 | — | |
| DOC-03 | Atualizar o diagrama draw.io: MariaDB para PostgreSQL, Mosquitto para AWS IoT Core | 2 | — | |
| DOC-04 | Aplicar em §7.2 as sete divergências listadas no §9 da especificação do backend | 2 | — | |
| DOC-05 | Confirmar ou corrigir as premissas P1 a P9 da especificação do backend | 1 | — | crítico |
| DOC-06 | Documentação acadêmica e preparação da banca — **marco 16/10** | 4 | — | crítico |

---

## Infraestrutura

| ID | Item | Fase | Depende | Prio |
|---|---|---|---|---|
| INFRA-01 | Pipeline de CI (GitHub Actions) para Backend Spring Boot e Apps Kotlin Multiplataforma (Web, Mobile, Desktop) | 1 | — | crítico |
| INFRA-02 | Otimização de build: Configuration Cache e cache avançado do Gradle no CI | 1 | INFRA-01 | |
| INFRA-03 | Testes de integração com banco PostgreSQL no CI (Testcontainers ou GitHub Actions Service) | 1 | INFRA-01 | |
| INFRA-04 | Pipeline de CI para compilação do Firmware ESP-IDF (ESP32) | 1 | FW-01, INFRA-01 | |

---

## Caminho crítico

```
HW-01 ─┬─ HW-02/03/04 ── HW-05 (marco 04/09)
       ├─ HW-06 ────────── FW-07
       └─ HW-10 ── HW-11 ── HW-12/13/14 ── HW-15 ── HW-16 ── HW-17 (marco 02/10)

BE-01 ── BE-02 ── BE-03 ── BE-04 (marco 04/09) ── BE-10 ── BE-13
FW-01 ── FW-02 ── FW-09 ── FW-10 ─┬─ FW-11/12/13
                                  └─ BE-11 ── APP-04 (marco 25/09)
ART-03 ── ART-05..12 ── ART-13 (marco 20/09)
```

A compra dos componentes (HW-01) é o gargalo declarado no `README.md`: bloqueia
bancada, desenho mecânico e validação elétrica ao mesmo tempo. As trilhas de
backend, aplicativos e artigo não dependem dela.

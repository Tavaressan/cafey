# Especificação Técnica — Cafeteira Conectada

**Projeto Integrador — 6º Semestre**
**Versão:** 1.1 (execução individual)
**Equipe:** autor único, com apoio da empresa para fabricação da estrutura e validação elétrica

---

## 1. Visão Geral

### 1.1 Problema

Cafeteiras domésticas de baixo custo não são programáveis. O usuário precisa estar fisicamente presente e acordado para preparar o café, e não tem qualquer registro de consumo ou de manutenção do equipamento.

Cafeteiras programáveis existem no mercado, mas exigem a troca do aparelho e ainda assim raramente oferecem controle remoto, histórico ou alertas de manutenção.

### 1.2 Solução

Um módulo IoT externo que transforma **qualquer cafeteira com chave mecânica** em um aparelho conectado, sem furos, sem modificação interna e sem perda de garantia.

O módulo é uma base de aço posicionada sob a cafeteira, contendo um ESP32 e um relé que controla a alimentação elétrica do aparelho. A cafeteira permanece com sua chave travada na posição ligada — quem decide se ela liga é o relé.

O controle é feito por aplicativos web, mobile e desktop construídos em Kotlin Multiplataforma, com backend próprio e comunicação via MQTT sobre AWS IoT.

### 1.3 Princípios de Projeto

| Princípio | Implicação |
|---|---|
| **Não invasivo** | Nenhuma alteração física na cafeteira |
| **Modular** | Compatível com qualquer cafeteira de chave mecânica |
| **Configuração mínima** | O usuário pluga e vincula; nada além disso |
| **Autonomia local** | O agendamento dispara mesmo sem internet |
| **Independência do celular** | Botão físico como caminho alternativo sempre disponível |

---

## 2. Escopo

### 2.1 Dentro do escopo

- Base de aço dobrada com tomada fêmea, cabo macho e relé
- Firmware ESP32 em C++ sobre ESP-IDF
- Backend em Kotlin + Spring Boot
- Clientes web, mobile e desktop em Kotlin Multiplataforma (ver §2.3 — paridade)
- Banco de dados PostgreSQL
- Comunicação MQTT/TLS via AWS IoT Core
- Fallback BLE entre aplicativo mobile e base
- Autenticação de usuários
- Agendamento com repetição semanal
- Histórico de preparos e estatísticas
- Alerta de descalcificação por contagem de ciclos

### 2.2 Fora do escopo (nesta entrega)

- Sensor de corrente não invasivo (ver §11 — Lista de Desejos)
- Sensor de temperatura e umidade interno à cafeteira
- Bateria de backup para o ESP32
- Controle de moagem, dosagem ou nível de água
- Aplicativo para smartwatch ou assistentes de voz
- Multi-tenant comercial / loja de aplicativos

### 2.3 Paridade entre clientes

O requisito da disciplina é que a solução seja multiplataforma, não que as três interfaces sejam idênticas. Como o Kotlin Multiplataforma compartilha modelo, cliente HTTP e regras de apresentação, a diferença de custo está apenas na camada de UI — que é justamente a parte cara para um autor único.

| Cliente | Nível de entrega | Justificativa |
|---|---|---|
| **Mobile** | Completo | É o uso real do produto: comandar a cafeteira de longe e receber alertas. Único cliente com BLE. |
| **Web** | Essencial | Login, operação, agendamento e histórico. Sem gráficos elaborados. |
| **Desktop** | Essencial | Mesmo conjunto da web, reaproveitando composables. |

A camada compartilhada (`shared`) concentra autenticação, chamadas REST, modelos e validação. Nenhuma regra de negócio vive na UI.

### 2.4 Ordem de cortes

Se o cronograma apertar, corta-se nesta ordem — do primeiro ao último:

1. Fallback BLE e proxy BLE
2. Gráficos de estatística (mantendo o histórico em lista)
3. Compartilhamento de dispositivo com convidado (UC-05)
4. Cliente desktop
5. Recuperação de senha (UC-03)

**Nunca cortar:** agendamento offline, botão físico, aterramento e isolamento elétrico.

---

## 3. Arquitetura

```
┌──────────────┐
│   Usuário    │
└──────┬───────┘
       │
┌──────┴──────────────────────────┐
│   Kotlin Multiplataforma        │
│   ┌────────┬────────┬────────┐  │
│   │ Mobile │  Web   │Desktop │  │
│   └────────┴────────┴────────┘  │
└──────┬──────────────────────┬───┘
       │ HTTP/REST            │ BLE (contingência)
       │                      │
┌──────┴───────────────┐      │
│  Backend Kotlin      │      │
│  Spring Boot         │      │
└───┬──────────────┬───┘      │
    │              │          │
┌───┴──────┐  ┌────┴───────┐  │
│PostgreSQL│  │ AWS IoT    │  │
└──────────┘  │ Core (MQTT)│  │
              └────┬───────┘  │
                   │ MQTT/TLS │
              ┌────┴──────────┴──┐
              │  ESP32 (C++)     │
              │  ┌────────────┐  │
              │  │ Relé       │──┼──> Cafeteira (127/220V)
              │  │ LED RGB    │  │
              │  │ Botão      │  │
              │  └────────────┘  │
              └──────────────────┘
```

### 3.1 Camadas

| Camada | Tecnologia | Responsabilidade |
|---|---|---|
| Apresentação | Kotlin Multiplataforma | Interface web, mobile e desktop |
| Aplicação | Kotlin + Spring Boot | Regras de negócio, autenticação, API REST |
| Persistência | PostgreSQL | Usuários, dispositivos, agendamentos, eventos |
| Mensageria | AWS IoT Core (MQTT) | Canal bidirecional com os dispositivos |
| Dispositivo | ESP32 / ESP-IDF / C++ | Controle do relé, agendamento local, telemetria |
| Contingência | BLE | Canal direto app ↔ base; proxy para a nuvem |

---

## 4. Especificação de Hardware

### 4.1 Componentes

| Item | Especificação | Observação |
|---|---|---|
| Microcontrolador | ESP32 (Wi-Fi + BLE) | Antena externa à estrutura metálica |
| Relé | Módulo com optoacoplador, ≥10 A | Verificar tensão nominal gravada no componente |
| LED | RGB (cátodo ou ânodo comum) | Indicação de estado |
| Botão | Momentâneo, painel frontal | Comando manual e cancelamento |
| Tomada fêmea | Padrão NBR 14136 | Saída para a cafeteira |
| Cabo macho | Padrão NBR 14136, com terra | Entrada da rede elétrica |
| Fonte 5 V | Carregador USB **ou** módulo AC-DC embutido | Ver §4.4 |
| Estrutura | Chapa de aço dobrada | Produzida internamente na empresa |

### 4.2 Topologia elétrica

A base atua como **tomada inteligente**:

```
Rede elétrica ──[cabo macho]──> BASE ──[tomada fêmea]──> Cafeteira
                                  │
                            relé corta/libera a FASE
```

A cafeteira permanece com a chave mecânica travada na posição ligada. Energizada, ela inicia o preparo; desenergizada, ela para.

> **Restrição:** o modelo de cafeteira deve possuir **chave mecânica**, não botão eletrônico. Aparelhos com acionamento eletrônico não retomam o funcionamento automaticamente ao serem energizados.

### 4.3 Dimensionamento do relé

Regra prática:

```
Corrente (A) = Potência (W) ÷ Tensão (V)
```

| Cafeteira | Tensão | Corrente aproximada |
|---|---|---|
| 800 W | 127 V | ~6,3 A |
| 800 W | 220 V | ~3,6 A |
| 1200 W | 127 V | ~9,4 A |

Adota-se relé de **10 A com folga de ~2×** sobre a corrente calculada. Como a carga é puramente resistiva (resistência de aquecimento), **não há pico de partida** como ocorre em motores, o que simplifica o dimensionamento.

> Validação obrigatória da montagem por profissional de elétrica antes do primeiro uso com carga real.

### 4.4 Alimentação

Duas alternativas em avaliação:

| Opção | Vantagem | Desvantagem |
|---|---|---|
| Carregador USB externo | Simples, seguro, barato | Dois cabos, acabamento inferior |
| Módulo AC-DC embutido (tipo Hi-Link 5 V) | Cabo único, acabamento profissional | Exige mais cuidado de isolamento |

**Decisão pendente.** Preferência estética pelo módulo embutido; preferência prática pelo carregador externo.

O ESP32 e a cafeteira **não compartilham alimentação** — o ESP32 precisa continuar energizado quando o relé estiver aberto.

### 4.5 Segurança elétrica

**Aterramento**

- O fio terra do cabo de entrada é fixado à chapa de aço com terminal olhal e arruela dentada
- O mesmo terra segue para o pino de terra da tomada fêmea
- Objetivo: qualquer contato acidental de fase com a estrutura provoca atuação do disjuntor ou DR, em vez de eletrificar a base

**Isolamento — três camadas**

1. **Separação física** — região de rede elétrica e região de eletrônica de baixa tensão em lados opostos da caixa, com divisória plástica ou distância adequada. Cabos de alta e baixa tensão nunca em paralelo.
2. **Isolamento galvânico** — módulo de relé com optoacoplador, separando eletricamente o ESP32 do lado da carga.
3. **Conexões protegidas** — todas as emendas de rede em bornes com tampa. Nenhum condutor exposto próximo à chapa.

**Blindagem de RF**

A estrutura de aço atenua o sinal Wi-Fi e BLE. A antena do ESP32 deve ficar externa à caixa ou posicionada em janela plástica.

---

## 5. Firmware

### 5.1 Plataforma

- **Framework:** ESP-IDF (oficial da Espressif)
- **Linguagem:** C++
- **RTOS:** FreeRTOS (exposto nativamente pelo IDF)
- **Build:** CMake + `idf.py`
- **Persistência:** NVS (Non-Volatile Storage)

> **Decisão:** uso do ESP-IDF nativo em vez do PlatformIO. O PlatformIO oferece setup mais rápido e boa integração com VS Code, mas abstrai as camadas de configuração (menuconfig, particionamento de flash, CMake) que constituem objetivo de aprendizado do projeto. Além disso, costuma ficar algumas versões atrás do IDF oficial.

### 5.2 Arquitetura — Active Objects

O firmware adota o padrão **Active Object**: cada componente é uma tarefa FreeRTOS com fila de mensagens própria e máquina de estados interna. Não há memória compartilhada — a comunicação ocorre exclusivamente por eventos assíncronos, o que elimina mutexes e condições de corrida por construção.

| Active Object | Responsabilidade |
|---|---|
| **Cafeteira** | Máquina de estados do preparo, acionamento do relé, LED, botão físico |
| **Conectividade** | Wi-Fi, MQTT/TLS, BLE, fila de eventos pendentes |
| **Agendador** | Relógio (NTP), lista de agendamentos, disparo por horário |

O LED reflete o estado publicado pelo Active Object **Cafeteira**, combinado com o estado de rede publicado por **Conectividade**.

### 5.3 Estados do LED

**Regra mental:** a **cor** indica o estado da cafeteira; o **piscar** indica problema de rede.

| Cor / Padrão | Significado |
|---|---|
| Azul respirando (lento) | Conectado e ocioso |
| Verde fixo | Agendamento armado |
| Âmbar fixo | Desconectado, com agendamento armado |
| Verde pulsando | Preparando café agora |
| Branco fixo (alguns minutos) | Café pronto |
| Amarelo piscando | Sem Wi-Fi, ocioso (operando offline) |
| Vermelho | Erro ou falha |

### 5.4 Persistência local

- **Agendamentos** gravados em NVS — sobrevivem a reboot e queda de energia
- **Fila de eventos de preparo** com carimbo de tempo, em buffer circular no NVS
- Acesso ao NVS protegido por mutex (único ponto de coordenação entre tarefas)

### 5.5 Autonomia offline

O dispositivo **não depende da nuvem no momento do disparo**. Recebe os agendamentos quando conectado, grava localmente e executa pelo relógio interno sincronizado por NTP.

Se a conexão cair, o agendamento permanece ativo. Os eventos gerados no período ficam enfileirados e são enviados quando a conectividade retorna — pela nuvem ou pelo proxy BLE (§6.5).

### 5.6 Botão físico

- **Toque curto:** liga/desliga a cafeteira imediatamente
- **Toque durante preparo:** cancela o preparo em andamento
- Todo acionamento gera evento com origem `BOTAO`, registrado no histórico

Garante operação independente de celular, aplicativo, Wi-Fi ou nuvem.

---

## 6. Protocolo de Comunicação

### 6.1 Transporte

- **Broker:** AWS IoT Core
- **Protocolo:** MQTT sobre TLS
- **Autenticação:** certificado X.509 por dispositivo (exigido pelo AWS IoT)
- **QoS:** 1 em todos os tópicos

> **TLS e QoS são camadas distintas.** O TLS protege o *canal* — faz o handshake com certificado, cifra o tráfego e autentica as pontas. O QoS trata da *entrega* das mensagens: QoS 0 envia sem confirmação, QoS 1 garante entrega ao menos uma vez (podendo duplicar) e QoS 2 garante exatamente uma vez, sendo mais pesado e não suportado pela AWS.

### 6.2 Tópicos

Raiz: `dispositivos/{deviceId}/`

**Descendentes (nuvem → dispositivo)**

| Tópico | Conteúdo | Retain |
|---|---|---|
| `.../comando` | Ligar / desligar / cancelar agora | Não |
| `.../agendamentos` | **Lista completa** de agendamentos | Sim |

**Ascendentes (dispositivo → nuvem)**

| Tópico | Conteúdo | Retain |
|---|---|---|
| `.../estado` | Estado atual (ocioso, preparando, erro) | Sim |
| `.../eventos` | Eventos de preparo para o histórico | Não |
| `.../saude` | Telemetria, uptime, RSSI; **Last Will** para detecção de queda | Não |

### 6.3 Idempotência

**Regra:** o tópico de agendamentos transporta sempre a **lista completa**, nunca operações incrementais.

*Idempotente* significa que repetir a operação produz o mesmo resultado que executá-la uma única vez. Se a lista completa chega duplicada por reenvio, o ESP32 sobrescreve com o mesmo conteúdo e nada muda. Se a mensagem fosse "adicionar agendamento X", a duplicata criaria dois registros.

Como o MQTT em QoS 1 pode entregar a mesma mensagem mais de uma vez, a idempotência elimina toda uma classe de bugs de dessincronização.

**Custo da decisão:** pacote maior por atualização. Uma lista de 10 a 20 agendamentos com hora e dias da semana ocupa menos de 1 KB em JSON — irrelevante para um ESP32 com 4 MB de flash. Trocamos eficiência de banda por consistência de dados, e a troca é barata nesta escala.

**Bônus:** com `retain` ativo, o dispositivo religa e recebe a lista vigente sem precisar solicitá-la.

### 6.4 Fallback BLE

O ESP32 anuncia continuamente um serviço BLE com características para comando, estado e agendamentos.

Fluxo do aplicativo mobile:

1. Tenta a nuvem
2. Sem resposta, conecta por BLE diretamente à base (que está a poucos metros)
3. Executa o comando localmente

### 6.5 Proxy BLE → Nuvem

Quando a base está sem internet mas o celular está conectado, o aplicativo atua como ponte:

1. Lê por BLE a fila de eventos pendentes do ESP32
2. Envia esses eventos ao backend em nome do dispositivo
3. Confirma o envio, permitindo que o ESP32 limpe a fila

**Requisitos para isso funcionar:**

- Eventos gravados com carimbo de tempo na origem
- Backend aceita eventos atrasados sem duplicar (chave de deduplicação por dispositivo + timestamp de início)

---

## 7. Backend e Modelo de Dados

### 7.1 Stack

- **Linguagem:** Kotlin
- **Framework:** Spring Boot
- **Banco:** PostgreSQL
- **API:** REST para os clientes; consumo MQTT para os dispositivos

> **Decisão:** PostgreSQL em substituição ao MariaDB previsto no diagrama original. Justificativa: tipos de data/hora com suporte a fuso horário (crítico para agendamento), JSONB nativo para payloads de telemetria, e funções de janela mais completas para as estatísticas de consumo. O MariaDB é mais leve e de setup mais rápido, mas o projeto tem componente analítico relevante.
>
> *Pendência: atualizar o diagrama de arquitetura no draw.io.*

### 7.2 Tabelas

**usuarios**

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | PK |
| nome | texto | |
| email | texto | Único |
| senha_hash | texto | Hash forte (bcrypt/Argon2) |
| criado_em | timestamptz | |

**dispositivos**

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | PK |
| serial | texto | Identificador do ESP32, único |
| apelido | texto | Ex.: "cafeteira da cozinha" |
| firmware | texto | Versão instalada |
| ultimo_visto | timestamptz | Atualizado pelo tópico de saúde |
| ciclos_desde_descalcificacao | inteiro | Base do alerta de manutenção |

**usuario_dispositivo** *(vínculo)*

| Campo | Tipo | Observação |
|---|---|---|
| usuario_id | UUID | FK |
| dispositivo_id | UUID | FK |
| papel | enum | `DONO` \| `CONVIDADO` |

**agendamentos**

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | PK |
| dispositivo_id | UUID | FK |
| hora | time | |
| dias_semana | conjunto | Repetição semanal |
| ativo | booleano | |
| criado_por | UUID | FK usuários |

**eventos_preparo**

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | PK |
| dispositivo_id | UUID | FK |
| inicio | timestamptz | |
| fim | timestamptz | |
| duracao | intervalo | |
| origem | enum | `APP` \| `AGENDAMENTO` \| `BOTAO` |

> A coluna `origem` é a mais valiosa para as estatísticas: revela quanto do uso é programado, quanto é remoto e quanto é manual.

### 7.3 Manutenção — Descalcificação

Descalcificação é a remoção do acúmulo de calcário que a água deixa na resistência e nas tubulações da cafeteira. Com o tempo, a incrustação obstrui o fluxo, torna o preparo mais lento e reduz a vida útil do aparelho.

Fabricantes recomendam o procedimento a cada **60 a 90 ciclos**. Como o sistema contabiliza cada preparo, o alerta é gerado automaticamente ao atingir o limiar configurado, e o contador é zerado quando o usuário confirma a manutenção no aplicativo.

O contador incrementa apenas em preparo com resultado `CONCLUIDO`. Preparo interrompido por comando (UC-09) não completa o ciclo de água e, portanto, não contribui para a incrustação que a descalcificação remove.

---

## 8. Casos de Uso

### 8.1 Conta e Acesso

| ID | Caso de uso |
|---|---|
| UC-01 | Cadastrar usuário |
| UC-02 | Autenticar (login) |
| UC-03 | Recuperar senha |
| UC-04 | Vincular cafeteira à conta (provisionamento de Wi-Fi) |
| UC-05 | Compartilhar dispositivo com outro usuário (papel convidado) |

### 8.2 Operação

| ID | Caso de uso |
|---|---|
| UC-06 | Ligar cafeteira agora |
| UC-07 | Desligar cafeteira agora |
| UC-08 | Consultar estado atual em tempo real |
| UC-09 | Cancelar preparo em andamento |

### 8.3 Agendamento

| ID | Caso de uso |
|---|---|
| UC-10 | Criar agendamento |
| UC-11 | Editar agendamento |
| UC-12 | Excluir agendamento |
| UC-13 | Ativar / desativar agendamento |

### 8.4 Histórico e Manutenção

| ID | Caso de uso |
|---|---|
| UC-14 | Consultar histórico de preparos |
| UC-15 | Visualizar estatísticas de consumo |
| UC-16 | Receber alerta de descalcificação |
| UC-17 | Dar baixa na descalcificação (zerar contador) |

---

## 9. Requisitos Não Funcionais

| Categoria | Requisito |
|---|---|
| **Segurança de dados** | MQTT sobre TLS; certificado X.509 por dispositivo; senhas com hash forte |
| **Segurança elétrica** | Estrutura aterrada; relé dimensionado com folga; isolamento galvânico por optoacoplador; separação física entre alta e baixa tensão |
| **Resiliência** | Agendamento executa sem internet; fila local de eventos; fallback BLE; proxy BLE para a nuvem |
| **Desempenho** | Comando emitido pelo app chega à base em menos de 2 s com conectividade normal |
| **Usabilidade** | Botão físico como caminho independente do celular; configuração inicial mínima; estado legível pelo LED sem consultar o app |
| **Portabilidade** | Compatível com qualquer cafeteira de chave mecânica dentro do limite de corrente |
| **Manutenibilidade** | Monorepo único; contrato de tópicos MQTT versionado junto do firmware e do backend |

---

## 10. Cronograma (8 semanas)

Execução individual. Hardware e software avançam **em paralelo**, mas nunca no mesmo dia: bancada e solda pedem bloco contínuo, código admite sessões curtas.

| Fase | Período | Entregas |
|---|---|---|
| **1 — Fundação** | 24/08 – 04/09 | Protótipo do relé em bancada; esqueleto do backend com autenticação |
| **2 — Núcleo** | 07/09 – 25/09 | Firmware com Active Objects; MQTT + integração AWS IoT; mobile com operação básica |
| **3 — Funcionalidade completa** | 28/09 – 09/10 | Agendamento fim a fim; histórico; web e desktop no nível essencial; base de aço montada |
| **4 — Fechamento** | 12/10 – 16/10 | Testes; BLE (se couber); documentação acadêmica |

**Marcos de aceitação**

| Data | Critério |
|---|---|
| 04/09 | Relé aciona a carga por comando do ESP32 e o backend autentica ponta a ponta |
| 25/09 | Comando emitido no mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional da área |
| 09/10 | Agendamento dispara offline; histórico exibindo eventos reais |
| 16/10 | Projeto pronto para a banca |

### 10.1 Conflito com o artigo científico

A submissão do artigo (EnGeTec) ocorre em **20/09**, dentro da Fase 2 — o período de maior carga técnica do projeto.

Mitigação: sessões fixas de escrita às terças, quintas e sábados, alimentadas pelas decisões já registradas nesta especificação. O artigo descreve a proposta e resultados parciais; não depende do projeto concluído.

> **Risco gerenciado:** o BLE fica deliberadamente na última fase. É o item de maior valor marginal e menor criticidade — primeiro candidato a corte se o cronograma apertar (ver §2.4).

### 10.2 Riscos da execução individual

| Risco | Mitigação |
|---|---|
| Bloqueio técnico sem par para destravar | Ordem de cortes definida em §2.4; nenhum item crítico depende de item opcional |
| Sobreposição artigo × Fase 2 | Escrita incremental desde a Semana 1, não concentrada |
| Fabricação da base depender da agenda da empresa | Solicitar dobra na Fase 1, montar na Fase 3; protótipo em bancada não espera a estrutura |
| Validação elétrica depender de terceiro | Agendada para 02/10, com folga de uma semana antes do marco final |

---

## 11. Ferramentas e Versionamento

### 11.1 Repositório

**Monorepo único no GitHub.**

```
/
├── firmware/     C++ / ESP-IDF
├── backend/      Kotlin / Spring Boot
└── apps/         Kotlin Multiplataforma (web, mobile, desktop)
```

**Justificativa:** o Kotlin Multiplataforma já opera como projeto Gradle único, com módulos compartilhados entre os três clientes — separá-los seria trabalhar contra a ferramenta. O firmware tem build independente, mas conviver no mesmo repositório mantém o contrato dos tópicos MQTT versionado em sincronia com quem o consome. Para a banca, também é preferível: um link, um histórico.

**Fluxo (autor único):** branches por funcionalidade, integração na `main` por pull request do próprio autor. Ainda que não haja revisor, o PR serve como registro de escopo e justificativa de cada bloco de trabalho — material direto para a documentação acadêmica.

### 11.2 Ferramental por camada

| Camada | Ferramenta |
|---|---|
| Firmware | ESP-IDF (`idf.py`, CMake, menuconfig) |
| Backend | Gradle |
| Clientes | Gradle (Kotlin Multiplataforma) |
| Nuvem | AWS IoT Core |

---

## 12. Lista de Desejos

Itens avaliados e conscientemente adiados.

### 12.1 Sensor de corrente não invasivo (SCT-013)

Sensor tipo grampo que envolve o cabo de força sem qualquer contato elétrico. Permitiria:

- Confirmar que a resistência efetivamente ligou (validação real, não presumida)
- Medir a duração real do ciclo de preparo
- Detectar queda de potência ao longo do tempo — indício de incrustação de calcário

**Vantagem sobre o sensor interno:** não exige furos nem modificação da cafeteira, e é montado dentro da base envolvendo o cabo que já passa por ali. **Custo zero de configuração para o usuário** — o esforço é apenas de montagem.

Adiado por escopo, não por mérito técnico.

### 12.2 Sensor de temperatura e umidade

Presente no diagrama original. Descartado nesta versão por exigir instalação interna à cafeteira, o que quebraria o princípio de modularidade e amarraria o produto a um único modelo de aparelho.

O sensor de corrente (§12.1) atende o mesmo objetivo analítico sem esse custo.

### 12.3 Alimentação redundante

Bateria ou supercapacitor para o ESP32.

**Análise:** o ganho é limitado. Em falta de energia a cafeteira também não opera, de modo que o backup serviria apenas para preservar relógio e agendamentos — o que o NVS já garante sem bateria, desde que haja ressincronização por NTP no retorno.

Se houver interesse futuro, um RTC com bateria de moeda ou supercapacitor resolve com custo e risco muito menores que uma solução de lítio dentro de caixa metálica fechada.

---

## 13. Pendências

| # | Item | Responsável |
|---|---|---|
| 1 | Decidir alimentação: carregador externo vs. módulo AC-DC embutido | — |
| 2 | Atualizar diagrama draw.io (MariaDB → PostgreSQL) | — |
| 3 | Definir posicionamento da antena (janela plástica vs. antena externa) | — |
| 4 | Validação da montagem elétrica por profissional da área | Eletricista da empresa |
| 5 | Selecionar modelo de cafeteira (confirmar chave mecânica e potência) | — |
| 6 | Definir limiar de ciclos para alerta de descalcificação | — |

---

## 14. Limitações Declaradas

Limites conhecidos da solução, decorrentes de decisões de escopo. Estão aqui
para serem declarados na banca, não para serem resolvidos nesta entrega.

### 14.1 O sistema reporta acionamento, não conclusão de extração

O módulo apenas chaveia a energia da cafeteira. Não há sensor de corrente
(adiado — §12.1), de temperatura (descartado — §12.2) nem de nível de água. O
dispositivo não observa o fim da extração: ele o **temporiza**, cortando a
energia após uma duração configurada.

Consequências diretas:

| Situação | Comportamento |
|---|---|
| Preparo normal | Evento com resultado `CONCLUIDO` ao fim do temporizador |
| Cancelamento pelo usuário | Evento com resultado `CANCELADO` (UC-09) |
| Queda de energia durante o preparo | **Nenhum evento é gerado** |
| Cafeteira sem água ou sem pó | Evento `CONCLUIDO` normalmente |

Não existe terceiro desfecho detectável. O sistema afirma que a cafeteira foi
energizada pelo tempo previsto, não que café foi produzido.

O sensor de corrente não invasivo da §12.1 é exatamente o que fecharia essa
lacuna, confirmando que a resistência efetivamente ligou e medindo a duração
real do ciclo. Foi adiado por escopo, não por mérito técnico.

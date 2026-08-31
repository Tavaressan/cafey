# Cafey — Cafeteira Conectada

Projeto Integrador do 6º semestre (Desenvolvimento de Software Multiplataforma, Fatec Itaquera).

Módulo IoT (Internet of Things — Internet das Coisas) externo, em formato de base de aço, que
transforma qualquer cafeteira de chave mecânica em aparelho conectado, sem modificação interna.
Um ESP32 aciona um relé que corta a fase da alimentação da cafeteira. Controle por app Kotlin
Multiplataforma (web/mobile/desktop), backend Kotlin + Spring Boot, PostgreSQL, MQTT (Message
Queuing Telemetry Transport) sobre AWS IoT Core.

## Estrutura

```
/
├── hardware/   KiCad — esquemático, PCB e bibliotecas do módulo eletrônico
├── firmware/   C++ / ESP-IDF — firmware do ESP32
├── backend/    Kotlin / Spring Boot — API e integração MQTT
├── apps/       Kotlin Multiplataforma — app web/mobile/desktop
└── docs/       Especificação e documentação acadêmica
```

## Abrindo o esquemático

Requer **KiCad 10.0.5** (ou outra versão 10.0.x) — a mesma versão maior usada para criar o
projeto. Abra `hardware/pi_dsm_26_2.kicad_pro`.

A biblioteca de símbolo de terceiros usada pelo ESP32 (`ESP32_30Pin`, DOIT ESP32 DevKit V1 de 30
pinos) está versionada em `hardware/libraries/esp32_30pin.kicad_sym` e registrada na tabela de
bibliotecas do projeto (`hardware/sym-lib-table`) com caminho relativo — não depende de nenhuma
biblioteca instalada fora do repositório.

## Aviso de segurança elétrica

O hardware envolve tensão de rede (127 V). A montagem exige validação por profissional de
elétrica antes do primeiro uso com carga real.

## Cronograma

Execução individual, oito semanas. Hardware e software avançam em paralelo, mas nunca no mesmo
dia: bancada e solda pedem bloco contínuo, código admite sessões curtas.

**Posição atual (31/08/2026):** Fase 1, esquemático elétrico validado. O gargalo do momento é a
compra dos componentes — ela bloqueia a bancada, o desenho mecânico e a validação elétrica ao
mesmo tempo.

### Fase 1 — Fundação (24/08 – 04/09)

| Estado | Entrega |
|---|---|
| ✅ | Especificação técnica v1.1 |
| ✅ | Seleção da cafeteira: Britânia CP30 Inox, 800 W, 127 V, chave KCD1-106 |
| ✅ | Decisões de hardware fechadas (alimentação, relé, LED, proteção, pinagem) |
| ✅ | Esquemático KiCad validado nó a nó contra a especificação |
| ✅ | Repositório e documentação de estado |
| ⬜ | Compra dos componentes |
| ⬜ | Conferência de bancada: serigrafia do DevKit, pinagem do módulo de relé, pernas do LED |
| ⬜ | Protótipo do relé em bancada acionado pelo ESP32 |
| ⬜ | Esqueleto do backend com autenticação |

### Fase 2 — Núcleo (07/09 – 25/09)

| Trilha | Entrega |
|---|---|
| Firmware | Estrutura de Active Objects sobre FreeRTOS; persistência em NVS (Non-Volatile Storage — armazenamento não volátil) |
| Firmware | MQTT sobre TLS (Transport Layer Security — segurança da camada de transporte) com certificado X.509; integração AWS IoT Core |
| Backend | API REST de dispositivos, comandos e agendamentos; consumo MQTT |
| Mobile | Operação básica: login, ligar, desligar, estado em tempo real |
| Estrutura | Desenho mecânico da chapa e solicitação de dobra à empresa |
| Artigo | Escrita incremental (ver trilha própria abaixo) |

### Fase 3 — Funcionalidade completa (28/09 – 09/10)

| Trilha | Entrega |
|---|---|
| Firmware | Agendamento local disparando por relógio sincronizado por NTP (Network Time Protocol — protocolo de tempo de rede) |
| Backend | Histórico de preparos, estatísticas, contador de descalcificação |
| Clientes | Web e desktop no nível essencial, reaproveitando composables |
| Estrutura | Montagem da base: fixação, aterramento, separação alta/baixa tensão |
| Segurança | Validação da montagem elétrica por profissional habilitado |

### Fase 4 — Fechamento (12/10 – 16/10)

| Trilha | Entrega |
|---|---|
| Testes | Ciclo completo com carga real; agendamento offline; queda de rede |
| Firmware | Fallback BLE (Bluetooth Low Energy — Bluetooth de baixa energia) e proxy BLE, se o cronograma permitir |
| Documentação | Documentação acadêmica e preparação para a banca |

## Estrutura física

A base é uma chapa de aço dobrada, produzida internamente na empresa. O caminho crítico passa
pela compra dos componentes: sem as dimensões reais das peças, o desenho não fecha.

| # | Atividade | Depende de |
|---|---|---|
| 1 | Levantar dimensões de ESP32, módulo de relé, HLK-PM01, tomada fêmea | Compras |
| 2 | Planificação da chapa: recortes de tomada, cabo, botão e LED | 1 |
| 3 | Definir separação física entre região de rede e região de baixa tensão | 2 |
| 4 | Resolver posicionamento da antena (janela plástica ou antena externa) | 2 |
| 5 | Prever fixação do terminal olhal de aterramento na chapa | 2 |
| 6 | Solicitar dobra à empresa | 2, 3, 4, 5 |
| 7 | Montagem: fiação de rede em bornes com tampa, fixação dos módulos | 6 |
| 8 | Validação elétrica por profissional habilitado | 7 |

O item 3 não é detalhe de acabamento: a especificação exige rede elétrica e eletrônica de baixa
tensão em lados opostos da caixa, sem cabos de alta e baixa tensão em paralelo. O item 4 existe
porque a estrutura metálica atenua Wi-Fi e BLE.

## Artigo — EnGeTec 2026

Submissão prevista para 20/09, dentro da Fase 2 — o período de maior carga técnica. Mitigação:
sessões fixas de escrita às terças, quintas e sábados, alimentadas pelas decisões já registradas
na especificação. O artigo descreve a proposta e resultados parciais; não depende do projeto
concluído.

**Confirmar a data no Teams** — a orientação da disciplina remete a ela, e a data acima vem da
especificação do projeto.

| Semana | Atividade |
|---|---|
| 31/08 – 06/09 | Cadastro no site do evento; levantamento bibliográfico |
| 31/08 – 06/09 | Definir recorte do artigo e estrutura das seções |
| 07/09 – 13/09 | Fundamentação teórica e método |
| 14/09 – 20/09 | Resultados parciais, conclusão, revisão de formatação |
| 20/09 | Submissão |
| a definir | Apresentação no evento (requisito para o certificado e para os anais) |

**Requisitos de submissão:**

- Template oficial do EnGeTec, sem qualquer alteração de formatação
- Mínimo de 10 artigos relacionados; referências em livros
- Não usar sites ou blogs como referência
- Título, resumo e palavras-chave em português, inglês e espanhol
- Resumo com no máximo 180 palavras
- Norma única em todo o texto: ABNT ou APA
- E-mail do professor orientador por último na lista de autores
- Declaração de uso (ou não uso) de ferramentas de IA ao final

## Marcos de aceitação

| Data | Critério |
|---|---|
| 04/09 | Relé aciona a carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Artigo submetido ao EnGeTec |
| 25/09 | Comando emitido no mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico exibindo eventos reais |
| 16/10 | Projeto pronto para a banca |

## Ordem de cortes

Se o cronograma apertar, corta-se nesta ordem:

1. Fallback BLE e proxy BLE
2. Gráficos de estatística (mantendo o histórico em lista)
3. Compartilhamento de dispositivo com convidado
4. Cliente desktop
5. Recuperação de senha

**Nunca cortar:** agendamento offline, botão físico, aterramento e isolamento elétrico.

Ver [`STATUS.md`](STATUS.md) para o estado atual do projeto.

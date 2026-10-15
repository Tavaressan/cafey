# Planejamento do Projeto — Cafey

Documento complementar de planejamento, marcos e cronograma de desenvolvimento do projeto **Cafey**.

---

## Cronograma

Execução individual, oito semanas. Hardware e software avançam em paralelo, mas nunca no mesmo dia: bancada e solda pedem bloco contínuo, código admite sessões curtas.

### Fase 1 — Fundação (24/08 – 04/09)

| Estado | Entrega |
|:---:|---|
| ✅ | Especificação técnica v1.1 |
| ✅ | Seleção da cafeteira: Britânia CP30 Inox, 800 W, 127 V, chave KCD1-106 |
| ✅ | Decisões de hardware fechadas (alimentação, relé, LED, proteção, pinagem) |
| ✅ | Esquemático KiCad validado nó a nó contra a especificação |
| ✅ | Repositório e documentação de estado |
| ✅ | Compra dos componentes |
| ✅ | Conferência de bancada: serigrafia do DevKit, pinagem do módulo de relé, pernas do LED |
| ✅ | Protótipo do relé em bancada acionado pelo ESP32 |
| ✅ | Esqueleto do backend com autenticação |

### Fase 2 — Núcleo (07/09 – 25/09)

| Trilha | Entrega |
|---|---|
| Firmware | Estrutura de Active Objects sobre FreeRTOS; persistência em NVS (Non-Volatile Storage) |
| Firmware | MQTT sobre TLS com certificado X.509; integração AWS IoT Core |
| Backend | API REST de dispositivos, comandos e agendamentos; consumo MQTT |
| Mobile | Operação básica: login, ligar, desligar, estado em tempo real |
| Estrutura | Desenho mecânico da chapa e solicitação de dobra à empresa |
| Artigo | Escrita incremental |

### Fase 3 — Funcionalidade completa (28/09 – 09/10)

| Trilha | Entrega |
|---|---|
| Firmware | Agendamento local disparando por relógio sincronizado por NTP (Network Time Protocol) |
| Backend | Histórico de preparos, estatísticas, contador de descalcificação |
| Clientes | Web e desktop no nível essencial, reaproveitando composables |
| Estrutura | Montagem da base: fixação, aterramento, separação alta/baixa tensão |
| Segurança | Validação da montagem elétrica por profissional habilitado |

### Fase 4 — Fechamento (12/10 – 16/10)

| Trilha | Entrega |
|---|---|
| Testes | Ciclo completo com carga real; agendamento offline; queda de rede |
| Firmware | Fallback BLE (Bluetooth Low Energy) e proxy BLE |
| Documentação | Documentação acadêmica e preparação para a banca |

---

## Estrutura física

A base é uma chapa de aço dobrada, produzida internamente na empresa. O caminho crítico passa pelas dimensões reais das peças para o fechamento dos recortes.

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

*Nota sobre a separação física:* a especificação exige rede elétrica e eletrônica de baixa tensão em lados opostos da caixa, sem cabos de alta e baixa tensão em paralelo, minimizando ruídos e riscos de isolamento.

---

## Artigo — EnGeTec 2026

Submissão de artigo acadêmico sobre o projeto ao EnGeTec.

| Semana | Atividade |
|---|---|
| 31/08 – 06/09 | Cadastro no site do evento; levantamento bibliográfico |
| 31/08 – 06/09 | Definir recorte do artigo e estrutura das seções |
| 07/09 – 13/09 | Fundamentação teórica e método |
| 14/09 – 20/09 | Resultados parciais, conclusão, revisão de formatação |
| 20/09 | Submissão |
| A definir | Apresentação no evento |

**Requisitos de submissão:**
- Template oficial do EnGeTec, sem qualquer alteração de formatação
- Mínimo de 10 artigos relacionados; referências em livros
- Não usar sites ou blogs como referência
- Título, resumo e palavras-chave em português, inglês e espanhol (resumo de até 180 palavras)
- Norma única em todo o texto (ABNT ou APA)
- E-mail do professor orientador por último na lista de autores
- Declaração de uso (ou não uso) de ferramentas de IA ao final

---

## Marcos de aceitação

| Data | Critério |
|---|---|
| 04/09 | Relé aciona a carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Artigo submetido ao EnGeTec |
| 25/09 | Comando emitido no mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico exibindo eventos reais |
| 16/10 | Projeto pronto para a banca |

---

## Ordem de cortes

Se o cronograma apertar, corta-se nesta ordem:

1. Fallback BLE e proxy BLE
2. Gráficos de estatística (mantendo o histórico em lista)
3. Compartilhamento de dispositivo com convidado
4. Cliente desktop
5. Recuperação de senha

**Nunca cortar:** agendamento offline, botão físico, aterramento e isolamento elétrico.

Para o status das tarefas, consulte também [`STATUS.md`](../STATUS.md).

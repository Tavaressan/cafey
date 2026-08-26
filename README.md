# Cafey — Cafeteira Conectada

Projeto Integrador do 6º semestre (Desenvolvimento de Software Multiplataforma, Fatec Zona Leste).

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

Ver [`STATUS.md`](STATUS.md) para o estado atual do projeto.

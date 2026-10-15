# Cafey — Cafeteira Conectada

Projeto Integrador do 6º semestre — Curso Superior de Tecnologia em Desenvolvimento de Software Multiplataforma (Fatec Itaquera).

---

## i) Nome do Projeto e do Grupo

- **Nome do Projeto:** Cafey — Cafeteira Conectada
- **Grupo:** Vitor Tavares Chaves

---

## ii) Link do Repositório no Git

- **Repositório GitHub:** [https://github.com/Tavaressan/cafey](https://github.com/Tavaressan/cafey)

---

## iii) Nome dos Integrantes e Responsabilidade das Entidades

- **Integrante:** Vitor Tavares Chaves (Execução individual)
- **Entidades sob responsabilidade:**
  - `Dispositivo`: Modelagem, persistência relacional, validação, regras de negócio e endpoints no backend; modelos de dados, repositórios e telas correspondentes no frontend.
  - `Agendamento`: Modelagem, persistência relacional com chave estrangeira e timezone, validação, busca JPQL com LIKE no backend; sincronização de payloads e telas de agendamento no frontend.
  - *Outras atribuições:* Arquitetura de autenticação segura (Spring Security com JWT RS256 e Ktor Client com Bearer Auth), pipeline de CI/CD, esquemático eletrônico no KiCad e firmware C++/FreeRTOS no ESP32.

---

## iv) Descrição da Aplicação e Problemas que Resolve

O **Cafey** é um ecossistema IoT (Internet das Coisas) composto por um módulo de hardware externo (em formato de base de apoio para cafeteiras), um firmware embarcado com conectividade em nuvem, um backend robusto em nuvem e aplicativos multiplataforma para os usuários finais.

### O que a aplicação faz
A solução transforma qualquer cafeteira comum de chave mecânica ("cafeteira convencional/burra") em uma cafeteira conectada e inteligente, **sem exigir nenhuma intervenção ou modificação nos circuitos internos do eletrodoméstico**, preservando integralmente a integridade física e a segurança do aparelho original. 

A cafeteira é conectada à tomada de saída da base Cafey e mantida com seu interruptor físico ligado; o módulo Cafey controla a passagem de alimentação através de um relé interno acionado por um microcontrolador ESP32.

### Problemas que resolve e melhorias proporcionadas
1. **Modernização Sustentável sem Descarte (Retrofit):** Permite usufruir de conveniências de automação e agendamento sem a necessidade de descartar aparelhos funcionais ou adquirir eletrodomésticos inteligentes proprietários de alto custo.
2. **Acionamento Remoto com Segurança:** Permite ligar, desligar ou cancelar preparos à distância através de aplicativos para Web, Celular (Android) e Computador (Desktop), com desligamento automático configurável (evitando que a resistência fique aquecida indefinidamente).
3. **Agendamento Autônomo e Resiliente (Offline-First):** Os agendamentos recorrentes configurados pelo usuário são armazenados na memória não-volátil do ESP32. Caso a conexão Wi-Fi ou a internet caiam, o microcontrolador mantém seu relógio sincronizado localmente e dispara o preparo do café com total independência da nuvem.
4. **Manutenção Preventiva e Cuidados com o Aparelho:** Monitora a volumetria e contadores de ciclos de preparo, alertando o usuário sobre momentos ideais para descalcificação do reservatório, enxágue preventivo da tubulação e substituição do filtro de água.
5. **Experiência Multiplataforma Integrada:** Permite o gerenciamento de múltiplos dispositivos e compartilhamento seguro entre membros de uma residência ou escritório com diferentes níveis de permissão (Proprietário e Convidado).

---

## v) Tecnologias Utilizadas

### Backend
- **Linguagem & Runtime:** Kotlin `2.3.21` sobre JDK `21` (Eclipse Temurin)
- **Framework Principal:** Spring Boot `4.1.1`
  - Spring MVC (`spring-boot-starter-webmvc`)
  - Spring Data JPA (`spring-boot-starter-data-jpa` com Hibernate `7.4`)
  - Spring Security (`spring-boot-starter-security` e resource server) com tokens JWT assimétricos (RS256)
  - Bean Validation (`spring-boot-starter-validation` com Hibernate Validator)
  - Spring Mail (`spring-boot-starter-mail` integrado ao AWS SES em produção)
- **Banco de Dados & Migrações:** PostgreSQL (produção/dev) com Flyway `12.4` (`flyway-database-postgresql`) e H2 Database em memória para testes unitários
- **Documentação de API:** SpringDoc OpenAPI 3 / Swagger UI `3.1.1`
- **Rate Limiting & Resiliência:** Bucket4j `8.19.0`
- **Testes & Integração:** JUnit 5, Mockito, Spring Boot Test, Testcontainers `1.20.4` (PostgreSQL)

### Frontend (Apps Multiplataforma)
- **Linguagem:** Kotlin `2.3.21` (Kotlin Multiplatform — KMP)
- **UI Framework:** Compose Multiplatform `1.11.1` (Jetpack Compose compartilhado)
- **Alvos Suportados:**
  - **Android:** Android Gradle Plugin `8.13.2` (compileSdk 36, targetSdk 36, minSdk 26)
  - **Desktop:** Compose Desktop JVM
  - **Web:** Kotlin/Wasm (WebAssembly) executado no navegador
- **Comunicação HTTP & Serialização:** Ktor Client `3.5.2` (com engines OkHttp para Android, CIO para Desktop e Js/Wasm para Web) e Kotlinx Serialization JSON `1.11.0`
- **Concorrência & Tempo:** Kotlinx Coroutines `1.11.0` e Kotlinx DateTime `0.7.1`

### Firmware, Hardware & Nuvem
- **Microcontrolador:** Espressif ESP32 (DOIT DevKit V1 de 30 pinos)
- **Framework de Firmware:** ESP-IDF em C++ com FreeRTOS (Active Objects, RAII e NVS Storage)
- **Protocolo IoT:** MQTT com criptografia TLS mútua (certificados X.509) sobre AWS IoT Core
- **Desenho Eletrônico (EDA):** KiCad `10.0.5`
- **Infraestrutura de Nuvem:**
  - **API & Banco:** AWS Lightsail (contêineres Docker atrás de proxy reverso Caddy com terminação TLS automática)
  - **Frontend Web:** Vercel (distribuição estática de assets Wasm)
  - **CI/CD:** GitHub Actions com testes automatizados, verificação de segredos (GitGuardian) e deploy automatizado

---

## vi) Camadas da Arquitetura e Estrutura de Pastas

A arquitetura do projeto segue o padrão em camadas e segregação por funcionalidade/domínio:

### 1. Backend (`backend/cafey-backend/src/main/kotlin/br/com/tavaressan/cafey/`)

| Camada / Responsabilidade | Pacote / Pasta | Arquivos e Classes Principais |
|---|---|---|
| **Entidades de Domínio (JPA)** | `device/`, `schedule/`, `user/`, `event/` | `Dispositivo.kt`, `Agendamento.kt`, `Usuario.kt`, `EventoPreparo.kt` |
| **Repositórios de Dados (Data JPA)** | `device/`, `schedule/`, `user/`, `event/` | `DispositivoRepository.kt` (JPQL com LIKE), `AgendamentoRepository.kt` (JPQL com LIKE), `UsuarioRepository.kt`, `EventoRepository.kt` |
| **Serviços de Negócio** | `device/`, `schedule/`, `auth/`, `event/` | `DispositivoService.kt`, `AgendamentoService.kt`, `AuthService.kt`, `EventoService.kt` |
| **Controladores REST (API)** | `device/`, `schedule/`, `auth/`, `event/` | `DispositivoController.kt`, `AgendamentoController.kt`, `AuthController.kt`, `EventoController.kt` |
| **Objetos de Transferência (DTOs com Validação)** | `device/`, `schedule/`, `auth/`, `event/` | `DeviceDto.kt`, `ScheduleDto.kt`, `AuthDto.kt`, `EventoDto.kt` |
| **Camada Mapper (Conversão Entidade ↔ DTO)** | `mapper/` | `DispositivoMapper.kt`, `AgendamentoMapper.kt` |
| **Segurança e Filtros JWT** | `security/` | `SecurityConfig.kt` (`SecurityFilterChain`), `JwtAuthenticationFilter.kt`, `JwtTokenService.kt`, `RateLimitingFilter.kt` |
| **Tratamento de Exceções (RFC 9457)** | `exception/` | `GlobalExceptionHandler.kt`, exceções especializadas de domínio |
| **Configurações Gerais** | `config/` | `OpenApiConfig.kt` (SpringDoc Swagger), `RateLimitConfig.kt` |
| **Integração MQTT / Nuvem** | `mqtt/` | `MqttService.kt`, `MqttPayloads.kt` |
| **Migrações de Banco de Dados** | `src/main/resources/db/migration/` | `V1__init.sql` a `V8__agendamentos_descricao.sql` |

### 2. Frontend / Apps Multiplataforma (`apps/`)

| Camada / Responsabilidade | Pacote / Pasta | Descrição |
|---|---|---|
| **Modelos de Domínio Compartilhados** | `shared/src/commonMain/.../domain/model/` | Classes de dados de domínio (`Device`, `Schedule`, `CareItem`, `UserSession`) consumidas pelas UIs |
| **Validação de Formulários** | `shared/src/commonMain/.../domain/validation/` | Validadores de regras de negócio de entrada para telas e formulários |
| **Rede e Consumo de API (Ktor)** | `shared/src/commonMain/.../network/` | Clientes HTTP tipados (`ApiClient.kt`, `DeviceApi.kt`, `ScheduleApi.kt`, `AuthApi.kt`) |
| **Gerenciamento de Autenticação** | `shared/src/commonMain/.../auth/` | Gerenciamento de token Bearer em memória e preferências criptografadas |
| **Interface com Usuário (Telas e ViewModels)** | `shared/src/commonMain/.../ui/` | Telas segregadas por módulo: `home/`, `device/`, `schedule/`, `care/`, `history/`, `account/`, `auth/` |
| **Design System e Tema** | `shared/src/commonMain/.../ui/theme/` | `CafeyTheme.kt`, `CafeyColors.kt`, `CafeyTypography.kt`, recursos visuais e fontes |
| **Roteamento e Navegação** | `shared/src/commonMain/.../ui/CafeyNavHost.kt` | Máquina de estados de navegação declarativa entre telas com suporte responsivo |
| **Ponto de Entrada Android** | `apps/androidApp/` | Configurações do SDK Android, Manifest e `MainActivity.kt` |
| **Ponto de Entrada Desktop** | `apps/desktopApp/` | Executável JVM para desktop (`Main.kt` com janela do Compose Desktop) |
| **Ponto de Entrada Web** | `apps/webApp/` | Ponto de entrada Wasm (`main.kt`), inicializador de Canvas HTML e configuração de bundle |

---

## vii) Instruções de Instalação, Execução e Acesso

### Pré-requisitos
- **Java Development Kit (JDK):** Versão 21 ou superior (recomendado Eclipse Temurin 21)
- **Docker & Docker Compose:** Versão 24+ instalada e ativa
- **Git:** Para clonagem e versionamento
- *(Opcional)* **Android Studio com Android SDK (API 36):** Caso deseje compilar ou executar o aplicativo Android

---

### 1. Executando o Backend

#### Opção A — Stack Completa via Docker Compose (Recomendado para avaliação local)
A partir da raiz do repositório:
```bash
cd backend
docker compose up --build
```
Isso iniciará:
1. O banco de dados PostgreSQL na porta `5432`.
2. A aplicação Spring Boot na porta `8080`, aplicando todas as migrações Flyway automaticamente.

#### Opção B — Executando a API diretamente no Host (via Gradle)
```bash
cd backend
docker compose up -d db     # Sobe apenas o banco de dados
cd cafey-backend
./gradlew bootRun           # No Windows: gradlew.bat bootRun
```

#### Acesso e Teste da API via Swagger UI:
- **Ambiente Local:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Especificação OpenAPI JSON Local:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Ambiente de Produção (Online na AWS):** [https://cafey-backend.duckdns.org/swagger-ui.html](https://cafey-backend.duckdns.org/swagger-ui.html)

---

### 2. Executando os Aplicativos Multiplataforma

A partir da pasta `apps/`:

#### A. Executar o Aplicativo Desktop (Windows / Linux / macOS)
```bash
cd apps
./gradlew :desktopApp:run   # No Windows: gradlew.bat :desktopApp:run
```

#### B. Executar o Aplicativo Web Localmente (Kotlin/Wasm)
```bash
cd apps
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```
Acesse o app no navegador através do endereço local indicado (geralmente `http://localhost:8081`).

#### C. Versão Web em Produção (Disponível Online)
O aplicativo Web compilado para WebAssembly está publicado e acessível publicamente em:
- **App Web Online:** [https://cafey-web.vercel.app](https://cafey-web.vercel.app)

#### D. Executar o Aplicativo Android
Com um emulador ou dispositivo físico conectado com depuração USB ativa:
```bash
cd apps
./gradlew :androidApp:installDebug
```

---

## Segurança e Premissas da API

1. **Lista Fechada de Rotas Públicas:** As seguintes rotas têm acesso público liberado no `SecurityConfig.kt` (todas as demais exigem obrigatoriamente autenticação Bearer JWT):
   - `POST /auth/signin` e `POST /auth/signup` (rotas oficiais de login e cadastro)
   - `POST /auth/login` e `POST /auth/registrar` (aliases mantidos para retrocompatibilidade)
   - `POST /auth/refresh` (renovação do access token via refresh token)
   - `POST /auth/recuperar-senha` e `POST /auth/redefinir-senha` (fluxo de recuperação)
   - `GET /redefinir-senha.html` (página web de redefinição de senha)
   - `/swagger-ui/**`, `/swagger-ui.html` e `/v3/api-docs/**` (documentação interativa da API)
2. **Isolamento de Dados por Usuário:** As consultas e listagens (`GET /dispositivos` e `GET /dispositivos/{id}/agendamentos`) não expõem dados globais entre usuários; a API retorna estritamente os recursos vinculados ao usuário autenticado identificado pelo token JWT.
3. **Terminação TLS e Cabeçalhos de Proxy:** O backend opera configurado com `server.forward-headers-strategy: native`, compatível com a terminação TLS do proxy reverso Caddy em produção.

---

## Hardware e Segurança Elétrica

- **Aviso de Segurança Elétrica:** O módulo de hardware atua diretamente sobre a tensão de rede alternada (127 V). A montagem e operação devem respeitar as diretrizes de isolamento galvânico entre alta e baixa tensão e aterramento da carcaça metálica.
- **Esquemático KiCad:** O projeto eletrônico está disponível na pasta `hardware/` e requer o **KiCad 10.0.5** (ou versão 10.0.x compatível). Abra o arquivo `hardware/pi_dsm_26_2.kicad_pro`. Todas as bibliotecas de símbolos e footprints foram configuradas com caminhos relativos ao repositório.

Para consultar o cronograma histórico de desenvolvimento, marcos de aceitação e ordem de prioridades, veja o documento de [Planejamento do Projeto](docs/planejamento.md).

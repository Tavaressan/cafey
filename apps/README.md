# Caféy — apps

Projeto Kotlin Multiplatform (KMP) dos clientes Caféy: Android, Desktop (JVM) e Web (Kotlin/Wasm).
Build Gradle independente do backend (`backend/cafey-backend`), com wrapper próprio.

Sem target iOS: o ambiente de desenvolvimento é Windows e o CI roda em `ubuntu-latest`, onde o
target iOS não compila.

## Módulos

- `shared` — modelos de domínio, cliente HTTP (Ktor), autenticação e o design system Caféy
  (`br.com.tavaressan.cafey.shared.ui.theme`), compartilhados pelos três clientes. Nenhuma regra de
  negócio mora na UI (spec §2.3).
- `androidApp` — cliente Android.
- `desktopApp` — cliente Desktop (JVM, Compose Desktop).
- `webApp` — cliente Web (Kotlin/Wasm, Compose Multiplatform).

## Rodando cada target

Todos os comandos a seguir rodam a partir de `apps/` (pasta `cafey-frontend/` no `Cafey-frontend.zip` da entrega).

### Android

Requer Android SDK instalado (`sdk.dir` em `apps/local.properties`, não versionado).

```bash
./gradlew :androidApp:assembleDebug
# instala num dispositivo/emulador conectado:
./gradlew :androidApp:installDebug
```

### Desktop

```bash
./gradlew :desktopApp:run
```

### Web

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Abre um servidor de desenvolvimento local servindo o Compose compilado para Wasm.

## Build completo (gate de CI)

```bash
./gradlew build
```

Compila os três targets e roda os testes de `shared` (`commonTest`).

## Design system

`shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/theme` traduz
`docs/docs_interface/prototype/assets/cafey.css` para Compose: `CafeyColors`, `CafeyTypography` e
`CafeyShapes`, expostos via o composable `CafeyTheme`. As fontes (Space Grotesk, Instrument Sans,
JetBrains Mono — SIL OFL) estão empacotadas em `shared/src/commonMain/composeResources/font`.

## Notificações locais (Desktop)

O cliente Desktop (`apps/desktopApp`) exibe notificações nativas do sistema operacional por meio de
um ícone na bandeja (`Tray`) enquanto o aplicativo estiver aberto (inclusive minimizado na bandeja).
A detecção de transições (café pronto, falha no preparo, preparo iniciado e cafeteira offline) é
feita em componente reutilizável e testável em `shared` (`DesktopNotificationDetector`), alimentado
pelos dados do polling do `HomeViewModel`.

Limitações atuais:
- **Só com o app aberto:** não há push do sistema operacional para o app JVM; se o app for encerrado,
  nenhuma notificação é entregue.
- **Eventos de preparo por `eventoId`:** além das transições de estado/online observadas em todos os
  dispositivos carregados, os eventos recentes de preparo (`CONCLUIDO`/`ERRO`) são consultados no
  polling apenas para o dispositivo selecionado (1 requisição extra por ciclo), para evitar
  multiplicar chamadas quando o usuário possui vários dispositivos. Transições de preparo iniciando e
  cafeteira offline são detectadas para todos os dispositivos presentes na lista.
- **Histórico:** na primeira leitura após abrir o app ou trocar de conta, o estado atual não gera
  notificações retroativas; apenas transições observadas depois disso são notificadas. Eventos são
  deduplicados por `eventoId`.

Por padrão, fechar a janela mantém o app ativo na bandeja para continuar notificando; use "Sair" no
menu da bandeja para encerrar. Clicar no ícone/menu da bandeja traz a janela para a frente e mantém
o dispositivo ativo selecionado (a seleção do dispositivo associado à última notificação é aplicada
ao reabrir).

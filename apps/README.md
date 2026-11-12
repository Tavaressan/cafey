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

#### Push (Firebase Cloud Messaging, #364)

O app Android suporta notificações push via Firebase Messaging **sem** o plugin `google-services` e
**sem** `google-services.json` versionado. O `FirebaseApp` é inicializado manualmente a partir de
`BuildConfig`, com valores lidos de `apps/local.properties` e/ou variáveis de ambiente:

| Chave em `local.properties` | Variável de ambiente | `BuildConfig` |
| --- | --- | --- |
| `cafey.firebase.apiKey` | `CAFEY_FIREBASE_API_KEY` | `FIREBASE_API_KEY` |
| `cafey.firebase.appId` | `CAFEY_FIREBASE_APP_ID` | `FIREBASE_APP_ID` |
| `cafey.firebase.projectId` | `CAFEY_FIREBASE_PROJECT_ID` | `FIREBASE_PROJECT_ID` |
| `cafey.firebase.gcmSenderId` | `CAFEY_FIREBASE_GCM_SENDER_ID` | `FIREBASE_GCM_SENDER_ID` |

Exemplo em `apps/local.properties` (não versione credenciais):

```properties
sdk.dir=/caminho/para/Android/Sdk
cafey.firebase.apiKey=...
cafey.firebase.appId=...
cafey.firebase.projectId=...
cafey.firebase.gcmSenderId=...
```

Se algum valor estiver ausente, o build continua funcionando normalmente e o push Android fica
desativado (apenas log). A permissão `POST_NOTIFICATIONS` (Android 13+) é solicitada em momento
contextual (após parear/selecionar a base ou após o primeiro preparo), nunca na primeira tela.

### Desktop

```bash
./gradlew :desktopApp:run
```

### Web

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Abre um servidor de desenvolvimento local servindo o Compose compilado para Wasm.

#### Push (Web Push padrão, #365)

O app Web usa Web Push padrão (VAPID), **sem Firebase**:

- Service worker: `apps/webApp/src/wasmJsMain/resources/sw.js` (copiado para a raiz do bundle como `/sw.js`).
- Manifest PWA: `apps/webApp/src/wasmJsMain/resources/manifest.webmanifest`.
- `apps/webApp/src/wasmJsMain/resources/vercel.json` (copiado para a raiz do bundle de produção) define cabeçalhos para `/sw.js` (`Cache-Control: no-cache, no-store, must-revalidate` e `Service-Worker-Allowed: /`) para garantir atualização correta e escopo na raiz.
- A ativação é feita por gesto do usuário no botão **"Ativar notificações"** na tela **Conta** (permissão solicitada apenas no clique). A chave pública VAPID é obtida de `GET /notificacoes/vapid-public-key`; se o servidor responder `503`, o recurso é considerado indisponível e o botão fica oculto.
- Estados exibidos: ativadas, negadas (com instrução para reativar nas configurações do navegador) ou não suportado.
- **Limitação Safari/iOS:** no Safari (iPhone/iPad), notificações Web Push só funcionam quando o app é instalado como PWA (Adicionar à Tela de Início). Em abas comuns do Safari/iOS, o recurso pode aparecer como não suportado.

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
pelos dados do polling do `HomeViewModel`. A detecção é habilitada apenas no Desktop (opt-in no
`AppContainer`) para não gerar requisições extras em outras plataformas.

Limitações atuais:
- **Só com o app aberto:** não há push do sistema operacional para o app JVM; se o app for encerrado,
  nenhuma notificação é entregue.
- **Eventos de preparo por `eventoId`:** além das transições de estado/online observadas em todos os
  dispositivos carregados, os eventos recentes de preparo (`CONCLUIDO`/`ERRO`) são consultados no
  polling apenas para o dispositivo selecionado (1 requisição extra por ciclo), para evitar
  multiplicar chamadas quando o usuário possui vários dispositivos. Para o dispositivo selecionado,
  pronto/falha são notificados apenas por eventos (evita duplicação quando o estado muda antes do
  evento chegar); para os demais, usa-se fallback por transição de estado. Preparo iniciado e
  cafeteira offline são detectados para todos os dispositivos presentes na lista.
- **Histórico:** na primeira leitura após abrir o app, trocar de conta ou trocar o dispositivo
  selecionado, o estado/eventos atuais são registrados sem notificação retroativa. Eventos são
  deduplicados por `eventoId`.
- **Bandeja indisponível:** se o sistema não suportar bandeja (ex.: alguns ambientes Linux/Wayland),
  o app não cria `Tray` e fechar a janela encerra o aplicativo. As notificações usam fallback via
  `notify-send` no Linux quando disponível (ou quando o envio pela bandeja falhar).

Por padrão, quando a bandeja está disponível, fechar a janela mantém o app ativo para continuar
notificando; use "Sair" no menu da bandeja para encerrar. Clicar no ícone/menu da bandeja traz a
janela para a frente e mantém/seleciona o dispositivo associado à última notificação.

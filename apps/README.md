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

Todos os comandos a seguir rodam a partir de `apps/`.

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

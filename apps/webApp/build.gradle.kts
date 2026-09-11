import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// URL do backend usada pelo app Web (issue #157, equivalente Web da #147/Android) — nunca
// hardcoded no código-fonte. Mesma ordem de prioridade usada em androidApp/build.gradle.kts:
// 1. `-PbackendBaseUrl=https://...` na linha de comando;
// 2. variável de ambiente `BACKEND_BASE_URL` (a forma usual de configurar build no Vercel);
// 3. chave `backendBaseUrl` em `local.properties` (arquivo local, não versionado);
// 4. `http://localhost:8080`, o padrão de desenvolvimento local (continua funcionando sem config).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val backendBaseUrl: String = (project.findProperty("backendBaseUrl") as String?)
    ?: System.getenv("BACKEND_BASE_URL")
    ?: localProperties.getProperty("backendBaseUrl")
    ?: "http://localhost:8080"

// wasmJs roda no navegador — sem `BuildConfig` como o Android. Gera um script estático servido
// junto ao bundle, lido por Main.kt (via `window.__CAFEY_BACKEND_URL__`) antes de chamar
// `App(baseUrl = ...)`.
val generateWebBackendConfig = tasks.register("generateWebBackendConfig") {
    val outputDir = layout.buildDirectory.dir("generated/resources/wasmJsMain")
    outputs.dir(outputDir)
    doLast {
        outputDir.get().asFile.apply { mkdirs() }
            .resolve("cafey-config.js")
            .writeText("window.__CAFEY_BACKEND_URL__ = \"$backendBaseUrl\";\n")
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "cafeyWeb.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    open = false
                    // Porta padrão (8080) colide com o backend Spring Boot (APP-08/#67): o app
                    // Web precisa ser servido de outra origem para o preflight de CORS fazer
                    // sentido. Mantida em sincronia com CorsProperties.allowedOrigins (backend).
                    port = 8081
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            resources.srcDir(generateWebBackendConfig)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}

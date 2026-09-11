import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

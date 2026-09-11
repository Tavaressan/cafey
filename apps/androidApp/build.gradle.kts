import java.net.URI
import java.util.Properties

// Módulo Android puro — o código compartilhado vem de `:shared`, que é quem aplica o KMP.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// URL do backend usada pelo app Android (issue #147) — nunca hardcoded no código-fonte. Resolvida
// nesta ordem de prioridade, para conviver emulador/aparelho físico/produção sem editar arquivo:
// 1. `-PbackendBaseUrl=http://192.168.x.x:8080` na linha de comando;
// 2. variável de ambiente `BACKEND_BASE_URL` (útil em CI);
// 3. chave `backendBaseUrl` em `local.properties` (arquivo local, não versionado, já documentado
//    no README para o `sdk.dir`);
// 4. o alias do emulador Android (`10.0.2.2`), que continua funcionando sem nenhuma configuração.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val backendBaseUrl: String = (project.findProperty("backendBaseUrl") as String?)
    ?: System.getenv("BACKEND_BASE_URL")
    ?: localProperties.getProperty("backendBaseUrl")
    ?: "http://10.0.2.2:8080"
val backendHost: String = URI(backendBaseUrl).host ?: "10.0.2.2"

// Gera o `network_security_config` de debug incluindo o host escolhido acima, além dos padrões de
// desenvolvimento (emulador e loopback do `adb reverse`) — sem isso, um IP de rede local
// (ex.: `192.168.x.x`) seria barrado pela política de cleartext mesmo com a URL corrigida. A
// política de release (`src/main/res/xml/network_security_config.xml`) não é tocada: continua
// proibindo cleartext sem exceção.
val generateDebugNetworkSecurityConfig = tasks.register("generateDebugNetworkSecurityConfig") {
    val outputDir = layout.buildDirectory.dir("generated/res/networkSecurityConfig/debug")
    outputs.dir(outputDir)
    doLast {
        val hosts = linkedSetOf("10.0.2.2", "localhost", "127.0.0.1", backendHost)
        val xmlDir = outputDir.get().dir("xml").asFile.apply { mkdirs() }
        xmlDir.resolve("network_security_config.xml").writeText(
            buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<!-- Gerado por generateDebugNetworkSecurityConfig a partir de backendBaseUrl=$backendBaseUrl -->")
                appendLine("<network-security-config>")
                appendLine("    <base-config cleartextTrafficPermitted=\"false\" />")
                appendLine("    <domain-config cleartextTrafficPermitted=\"true\">")
                hosts.forEach { appendLine("        <domain includeSubdomains=\"false\">$it</domain>") }
                appendLine("    </domain-config>")
                appendLine("</network-security-config>")
            },
        )
    }
}

android {
    namespace = "br.com.tavaressan.cafey.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.com.tavaressan.cafey.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("debug") {
        res.srcDir(generateDebugNetworkSecurityConfig)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.preview)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}

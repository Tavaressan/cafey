package br.com.tavaressan.cafey.shared

import br.com.tavaressan.cafey.shared.network.platformDefaultBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regressão da issue #147: `AppContainer` precisa honrar uma `baseUrl` explícita (vinda de
 * configuração de build por plataforma), não só o padrão de desenvolvimento — senão não há como
 * apontar o app para o backend na rede local ao rodar num aparelho físico.
 */
class AppContainerTest {

    @Test
    fun explicitBaseUrl_overridesPlatformDefault() {
        val container = AppContainer(baseUrl = "http://192.168.1.50:8080")

        assertEquals("http://192.168.1.50:8080", container.baseUrl)
    }

    @Test
    fun noArgument_fallsBackToPlatformDefault() {
        val container = AppContainer()

        assertEquals(platformDefaultBaseUrl, container.baseUrl)
    }
}

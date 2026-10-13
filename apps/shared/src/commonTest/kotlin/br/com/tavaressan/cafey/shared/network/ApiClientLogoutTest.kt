package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

/** Issue #183 — `ApiClient.logout()` (`ApiClient.kt:93`) não tinha nenhum call site nem teste. */
class ApiClientLogoutTest {

    @Test
    fun logoutClearsStoredTokens() = runTest {
        val tokenStorage = FakeTokenStorage(StoredTokens(accessToken = "access", refreshToken = "refresh"))
        val apiClient = ApiClient(baseUrl = "https://api.cafey.test", tokenStorage = tokenStorage)

        apiClient.logout()

        assertNull(tokenStorage.load())
    }
}

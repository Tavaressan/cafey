package br.com.tavaressan.cafey.shared.ui.push

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.push.WebPushUiSupport
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlinx.coroutines.launch

@Composable
actual fun PushNotificationsSection(modifier: Modifier) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var supported by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf("unsupported") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var vapidDisponivel by remember { mutableStateOf(true) }

    fun refresh() {
        supported = WebPushUiSupport.isSupported()
        permission = WebPushUiSupport.permission()
    }

    LaunchedEffect(Unit) {
        refresh()
        // Pré-checa se o servidor tem VAPID (503 = ocultar botão).
        try {
            container.pushApi.obterChaveVapid()
            vapidDisponivel = true
        } catch (e: ApiError.Http) {
            if (e.status == 503) vapidDisponivel = false
        } catch (_: Throwable) {
            // Falha de rede: mantém botão visível e trata no clique.
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            "Notificações",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
        )

        val estadoTexto = when {
            !supported -> "Não suportado neste navegador."
            permission == "granted" -> "Ativadas."
            permission == "denied" -> "Negadas. Para reativar, permita notificações para este site nas configurações do navegador e recarregue a página."
            else -> "Desativadas."
        }
        Text(
            estadoTexto,
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            "No Safari/iOS, notificações push funcionam apenas com o app instalado como PWA.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (supported && permission != "denied" && permission != "granted" && vapidDisponivel) {
            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    message = null
                    scope.launch {
                        val result = WebPushUiSupport.requestPermissionAndSubscribe(container.pushApi)
                        loading = false
                        result.onSuccess {
                            message = "Notificações ativadas."
                        }.onFailure { e ->
                            val msg = e.message.orEmpty()
                            message = when {
                                msg.contains("vapid_indisponivel") -> "Notificações indisponíveis no servidor no momento."
                                msg.contains("permission:denied") -> "Permissão negada. Você pode reativar nas configurações do navegador."
                                msg.contains("permission:") -> "Permissão não concedida."
                                else -> "Não foi possível ativar notificações."
                            }
                            if (msg.contains("vapid_indisponivel")) vapidDisponivel = false
                        }
                        refresh()
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(if (loading) "Ativando..." else "Ativar notificações", color = CafeyTheme.colors.brandOn)
            }
        }

        message?.let {
            Text(
                it,
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.brand,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

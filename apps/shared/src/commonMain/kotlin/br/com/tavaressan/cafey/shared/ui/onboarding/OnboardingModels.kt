package br.com.tavaressan.cafey.shared.ui.onboarding

/** Etapas do tutorial de primeira utilização (issue #312). */
enum class OnboardingStep(val index: Int, val title: String) {
    CONHECENDO(0, "Conhecendo o Cafey"),
    CONFIGURANDO_BASE(1, "Configurando a base física"),
    BLUETOOTH_WIFI(2, "Bluetooth e Wi-Fi"),
    PRIMEIRO_CAFE(3, "Preparando o primeiro café");

    companion object {
        val TOTAL = entries.size
        fun fromIndex(index: Int): OnboardingStep = entries[index.coerceIn(0, entries.lastIndex)]
    }
}

/**
 * Estado do ambiente BLE/permissões na etapa 3, reportado pela camada de plataforma.
 * Mantido em modelo simples para testes de lógica em `commonTest`.
 */
data class OnboardingBleStatus(
    /** BLE/provisionamento suportado nesta plataforma (Android com adaptador). */
    val supported: Boolean = false,
    /** Permissões de Bluetooth/localização necessárias já concedidas. */
    val permissionsGranted: Boolean = false,
    /** Usuário negou e selecionou "não perguntar novamente" (ou equivalente): só resta abrir Configurações. */
    val permissionsPermanentlyDenied: Boolean = false,
    /** Adaptador Bluetooth ligado. `null` quando desconhecido/não suportado. */
    val bluetoothEnabled: Boolean? = null,
)

/** Resultado da conclusão do onboarding (persistido para não exibir automaticamente). */
enum class OnboardingExit {
    /** Concluiu o tutorial. */
    COMPLETED,
    /** Pulou o tutorial. */
    SKIPPED,
    /** Abriu explicitamente por "Ver tutorial" e fechou (não altera regra além do que já estava persistido). */
    DISMISSED,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.CONHECENDO,
    /** `true` quando aberto explicitamente ("Ver tutorial"): pular/fechar não é "primeira utilização". */
    val revisit: Boolean = false,
    val bleStatus: OnboardingBleStatus = OnboardingBleStatus(),
    val finished: Boolean = false,
    val exit: OnboardingExit? = null,
) {
    val canGoBack: Boolean get() = step.index > 0
    val canAdvance: Boolean get() = step.index < OnboardingStep.TOTAL - 1
    val isLastStep: Boolean get() = step == OnboardingStep.PRIMEIRO_CAFE
}

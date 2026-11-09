package br.com.tavaressan.cafey.shared.ui.onboarding

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Test
    fun startsAtFirstStep() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)

        assertEquals(OnboardingStep.CONHECENDO, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canGoBack)
        assertTrue(vm.uiState.value.canAdvance)
        assertFalse(vm.uiState.value.finished)
    }

    @Test
    fun nextAndBackNavigateBetweenSteps() = runTest {
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)

        vm.next()
        assertEquals(OnboardingStep.CONFIGURANDO_BASE, vm.uiState.value.step)
        assertTrue(vm.uiState.value.canGoBack)

        vm.next()
        assertEquals(OnboardingStep.BLUETOOTH_WIFI, vm.uiState.value.step)

        vm.back()
        assertEquals(OnboardingStep.CONFIGURANDO_BASE, vm.uiState.value.step)

        vm.back()
        assertEquals(OnboardingStep.CONHECENDO, vm.uiState.value.step)
        assertFalse(vm.uiState.value.canGoBack)
    }

    @Test
    fun nextAtLastStepDoesNotOverflow() = runTest {
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)
        vm.goToStep(OnboardingStep.PRIMEIRO_CAFE)

        vm.next()
        assertEquals(OnboardingStep.PRIMEIRO_CAFE, vm.uiState.value.step)
        assertTrue(vm.uiState.value.isLastStep)
    }

    @Test
    fun skipPersistsCompletionAndFinishes() = runTest {
        var completions = 0
        val vm = OnboardingViewModel(markCompleted = { completions++ }, coroutineScope = this)

        vm.skip()
        advanceUntilIdle()

        assertEquals(1, completions)
        assertTrue(vm.uiState.value.finished)
        assertEquals(OnboardingExit.SKIPPED, vm.uiState.value.exit)
    }

    @Test
    fun completePersistsCompletionAndFinishes() = runTest {
        var completions = 0
        val vm = OnboardingViewModel(markCompleted = { completions++ }, coroutineScope = this)
        vm.goToStep(OnboardingStep.PRIMEIRO_CAFE)

        vm.complete()
        advanceUntilIdle()

        assertEquals(1, completions)
        assertTrue(vm.uiState.value.finished)
        assertEquals(OnboardingExit.COMPLETED, vm.uiState.value.exit)
    }

    @Test
    fun dismissFromRevisitPersistsAndMarksDismissed() = runTest {
        var completions = 0
        val vm = OnboardingViewModel(markCompleted = { completions++ }, revisit = true, coroutineScope = this)

        vm.dismiss()
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(OnboardingExit.DISMISSED, vm.uiState.value.exit)
    }

    @Test
    fun finishIsIdempotent() = runTest {
        var completions = 0
        val vm = OnboardingViewModel(markCompleted = { completions++ }, coroutineScope = this)

        vm.complete()
        vm.skip()
        advanceUntilIdle()

        assertEquals(1, completions)
        assertEquals(OnboardingExit.COMPLETED, vm.uiState.value.exit)
    }

    @Test
    fun updateBleStatusReflectedInState() = runTest {
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)

        vm.updateBleStatus(
            OnboardingBleStatus(
                supported = true,
                permissionsGranted = true,
                bluetoothEnabled = false,
            ),
        )

        val ble = vm.uiState.value.bleStatus
        assertTrue(ble.supported)
        assertTrue(ble.permissionsGranted)
        assertEquals(false, ble.bluetoothEnabled)
        assertFalse(vm.canProvisionFromStep3())
    }

    @Test
    fun canProvisionOnlyWhenSupportedPermissionsGrantedAndBluetoothOn() = runTest {
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)

        vm.updateBleStatus(OnboardingBleStatus(supported = false, permissionsGranted = true, bluetoothEnabled = true))
        assertFalse(vm.canProvisionFromStep3())

        vm.updateBleStatus(OnboardingBleStatus(supported = true, permissionsGranted = false, bluetoothEnabled = true))
        assertFalse(vm.canProvisionFromStep3())

        vm.updateBleStatus(OnboardingBleStatus(supported = true, permissionsGranted = true, bluetoothEnabled = false))
        assertFalse(vm.canProvisionFromStep3())

        vm.updateBleStatus(OnboardingBleStatus(supported = true, permissionsGranted = true, bluetoothEnabled = true))
        assertTrue(vm.canProvisionFromStep3())
    }

    @Test
    fun permanentlyDeniedStateIsRepresented() = runTest {
        val vm = OnboardingViewModel(markCompleted = {}, coroutineScope = this)
        vm.updateBleStatus(
            OnboardingBleStatus(
                supported = true,
                permissionsGranted = false,
                permissionsPermanentlyDenied = true,
                bluetoothEnabled = true,
            ),
        )
        assertTrue(vm.uiState.value.bleStatus.permissionsPermanentlyDenied)
        assertFalse(vm.canProvisionFromStep3())
    }

    @Test
    fun stepMetadataCoversFourSteps() {
        assertEquals(4, OnboardingStep.TOTAL)
        assertEquals("Conhecendo o Cafey", OnboardingStep.CONHECENDO.title)
        assertEquals("Preparando o primeiro café", OnboardingStep.PRIMEIRO_CAFE.title)
        assertNull(OnboardingStep.entries.find { it.index < 0 || it.index > 3 })
    }
}

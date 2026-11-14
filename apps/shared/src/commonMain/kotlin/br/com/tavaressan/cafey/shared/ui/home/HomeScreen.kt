package br.com.tavaressan.cafey.shared.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.TimeZone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.domain.model.ProximoPreparo
import br.com.tavaressan.cafey.shared.ui.LocalNavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.NavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLightColors
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLogo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyStar
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * UC-06/07/08/09 — operação e estado em tempo real. Espelha
 * `docs/docs_interface/prototype/home.html` (layout mobile: `.shell`, `.stage-card`, `.card`,
 * `.duo`). Simplificações conscientes por escopo:
 * - Sem o anel decorativo pontilhado do mostrador (puramente estético, `.stage__ring`).
 */
@Composable
fun HomeScreen(onGoToDeviceRegister: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HomeViewModel>(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.deviceApi,
                    container.commandApi,
                    container.scheduleApi,
                    container.eventApi,
                    container.bleClient,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(22.dp),
    ) {
        if (state.loading) {
            DeviceHeader(
                device = state.device,
                devices = state.devices,
                isOffline = state.isOffline,
                onSelectDevice = viewModel::selectDevice,
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        state.errorMessage?.let { ErrorBanner(it) }

        // APP-13 — conta nova sem dispositivo: caminho explícito para cadastrar, em vez de
        // deixar a tela de operação sem sentido (sem dispositivo, não há o que operar).
        if (state.device == null) {
            EmptyDeviceState(onRegister = onGoToDeviceRegister)
            return@Column
        }

        DeviceHeader(
            device = state.device,
            devices = state.devices,
            isOffline = state.isOffline,
            onSelectDevice = viewModel::selectDevice,
        )

        if (state.isOffline) {
            OfflineBanner()
        }

        HomeContentLayout(
            sizeClass = LocalNavShellSizeClass.current,
            stage = {
                StageCard(
                    deviceState = state.deviceState,
                    commandInFlight = state.commandInFlight,
                    canSendCommand = state.canSendCommand,
                    isOffline = state.isOffline,
                    preparoIniciadoEm = state.device?.preparoIniciadoEm,
                    duracaoPreparoS = state.device?.duracaoPreparoS ?: 0,
                    onPrepare = viewModel::ligar,
                    onCancel = viewModel::cancelar,
                )
            },
            sidebar = {
                NextPreparoCard(state.proximoPreparo)
                StreakCard(state.sequenciaManhasDias)
            },
        )
    }
}

/**
 * Desktop (≥1024.dp, `.wide--home` de cafey.css): [stage] (mostrador+ação) numa coluna, [sidebar]
 * (próximo preparo+sequência de manhãs) noutra, lado a lado (grid 1.22fr/.78fr) — issue #188. Abaixo
 * disso (inclusive tablet), coluna única, igual ao comportamento anterior. `internal` para ser
 * exercitado por teste de composição em `desktopTest`.
 */
@Composable
internal fun HomeContentLayout(
    sizeClass: NavShellSizeClass,
    stage: @Composable () -> Unit,
    sidebar: @Composable () -> Unit,
) {
    if (sizeClass == NavShellSizeClass.Expanded) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(modifier = Modifier.weight(1.22f)) { stage() }
            Column(modifier = Modifier.weight(0.78f)) { sidebar() }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            stage()
            sidebar()
        }
    }
}

/** Rótulo curto do dia relativo a hoje: "hoje" (0), "amanhã" (1) ou "daqui a N dias" (2–6). */
private fun rotuloDiaRelativo(diasAteOProximo: Int): String = when (diasAteOProximo) {
    0 -> "hoje"
    1 -> "amanhã"
    else -> "daqui a $diasAteOProximo dias"
}

@Composable
private fun NextPreparoCard(proximoPreparo: ProximoPreparo?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CafeyStar(color = CafeyTheme.colors.blue, size = 15.dp)
            Text(
                "Próximo preparo",
                style = CafeyTheme.typography.caption,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        if (proximoPreparo == null) {
            Text(
                "Nenhum agendamento ativo",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Crie um horário na tela de agendamentos para a Caféy preparar sozinha.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                Text(proximoPreparo.agendamento.hora, style = CafeyTheme.typography.cardHero, color = CafeyTheme.colors.ink)
                Text(
                    rotuloDiaRelativo(proximoPreparo.diasAteOProximo),
                    style = CafeyTheme.typography.body,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
                )
            }
        }
    }
}

/** Issue #177 — "sequência de manhãs" (`SequenciaManhas` no backend). Sem endpoint próprio, é um
 * campo derivado de `/estatisticas` (`EstatisticasConsumoResponse.sequenciaManhasDias`). */
@Composable
private fun StreakCard(sequenciaManhasDias: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text("Sequência de manhãs", style = CafeyTheme.typography.caption, color = CafeyTheme.colors.muted)

        if (sequenciaManhasDias <= 0) {
            Text(
                "Nenhuma sequência ainda",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Prepare antes do meio-dia para começar uma sequência.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                "$sequenciaManhasDias dias",
                style = CafeyTheme.typography.cardHero,
                color = CafeyTheme.colors.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                repeat(7) { indice ->
                    CafeyStar(
                        color = if (indice < sequenciaManhasDias) CafeyTheme.colors.blue else CafeyTheme.colors.blueOff,
                        size = 10.dp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(
    device: DispositivoResponse?,
    devices: List<DispositivoResponse> = emptyList(),
    isOffline: Boolean = false,
    onSelectDevice: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val name = device?.nome ?: "Caféy"
    val online = (device?.online == true) && !isOffline
    val hasMultiple = devices.size > 1

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        CafeyLogo(size = 34.dp)
        Box(modifier = Modifier.padding(start = 11.dp)) {
            Column(
                modifier = Modifier
                    .clip(CafeyTheme.shapes.small)
                    .clickable(enabled = hasMultiple) { expanded = true }
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
                    if (hasMultiple) {
                        Text(
                            text = " ▼",
                            style = CafeyTheme.typography.caption,
                            color = CafeyTheme.colors.muted,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (online) CafeyTheme.colors.blue else CafeyTheme.colors.dim),
                    )
                    Text(
                        text = if (online) "Online" else "Offline",
                        style = CafeyTheme.typography.caption,
                        color = CafeyTheme.colors.muted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            if (hasMultiple) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(CafeyTheme.colors.surface),
                ) {
                    devices.forEach { dev ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (dev.online) CafeyTheme.colors.blue else CafeyTheme.colors.dim),
                                    )
                                    Text(
                                        text = dev.nome,
                                        style = if (dev.id == device?.id) CafeyTheme.typography.cardTitle else CafeyTheme.typography.body,
                                        color = CafeyTheme.colors.ink,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelectDevice(dev.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** APP-13 — estado vazio: conta nova ainda sem dispositivo cadastrado. */
@Composable
private fun EmptyDeviceState(onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CafeyStar(color = CafeyTheme.colors.muted, size = 40.dp)
        Text(
            "Nenhum dispositivo cadastrado",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Cadastre sua cafeteira para começar a usar o Caféy.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onRegister,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text("Cadastrar dispositivo", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(CafeyTheme.colors.brandTint, CafeyTheme.shapes.medium)
            .padding(16.dp),
    ) {
        // O brandTint fica claro nos dois temas, então o texto usa o brandDeep do tema claro.
        Text(message, color = CafeyLightColors.brandDeep, style = CafeyTheme.typography.bodySmall)
    }
}

/**
 * Alerta visual exibido quando o dispositivo está desconectado da nuvem (via Last Will ou tolerância > 2 min).
 */
@Composable
internal fun OfflineBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(CafeyTheme.colors.sunken, CafeyTheme.shapes.medium)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CafeyTheme.colors.dim),
            )
            Text(
                text = "Cafeteira desconectada da nuvem",
                color = CafeyTheme.colors.ink,
                style = CafeyTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun StageCard(
    deviceState: DeviceState,
    commandInFlight: Boolean,
    canSendCommand: Boolean = !commandInFlight,
    isOffline: Boolean = false,
    preparoIniciadoEm: String? = null,
    duracaoPreparoS: Int = 0,
    onPrepare: () -> Unit,
    onCancel: () -> Unit,
) {
    val (stateLabel, starColor) = when (deviceState) {
        DeviceState.Idle -> "Pronta" to CafeyTheme.colors.blue
        DeviceState.Brewing -> "Preparando" to CafeyTheme.colors.brand
        DeviceState.Ready -> "Café pronto" to CafeyTheme.colors.blue
        DeviceState.Error -> "Erro" to CafeyTheme.colors.brandDeep
        is DeviceState.Unknown -> deviceState.raw.ifBlank { "Sem dados" } to CafeyTheme.colors.muted
    }

    var animatedNowMs by remember(deviceState, preparoIniciadoEm, duracaoPreparoS) {
        mutableStateOf(Clock.System.now().toEpochMilliseconds())
    }
    LaunchedEffect(deviceState, preparoIniciadoEm, duracaoPreparoS) {
        if (deviceState == DeviceState.Brewing && !preparoIniciadoEm.isNullOrBlank() && duracaoPreparoS > 0) {
            while (isActive) {
                animatedNowMs = Clock.System.now().toEpochMilliseconds()
                delay(50)
            }
        }
    }

    val brewingProgress = calcularProgressoPreparo(deviceState, preparoIniciadoEm, duracaoPreparoS, animatedNowMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrewingDial(
            stateLabel = stateLabel,
            starColor = starColor,
            deviceState = deviceState,
            brewingProgress = brewingProgress,
            duracaoPreparoS = duracaoPreparoS,
            estimado = isOffline,
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (deviceState == DeviceState.Brewing) {
                Button(
                    onClick = onCancel,
                    enabled = canSendCommand,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar preparo", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
                }
            } else {
                Button(
                    onClick = onPrepare,
                    enabled = canSendCommand,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preparar agora", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
                }
            }

            if (isOffline) {
                Text(
                    text = "Cafeteira desconectada. Comandos indisponíveis.",
                    style = CafeyTheme.typography.caption,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun BrewingDial(
    stateLabel: String,
    starColor: androidx.compose.ui.graphics.Color,
    deviceState: DeviceState,
    brewingProgress: BrewingProgress,
    duracaoPreparoS: Int = 0,
    estimado: Boolean = false,
) {
    val ringSize = 176.dp
    val strokeWidth = 6.dp
    val trackColor = CafeyTheme.colors.line
    val progressColor = when (deviceState) {
        DeviceState.Ready -> CafeyTheme.colors.blue
        else -> CafeyTheme.colors.brand
    }
    val indeterminateRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(brewingProgress) {
        if (brewingProgress is BrewingProgress.Indeterminate) {
            while (isActive) {
                indeterminateRotation.snapTo(0f)
                indeterminateRotation.animateTo(
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 1400,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        } else {
            indeterminateRotation.snapTo(0f)
        }
    }

    // Brilho suave do ultimo minuto (#376): so pulsa alpha, sem mexer no layout.
    val determinado = brewingProgress as? BrewingProgress.Determinate
    // A animacao infinita so existe no ultimo minuto; fora dele nada anima sem necessidade.
    val noUltimoMinuto = determinado != null && determinado.restanteS in 1 until LIMITE_MENOS_DE_UM_MINUTO_S
    val brilho = if (noUltimoMinuto) {
        val transicao = rememberInfiniteTransition(label = "brilho-ultimo-minuto")
        val alpha by transicao.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "brilho",
        )
        alpha
    } else {
        0f
    }
    var ultimoDeterminado by remember { mutableStateOf<BrewingProgress.Determinate?>(null) }
    if (determinado != null) ultimoDeterminado = determinado
    var mostrarHorario by remember { mutableStateOf(false) }
    val fuso = remember { TimeZone.currentSystemDefault() }

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            when (brewingProgress) {
                BrewingProgress.Inactive -> {
                    if (deviceState == DeviceState.Ready) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                }
                BrewingProgress.Indeterminate -> {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx),
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f + indeterminateRotation.value,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                is BrewingProgress.Determinate -> {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx),
                    )
                    if (brewingProgress.fraction > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * brewingProgress.fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                        // Ultimo minuto: halo largo e translucido so sobre o trecho final.
                        if (duracaoPreparoS > 0 && brewingProgress.restanteS in 1 until LIMITE_MENOS_DE_UM_MINUTO_S) {
                            val inicioHalo = 360f * (1f - LIMITE_MENOS_DE_UM_MINUTO_S.toFloat() / duracaoPreparoS)
                                .coerceAtLeast(0f)
                            val sweepHalo = (360f * brewingProgress.fraction - inicioHalo).coerceAtLeast(0f)
                            if (sweepHalo > 0f) {
                                drawArc(
                                    color = progressColor.copy(alpha = 0.28f * brilho),
                                    startAngle = -90f + inicioHalo,
                                    sweepAngle = sweepHalo,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokePx * 2.4f, cap = StrokeCap.Round),
                                )
                            }
                        }
                        // Marcador na ponta do arco: da movimento sem acrescentar texto.
                        if (brewingProgress.fraction < 1f) {
                            val ang = (-90f + 360f * brewingProgress.fraction) * (kotlin.math.PI.toFloat() / 180f)
                            val raio = diameter / 2f
                            val centro = Offset(size.width / 2f + raio * kotlin.math.cos(ang), size.height / 2f + raio * kotlin.math.sin(ang))
                            drawCircle(color = progressColor, radius = strokePx * 0.95f, center = centro)
                            drawCircle(color = trackColor.copy(alpha = 1f), radius = strokePx * 0.35f, center = centro)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(176.dp)
                .padding(strokeWidth)
                .clip(CircleShape)
                .background(CafeyTheme.colors.surface)
                .border(1.dp, CafeyTheme.colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = determinado != null, label = "centro-mostrador") { comTempo ->
                val d = ultimoDeterminado
                if (comTempo && d != null) {
                    val tempo = textoTempoRestante(d.restanteS, estimado)
                    val grande = d.restanteS >= LIMITE_MENOS_DE_UM_MINUTO_S
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "Alternar entre tempo restante e horário previsto",
                                role = Role.Button,
                            ) { mostrarHorario = !mostrarHorario }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Preparando, ${descricaoRestanteAcessivel(d.restanteS)}"
                                liveRegion = LiveRegionMode.Polite
                                role = Role.Button
                            },
                    ) {
                        CafeyStar(color = starColor, size = 24.dp)
                        Text(
                            text = if (mostrarHorario) horaPrevista(d.terminaEmMs, fuso) else tempo,
                            style = (if (grande || mostrarHorario) CafeyTheme.typography.cardHero else CafeyTheme.typography.cardTitle)
                                .copy(fontFeatureSettings = "tnum"),
                            color = CafeyTheme.colors.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = if (mostrarHorario) "pronto às" else stateLabel,
                            style = CafeyTheme.typography.bodySmall,
                            color = CafeyTheme.colors.muted,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CafeyStar(color = starColor, size = 44.dp)
                        Text(
                            stateLabel,
                            style = CafeyTheme.typography.cardTitle,
                            color = CafeyTheme.colors.ink,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

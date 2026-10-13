package br.com.tavaressan.cafey.shared.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.AgendamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.DIAS_SEMANA_LABELS
import br.com.tavaressan.cafey.shared.domain.model.DURACOES_PREPARO_S
import br.com.tavaressan.cafey.shared.domain.model.diaAtivo
import br.com.tavaressan.cafey.shared.ui.LocalNavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.NavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-10/11/12/13 — CRUD de agendamentos (APP-05). Espelha
 * `docs/docs_interface/prototype/schedule.html` (lista de cartões + formulário), simplificado por
 * escopo:
 * - O seletor "Desliga sozinha após" (4/6/8/10 min, `.seg`) grava `duracaoPreparoS` por agendamento
 *   (issue #189); o cartão da lista ainda não exibe a duração ("desliga sozinha após 8 min").
 * - O seletor de hora é um campo de texto validado (HH:mm) em vez do "wheel" animado do protótipo.
 */
@Composable
fun ScheduleScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<ScheduleViewModel>(
        factory = viewModelFactory { initializer { ScheduleViewModel(container.deviceApi, container.scheduleApi) } },
    )
    val state by viewModel.uiState.collectAsState()
    val editing = state.editing
    // Desktop (≥1024.dp, `.wide--late` de cafey.css): lista e editor lado a lado (grid 1fr/1fr) em
    // vez do editor substituir a tela toda — issue #188. Abaixo disso, comportamento anterior.
    val isExpanded = LocalNavShellSizeClass.current == NavShellSizeClass.Expanded

    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        if (editing != null && !isExpanded) {
            ScheduleForm(
                form = editing,
                saving = state.saving,
                onHoraChange = viewModel::onHoraChange,
                onDiaToggle = viewModel::onDiaToggle,
                onDuracaoChange = viewModel::onDuracaoChange,
                onSave = viewModel::submit,
                onCancel = viewModel::cancelEdit,
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Agendamentos", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::startCreate, shape = CafeyTheme.shapes.small) {
                Text("Novo")
            }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        ScheduleContentLayout(
            isExpanded = isExpanded,
            list = { ScheduleList(agendamentos = state.agendamentos, viewModel = viewModel, topPadding = if (isExpanded) 0.dp else 16.dp) },
            editor = {
                if (editing != null) {
                    ScheduleForm(
                        form = editing,
                        saving = state.saving,
                        onHoraChange = viewModel::onHoraChange,
                        onDiaToggle = viewModel::onDiaToggle,
                        onDuracaoChange = viewModel::onDuracaoChange,
                        onSave = viewModel::submit,
                        onCancel = viewModel::cancelEdit,
                    )
                } else {
                    Text(
                        "Selecione um agendamento para editar, ou crie um novo.",
                        style = CafeyTheme.typography.body,
                        color = CafeyTheme.colors.muted,
                    )
                }
            },
        )
    }
}

/**
 * Desktop (≥1024.dp, `.wide--late` de cafey.css): [list] e [editor] lado a lado (grid 1fr/1fr), em
 * vez do editor substituir a lista na tela toda — issue #188. Abaixo disso, só [list] (o caller
 * mostra o editor em tela cheia separadamente nesse caso). `internal` para ser exercitado por teste
 * de composição em `desktopTest`.
 */
@Composable
internal fun ScheduleContentLayout(
    isExpanded: Boolean,
    list: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    if (isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) { list() }
            Box(modifier = Modifier.weight(1f)) { editor() }
        }
    } else {
        list()
    }
}

@Composable
private fun ScheduleList(agendamentos: List<AgendamentoResponse>, viewModel: ScheduleViewModel, topPadding: Dp) {
    if (agendamentos.isEmpty()) {
        Text(
            "Nenhum agendamento ainda.",
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 24.dp),
        )
    } else {
        LazyColumn(
            modifier = Modifier.padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(agendamentos, key = { it.id }) { agendamento ->
                ScheduleCard(
                    agendamento = agendamento,
                    onToggleAtivo = { viewModel.toggleAtivo(agendamento) },
                    onEdit = { viewModel.startEdit(agendamento) },
                    onDelete = { viewModel.excluir(agendamento) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    agendamento: AgendamentoResponse,
    onToggleAtivo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Café pronto às ${agendamento.hora}",
                style = CafeyTheme.typography.cardTitle,
                color = if (agendamento.ativo) CafeyTheme.colors.ink else CafeyTheme.colors.dim,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = agendamento.ativo,
                onCheckedChange = { onToggleAtivo() },
                colors = SwitchDefaults.colors(checkedTrackColor = CafeyTheme.colors.brand),
            )
        }
        DaysRow(diasSemana = agendamento.diasSemana, dimmed = !agendamento.ativo)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onEdit) { Text("Editar", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.blueDeep) }
            IconButton(onClick = onDelete) { Text("Excluir", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.brandDeep) }
        }
    }
}

@Composable
private fun DaysRow(diasSemana: Short, dimmed: Boolean) {
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DIAS_SEMANA_LABELS.forEachIndexed { indice, label ->
            val ativo = diasSemana.diaAtivo(indice)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (ativo && !dimmed) CafeyTheme.colors.blueTint else CafeyTheme.colors.sunken),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = CafeyTheme.typography.caption,
                    color = if (ativo && !dimmed) CafeyTheme.colors.blueInk else CafeyTheme.colors.dim,
                )
            }
        }
    }
}

@Composable
private fun ScheduleForm(
    form: ScheduleFormState,
    saving: Boolean,
    onHoraChange: (String) -> Unit,
    onDiaToggle: (Int) -> Unit,
    onDuracaoChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Text(
            if (form.agendamentoId == null) "Novo agendamento" else "Editar agendamento",
            style = CafeyTheme.typography.screenTitle,
            color = CafeyTheme.colors.ink,
        )

        OutlinedTextField(
            value = form.hora,
            onValueChange = onHoraChange,
            label = { Text("Café pronto às (HH:mm)") },
            isError = form.errors.hora != null,
            supportingText = { fieldErrorMessage(form.errors.hora)?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        Text("Nestes dias", style = CafeyTheme.typography.body, color = CafeyTheme.colors.ink2, modifier = Modifier.padding(top = 20.dp))
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DIAS_SEMANA_LABELS.forEachIndexed { indice, label ->
                val ativo = form.diasAtivos[indice]
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (ativo) CafeyTheme.colors.brand else CafeyTheme.colors.sunken)
                        .border(1.dp, if (ativo) CafeyTheme.colors.brand else CafeyTheme.colors.line, CircleShape)
                        .clickable { onDiaToggle(indice) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = CafeyTheme.typography.caption,
                        color = if (ativo) CafeyTheme.colors.brandOn else CafeyTheme.colors.ink3,
                    )
                }
            }
        }
        fieldErrorMessage(form.errors.diasSemana)?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        Text("Desliga sozinha após", style = CafeyTheme.typography.body, color = CafeyTheme.colors.ink2, modifier = Modifier.padding(top = 20.dp))
        DurationSegmentedControl(
            selected = form.duracaoPreparoS,
            onSelect = onDuracaoChange,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Não há sensor de água na base, então é o temporizador que encerra o preparo.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.dim,
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSave,
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
                } else {
                    Text("Salvar agendamento")
                }
            }
            OutlinedButton(onClick = onCancel, shape = CafeyTheme.shapes.small) {
                Text("Cancelar")
            }
        }
    }
}

/**
 * `.seg` de cafey.css — controle segmentado de seleção única "Desliga sozinha após" (4/6/8/10 min).
 * Cada opção reporta os segundos que o backend aceita. [selected] `null` = nenhuma opção marcada
 * (agendamento antigo, sem duração própria). `internal` para ser exercitado em `desktopTest`.
 */
@Composable
internal fun DurationSegmentedControl(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val optionShape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CafeyTheme.colors.sunken, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        DURACOES_PREPARO_S.forEach { duracaoS ->
            val on = duracaoS == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(if (on) Modifier.shadow(2.dp, optionShape) else Modifier)
                    .background(if (on) CafeyTheme.colors.surface else Color.Transparent, optionShape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(duracaoS) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${duracaoS / 60} min",
                    style = CafeyTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (on) CafeyTheme.colors.ink else CafeyTheme.colors.muted,
                )
            }
        }
    }
}

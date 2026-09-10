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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.AgendamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.DIAS_SEMANA_LABELS
import br.com.tavaressan.cafey.shared.domain.model.diaAtivo
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-10/11/12/13 — CRUD de agendamentos (APP-05). Espelha
 * `docs/docs_interface/prototype/schedule.html` (lista de cartões + formulário), simplificado por
 * escopo:
 * - O seletor "Desliga sozinha após" (4/6/8/10 min) do protótipo não tem campo correspondente em
 *   `CriarAgendamentoRequest`/`AtualizarAgendamentoRequest` (a duração de preparo é do dispositivo
 *   inteiro, não por agendamento) — omitido em vez de inventado.
 * - O seletor de hora é um campo de texto validado (HH:mm) em vez do "wheel" animado do protótipo.
 */
@Composable
fun ScheduleScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<ScheduleViewModel>(
        factory = viewModelFactory { initializer { ScheduleViewModel(container.deviceApi, container.scheduleApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        val editing = state.editing
        if (editing != null) {
            ScheduleForm(
                form = editing,
                saving = state.saving,
                onHoraChange = viewModel::onHoraChange,
                onDiaToggle = viewModel::onDiaToggle,
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

        if (state.agendamentos.isEmpty()) {
            Text(
                "Nenhum agendamento ainda.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.agendamentos, key = { it.id }) { agendamento ->
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

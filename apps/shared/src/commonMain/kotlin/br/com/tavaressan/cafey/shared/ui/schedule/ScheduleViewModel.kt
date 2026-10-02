package br.com.tavaressan.cafey.shared.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.AgendamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.AtualizarAgendamentoRequest
import br.com.tavaressan.cafey.shared.domain.model.CriarAgendamentoRequest
import br.com.tavaressan.cafey.shared.domain.model.DURACAO_PREPARO_PADRAO_S
import br.com.tavaressan.cafey.shared.domain.model.diaAtivo
import br.com.tavaressan.cafey.shared.domain.model.diasSemanaMask
import br.com.tavaressan.cafey.shared.domain.validation.ScheduleFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.ScheduleApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado do formulário de criação/edição — `null` em [ScheduleUiState.editing] = modo lista. */
data class ScheduleFormState(
    val agendamentoId: String? = null,
    val hora: String = "07:00",
    // Índice 0 = domingo … 6 = sábado. Seg-sex ligado por padrão, o caso mais comum (UC-10).
    val diasAtivos: List<Boolean> = List(7) { it in 1..5 },
    // null = agendamento sem duração própria (usa a do dispositivo); só nasce de uma edição.
    val duracaoPreparoS: Int? = DURACAO_PREPARO_PADRAO_S,
    val errors: ScheduleFormErrors = ScheduleFormErrors(),
) {
    companion object {
        fun forEdit(agendamento: AgendamentoResponse) = ScheduleFormState(
            agendamentoId = agendamento.id,
            hora = agendamento.hora,
            diasAtivos = (0..6).map { agendamento.diasSemana.diaAtivo(it) },
            // Não pré-seleciona o padrão: salvar só a hora de um agendamento antigo não deve
            // trocar a duração dele (a do dispositivo) por 8 min.
            duracaoPreparoS = agendamento.duracaoPreparoS,
        )
    }
}

data class ScheduleUiState(
    val loading: Boolean = true,
    val deviceId: String? = null,
    val agendamentos: List<AgendamentoResponse> = emptyList(),
    val editing: ScheduleFormState? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
)

/** UC-10/11/12/13 — CRUD de agendamentos (APP-05). */
class ScheduleViewModel(
    private val deviceApi: DeviceApi,
    private val scheduleApi: ScheduleApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            // MVP de um único dispositivo, mesma simplificação de HomeViewModel (APP-04).
            val deviceId = deviceApi.listar().firstOrNull()?.id
            val agendamentos = deviceId?.let { scheduleApi.listar(it) }.orEmpty()
            _uiState.update { it.copy(loading = false, deviceId = deviceId, agendamentos = agendamentos, errorMessage = null) }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    fun refresh() = viewModelScope.launch { load() }

    fun startCreate() {
        _uiState.update { it.copy(editing = ScheduleFormState()) }
    }

    fun startEdit(agendamento: AgendamentoResponse) {
        _uiState.update { it.copy(editing = ScheduleFormState.forEdit(agendamento)) }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editing = null) }
    }

    fun onHoraChange(hora: String) {
        _uiState.update { state -> state.editing?.let { state.copy(editing = it.copy(hora = hora, errors = it.errors.copy(hora = null))) } ?: state }
    }

    fun onDiaToggle(indice: Int) {
        _uiState.update { state ->
            state.editing?.let { form ->
                val diasAtivos = form.diasAtivos.toMutableList().apply { this[indice] = !this[indice] }
                state.copy(editing = form.copy(diasAtivos = diasAtivos, errors = form.errors.copy(diasSemana = null)))
            } ?: state
        }
    }

    fun onDuracaoChange(duracaoPreparoS: Int) {
        _uiState.update { state -> state.editing?.let { state.copy(editing = it.copy(duracaoPreparoS = duracaoPreparoS)) } ?: state }
    }

    fun submit() {
        val state = _uiState.value
        val deviceId = state.deviceId ?: return
        val form = state.editing ?: return
        val diasSemana = diasSemanaMask(form.diasAtivos)
        val errors = ScheduleFormErrors.validate(form.hora, diasSemana)
        if (!errors.isValid) {
            _uiState.update { it.copy(editing = form.copy(errors = errors)) }
            return
        }

        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (form.agendamentoId == null) {
                    scheduleApi.criar(
                        deviceId,
                        CriarAgendamentoRequest(hora = form.hora, diasSemana = diasSemana, duracaoPreparoS = form.duracaoPreparoS),
                    )
                } else {
                    scheduleApi.atualizar(
                        deviceId,
                        form.agendamentoId,
                        AtualizarAgendamentoRequest(hora = form.hora, diasSemana = diasSemana, duracaoPreparoS = form.duracaoPreparoS),
                    )
                }
                _uiState.update { it.copy(saving = false, editing = null) }
                load()
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }

    fun excluir(agendamento: AgendamentoResponse) {
        val deviceId = _uiState.value.deviceId ?: return
        viewModelScope.launch {
            try {
                scheduleApi.excluir(deviceId, agendamento.id)
                load()
            } catch (e: ApiError) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** UC-13 — ativa/desativa sem abrir o formulário de edição. */
    fun toggleAtivo(agendamento: AgendamentoResponse) {
        val deviceId = _uiState.value.deviceId ?: return
        viewModelScope.launch {
            try {
                scheduleApi.atualizar(deviceId, agendamento.id, AtualizarAgendamentoRequest(ativo = !agendamento.ativo))
                load()
            } catch (e: ApiError) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}

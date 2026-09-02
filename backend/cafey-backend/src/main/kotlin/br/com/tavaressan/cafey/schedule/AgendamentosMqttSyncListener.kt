package br.com.cafey.schedule

import br.com.cafey.config.AwsIotProperties
import br.com.cafey.device.DispositivoRepository
import br.com.cafey.mqtt.AgendamentoItemPayload
import br.com.cafey.mqtt.AgendamentosPayload
import br.com.cafey.mqtt.DispositivoOnlineEvent
import br.com.cafey.mqtt.MqttClientService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class AgendamentosMqttSyncListener(
    private val dispositivoRepository: DispositivoRepository,
    private val agendamentoRepository: AgendamentoRepository,
    private val mqttClientService: MqttClientService
) {
    private val logger = LoggerFactory.getLogger(AgendamentosMqttSyncListener::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAgendamentosAlterados(event: AgendamentosAlteradosEvent) {
        sincronizarAgendamentos(event.dispositivoId)
    }

    @EventListener
    fun onDispositivoOnline(event: DispositivoOnlineEvent) {
        logger.info("Dispositivo {} retornou online. Republicando lista de agendamentos...", event.dispositivoId)
        sincronizarAgendamentos(event.dispositivoId)
    }

    fun sincronizarAgendamentos(dispositivoId: UUID) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null) ?: return
        val agendamentos = agendamentoRepository.findByDispositivoId(dispositivoId)

        val items = agendamentos.map {
            AgendamentoItemPayload(
                id = it.id.toString(),
                hora = it.hora.format(timeFormatter),
                diasSemana = it.diasSemana.toInt(),
                ativo = it.ativo
            )
        }

        val payload = AgendamentosPayload(
            versao = dispositivo.versaoAgendamentos,
            timezone = dispositivo.timezone,
            duracaoS = dispositivo.duracaoPreparoS,
            agendamentos = items
        )

        val topic = AwsIotProperties.topicAgendamentos(dispositivoId.toString())
        logger.info("Publicando lista de agendamentos no tópico [{}] com versão {}: {} agendamento(s)", topic, dispositivo.versaoAgendamentos, items.size)
        mqttClientService.publish(topic, payload, retain = true)
    }
}

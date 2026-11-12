# ADR 0001 — Mecanismo de notificações push por plataforma

- **Status:** aceito (12/11/2026)
- **Issues:** #359 (spike), épico #318; implementação em #360–#367
- **Decisores:** responsável pelo projeto

## Contexto

O Caféy não tinha notificações: o app faz polling de 5 s com a tela aberta. Os eventos que interessam ao
usuário já chegam ao backend pela ingestão MQTT (`MqttIngestionService`): início do preparo, café pronto,
falha e queda da base (Last Will). Os apps são Kotlin Multiplataforma em três alvos: Android, desktop
(Compose Desktop/JVM) e web (Kotlin/Wasm na Vercel). A #318 pedia notificar sem dependências
desnecessárias.

## Decisão

| Plataforma | Mecanismo | Por quê |
|---|---|---|
| Android | **Firebase Cloud Messaging (HTTP v1)** | Único jeito de entregar com o app fechado sem manter um serviço próprio em segundo plano (que o Android mata e que gasta bateria). Gratuito. O responsável aceitou a dependência do Firebase em 12/11/2026. |
| Web | **Web Push padrão (RFC 8030/8291/8292, VAPID)**, sem Firebase | Funciona em Chrome, Edge, Firefox e Safari (este só em PWA instalada, iOS 16.4+). Não exige conta em terceiros: o backend assina com o próprio par VAPID. |
| Desktop | **Notificação local** na bandeja (`Tray` do Compose Desktop), só com o app aberto | Não existe serviço de push do SO para apps JVM. Reaproveita o polling já existente; nenhuma dependência nova. |

Decisões complementares:

- **Convidados recebem** as notificações dos dispositivos compartilhados com eles (decisão do responsável,
  12/11/2026). Revogar o compartilhamento interrompe as notificações.
- **Backend sem SDKs de terceiros:** o FCM é chamado por HTTP com OAuth da conta de serviço (JWT RS256
  assinado com a JDK) e o Web Push é cifrado com a JDK (ECDH P-256, HKDF, AES-GCM). Menos superfície e
  nenhuma biblioteca abandonada no caminho crítico.
- **Entrega assíncrona e depois do commit:** a ingestão MQTT nunca espera um serviço de push.
- **Endpoint de Web Push restrito** a serviços de push conhecidos, porque ele vem do cliente (SSRF).

## Alternativas descartadas

- **UnifiedPush/ntfy no Android:** evita o Google, mas exige que o usuário instale um app distribuidor.
  Inviável para a demonstração e para usuários comuns.
- **Firebase também na web:** traria o SDK JS do Firebase para o app Wasm sem ganho sobre o Web Push padrão.
- **Serviço em primeiro plano no Android com conexão MQTT/WebSocket própria:** notificação fixa na barra,
  consumo de bateria e restrições do Android 14+ para serviços em primeiro plano.
- **SSE/WebSocket no backend para todos os apps:** resolve só com o app aberto; fica como evolução para
  o desktop e para a UI, não substitui o push.

## Consequências

- É preciso um projeto Firebase e uma conta de serviço (#367); sem ela o canal Android fica desligado e o
  resto do backend segue normal. O mesmo vale para o par VAPID.
- O app Android inicializa o Firebase com valores de build (sem `google-services.json` versionado).
- No desktop a notificação só existe com o app aberto (limitação documentada no README dos apps).
- Safari/iOS só recebe Web Push com a PWA instalada.
- Ações na notificação (#319) e preferências por usuário (#322) se apoiam neste mecanismo.

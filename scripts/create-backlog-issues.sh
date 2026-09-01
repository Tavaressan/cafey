#!/usr/bin/env bash
# Cria labels, milestones e issues do BACKLOG.md no repositório.
# Requer gh autenticado: gh auth login
#
#   ./scripts/create-backlog-issues.sh                  # cria tudo
#   DRY_RUN=1 ./scripts/create-backlog-issues.sh        # só imprime

set -euo pipefail

REPO="${REPO:-Tavaressan/cafey}"
DRY_RUN="${DRY_RUN:-0}"

label() {
  [[ "$DRY_RUN" == "1" ]] && { echo "label: $1"; return; }
  gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force
}

milestone() {
  [[ "$DRY_RUN" == "1" ]] && { echo "milestone: $1"; return; }
  # gh não tem subcomando para milestone; vai pela API. Ignora se já existir.
  gh api "repos/$REPO/milestones" -f title="$1" -f due_on="$2" -f description="$3" \
    >/dev/null 2>&1 || echo "milestone '$1' já existe, seguindo"
}

# $1=id  $2=título  $3=trilha  $4=fase  $5=prioridade ("" se nenhuma)  $6=depende  $7=origem
issue() {
  local labels="$3"
  [[ -n "$5" ]] && labels="$labels,$5"
  local body="**Depende de:** ${6:-nada}

**Origem:** $7

Detalhe e critério de aceite em \`BACKLOG.md\`, item $1."

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "issue: [$1] $2 | labels=$labels | Fase $4"
    return
  fi
  gh issue create --repo "$REPO" \
    --title "[$1] $2" --body "$body" \
    --label "$labels" --milestone "Fase $4"
}

label hardware  B60205 "Chapa, esquemático, montagem elétrica"
label firmware  D93F0B "ESP32, ESP-IDF, C++"
label backend   0E8A16 "Kotlin, Spring Boot, PostgreSQL"
label apps      1D76DB "Kotlin Multiplataforma"
label artigo    5319E7 "EnGeTec 2026"
label docs      C5DEF5 "Especificação e documentação acadêmica"
label critico   D73A4A "Não cortar sob nenhuma circunstância"
label corte-1   FBCA04 "Primeiro a cair se o cronograma apertar"
label corte-2   FBCA04 "Segundo a cair"
label corte-3   FBCA04 "Terceiro a cair"
label corte-4   FBCA04 "Quarto a cair"
label corte-5   FBCA04 "Último a cair"

milestone "Fase 1" "2026-09-04T23:59:59Z" "Fundação (24/08 a 04/09)"
milestone "Fase 2" "2026-09-25T23:59:59Z" "Núcleo (07/09 a 25/09)"
milestone "Fase 3" "2026-10-09T23:59:59Z" "Funcionalidade completa (28/09 a 09/10)"
milestone "Fase 4" "2026-10-16T23:59:59Z" "Fechamento (12/10 a 16/10)"

# ---------------------------------------------------------------- hardware
issue HW-01 "Comprar componentes do módulo" hardware 1 critico "" "STATUS.md, Compras pendentes"
issue HW-02 "Conferir serigrafia do DevKit contra o símbolo ESP32_30pin" hardware 1 "" "HW-01" "STATUS.md, Pendências de bancada"
issue HW-03 "Conferir ordem VCC/GND/IN e NC/COM/NO do módulo de relé" hardware 1 "" "HW-01" "STATUS.md, Pendências de bancada"
issue HW-04 "Identificar as pernas físicas do LED RGB com multímetro" hardware 1 "" "HW-01" "STATUS.md, Pendências de bancada"
issue HW-05 "Protótipo do relé em bancada acionado pelo ESP32 (marco 04/09)" hardware 1 critico "HW-02, HW-03" "spec §10, marcos de aceitação"
issue HW-06 "Medir o tempo real de extração e fixar duracao_preparo_s" hardware 2 "" "HW-01" "spec-backend §11, pendência 7"
issue HW-07 "Atualizar o Valor de F1 se o fusível comprado for 8 A" hardware 2 "" "HW-01" "STATUS.md, Compras pendentes"
issue HW-08 "Atribuir footprints a todos os componentes no KiCad" hardware 2 "" "HW-01" "STATUS.md, Esquemático — o que falta"
issue HW-09 "Registrar as exclusões de ERC documentadas" hardware 2 "" "" "STATUS.md, Esquemático — o que falta"
issue HW-10 "Levantar dimensões físicas dos módulos" hardware 2 "" "HW-01" "README.md, Estrutura física"
issue HW-11 "Planificação da chapa" hardware 2 "" "HW-10" "README.md, Estrutura física"
issue HW-12 "Definir separação física entre rede e baixa tensão" hardware 2 critico "HW-11" "spec §4.5, isolamento"
issue HW-13 "Resolver posicionamento da antena" hardware 2 "" "HW-11" "spec §13, pendência 3"
issue HW-14 "Prever fixação do terminal olhal de aterramento" hardware 2 critico "HW-11" "spec §4.5, aterramento"
issue HW-15 "Solicitar dobra da chapa à empresa" hardware 2 "" "HW-11, HW-12, HW-13, HW-14" "README.md, Estrutura física"
issue HW-16 "Montagem: fiação em bornes com tampa e fixação dos módulos" hardware 3 critico "HW-15" "README.md, Estrutura física"
issue HW-17 "Validação elétrica por profissional habilitado (marco 02/10)" hardware 3 critico "HW-16" "spec §4.3, validação obrigatória"
issue HW-18 "Corrigir o símbolo ESP32_30pin e remover o PWR_FLAG" hardware 4 "" "" "STATUS.md, Pinagem definida"

# ---------------------------------------------------------------- firmware
issue FW-01 "Esqueleto ESP-IDF com CMake e menuconfig" firmware 1 "" "" "spec §5.1"
issue FW-02 "Estrutura de Active Objects sobre FreeRTOS" firmware 2 "" "FW-01" "spec §5.2"
issue FW-03 "Driver do relé com gatilho alto e pull-down" firmware 1 critico "FW-01, HW-03" "STATUS.md, Pinagem definida"
issue FW-04 "Botão físico: liga, desliga e cancela preparo" firmware 2 critico "FW-02" "spec §5.6"
issue FW-05 "LED RGB conforme a tabela de estados" firmware 2 "" "FW-02, HW-04" "spec §5.3"
issue FW-06 "Persistência em NVS de agendamentos e fila de eventos" firmware 2 critico "FW-02" "spec §5.4"
issue FW-07 "Temporizador local de preparo com resultado CONCLUIDO" firmware 2 critico "FW-03, HW-06" "spec-backend §1, fim do preparo"
issue FW-08 "Geração de evento_id no formato bootId:seq" firmware 2 "" "FW-06" "spec-backend §3, eventos_preparo"
issue FW-09 "Wi-Fi com reconexão e provisionamento de credenciais" firmware 2 "" "FW-02" "spec §8.1, UC-04"
issue FW-10 "MQTT sobre TLS com X.509 no AWS IoT Core (marco 25/09)" firmware 2 "" "FW-09" "spec §6.1"
issue FW-11 "Assinar tópicos descendentes pelo nome exato" firmware 2 critico "FW-10" "spec-backend §6.3, retain e wildcard"
issue FW-12 "Publicar estado com retain, eventos e saude com Last Will" firmware 2 "" "FW-10" "spec §6.2"
issue FW-13 "Ignorar lista de agendamentos com versao menor ou igual" firmware 2 critico "FW-06, FW-11" "spec-backend §6.2"
issue FW-14 "NTP e agendador local disparando offline (marco 09/10)" firmware 3 critico "FW-06" "spec §5.5"
issue FW-15 "Definir comportamento quando o preparo antecede a sincronização NTP" firmware 3 "" "FW-08, FW-14" "spec-backend §11, pendência 2"
issue FW-16 "Fila de eventos pendentes drenada ao reconectar" firmware 3 critico "FW-06, FW-10" "spec §5.5"
issue FW-17 "Serviço BLE de comando, estado e agendamentos" firmware 4 corte-1 "FW-02" "spec §6.4"
issue FW-18 "Proxy BLE: expor e limpar a fila de eventos pendentes" firmware 4 corte-1 "FW-16, FW-17" "spec §6.5"

# ---------------------------------------------------------------- backend
issue BE-01 "Esqueleto do Initializr e Postgres via Docker Compose" backend 1 "" "" "spec-backend §1.2"
issue BE-02 "Migrações V1 e V2; domínio de usuário com BCrypt" backend 1 "" "BE-01" "spec-backend §8"
issue BE-03 "Emissão de JWT RS256 e SecurityFilterChain" backend 1 "" "BE-02" "spec-backend §4.1"
issue BE-04 "Endpoints de registro, login e refresh (marco 04/09)" backend 1 critico "BE-03" "spec-backend §5.1"
issue BE-05 "Rotação de refresh com detecção de reuso por familia_id" backend 1 "" "BE-04" "spec-backend §4.3"
issue BE-06 "Tratamento de erros em Problem Details (RFC 9457)" backend 1 "" "BE-01" "spec-backend §5"
issue BE-07 "Verificar que o Flyway executa de fato após a primeira subida" backend 1 critico "BE-02" "spec-backend §1.1"
issue BE-08 "Criar thing e certificado do backend no AWS IoT Core" backend 2 "" "" "spec-backend §11, pendência 4"
issue BE-09 "Fixar a versão do aws-iot-device-sdk" backend 2 "" "" "spec-backend §11, pendência 5"
issue BE-10 "Migração V3, CRUD de dispositivos e vínculo" backend 2 "" "BE-04" "spec-backend §5.1"
issue BE-11 "Cliente MQTT persistente com backoff (marco 25/09)" backend 2 "" "BE-08, BE-09" "spec-backend §6.1"
issue BE-12 "Ingestão de estado e saude" backend 2 "" "BE-11" "spec-backend §6.2"
issue BE-13 "Migração V4 e republicação da lista completa em AFTER_COMMIT" backend 2 critico "BE-10, BE-11" "spec-backend §5.3"
issue BE-14 "Republicar agendamentos quando o dispositivo voltar online" backend 2 critico "BE-12, BE-13" "spec-backend §5.3"
issue BE-15 "Migração V5 e ingestão de eventos com ON CONFLICT DO NOTHING" backend 3 "" "BE-11" "spec-backend §3"
issue BE-16 "Histórico de preparos (UC-14)" backend 3 "" "BE-15" "spec §8.4"
issue BE-17 "Contador de descalcificação, alerta e baixa (UC-16, UC-17)" backend 3 "" "BE-15" "spec §7.3"
issue BE-18 "Estatísticas de consumo por origem (UC-15)" backend 3 corte-2 "BE-16" "spec §8.4"
issue BE-19 "Compartilhamento com convidado (UC-05)" backend 3 corte-3 "BE-10" "spec §8.1"
issue BE-20 "Rota do proxy BLE com validação de inicio e origem" backend 4 corte-1 "BE-15" "spec-backend §4.4"
issue BE-21 "Recuperação de senha (UC-03) e migração V6" backend 4 corte-5 "BE-04" "spec §8.1"

# ---------------------------------------------------------------- apps
issue APP-01 "Projeto KMP com shared e targets mobile, web e desktop" apps 2 "" "" "spec §2.3"
issue APP-02 "Camada shared: modelos, cliente HTTP e token" apps 2 "" "APP-01, BE-04" "spec §2.3"
issue APP-03 "Mobile: cadastro e login" apps 2 "" "APP-02" "spec §8.1"
issue APP-04 "Mobile: operação e estado em tempo real (marco 25/09)" apps 2 critico "APP-03, BE-11" "spec §8.2"
issue APP-05 "Mobile: CRUD de agendamentos" apps 3 critico "APP-04, BE-13" "spec §8.3"
issue APP-06 "Mobile: histórico de preparos em lista" apps 3 "" "APP-04, BE-16" "spec §8.4"
issue APP-07 "Mobile: alerta de descalcificação e baixa" apps 3 "" "APP-04, BE-17" "spec §8.4"
issue APP-08 "Web no nível essencial" apps 3 "" "APP-05, APP-06" "spec §2.3"
issue APP-09 "Desktop no nível essencial" apps 3 corte-4 "APP-08" "spec §2.3"
issue APP-10 "Gráficos de estatística" apps 4 corte-2 "APP-06, BE-18" "spec §2.4"
issue APP-11 "Cliente BLE: fallback de comando e proxy de eventos" apps 4 corte-1 "APP-04, FW-18" "spec §6.4, §6.5"

# ---------------------------------------------------------------- artigo
issue ART-01 "Cadastro no site do EnGeTec" artigo 1 critico "" "Orientação 3 do enunciado"
issue ART-02 "Confirmar no Teams a data de entrega" artigo 1 critico "" "README.md, trilha do artigo"
issue ART-03 "Resolver com o professor as dúvidas do template" artigo 1 critico "" "Template EnGeTec 2026"
issue ART-04 "Preâmbulo em português, inglês e espanhol" artigo 2 critico "ART-03" "Estrutura prevista do artigo"
issue ART-05 "Seção 1 — Introdução" artigo 2 critico "ART-03" "Estrutura prevista do artigo"
issue ART-06 "Seção 2 — Fundamentação Teórica" artigo 2 critico "ART-05" "Estrutura prevista do artigo"
issue ART-07 "Seção 3 — Materiais e Métodos" artigo 2 critico "ART-06" "Estrutura prevista do artigo"
issue ART-08 "Seção 4 — Resultados e Discussões" artigo 2 critico "HW-05, ART-07" "Estrutura prevista do artigo"
issue ART-09 "Seção 5 — Considerações Finais" artigo 2 critico "ART-08" "Estrutura prevista do artigo"
issue ART-10 "Referências em norma única" artigo 2 critico "ART-09" "Template, seção Método"
issue ART-11 "Declarações de responsabilidade e de uso de IA" artigo 2 critico "ART-10" "Template, seção final"
issue ART-12 "Revisão de formatação contra o template" artigo 2 critico "ART-11" "Orientação 2 do enunciado"
issue ART-13 "Submissão (marco 20/09)" artigo 2 critico "ART-12" "README.md, marcos"
issue ART-14 "Apresentação no evento" artigo 4 "" "ART-13" "Orientação 5 do enunciado"

# ---------------------------------------------------------------- docs
issue DOC-01 "Corrigir a afirmação de isolamento galvânico em §4.5 e §9" docs 2 critico "" "STATUS.md, decisões fechadas"
issue DOC-02 "Atualizar §4.4: alimentação resolvida com HLK-PM01" docs 2 "" "" "STATUS.md, decisões fechadas"
issue DOC-03 "Atualizar o diagrama draw.io" docs 2 "" "" "spec §13, pendência 2"
issue DOC-04 "Aplicar em §7.2 as divergências do backend" docs 2 "" "" "spec-backend §9"
issue DOC-05 "Confirmar as premissas P1 a P9" docs 1 critico "" "spec-backend §2"
issue DOC-06 "Documentação acadêmica e banca (marco 16/10)" docs 4 critico "" "spec §10, Fase 4"

echo "Concluído."

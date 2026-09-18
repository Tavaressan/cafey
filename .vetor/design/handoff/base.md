# Design Contract — Base

## Objetivo da experiência

Dar visibilidade ao hardware físico — conectividade, firmware e o significado das cores do LED — para o usuário entender e diagnosticar o aparelho sem precisar de um app técnico separado.

## Telas

Uma tela só, com tema escuro fixo (não segue o tema claro das outras 4 telas — 'a base, no fundo escuro', index.html).

## Hierarquia

1) Identidade do aparelho (nome, modelo, potência, estado online); 2) conectividade (Wi-Fi + nuvem) e firmware, lado a lado a partir do tablet; 3) legenda das cores do LED físico.

## Layout

Celular (<768): os três blocos (identidade, conectividade, firmware) empilhados em coluna única. Tablet+ (≥768): conectividade e firmware ficam lado a lado (.wide em grid 1fr/1fr); a legenda do LED permanece full-width abaixo.

## Componentes

card--flush (deflist de conectividade, sem padding interno extra); deflist (linhas rótulo/valor, ex.: Wi-Fi, Nuvem); signal (barras de força do sinal Wi-Fi); pill (selo 'Atualizado' do firmware); legend (legenda das cores do LED, com estrela colorida por linha); btn--ghost (buscar atualizações).

## Tokens

Tema escuro exclusivo desta tela (.theme-dark, redefine --ground/--surface/--ink etc. da mesma gramática de cor, invertida). A cor do pill de firmware atualizado (#22303F/#A8C4E4) não faz parte da paleta clara usada nas outras telas.

## Conteúdo

'Base da cozinha', 'O módulo debaixo da sua cafeteira.', 'Caféy Base', 'CFY-0A31 · Britânia CP30 · 800 W', 'Online', 'Wi-Fi', 'Cozinha', '−52 dBm', 'Nuvem', 'Conectada', 'Bluetooth reserva pronto', 'Firmware', '1.4.2', 'Atualizado', 'Instalado em 12 de agosto de 2026 · nunca atualiza durante um preparo.', 'Buscar atualizações', 'A luz da base', 'Azul fixo — pronta', 'Vermelho pulsando — preparando', 'Cinza fraco — offline, os agendamentos continuam disparando', 'O botão do pedestal prepara café sem o app.'.

## Interações

'Buscar atualizações' verifica manualmente se há firmware novo (a atualização automática é implícita: 'nunca atualiza durante um preparo' sugere que ela ocorre sozinha em outros momentos).

## Estados

### State: populated

Trigger:
Tela aberta com a base já pareada e identificada.

Expected behavior:
Mostra identidade, conectividade e firmware reais do aparelho.

Evidence:
Prototype

### State: success

Trigger:
Firmware já está na versão mais recente disponível.

Expected behavior:
Mostra o selo 'Atualizado' ao lado do número da versão; o botão 'Buscar atualizações' continua disponível para checagem manual.

Evidence:
Prototype

### State: offline

Trigger:
Base perde conexão com Wi-Fi ou nuvem.

Expected behavior:
O LED físico fica cinza fraco; os agendamentos já salvos continuam disparando normalmente porque vivem na memória da base, não na nuvem — comportamento descrito na legenda do LED, mas sem uma composição visual desta própria tela nesse estado (ver questão aberta).

Evidence:
Prototype

### State: permission denied

OPEN_QUESTION
Correção (2026-09-18): este estado foi marcado como "não aplicável" com base só no protótipo estático, que não modela perfis. A implementação real tem um modelo de papel por dispositivo (`PapelDispositivo`, confirmado em `care/CareViewModel.kt`) que já nega pelo menos uma ação (dar baixa em descalcificação, na tela Cuidados) a quem não é proprietário. Como esta tela ainda não foi implementada (ver seção "Estado de implementação"), não há como confirmar se a mesma regra se aplicaria a "Buscar atualizações" ou à visualização de conectividade/firmware.

Impact:
Ao implementar esta tela, checar a regra de papel do backend antes de assumir que qualquer usuário vinculado ao dispositivo vê ou age sobre os dados de hardware.

### State: loading

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

### State: empty

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

### State: error

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

### State: partial failure

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

### State: disabled

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

## Responsividade

Celular (<768): três blocos empilhados. Tablet+ (≥768): conectividade e firmware lado a lado; legenda do LED permanece full-width.

## Acessibilidade

A legenda de cores do LED (.legend) usa cor + texto adjacente ('Azul fixo — pronta'), o que é acessível a leitor de tela, mas ainda depende de percepção de cor para associar à luz física real do aparelho. O indicador de força de sinal (.signal) é puramente visual, sem valor textual equivalente ao lado (só o dBm em fonte mono, técnico).

## Restrições

O firmware nunca atualiza durante um preparo em andamento (regra explícita no texto). A base tem Bluetooth como conexão reserva além do Wi-Fi.

## Referências

- docs/docs_interface/prototype/base.html
- docs/docs_interface/prototype/assets/cafey.css
- docs/docs_interface/prototype/README.md
- docs/docs_interface/prototype/index.html

## Decisões

CONFIRMED
Esta é a única das 5 telas com tema escuro fixo — não responde a um toggle de tema do usuário, é uma decisão de identidade para a tela do hardware.
Source: base.html (<body class="theme-dark">, sem controle de alternância em nenhuma das 5 telas) + index.html ('a base, no fundo escuro')

CONFIRMED
As cores do LED físico (azul fixo, vermelho pulsando, cinza fraco) mapeiam 1:1 para os três estados narrativos do dispositivo: pronta, preparando, offline.
Source: base.html (.legend)

INFERRED
'Buscar atualizações' é uma ação manual disponível mesmo quando já 'Atualizado', sugerindo que existe checagem automática mas o usuário pode forçá-la.
Source: base.html (botão sempre presente ao lado do selo 'Atualizado')

## Estado de implementação (observado em 2026-09-18, confirmado contra `origin/main` sincronizado)

Esta tela **não foi implementada**. O comentário em `CafeyNavHost.kt` ainda diz que a aba "Base" "não tem issue nem tela correspondente ainda" — mas isso está desatualizado: **existe a issue #182** ("[APP] Tela 'Base' ausente (dispositivo + alternador de tema claro/escuro)", labels `enhancement,apps,backlog,ai-generated`), aberta por uma sessão de backlog anterior a esta. Vale atualizar o comentário no código para referenciá-la, e usar #182 (não abrir uma nova) se este contrato virar trabalho de implementação.

Nenhum campo deste contrato (Wi-Fi, nuvem, firmware, legenda do LED) tem código real para comparar — todas as questões abertas abaixo permanecem totalmente em aberto, sem nenhuma resolução parcial como aconteceu nas outras 4 telas. A navegação lateral responsiva (`CafeyNavHost.kt`/`NavShellSizeClass.kt`) já existe para as 4 abas implementadas, mas isso não antecipa nada do conteúdo específico desta tela.

## Questões abertas

OPEN_QUESTION
A tela mostra fixamente 'Online' no cabeçalho e na linha de Wi-Fi — não existe uma composição desta tela para quando a base está realmente offline, mesmo a legenda do LED descrevendo esse estado.
Impact: Sem essa definição, a implementação pode assumir que os campos de conectividade sempre têm valor válido, quebrando a tela quando a base cai.

OPEN_QUESTION
Não há tela para firmware desatualizado com atualização disponível (o selo mostrado é sempre 'Atualizado') nem para falha na atualização.
Impact: O fluxo de 'Buscar atualizações' → encontrar novidade → aplicar não está desenhado.

OPEN_QUESTION
Não há tela para o processo de pareamento inicial da base (esta tela assume uma base já pareada e nomeada 'Base da cozinha').
Impact: O onboarding de primeira base pode precisar de uma tela própria, fora do escopo deste protótipo.

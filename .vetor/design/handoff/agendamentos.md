# Design Contract — Agendamentos

## Objetivo da experiência

Gerenciar horários automáticos de preparo, expressos como 'café pronto às' (não 'ligar às') — listar os existentes e criar um novo.

## Telas

Uma tela com lista de agendamentos + editor. No celular, lista e editor ficam empilhados na mesma página (desvio documentado no README — no design original eram duas pranchetas separadas); no desktop, ficam lado a lado.

## Hierarquia

1) Próximo agendamento ativo (destacado) no topo da lista; 2) demais agendamentos (ativo 'domingo', pausado); 3) nota informativa sobre funcionamento offline; 4) editor de novo agendamento (hora, dias, timeout, salvar/cancelar).

## Layout

Celular (<768): lista e editor empilhados verticalmente, editor com barra de cabeçalho própria (Cancelar/Novo agendamento/Salvar). Tablet (768–1023): mesmo empilhamento (wide--late só abre no desktop). Desktop (≥1024): lista e editor lado a lado (grid 1fr/1fr), sem a barra de cabeçalho do editor (usa botões Salvar/Cancelar no rodapé em vez disso).

## Componentes

card (variantes accent/default/muted+is-paused); switch (liga/desliga agendamento); days (indicador read-only de dias ativos, só celular); daypick (seletor de dias interativo no editor); wheel (seletor de hora em formato 24h); seg (controle segmentado de timeout); note (aviso informativo); btn--primary/btn--ghost (salvar/cancelar).

## Tokens

Agendamento ativo mais próximo usa borda --brand (card--accent). Pausado usa fundo --sunken + cor --dim (is-paused). Números do wheel em Space Grotesk 34px.

## Conteúdo

'Agendamentos', 'Novo', 'O café fica pronto quando você chega na cozinha. Dois agendamentos estão armados.', 'Próximo · amanhã', 'Café pronto às 06:45', 'Seg–sex · desliga sozinha após 8 min', 'Domingo', 'Café pronto às 08:30', 'Começo lento · desliga sozinha após 8 min', 'Pausado', 'Café pronto às 14:10', 'Turno da tarde · seg, qua', 'A base guarda estes horários na memória dela. Se o Wi-Fi cair de madrugada, o café acontece do mesmo jeito.', 'Café pronto às', 'Nestes dias', 'Desliga sozinha após', 'Não há sensor de água na base, então é o temporizador que encerra o preparo.', 'Salvo na base além da nuvem, para disparar offline.', 'Salvar agendamento'.

## Interações

O switch de cada card liga/desliga o agendamento sem abrir o editor. 'Novo' abre o editor para criar um agendamento. No editor: girar o wheel ajusta a hora; tocar um dia no daypick alterna ativo/inativo; o seg de timeout tem seleção única; 'Salvar agendamento' persiste; 'Cancelar' descarta.

## Estados

### State: populated

Trigger:
Tela aberta com agendamentos já criados.

Expected behavior:
Lista mostra o próximo agendamento em destaque, seguido dos demais (ativos e pausados) em ordem.

Evidence:
Prototype

### State: disabled

Trigger:
Usuário desativa o switch de um agendamento.

Expected behavior:
O card muda para o tratamento visual 'pausado' (fundo --sunken, texto --dim, eyebrow atenuado) e o switch move para a posição desligada; o agendamento deixa de disparar mas continua na lista.

Evidence:
Prototype

### State: offline

Trigger:
Conexão Wi-Fi ou nuvem cai.

Expected behavior:
Os agendamentos já salvos continuam disparando pelo relógio interno da base, sem depender do app ou da nuvem — o texto do app afirma isso explicitamente, mas nenhuma tela mostra um indicador visual de 'base offline' nesta lista.

Evidence:
Prototype

### State: permission denied

Not applicable:
Verificado no backend (2026-09-18): `AgendamentoService.kt` importa `PapelDispositivo` mas não checa `.papel` em nenhum dos seus métodos — criar/editar/excluir/ativar agendamento está disponível a qualquer usuário vinculado ao dispositivo, incluindo convidado. Corrigindo minha própria correção anterior — não é lacuna, é a regra de negócio real (só administração do dispositivo e baixa de descalcificação são exclusivas do proprietário).

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

### State: success

OPEN_QUESTION
Estado não avaliado nesta extração do protótipo — nem especificado, nem marcado
como não aplicável.

Impact:
A implementação não deve assumir o comportamento deste estado sem confirmação —
esta lacuna precisa ser resolvida antes ou durante a implementação.

## Responsividade

Celular (<768): lista e editor empilhados, editor com barra de cabeçalho própria. Tablet (768–1023): mesmo empilhamento do celular para este bloco. Desktop (≥1024): lista e editor lado a lado, editor sem a barra de cabeçalho (usa botões no rodapé).

## Acessibilidade

Os cards de agendamento usam <span class="switch"> sem role="switch" nem aria-checked — não é um controle nativo acessível por teclado ou leitor de tela. O daypick e o seg do editor usam <button> reais (acessíveis por teclado), mas sem aria-pressed para indicar seleção.

## Restrições

Sem sensor de água — o timeout é quem encerra o preparo, não um sensor de nível. Horários e dias ficam salvos na própria base (não só no app/nuvem), garantindo disparo offline.

## Referências

- docs/docs_interface/prototype/schedule.html
- docs/docs_interface/prototype/assets/cafey.css
- docs/docs_interface/prototype/README.md

## Decisões

CONFIRMED
O agendamento ativo mais próximo recebe tratamento visual diferenciado (card--accent) dos demais ativos.
Source: schedule.html (.card.card--accent vs. .card sem modificador)

CONFIRMED
Lista e editor de novo agendamento vivem na mesma página — desvio do design original, que separava 'lista' e 'novo agendamento' em pranchetas distintas no celular.
Source: docs/docs_interface/prototype/README.md (item 4 dos desvios)

ASSUMED
O botão 'Novo' sempre abre um editor vazio (não pré-populado); editar um agendamento existente reaproveitaria o mesmo editor, mas o protótipo não mostra esse fluxo — só criação.
Reason: Nenhuma tela mostra um card levando a um editor pré-preenchido; a suposição segue o padrão mais comum de reuso de editor, mas não é confirmada pelo protótipo.

## Estado de implementação (observado em 2026-09-18)

`ScheduleScreen.kt`/`ScheduleViewModel.kt` (Compose Multiplatform) resolvem a questão aberta de lista vazia ("Nenhum agendamento ainda."), além de loading e um estado "salvando" no botão. Também adicionam editar/excluir, que este contrato não previa.

Divergências reais com este contrato, documentadas no próprio código:
- O "wheel" animado de hora foi substituído por um campo de texto validado (HH:mm), com erro exibido por campo — na prática mais acessível que o `<span class="switch">` do protótipo (aqui é um `Switch` real do Material3, com semântica de toggle nativa).
- Não existe o seletor "desliga sozinha após" — não há campo correspondente em `CriarAgendamentoRequest`/`AtualizarAgendamentoRequest` (a duração é do dispositivo inteiro, não por agendamento).
- Sem o destaque visual "próximo · amanhã" (card--accent) — a lista atual não distingue visualmente o próximo agendamento dos demais.

Correção (2026-09-18, checkout sincronizado com `origin/main`): a navegação (trilho de ícones/sidebar por breakpoint) já adapta em nível de app inteiro via `CafeyNavHost.kt`/`NavShellSizeClass.kt` — o que eu tinha registrado como "sem trilho/sidebar" não é mais verdade em nenhuma tela. O que continua faltando é só o grid de duas colunas *dentro* desta tela (lista ao lado do editor no desktop) — a lista e o editor aqui ainda ocupam a coluna inteira, empilhados.

Fontes: `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/schedule/ScheduleScreen.kt`, `ScheduleViewModel.kt`.

## Questões abertas

OPEN_QUESTION
Não há composição para 'nenhum agendamento criado ainda' (lista vazia). **Resolvido na implementação real (ver seção "Estado de implementação" acima) — este contrato ainda não refletia isso.**
Impact: A tela pode ficar sem conteúdo de destaque na primeira execução do app, antes do usuário criar o primeiro agendamento.

OPEN_QUESTION
Falha ao salvar um agendamento (ex.: base não responde) não tem feedback visual definido, nem confirmação de sucesso após salvar.
Impact: O usuário pode não saber se o agendamento foi realmente persistido na base.

OPEN_QUESTION
Comportamento de dois agendamentos no mesmo horário (conflito) não está definido.
Impact: Pode ser necessário decidir se o app impede, avisa, ou permite silenciosamente a sobreposição antes de implementar o editor.

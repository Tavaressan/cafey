# Design Contract — Cuidados

## Objetivo da experiência

Comunicar manutenção preventiva da máquina (enxágue, filtro, descalcificação) contada em número de preparos, não em datas — 'contado em preparos, dito em palavras simples' (index.html).

## Telas

Uma tela só. O item mais urgente ('chegando') recebe um cartão de destaque no topo; os demais (mais distantes no tempo) ficam abaixo, lado a lado a partir do tablet.

## Hierarquia

1) Cuidado mais próximo (Enxaguar o circuito, com barra de progresso + ação primária/secundária); 2) cuidados futuros (filtro de água, descalcificação), ordenados por proximidade; 3) rodapé explicando a origem da contagem.

## Layout

Celular (<768): cartão de destaque em coluna (a barra linear de progresso é 'only-narrow', ou seja, o gauge circular fica oculto). Tablet+ (≥768): cartão de destaque em linha (texto + gauge circular lado a lado); os dois cards futuros ficam lado a lado (.wide em grid 1fr/1fr).

## Componentes

card--accent (cuidado urgente); progress (linear, 3 variantes de cor: brand/blue/dim conforme proximidade); gauge (circular SVG, só tablet+); btn--primary/btn--ghost (iniciar enxágue / já fiz isso); card--muted+is-paused (cuidado distante, tratamento atenuado); link (lembrete configurável).

## Tokens

Cor da barra de progresso indica o tipo de cuidado: --brand (enxágue, mais urgente), --blue (filtro), --dim (descalcificação, mais distante) — a mesma paleta usada nos gráficos (--chart-*).

## Conteúdo

'Cuidados', 'Uma coisa chegando. Nada urgente.', 'Chegando', 'Enxaguar o circuito', '34 de 40 preparos desde o último enxágue', 'Passe uma jarra de água pura, sem café no cesto. Leva uns quatro minutos e tira o óleo velho da linha.', 'Iniciar enxágue', 'Já fiz isso', 'Trocar o filtro de água', '210 de 300 preparos', 'Faltam umas três semanas no seu ritmo. Vale comprar um agora para já ficar na gaveta.', 'Lembrar aos 280', 'Descalcificar', '60 de 400 preparos', 'Ainda longe — por volta de fevereiro nesse ritmo. A gente avisa quando importar.', 'A base conta cada preparo sozinha, inclusive os que você inicia pelo botão do pedestal.'.

## Interações

'Iniciar enxágue' dispara o ciclo de enxágue pela própria máquina. 'Já fiz isso' marca a manutenção como concluída manualmente, para quando o usuário enxaguou sem usar o app. 'Lembrar aos 280' agenda um lembrete futuro para o cuidado do filtro.

## Estados

### State: populated

Trigger:
Tela aberta com pelo menos um cuidado pendente.

Expected behavior:
Mostra o cuidado mais urgente em destaque e os demais em cards secundários, ordenados por proximidade.

Evidence:
Prototype

### State: disabled

Trigger:
Cuidado está muito distante no tempo (ex.: descalcificação a mais de 300 preparos de distância).

Expected behavior:
Recebe o mesmo tratamento visual 'pausado' dos agendamentos desativados (fundo --sunken, texto --dim), mesmo sem ser literalmente uma opção que o usuário desligou — sinaliza baixa prioridade, não indisponibilidade.

Evidence:
Prototype

### State: permission denied

Trigger:
Usuário vinculado ao dispositivo não é o proprietário (`PapelDispositivo` ≠ `PROPRIETARIO`) e a tela carrega o status de descalcificação.

Expected behavior:
A ação "Já fiz isso" não é exibida; em seu lugar aparece o texto "Só o proprietário do dispositivo pode registrar a descalcificação.". O restante do card (progresso, contagem) continua visível normalmente.

Evidence:
Implementation (não confirmado pelo protótipo original, que não modelava papéis — correção de 2026-09-18 a este contrato, que antes marcava este estado como "não aplicável")

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

### State: offline

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

Celular (<768): cartão de destaque em coluna, com barra linear. Tablet+ (≥768): cartão de destaque em linha com gauge circular; cards futuros lado a lado.

## Acessibilidade

O gauge circular (SVG) não tem texto alternativo além do valor numérico ao lado (.gauge__value/.gauge__unit), que já é acessível em texto; a barra de progresso linear (.progress__fill) não expõe role="progressbar" nem aria-valuenow.

## Restrições

A contagem de preparos é feita pela própria base, incluindo os preparos iniciados pelo botão físico do pedestal (sem passar pelo app).

## Referências

- docs/docs_interface/prototype/care.html
- docs/docs_interface/prototype/assets/cafey.css
- docs/docs_interface/prototype/README.md
- docs/docs_interface/prototype/index.html

## Decisões

CONFIRMED
O cuidado mais urgente sempre recebe o cartão de destaque no topo (card--accent), com barra de progresso e gauge; os demais usam card padrão ou atenuado, sem gauge.
Source: care.html

CONFIRMED
O gauge circular é uma representação redundante da mesma barra de progresso linear, exibida só a partir do tablet.
Source: care.html (.col.only-narrow vs. .gauge) + assets/cafey.css (media query 768px, .gauge{display:grid})

ASSUMED
'Já fiz isso' existe porque o usuário pode enxaguar manualmente sem usar o app, não porque o enxágue automático falhou.
Reason: É a leitura mais direta do rótulo, mas o protótipo não explica a diferença entre as duas ações além do texto do botão.

## Estado de implementação (observado em 2026-09-18)

`CareScreen.kt`/`CareViewModel.kt` (Compose Multiplatform) implementam só o cartão "Descalcificar" com dado real (`StatusDescalcificacaoResponse`, contador e limiar de preparos) — "Enxaguar o circuito" e "Trocar o filtro de água", que este contrato documenta em detalhe (com gauge circular e barras de progresso próprias), **não têm contador nem endpoint no backend hoje** e foram omitidos do código, com comentário explícito nesse sentido. Não há o tratamento hero/gauge circular descrito nas seções "Layout"/"Componentes" acima — é um único card, sem `card--accent` nem `gauge`, sem grid de duas colunas dentro desta tela.

Correção (2026-09-18, checkout sincronizado com `origin/main`): a navegação (trilho de ícones/sidebar por breakpoint) já adapta em nível de app inteiro via `CafeyNavHost.kt`/`NavShellSizeClass.kt` — o que eu tinha registrado como "sem trilho/sidebar" não é mais verdade em nenhuma tela.

Resolvido: loading, estado "nenhum dispositivo vinculado" e o estado `permission denied` (ver Estados acima, que corrige o notApplicable original deste contrato).

Fontes: `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/care/CareScreen.kt`, `CareViewModel.kt`.

## Questões abertas

OPEN_QUESTION
Não há tela para 'nenhum cuidado pendente' (todos os itens de manutenção em dia) — a lede pressupõe sempre pelo menos um item 'chegando'. **Nota (2026-09-18): a implementação atual só modela "Descalcificar" — ver "Estado de implementação" acima.**
Impact: O layout pode ficar sem conteúdo de destaque se implementado sem esse caso.

OPEN_QUESTION
'Iniciar enxágue' e 'Já fiz isso' não têm confirmação visual (sucesso, ou erro se a base não responder) definida.
Impact: O usuário pode não saber se a ação realmente foi registrada pela base.

OPEN_QUESTION
Comportamento quando dois ou mais cuidados estão igualmente urgentes (ex.: enxágue e filtro no mesmo momento) não está definido — só um cartão de destaque existe na tela.
Impact: Pode ser preciso decidir um critério de priorização (ordem, múltiplos destaques) antes de implementar.

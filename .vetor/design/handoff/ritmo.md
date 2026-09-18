# Design Contract — Ritmo

## Objetivo da experiência

Mostrar padrões de uso ao longo do tempo (hora do dia, sequência de dias, semana) de forma legível em frase antes de gráfico — 'frases primeiro, gráficos depois' (index.html).

## Telas

Uma tela só, sem interação — puramente informativa/analítica.

## Hierarquia

1) Frase-resumo do padrão predominante + gráfico de distribuição por hora (24h); 2) sequência de dias consecutivos (streak) e gráfico da semana atual, lado a lado a partir do tablet; 3) rodapé explicando a origem da contagem.

## Layout

Celular (<768): tudo em coluna única. Tablet+ (≥768): os dois cards secundários (streak e semana) ficam lado a lado (.wide vira grid 1fr/1fr).

## Componentes

card; sentence (frase de destaque, com <em> para o dado numérico); bars (gráfico de distribuição por hora, 24h); bars-axis (eixo de horas, alguns marcadores só aparecem no tablet+); estrela (indicador de streak, uma por dia, azul=ativo/off=inativo); bars--week (gráfico compacto da semana, 7 barras); bars-axis--split (dias da semana, dia atual destacado).

## Tokens

Barras usam --chart-hi (dia de pico), --chart-mid (uso moderado) e cor neutra (--chart-flat/--line) para baixo uso — a mesma escala em ambos os gráficos da tela.

## Conteúdo

'Seu ritmo', 'As últimas quatro semanas de manhãs.', 'Você prepara entre 06:30 e 07:15 em quase todo dia útil, e de novo depois do almoço nas quartas.', '12 manhãs seguidas.', 'Maior sequência até aqui: 21 manhãs, lá em julho.', '9 preparos esta semana — dois a mais que na anterior.', 'Contado pela base, não pelo app — os números continuam certos mesmo com o celular desligado.'.

## Interações

Nenhuma — tela somente leitura no protótipo (nenhum botão, link ou controle interativo).

## Estados

### State: populated

Trigger:
Tela aberta com histórico de uso já acumulado.

Expected behavior:
Mostra frase-resumo e gráficos com dados reais das últimas quatro semanas e da semana atual.

Evidence:
Prototype

### State: offline

Trigger:
Celular desligado ou sem conexão com a base/nuvem.

Expected behavior:
A contagem de sequência e de preparos permanece correta porque é feita pela base, não pelo app — a tela não perde precisão mesmo sem o celular estar por perto.

Evidence:
Prototype

### State: permission denied

Not applicable:
Verificado no backend (2026-09-18): `EventoService.kt` só checa `papel != PROPRIETARIO` para a ação de dar baixa em descalcificação (linha usada por Cuidados); a leitura de histórico/estatísticas não tem checagem de papel. Corrigindo minha própria correção anterior — não é lacuna, é a regra de negócio real (histórico é visível a qualquer usuário vinculado, só a baixa de descalcificação é exclusiva do proprietário).

### State: disabled

Not applicable:
Tela somente leitura, sem nenhum controle interativo — não há elemento que possa ficar desabilitado.

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

Celular (<768): coluna única. Tablet+ (≥768): streak e gráfico da semana ficam lado a lado.

## Acessibilidade

O gráfico de barras (.bars) é puramente visual (divs com estilo inline), sem texto alternativo nem tabela de dados subjacente acessível a leitor de tela — só a frase-resumo (.sentence) carrega o dado em texto.

## Restrições

Os números vêm da contagem da própria base, não de um cálculo do app — por isso permanecem corretos mesmo com o app fechado ou o celular desligado.

## Referências

- docs/docs_interface/prototype/rhythm.html
- docs/docs_interface/prototype/assets/cafey.css
- docs/docs_interface/prototype/README.md
- docs/docs_interface/prototype/index.html

## Decisões

CONFIRMED
A hierarquia da tela é deliberadamente 'frase primeiro, gráfico depois' — cada card abre com uma sentença em linguagem natural antes do gráfico correspondente.
Source: index.html ('Frases primeiro, gráficos depois') + rhythm.html (.sentence sempre antes de .bars em cada card)

INFERRED
O eixo de horas do gráfico principal (04h/12h/21h sempre visíveis; 08h/16h só a partir do tablet) prioriza legibilidade no celular reduzindo a densidade de rótulos, não removendo dados.
Source: rhythm.html (.bars-axis span.only-wide) + assets/cafey.css (media query 768px)

## Estado de implementação (observado em 2026-09-18, corrigido no mesmo dia — a primeira leitura foi feita contra um checkout local 17 commits atrás de `origin/main`)

Depois de sincronizar com `origin/main`, o quadro mudou em um ponto e se confirmou em outro:

- **Mudou:** a "sequência de manhãs" (streak) que eu tinha listado como ausente **existe**, mas não nesta tela — foi implementada na Home (`StreakCard`, issue #177). O comentário no topo de `HistoryScreen.kt` ainda diz "a 'sequência de manhãs' (streak) do protótipo não tem endpoint correspondente", o que hoje é impreciso (o endpoint existe, `EstatisticasConsumoResponse.sequenciaManhasDias`, só que consumido pela Home, não pelo Ritmo) — comentário desatualizado no próprio repositório, vale um ajuste ali independente deste contrato.
- **Também novo:** um gráfico de barras por origem do preparo (`OrigemBarChart`, reaproveitando `LinearProgressIndicator`, issue #69/APP-10) foi adicionado ao card de estatísticas — não existia na minha leitura anterior.
- **Confirmado, não resolvido:** mesmo com essas adições, a tela continua sendo "estatísticas totais + gráfico por origem + lista paginada de eventos", não "frase primeiro, gráfico de distribuição horária depois + streak" como este contrato documenta. Não há frase-resumo em linguagem natural, não há gráfico de distribuição por hora do dia, e o streak não aparece aqui (está na Home). Isto continua sendo uma divergência de forma entre o que o Design Contract documenta como intenção e o que foi construído — não decidi qual dos dois é o caminho certo (ver `design-vocabulary.md` §7 e "Fix vs. Escalação" da skill de design); seria uma decisão de produto, não algo para este arquivo resolver sozinho.

Resolvido: estado vazio ("Nenhum preparo registrado ainda."), loading, loading da paginação e erro.

Fontes: `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/history/HistoryScreen.kt`, `HistoryViewModel.kt`, `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/home/HomeScreen.kt` (streak real).

## Questões abertas

OPEN_QUESTION
Não há composição para o caso de um usuário novo, sem histórico suficiente para preencher 4 semanas de gráfico (streak zerado, poucas barras). **Nota (2026-09-18): a implementação atual não tem gráficos nem streak — ver "Estado de implementação" acima; esta questão pode estar obsoleta dependendo de como o conflito de forma for resolvido.**
Impact: Implementar assumindo sempre dados abundantes pode quebrar o layout dos gráficos ou exibir estatísticas sem sentido (ex.: 'maior sequência: 0 manhãs') para contas novas.

OPEN_QUESTION
Não é possível saber, pela tela, o que aconteceria se a base perdesse o histórico (reset de fábrica, troca de base) — o rodapé afirma que a contagem é 'da base', mas não cobre esse caso.
Impact: Sem definição, a implementação pode assumir que o histórico é sempre preservado indefinidamente.

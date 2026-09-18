# Design Contract — Início

## Objetivo da experiência

Mostrar de imediato o estado atual da máquina e permitir preparar café manualmente, sem exigir navegação — é a tela de abertura do app.

## Telas

Uma tela só, com dois arranjos por breakpoint: celular (mostrador solto sobre o fundo, ação primária, próximo preparo e tiles de métricas) e tablet/desktop (o mostrador ganha card próprio e, só a partir do desktop, aparecem cabeçalho de saudação, ritmo semanal e banner de cuidado que não existem no celular).

## Hierarquia

1) Estado da máquina + ação primária 'Preparar agora' (topo, sempre visível); 2) próximo preparo agendado; 3) métricas secundárias — no celular, tiles compactos de enxágue/sequência; no desktop, ritmo semanal e banner de cuidado substituem os tiles.

## Layout

Celular (<768): coluna única, mostrador e ação centralizados, sem cabeçalho de saudação. Tablet (768–1023): ainda coluna única para este bloco (classe wide--late só abre no desktop; wide--home sem efeito aqui). Desktop (≥1024): grid de duas colunas 1.22fr/.78fr — mostrador+ação à esquerda, próximo preparo+ritmo+banner à direita; cabeçalho 'Bom dia, Sandro' e chip de contagem regressiva aparecem só aqui.

## Componentes

stage (mostrador circular com anel decorativo, estrela pulsante, estado textual e subtítulo); btn--primary/btn--lg (ação principal); btn--ghost (ação secundária de horário, só desktop); card (próximo preparo, ritmo semanal); duo/tile (métricas de enxágue e sequência, só celular); banner (aviso de cuidado, só tablet+); bars--week (gráfico de ritmo, só tablet+); device header (nome da base + estado online, só celular).

## Tokens

Estrela azul (--blue #6E8FBC) como ícone de estado 'conectado', mesmo símbolo do LED físico da base. Ação primária em --brand (#A33A21). Números de horário em Space Grotesk 26–34px (.card__hero).

## Conteúdo

'Pronta', 'Último preparo 07:12', 'Preparar agora', 'Preparar às 06:45', 'Desliga sozinha após 8 min', 'Próximo preparo', 'Pular', '06:45 · amanhã', 'Manhã de dia útil · seg–sex · guardado na memória da própria base', 'Bom dia, Sandro', 'Segunda-feira, 9 de setembro · nada precisa de você agora', 'A base não tem sensor de água nem de temperatura — confira a jarra antes de iniciar daqui.'.

## Interações

'Preparar agora' dispara o preparo manual. 'Pular' cancela a próxima ocorrência do agendamento sem abrir o editor. 'Alterar' (celular, texto) e 'Preparar às 06:45' (desktop, botão) levam ao ajuste do horário do próximo preparo. 'Abrir' no banner de cuidado (desktop) leva à tela Cuidados.

## Estados

### State: populated

Trigger:
Tela aberta com dados reais da base (mostrador, próximo preparo, métricas de uso).

Expected behavior:
Mostra o estado atual (Pronta + hora do último preparo), o próximo preparo agendado e métricas de uso; a ação primária 'Preparar agora' está sempre disponível.

Evidence:
Prototype

### State: permission denied

Not applicable:
Verificado no backend (2026-09-18): `DispositivoService.comandar()` (ligar/desligar/cancelar) não checa `papel` — qualquer usuário vinculado ao dispositivo, incluindo convidado (`PapelDispositivo.CONVIDADO`), pode operar. Corrigindo minha própria correção anterior — eu tinha marcado isto como `OPEN_QUESTION`, mas na verdade dá para confirmar: não é lacuna, é a regra de negócio real (o modelo de papel restringe só administração do dispositivo e baixa de descalcificação, não operação do dia a dia).

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

### State: disabled

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

Celular (<768): coluna única, sem cabeçalho de saudação, ação primária full-width, tiles de métricas visíveis. Tablet (768–1023): mesmo arranjo em coluna única para este bloco (a mudança de tablet aqui é só no chrome — abas cede lugar ao trilho de ícones). Desktop (≥1024): grid 1.22fr/.78fr, cabeçalho de saudação e chip de contagem visíveis, ritmo semanal e banner de cuidado substituem os tiles do celular.

## Acessibilidade

Ícones SVG decorativos usam aria-hidden corretamente. Não há aria-label nos botões (mas todos têm texto visível). Estado de foco por teclado e ordem de tabulação não são verificáveis numa leitura estática sem MCP de browser.

## Restrições

A base não tem sensor de água nem de temperatura — a confirmação de que há café/água suficiente depende do usuário, não do app. O horário do próximo preparo fica guardado na memória da própria base, não só no app/nuvem.

## Referências

- docs/docs_interface/prototype/home.html
- docs/docs_interface/prototype/assets/cafey.css
- docs/docs_interface/prototype/assets/nav.js
- docs/docs_interface/prototype/README.md

## Decisões

CONFIRMED
A estrela azul é o mesmo símbolo do LED físico da base, reutilizado como ícone de estado 'conectado' em toda a UI.
Source: assets/cafey.css (comentário 'Estrela: o mesmo símbolo do LED da base') + base.html (legenda do LED: 'Azul fixo — pronta')

CONFIRMED
O ajuste de horário do próximo preparo só tem affordance de botão no desktop; no celular é um link de texto dentro do aside da ação primária.
Source: home.html (.stage__actions .btn--ghost.only-wide vs. .stage__aside.only-mobile)

ASSUMED
'Pular' cancela apenas a próxima ocorrência do agendamento recorrente, não a série inteira.
Reason: É o comportamento mais comum para essa ação em apps de agendamento recorrente, mas o protótipo não distingue visualmente os dois efeitos possíveis.

## Estado de implementação (observado em 2026-09-18, corrigido no mesmo dia — a primeira leitura foi feita contra um checkout local 17 commits atrás de `origin/main`)

`HomeScreen.kt`/`HomeViewModel.kt` (Compose Multiplatform) resolvem hoje a maior parte das questões abertas originais desta extração:
- Estado "preparando" (`DeviceState.Brewing`, ação primária vira "Cancelar preparo") e ações desabilitadas com comando em andamento (`commandInFlight`). Loading e erro também tratados.
- Card "Próximo preparo" com dado real (`NextPreparoCard`, calculado a partir dos agendamentos — issue #176) e "Sequência de manhãs" (`StreakCard`, `sequenciaManhasDias` — issue #177) **já existem**, cada um com estado vazio próprio ("Nenhum agendamento ativo" / "Nenhuma sequência ainda") — o que eu tinha registrado nesta seção como omissão estava incorreto (checkout desatualizado).
- Navegação lateral responsiva (trilho de ícones no tablet, sidebar no desktop, breakpoints 768/1024 idênticos ao `cafey.css`) já existe em `CafeyNavHost.kt`/`NavShellSizeClass.kt`.
- Novo, fora do que este contrato previa: estado vazio de "nenhum dispositivo cadastrado" (`EmptyDeviceState`, issue APP-13), com CTA para uma tela de cadastro que não está no protótipo original.

Divergência real que persiste mesmo após a correção: a navegação lateral adapta, mas o **conteúdo** da tela (mostrador + próximo preparo + streak) continua numa única coluna em qualquer largura — não reproduz o grid assimétrico `.wide--home` (mostrador à esquerda, cartões à direita) que este contrato descreve para desktop.

Fontes: `apps/shared/src/commonMain/kotlin/br/com/tavaressan/cafey/shared/ui/home/HomeScreen.kt`, `HomeViewModel.kt`, `CafeyNavHost.kt`, `NavShellSizeClass.kt`.

## Questões abertas

OPEN_QUESTION
Não há composição de tela para o estado 'preparando' (brewing), citado na prancheta de origem (1a, conforme README) e sugerido pela classe CSS .stage__star--brewing (animação mais rápida), que existe mas não é usada em nenhum HTML. **Resolvido na implementação real (ver seção "Estado de implementação" acima) — este contrato ainda não refletia isso.**
Impact: Implementar a tela de Início sem essa definição arrisca deixar o app sem feedback visual durante o preparo — o momento de maior atenção do usuário.

OPEN_QUESTION
Comportamento de 'Preparar agora' quando a base está offline, sem café/água, ou falha ao iniciar não está especificado em nenhuma tela.
Impact: Sem essa definição, a implementação pode assumir sucesso sempre, mascarando falhas reais de hardware.

OPEN_QUESTION
Não há composição para 'nenhum próximo preparo agendado' (todos os agendamentos pausados ou nenhum criado). **Resolvido na implementação real (ver seção "Estado de implementação" acima) — `NextPreparoCard` já trata `proximoPreparo == null` com texto próprio.**
Impact: O card 'Próximo preparo' pode quebrar visualmente ou exibir dado inválido se implementado sem esse caso coberto.

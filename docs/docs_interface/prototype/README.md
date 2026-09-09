# Protótipo de interface

Implementação em HTML e CSS das cinco telas do app, a partir do projeto Claude Design
**Caféy coffee machine control UI** (`Cafey App.dc.html`, exportado em
`../Caféy coffee machine control UI-handoff.zip`).

Abra `index.html` no navegador. Não há build nem dependências — só as fontes do Google Fonts,
que caem para as do sistema se não houver rede.

## Arquivos

| Arquivo | Prancheta de origem |
|---|---|
| `home.html` | 1a — Status stage (celular pronta / celular preparando / desktop) |
| `schedule.html` | 2a — Schedule (lista, editor e desktop) |
| `rhythm.html` | 2b — Rhythm |
| `care.html` | 2c — Care (celular e tablet) |
| `base.html` | 2d — Base, no fundo escuro |
| `assets/cafey.css` | Tokens de cor, tipografia e componentes |
| `assets/nav.js` | Barra de abas, trilho de ícones e barra lateral (equivale ao `cafey-tabs.js`) |

A marca vem de `../logo/cafey-logo.svg` por caminho relativo — sem cópia. Mexer no arquivo da
marca muda o protótipo junto.

## Pontos de quebra

Cada tela é responsiva e atravessa os três tamanhos do projeto de design:

- **< 768** — barra de abas embaixo, coluna única (celular 390).
- **768–1023** — trilho de ícones à esquerda, blocos em duas colunas (tablet 834).
- **≥ 1024** — barra lateral com rótulos e o cartão da base no rodapé (desktop 1180).

## Desvios em relação ao projeto de design

1. **Horas em formato 24 h.** O design mostra 6:45 AM / 2:10 PM; as telas em português usam
   06:45 e 14:10, e o seletor de hora do editor perde o par de botões AM/PM.
2. **Textos em português.** Vieram da tabela PT-BR da prancheta 2d; o restante segue o mesmo
   registro. O design deixa explícito que as telas em inglês eram só de apresentação.
3. **Dias da semana com um domingo só.** Na prancheta, a lista começa na segunda e o seletor
   começa no domingo. Aqui os dois começam no domingo.
4. **Uma página por tela.** No design, "lista" e "novo agendamento" são pranchetas separadas no
   celular; aqui as duas ficam em `schedule.html`, empilhadas no celular e lado a lado no desktop.

Os dados são de exemplo — nada aqui conversa com o backend nem com a base.

# Marca na peça física (#303)

Composição horizontal monocromática para a carcaça impressa em FDM:

```
[SÍMBOLO]  Caféy
```

**Grafia decidida pelo responsável em 09/11/2026: "Caféy"**, a mesma do wordmark dos apps.

## Arquivos

| Arquivo | O que é |
|---|---|
| `cafey-lockup-mono.svg` | Composição com **"Caféy"** (grafia decidida) |
| `cafey-lockup-mono-sem-acento.svg` | Alternativa com "Cafey", sem acento, se o acento não imprimir bem |
| `cafey-symbol-mono.svg` | Só o símbolo, em áreas preenchidas |
| `gen_lockup.py` | Gerador dos três SVGs e das métricas abaixo |
| `../../../mechanical/scripts/build_corpo_prova_marca.py` | Gera o corpo de prova em STL (seção 6) |

Cada SVG é **um único `<path>` preenchido** (`fill-rule="evenodd"`), sem traços, pronto para importar no CAD. O SVG usa y para baixo: ao importar, conferir se o texto não ficou espelhado.

```sh
cd docs/docs_interface/logo && uv run gen_lockup.py
```

## 1. Símbolo: traço convertido em contorno

- A geometria vem **importada** de `gen_logo.py` (`OUTLINE`, `s_curve`, `star_path`), não redesenhada.
- Cada traço vira área com o mesmo `stroke-width` (16 no contorno, 13 nas curvas) e pontas e junções arredondadas, como no SVG original.
- **Verificação:** o `cafey-symbol-mono.svg` e o `cafey-logo.svg` pintado de preto, renderizados na mesma escala (610 × 826 px), diferem em **0 pixels**.

## 2. Lettering para FDM

Ponto de partida: Space Grotesk, a fonte dos apps (`apps/shared/.../font/space_grotesk_variable.ttf`).

| Parâmetro | Apps (`CafeyWordmark`) | Peça física | Motivo |
|---|---|---|---|
| Peso | 600 (SemiBold) | **700** (Bold) | Hastes mais grossas sobrevivem melhor ao bico de 0,4 mm |
| Tracking | −0,03 em | **+0,02 em** | Abre o espaço entre letras; folgas estreitas fecham na impressão |
| Tamanho do texto / altura do símbolo | 0,94 (27 sp / 28,8 dp) | 0,94 | Mantém a proporção da marca |
| Espaço símbolo → texto | ≈ 0,62 da altura do símbolo | 0,62 | Igual aos apps |
| Alinhamento vertical | Centro da linha | Centro da altura das maiúsculas no centro do símbolo | Fica visualmente centrado sem depender da altura de linha |
| Kerning | Da fonte | Da fonte, via HarfBuzz | — |

## 3. Métricas e escala mínima

Valores em unidades do desenho (o símbolo tem 356 de altura) e em mm para algumas alturas de símbolo:

| Detalhe | Unidades | Fração do símbolo | 15 mm | 20 mm | 25 mm | 30 mm |
|---|---|---|---|---|---|---|
| Traço do contorno | 16,0 | 0,0449 | 0,67 | 0,90 | 1,12 | 1,35 |
| Traço das curvas internas | 13,0 | 0,0365 | 0,55 | 0,73 | 0,91 | 1,10 |
| Folga contorno × pontas das curvas | 3,5 | 0,0098 | 0,15 | 0,20 | 0,25 | 0,29 |
| Folga estrela × curvas | 8,2 | 0,0230 | 0,35 | 0,46 | 0,58 | 0,69 |
| Texto: trecho mais fino ("Caféy") | 23,3 | 0,0654 | 0,98 | 1,31 | 1,64 | 1,96 |
| Texto: abertura mais estreita ("Caféy") | 12,9 | 0,0362 | 0,54 | 0,72 | 0,91 | 1,09 |
| Acento: folga até o "e" | 14,0 | 0,0395 | 0,59 | 0,79 | 0,99 | 1,19 |

A composição tem **largura ≈ 3,9 × a altura do símbolo**: com 20 mm, 78 mm de largura; com 25 mm, 98 mm.

**Altura mínima do símbolo.** Com bico de 0,4 mm, um detalhe positivo robusto precisa de cerca de 0,8 mm (duas linhas de extrusão), e uma folga precisa de cerca de 0,5 mm para não fechar. **Esses limites são da prática comum e precisam ser confirmados no corpo de prova.**

| Restrição | Altura mínima do símbolo |
|---|---|
| Curvas internas ≥ 0,8 mm | 0,8 / 0,0365 ≈ **22 mm** |
| Folga estrela × curvas ≥ 0,5 mm | 0,5 / 0,0230 ≈ **22 mm** |
| Aberturas do texto e folga do acento ≥ 0,5 mm | 0,5 / 0,0362 ≈ 14 mm |
| Folga nas pontas das curvas ≥ 0,5 mm | 0,5 / 0,0098 ≈ 51 mm (inviável) |

**Proposta:** símbolo com **22 a 25 mm** de altura, ou seja, composição com 86 a 98 mm de largura.

**Risco conhecido.** As pontas das curvas em S quase encostam no contorno (y ≈ 98 e 414 do desenho). Em qualquer escala viável, essa folga fecha na impressão e as curvas parecem nascer do contorno. A diferença é local e não se resolve sem redesenhar o símbolo, o que a issue não permite.

**Acento.** Com o símbolo de 20 mm, o acento mede 4,05 × 2,78 mm e fica a 0,79 mm do "e". Imprime, mas é a ilha mais frágil da composição: no gravado ela vira um bolsão pequeno; no relevo, uma peça pequena que pode descolar. Conferir no corpo de prova. Se falhar, a alternativa sem acento já está gerada.

## 4. Tipo de marcação

| Alternativa | Prós | Contras |
|---|---|---|
| **Baixo-relevo (gravado)** | Nada sobressai para quebrar ou enganchar. Fica limpo em parede impressa na vertical. Uma cor só | Contraste vem só da sombra; as folgas do símbolo viram paredes finas |
| **Alto-relevo** | Mais visível com luz lateral | Detalhes finos (0,7 mm) podem quebrar. Na vertical, cada traço é uma parede fina de poucas linhas |
| **Peça separada encaixada** | Pode ter **segunda cor**. Pode ser impressa deitada, com a melhor resolução em XY e a face de topo lisa. Separa o risco da carcaça | Exige rebaixo com folga (~0,2 mm) e cola ou encaixe. Em parede curva, a peça também precisa ser curva |
| **Área plana dedicada** | Evita distorção na parede curva | Numa carcaça circular, uma faixa plana do tamanho da composição se afasta muito da parede (tabela abaixo) |

**Recomendação:**
- **Um filamento:** baixo-relevo de **0,8 mm**, projetado sobre a parede curva.
- **Duas cores:** peça separada, rebaixada na parede.

A decisão depende de "um filamento ou duas cores?", que está pendente com o responsável.

### Parede curva

A flecha de uma faixa plana de largura W numa parede de raio R é `R − √(R² − (W/2)²)`. É o quanto o centro da faixa se afasta do arco:

| Largura da composição | R = 60 mm | R = 80 mm | R = 100 mm | R = 130 mm | R = 160 mm |
|---|---|---|---|---|---|
| 58,5 mm (símbolo 15 mm) | 7,6 | 5,5 | 4,4 | 3,3 | 2,7 |
| 78,1 mm (símbolo 20 mm) | 14,4 | 10,2 | 7,9 | 6,0 | 4,8 |
| 97,6 mm (símbolo 25 mm) | 25,1 | 16,6 | 12,7 | 9,5 | 7,6 |

Com raios típicos de uma carcaça desse porte, um painel plano local fica grosso demais. O caminho natural é **projetar a marca sobre o cilindro** (wrap) com profundidade constante. O raio real só sai de #302.

## 5. Parâmetros para o CAD (#302)

Nomes propostos, para entrar no arquivo de parâmetros que #302 escolher:

| Parâmetro | Valor inicial | Observação |
|---|---|---|
| `marca_tipo` | `gravado` | `gravado`, `relevo` ou `encaixe` |
| `marca_altura_simbolo` | 22 mm | Confirmar no corpo de prova |
| `marca_profundidade` | 0,8 mm | Profundidade do gravado ou altura do relevo |
| `marca_angulo` | a definir | Posição no cilindro, ao lado do botão RGB (face frontal, esboço de 08/11) |
| `marca_z` | a definir | Altura do centro da composição na parede |
| `marca_arquivo` | `cafey-lockup-mono.svg` | "Caféy" (decidido em 09/11/2026) |

## 6. Corpo de prova

```sh
uv run mechanical/scripts/build_corpo_prova_marca.py
```

O script gera em `mechanical/build/` (pasta não versionada) duas placas de 107,6 × 82,9 × 3 mm, com a composição em três escalas. **De cima para baixo, os símbolos têm 25, 20 e 15 mm.**

- `corpo_prova_marca_gravado.stl`: gravado de 0,8 mm, sólido único e estanque.
- `corpo_prova_marca_relevo.stl`: relevo de 0,8 mm sobre a placa.

**Como imprimir:** **em pé** (placa na vertical) reproduz a parede da carcaça. Deitada reproduz a peça encaixada.

**O que registrar na #303, com foto:**
- a menor escala legível a ~1 m;
- se a folga estrela × curvas e as aberturas do "a" e do "e" ficaram abertas;
- como ficaram as pontas das curvas;
- qual versão, gravada ou em relevo, ficou melhor.

Se faltar tempo, o risco fica registrado na issue.

## 7. Pendências

- [x] Grafia na peça: **"Caféy"** (responsável, 09/11/2026).
- [ ] Um filamento ou duas cores (responsável).
- [ ] Raio e posição na carcaça, e integração ao CAD (#302).
- [ ] Corpo de prova impresso e fotografado.

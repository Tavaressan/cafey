# Módulo físico — pedestal em aço inox

Data: 01/09/2026. Sessão de decisão de arquitetura da peça. Nenhuma geometria
modelada ainda; o que segue é o que entra no CAD.

Ferramenta escolhida: FreeCAD com a workbench SheetMetal (addon, instalável pelo
Addon Manager). Motivo: é a única das candidatas que desdobra chapa aplicando
fator K e exporta a planificação. OpenSCAD foi descartado por não ter nenhum
conceito de dobra ou linha neutra.

O modelo será revisado por profissional de desenho CAD e dobra na empresa. Por
isso tudo depende de parâmetros: a crítica do revisor vira valor na planilha, não
retrabalho de geometria.

## Forma geral

Pedestal. A cafeteira apoia sobre o módulo; a eletrônica fica embaixo.

| Parâmetro | Valor | Origem |
|---|---|---|
| Pegada | 260 × 210 mm | catálogo da cafeteira (257 × 198), com folga |
| Altura externa | 45 mm | empilhamento dos módulos |
| Altura interna livre | 41,4 mm | descontados tampo, aba e fundo |
| Pés | 4 × 10 mm, EPDM ou silicone, antiderrapante | |
| Elevação total | 55 a 60 mm | conforme apoio da cafeteira |
| Massa em operação | ~2,9 kg | 1,64 kg mais 1,2 L |

## Construção

Duas peças.

**Peça 1 — dobrada.** Tampo, quatro saias de 45 mm para baixo e quatro abas de
12 mm dobradas para dentro na borda inferior das saias. Oito dobras.

**Peça 2 — plana.** Fundo removível, apoiado sobre as abas, sem nenhuma dobra.

| Item | Decisão |
|---|---|
| Cantos | Aba de 20 mm saindo das saias frontal e traseira, sobrepondo a face interna das laterais |
| Fixação dos cantos | 2 por canto, 8 no total. Parafuso M3 inox (premissa; rebite é alternativa paramétrica) |
| Fixação do fundo | 8 × M3 em porca-rebite nas abas do fundo |
| Alívio de canto | Entalhe de 2,4 mm no encontro das dobras |
| Planificação | ~366 × 316 mm; chapa bruta ~400 × 350 mm |

A aba do fundo não existe só para prender a tampa: ela é a aba de retorno que
enrijece as saias e responde ao risco de flexão do tampo sob carga.

**Ordem de dobra:** abas do fundo primeiro, com a chapa ainda plana; saias
depois. Ao contrário, a viradeira não alcança o interior de uma caixa de 45 mm de
profundidade por 260 mm de largura. Isso vai na nota do desenho.

## Arranjo interno

Rede entra e sai pela traseira; baixa tensão vive na frente. Os dois caminhos
correm em faixas paralelas e nunca lado a lado.

| Faixa | Profundidade | Conteúdo |
|---|---|---|
| Rede, 127 V | 120 mm, traseira | Prensa-cabo, bornes com tampa, F1, HLK-PM01, módulo de relé, tomada J1, terra J3 |
| Divisória | 2 a 3 mm | Policarbonato ou acrílico, altura plena, em cantoneiras na chapa do fundo |
| Baixa tensão, 5 V | 84 mm, frontal | ESP32, placa auxiliar de R e C, LED, botão |

O módulo de relé e o HLK-PM01 são híbridos e ficam do lado da rede, com os
terminais de baixa tensão voltados para a divisória.

**Travessia única**, por rasgo com passa-fio: +5 V e GND do HLK-PM01, mais VCC,
GND e IN do relé. Cinco fios, perpendiculares à divisória.

Nenhum condutor de rede a menos de 10 mm da divisória. Folga de projeto, não
número normativo; quem valida é o eletricista no marco de 02/10.

C1 (470 µF) e C2 (100 nF) montados na placa auxiliar, do lado da baixa tensão, na
entrada do rail. O ideal eletrônico seria junto à bobina, mas isso colocaria
eletrólito do lado da rede. Se o ESP32 resetar ao acionar o relé na bancada, o
diagnóstico já está escrito.

## Aberturas por face

Todas as aberturas respeitam duas margens: no mínimo 6 mm da linha de dobra
(deformação na viradeira) e no mínimo 25 mm das extremidades (zona das abas de
canto).

| Face | Abertura | Posição |
|---|---|---|
| Tampo | nenhuma | regra de derrame |
| Frontal | Janela de RF, 40 × 28 mm | x = 75 mm |
| Frontal | LED RGB, Ø 5 mm | x = 150 mm |
| Frontal | Botão, Ø 12 mm | x = 195 mm |
| Traseira | Prensa-cabo PG9, Ø 15 mm | x = 70 mm |
| Traseira | Tomada J1, 45,5 × 23 mm | x = 185 mm |
| Lateral esquerda | 4 rasgos de 25 × 3 mm | 15 mm da borda inferior |
| Lateral esquerda | Recorte do USB de painel | y = 165 mm da traseira |
| Lateral direita | 5 rasgos de 25 × 3 mm | 15 mm da borda inferior |

Centros das aberturas frontais e traseiras a 22 mm do tampo.

A assimetria de rasgos entre as laterais é proposital: o recorte do USB ocupa a
posição de um rasgo na esquerda. Não corrigir.

## Decisões específicas e seus motivos

**Tomada J1 girada 90°.** O módulo 2P+T do sistema modular mede 45 × 22,5 mm com
~41 mm de profundidade. Um recorte de 45 mm numa saia de 45 mm não deixa
material. Girado, o recorte fica com 23 mm de altura e sobram ~10 mm de chapa
acima e abaixo. O plugue NBR 14136 é de corpo redondo, então girar não muda nada
visualmente; só o cabo sai de lado.

**Prensa-cabo PG9 em vez de passa-fio ou conector C14.** O prensa-cabo entrega
alívio de tração normalizado, que é o item que a inspeção elétrica procura, e faz
o menor recorte dos três numa saia que já perdeu 45,5 × 23 mm para a tomada. O
C14 daria cabo destacável, mas abriria um segundo retângulo grande na mesma saia
e mudaria o componente P1 no esquemático.

**Porta-fusível em linha, de corpo fechado, preso por abraçadeira.** Nenhum furo
adicional em chapa nenhuma. Interno, portanto a troca exige abrir o fundo — razão
a mais para acertar o valor do fusível.

**ESP32 com eixo longo no sentido frente-fundo**, antena encostada na janela de
RF da saia frontal, com ~5 mm de folga e nenhum metal na frente dela. O USB fica
apontando para a divisória e chega à saia lateral por um cabo de painel. Esse
cabo é o que desacopla a posição do conector da posição da placa: sem ele, USB
acessível e Wi-Fi decente seriam mutuamente exclusivos, porque no DevKit V1 a
antena e o micro-USB ficam em extremidades opostas.

**Estratégia térmica: pés mais rasgos laterais.** A fonte de calor está em cima e
o topo é fechado, então não existe efeito chaminé. Os rasgos e os pés impedem que
a caixa vire um forno selado e deixam o fundo trocar calor com o vão de ar. O
gargalo real é a condução pelo contato do tampo com a base da cafeteira, e isso
depende de medição.

**Largura de 3 mm nos rasgos** bloqueia dedo, não bloqueia arame fino. Bornes com
tampa na faixa de rede deixam de ser recomendação e passam a ser obrigatórios.

**Lábio de contenção de derrame: descartado por geometria.** As quatro bordas do
tampo já estão ocupadas pelas dobras das saias; não sobra aresta livre para uma
aba para cima. Um degrau embutido exigiria repuxo com matriz, que é outro
processo. O tratamento do derrame fica sendo a ausência de aberturas no tampo.

## Parâmetros do modelo

Os quatro primeiros são placeholders arbitrados. São eles que o revisor da
empresa vai corrigir, e é por isso que toda a geometria precisa depender deles por
expressão, nunca por número digitado.

```
espessura         1.2 mm     arbitrado
raio_dobra        1.2 mm     arbitrado
fator_k           0.40       arbitrado (ANSI)
material          inox austenítico   a confirmar

pegada_x          260 mm
pegada_y          210 mm
altura_externa    45 mm
altura_livre      41.4 mm
aba_fundo         12 mm
aba_canto         20 mm
recuo_aba         espessura + 0.3
alivio_canto      raio_dobra + espessura

pe_altura         10 mm
apoio_tipo        trilho | coxim | nenhum
apoio_altura      0 .. 5 mm
elevacao_total    altura_externa + pe_altura + apoio_altura

faixa_rede        120 mm
divisoria         2 a 3 mm
faixa_baixa       84 mm

rasgo             25 x 3 mm
rasgo_altura      15 mm da borda inferior
rasgo_margem      >= 6 mm da dobra, >= 25 mm das extremidades

janela_rf         40 x 28 mm, x=75
furo_led          5 mm, x=150
furo_botao        12 mm, x=195
prensa_cabo       PG9, Ø15 mm, x=70
tomada_j1         45.5 x 23 mm, x=185
usb_recorte       paramétrico, y=165 na lateral esquerda
furo_fixacao      3.2 mm (M3)
furo_porca_rebite ~4.5 mm, a confirmar
```

## Divergências a corrigir fora deste documento

| Onde | O quê |
|---|---|
| Esquemático KiCad | Valor de F1 de 10 A para 8 A |
| Especificação §2.1 | "Base de aço" passa a pedestal em inox, com a cafeteira apoiada em cima |
| Especificação §4.1 | Fonte 5 V já resolvida (HLK-PM01); a tabela ainda dá as duas opções |
| Especificação §4.5 e §9 | Isolamento galvânico por optoacoplador é falso com o jumper JD-VCC; já registrado antes desta sessão |

## Lista de verificação

Nenhum item abaixo é decisão. São medições, consultas ou ensaios.

### Medição no componente, depois da compra

| # | Item |
|---|---|
| 1 | Fixação do módulo de tomada em chapa de 1,2 mm: as garras travam no aço, ou exigem o suporte plástico atrás? Alternativas: suporte parafusado, parafuso M3 nas abas do módulo, cantoneira de retenção |
| 2 | Pegada real da base da cafeteira. O catálogo dá 257 × 198 mm, que é caixa envolvente e inclui a alça da jarra |
| 2b | A cafeteira tem pés próprios? Quantos, que altura, em que posições |
| 2c | Posições dos pés, para alinhar coxins caso seja esse o caminho |
| 3 | Posição da placa aquecedora na base, para afastar o HLK-PM01 da projeção dela |
| 4 | Dimensões reais do DevKit V1 de 30 pinos e do módulo de relé. Os valores no modelo são nominais de mercado |
| 5 | Diâmetro real do botão de painel e do LED, contra os furos de 12 mm e 5 mm |
| 14 | Faixa de aperto do PG9 comprado contra o diâmetro externo real do cabo. Nominal 4 a 8 mm |
| 16 | Flange e recorte do conector de painel micro-USB. Variam bastante entre modelos |
| 17 | Diâmetro de furo da porca-rebite M3 para chapa de 1,2 mm. Nominal ~4,5 mm |

### Consulta à empresa

| # | Item |
|---|---|
| 6 | Espessura disponível, raio mínimo de dobra e fator K da ferramenta. São os três que invalidam a planificação se estiverem errados |
| 7 | Formato de arquivo para o revisor: STEP, DXF da planificação, desenho 2D cotado ou o FCStd |
| 8 | Rigidez: 1,2 mm com 2,9 kg num vão de 260 mm basta, já com a aba de retorno de 12 mm? Levar a saia frontal com os recortes desenhados, não a saia lisa |
| 9 | Disponibilidade de rebite ou parafuso de inox. Alumínio em inox forma par galvânico, e derrame de café é rotina aqui |

### Ensaio de bancada

| # | Item |
|---|---|
| 10 | Temperatura da base da cafeteira após um ciclo completo. Não existe publicado, dimensiona toda a estratégia térmica e vira resultado citável na seção 4 do artigo |
| 11 | Atenuação de Wi-Fi com o ESP32 na caixa e a cafeteira em cima, para validar a janela de RF |
| 12 | Reset do ESP32 no acionamento do relé, para validar C1 e C2 na faixa de baixa tensão |

### Componentes a especificar

| # | Item |
|---|---|
| 13 | Isolamento térmico sob o tampo. Depende do 10. Verificar classe de temperatura e de inflamabilidade; comparar espuma injetada contra placa pré-cortada sobre falso teto. Custo geométrico: falso teto como peça adicional e ~12 mm de altura interna |
| 15 | Porta-fusível em linha fechado, 250 V, 5 × 20 mm. Fusível 8 A cerâmico; verificar capacidade de interrupção na folha de dados. Vidro tem capacidade baixa demais para proteção de rede |

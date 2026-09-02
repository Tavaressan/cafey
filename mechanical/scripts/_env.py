"""Helpers compartilhados pelos scripts de modelagem do modulo fisico.

Sem dependencia do addon SheetMetal: as pecas sao solidos Part parametricos e a
planificacao e' analitica (ver unfold_peca1.py / flatten_peca1.py).
"""

import csv
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PARAMS_CSV = os.path.join(ROOT, "params", "parametros.csv")
BUILD_DIR = os.path.join(ROOT, "build")
FCSTD = os.path.join(BUILD_DIR, "peca1.FCStd")
STEP = os.path.join(BUILD_DIR, "peca1.step")
FCSTD2 = os.path.join(BUILD_DIR, "peca2.FCStd")
STEP2 = os.path.join(BUILD_DIR, "peca2.step")
MONTAGEM_FCSTD = os.path.join(BUILD_DIR, "montagem.FCStd")
MONTAGEM_STEP = os.path.join(BUILD_DIR, "montagem.step")
PLANO1_FCSTD = os.path.join(BUILD_DIR, "peca1_plano.FCStd")
DXF1 = os.path.join(BUILD_DIR, "peca1_plano.dxf")
DXF2 = os.path.join(BUILD_DIR, "peca2_plano.dxf")

# Expressoes das celulas derivadas (alias -> formula em termos de outros alias).
DERIVED = {
    "recuo_aba": "espessura + 0.3",
    "alivio_canto": "raio_dobra + espessura",
    "elevacao_total": "altura_externa + pe_altura + apoio_altura",
}

MATERIAL_TXT = "inox austenitico (a confirmar)"
DENSIDADE_INOX = 7.9e-9  # tonne/mm^3 (inox austenitico ~7900 kg/m3)


def load_params():
    """Le parametros.csv -> lista de (alias, value_float_or_None, unit, origin)."""
    rows = []
    with open(PARAMS_CSV, newline="") as fh:
        reader = csv.reader(fh)
        next(reader)  # header
        for r in reader:
            if not r or r[0].strip().startswith("#"):
                continue
            alias = r[0].strip()
            raw = r[1].strip() if len(r) > 1 else ""
            value = float(raw) if raw else None
            unit = r[2].strip() if len(r) > 2 else ""
            origin = r[3].strip() if len(r) > 3 else ""
            rows.append((alias, value, unit, origin))
    return rows


# Descricoes (viram Label legivel + Label2/comentario nos objetos do FCStd).
# Chave = nome do objeto ou prefixo (casado por startswith).
DESCRIPTIONS = {
    "params": ("Parametros do modelo",
               "Placeholders arbitrados (espessura, raio_dobra, fator_k, material); "
               "o revisor de dobra da empresa corrige. Toda a geometria depende "
               "destas celulas por expressao, nunca por numero digitado."),
    "planificacao": ("Planificacao analitica",
                     "Comprimento plano por direcao = soma dos trechos - 4 x bend "
                     "deduction (BD = 2*setback - BA). Sem geometria de unfold."),
    "tampo": ("Tampo", "A cafeteira apoia aqui. Sem aberturas (regra de derrame)."),
    "saia_frontal": ("Saia frontal (Y=0)",
                     "Face da baixa tensao: janela de RF, LED, botao. Dobrada para baixo."),
    "saia_traseira": ("Saia traseira (Y=pegada_y)",
                      "Face da rede: prensa-cabo PG9 e tomada J1. Dobrada para baixo."),
    "saia_esquerda": ("Saia lateral esquerda (X=0)",
                      "4 rasgos de ventilacao; o recorte do USB de painel ocupa a 5a posicao."),
    "saia_direita": ("Saia lateral direita (X=pegada_x)", "5 rasgos de ventilacao."),
    "aba_frontal": ("Aba de retorno - saia frontal",
                    "12 mm dobrada para dentro. Enrijece a saia e apoia o fundo removivel."),
    "aba_traseira": ("Aba de retorno - saia traseira", "12 mm dobrada para dentro."),
    "aba_esquerda": ("Aba de retorno - saia esquerda", "12 mm dobrada para dentro."),
    "aba_direita": ("Aba de retorno - saia direita", "12 mm dobrada para dentro."),
    "canto_fl": ("Aba de canto frontal-esquerda",
                 "Sai da saia frontal, sobrepoe a face interna da lateral. 2 x M3 por canto."),
    "canto_fr": ("Aba de canto frontal-direita", "Sobrepoe a face interna da lateral. 2 x M3."),
    "canto_tl": ("Aba de canto traseira-esquerda", "Sobrepoe a face interna da lateral. 2 x M3."),
    "canto_tr": ("Aba de canto traseira-direita", "Sobrepoe a face interna da lateral. 2 x M3."),
    "relief_": ("Alivio de canto",
                "Entalhe raio_dobra + espessura (~2,4 mm) no encontro das dobras verticais."),
    "janela_rf": ("Janela de RF 40 x 28 (x=75, z=-22)",
                  "SAIDA DE RADIO. Deixa a antena Wi-Fi do ESP32 (encostada por "
                  "dentro, ~5 mm de folga, sem metal na frente) enxergar para fora. "
                  "Nao e' acesso a componente. Tamanho: cobre a area da antena "
                  "PCB do DevKit V1 com margem. Valida no ensaio 11 (atenuacao)."),
    "furo_led": ("Furo do LED RGB Ø5 (x=150, z=-22)",
                 "SINALIZACAO. Passagem do LED RGB de status, montado na placa "
                 "auxiliar da faixa de baixa tensao. Ø5 nominal - conferir contra "
                 "o LED/porta-LED real comprado (item 5)."),
    "furo_botao": ("Furo do botao de painel Ø12 (x=195, z=-22)",
                   "COMANDO. Passagem do botao de painel (liga/desliga / pareamento). "
                   "Ø12 nominal - conferir contra o botao real (item 5)."),
    "prensa_cabo": ("Prensa-cabo PG9 Ø15 (x=70, z=-22)",
                    "ENTRADA DE ENERGIA DO MODULO (rede 127 V vinda da parede). "
                    "Recebe o prensa-cabo PG9, que da o alivio de tracao "
                    "normalizado que a inspecao eletrica procura. Escolhido em vez "
                    "de passa-fio ou C14: menor recorte numa saia que ja' perdeu a "
                    "tomada J1. Faixa de aperto 4-8 mm nominal vs cabo real (item 14)."),
    "tomada_j1": ("Recorte da tomada J1 45,5 x 23 (x=185, z=-22)",
                  "SAIDA DE ENERGIA. Recorte do modulo de tomada 2P+T (NBR 14136) "
                  "do sistema modular onde a CAFETEIRA e' plugada - o modulo comuta "
                  "essa tomada pelo rele. Girado 90 graus: 45 mm de recorte numa "
                  "saia de 45 mm nao deixaria material; girado sobra ~10 mm acima "
                  "e abaixo. Travamento das garras em chapa 1,2 mm a verificar (item 1)."),
    "rasgo_e_": ("Rasgo de ventilacao (lateral esquerda)",
                 "TERMICA. 25 x 3 mm, centro a 15 mm da borda inferior da saia. "
                 "Com os pes, impede a caixa de virar forno selado e deixa o fundo "
                 "trocar calor com o vao de ar (a fonte de calor esta em cima e o "
                 "topo e' fechado: sem efeito chamine). 3 mm bloqueia dedo mas nao "
                 "arame fino -> bornes com tampa na faixa de rede sao OBRIGATORIOS. "
                 "Esquerda tem 4: o recorte do USB ocupa a 5a posicao da grade."),
    "rasgo_d_": ("Rasgo de ventilacao (lateral direita)",
                 "TERMICA. 25 x 3 mm, centro a 15 mm da borda inferior. Mesma "
                 "funcao dos rasgos da esquerda. Direita tem os 5 da grade "
                 "(assimetria com a esquerda e' proposital - nao corrigir)."),
    "recorte_usb": ("Recorte do USB de painel (lateral esquerda, y=grade)",
                    "GRAVACAO / DEBUG. Passagem do conector USB de painel, ligado "
                    "ao micro-USB do ESP32 por um cabo de painel. Esse cabo "
                    "desacopla a posicao do conector da posicao da placa: no DevKit "
                    "V1 a antena e o micro-USB ficam em extremidades opostas, entao "
                    "sem ele USB acessivel e Wi-Fi decente seriam exclusivos. "
                    "Dimensao placeholder - medir flange do conector real (item 16)."),
    "fix_": ("Furo M3 de fixacao de canto (Ø3,2, eixo X)",
             "MONTAGEM DA CAIXA. 2 por canto, 8 no total. Prende a aba de canto "
             "(que sai da saia frontal/traseira) contra a face interna da lateral, "
             "fechando o corpo. M3 inox (premissa) ou rebite (alternativa "
             "parametrica) - aluminio em inox forma par galvanico e aqui derrame "
             "de cafe e' rotina (item 9). Nome: fix_<lado e|d><canto f|t>_<lo|hi>. "
             "Alturas 1/3 e 2/3 da saia - provisorias."),
    "pr_": ("Furo de porca-rebite M3 - aba do fundo (Ø~4,5, eixo Z)",
            "FIXACAO DO FUNDO (lado Peca 1). 8 no total, 2 em cada aba de retorno. "
            "Recebe a porca-rebite M3 onde os parafusos da Peca 2 entram. Nome: "
            "pr_<aba f|t|e|d><coluna a|b>. Ø de furo da porca-rebite para chapa "
            "1,2 mm: ~4,5 mm nominal, confirmar item 17."),
    # --- Peca 2 (fundo) e montagem ---
    "fundo": ("Peca 2 - fundo plano removivel (tampa por baixo)",
              "Chapa plana, sem nenhuma dobra, rente a' face externa das saias "
              "(pegada_x x pegada_y). Parafusada na FACE INFERIOR das abas de "
              "retorno da Peca 1 - assim sai por baixo para manutencao, sem ficar "
              "presa pelas abas viradas para dentro (um fundo maior que o vao "
              "entre as abas nao passaria). Protrai espessura_fundo abaixo da "
              "borda das saias; os pes montam nela. Abrir o fundo = trocar o "
              "fusivel e acessar todo o interior."),
    "fundo_furo_": ("Furo de passagem M3 do fundo (Ø3,2, eixo Z)",
                    "FIXACAO DO FUNDO (lado Peca 2). 8 no total, coincidentes com "
                    "as porcas-rebite pr_* das abas da Peca 1 (mesmas formulas em "
                    "_env.porca_rebite_holes; check_peca2.py confirma). O parafuso "
                    "entra POR BAIXO e rosqueia na porca-rebite da aba; por isso "
                    "aqui e' folga M3, nao o furo da porca-rebite. Cabeca panela "
                    "no vao de ar entre os pes."),
    "cant_furo_": ("Furo de cantoneira da divisoria (Ø3,2, eixo Z)",
                   "SEPARACAO ELETRICA. 2 furos na linha y = espessura + faixa_baixa. "
                   "Fixam as cantoneiras que seguram a divisoria (policarbonato ou "
                   "acrilico, altura plena) entre a faixa de rede (127 V, tras) e a "
                   "de baixa tensao (5 V, frente). Nenhum condutor de rede a menos "
                   "de 10 mm da divisoria - valida com o eletricista (marco 02/10)."),
    "peca2": ("Peca 2 - fundo plano",
              "Fundo removivel + 8 furos M3 + recortes de canto + furos das "
              "cantoneiras da divisoria."),
    "asm_peca1": ("Peca 1 (montagem)", "Peca dobrada na posicao de projeto."),
    "asm_peca2": ("Peca 2 (montagem)", "Fundo apoiado sobre as abas de retorno."),
    "corpo": ("Corpo (uniao antes dos recortes)",
              "Fusao de tampo + saias + abas + cantos."),
    "recortes": ("Recortes reunidos", "Fusao de todos os cortes para um unico Part::Cut."),
    "peca1": ("Peca 1 - dobrada",
              "Tampo + 4 saias de 45 mm + 4 abas de fundo de 12 mm + 4 abas de canto. "
              "8 dobras. Ordem de dobra: abas do fundo primeiro com a chapa plana, "
              "saias depois - a viradeira nao alcanca o interior de uma caixa de "
              "45 x 260 mm. Isso vai na nota do desenho."),
}

NOTAS_FABRICACAO = """\
Peca 1 - pedestal em aco inox (chapa dobrada 1,2 mm). NAO E' GEOMETRIA.

Ordem de dobra
  Abas do fundo primeiro, com a chapa ainda plana. Saias depois. Ao contrario,
  a viradeira nao alcanca o interior de uma caixa de 45 mm de profundidade por
  260 mm de largura.

Parametros de processo
  espessura, raio_dobra, fator_k e material sao placeholders arbitrados. Sao os
  quatro que o revisor da empresa corrige na planilha 'params'. Consulta 6 da
  lista de verificacao: espessura disponivel, raio minimo de dobra e fator K da
  ferramenta.

Planificacao
  ~365 x 315 mm (Spreadsheet 'planificacao'). Chapa bruta sugerida ~400 x 350 mm.

Fixacao
  Cantos: 2 parafusos M3 inox por canto, 8 no total (rebite e' alternativa).
  Fundo: 8 x M3 em porca-rebite nas abas do fundo.
  Aluminio em inox forma par galvanico e derrame de cafe e' rotina - consulta 9.

Porta-fusivel
  Em linha, corpo fechado, preso por abracadeira. Nenhum furo adicional em chapa.

Sem raios de dobra no solido (arestas vivas). Cosmetico para o STEP; a linha
neutra real esta na planificacao analitica.
"""

NOTAS_FUROS_P1 = """\
CATALOGO DE FUROS E ABERTURAS - PECA 1. Centro Z das aberturas de saia a 22 mm
abaixo do tampo. x medido de X=0 (lateral esquerda). Ver tambem mechanical/FUROS.md.

ABERTURAS DE FACE
  janela_rf     retangulo 40x28   saia frontal  x=75    Janela de radio para a
                antena Wi-Fi do ESP32 (encostada por dentro, ~5 mm, sem metal na
                frente). Nao e' acesso a componente. Ensaio 11.
  furo_led      Ø5                saia frontal  x=150   Passagem do LED RGB de
                status. Conferir Ø real (item 5).
  furo_botao    Ø12               saia frontal  x=195   Passagem do botao de
                painel. Conferir Ø real (item 5).
  prensa_cabo   Ø15               saia traseira x=70    ENTRADA DE ENERGIA DO
                MODULO. Prensa-cabo PG9, alivio de tracao normalizado. Faixa de
                aperto 4-8 mm vs cabo real (item 14).
  tomada_j1     retangulo 45,5x23 saia traseira x=185   SAIDA. Modulo de tomada
                2P+T onde a cafeteira e' plugada; comutada pelo rele. Girado 90
                graus. Travamento em chapa 1,2 mm a verificar (item 1).
  recorte_usb   ~16x9 (placeholder) lateral esq  grade  Conector USB de painel
                para gravar/depurar o ESP32, via cabo de painel. Medir flange
                real (item 16).

RASGOS DE VENTILACAO (termica; 25x3 mm, centro 15 mm acima da borda inferior)
  rasgo_e_1..4  lateral esquerda   4 unidades (USB ocupa a 5a posicao da grade)
  rasgo_d_0..4  lateral direita    5 unidades
  3 mm bloqueia dedo, nao arame -> bornes com tampa na faixa de rede obrigatorios.

FURACAO
  fix_<e|d><f|t>_<lo|hi>  Ø3,2 (M3) eixo X   8 furos, 2 por canto. Prendem a aba
                de canto contra a face interna da lateral, fechando o corpo.
                Parafuso M3 inox ou rebite (item 9). Alturas 1/3 e 2/3 - provisorias.
  pr_<f|t|e|d><a|b>       Ø~4,5 eixo Z        8 furos nas abas de fundo. Recebem
                as porcas-rebite M3 onde a Peca 2 parafusa. Confirmar Ø (item 17).

SEM FURO
  Tampo: nenhuma abertura (regra de derrame).
  Fusivel: porta-fusivel em linha preso por abracadeira - troca abrindo o fundo.
  Travessia de 5 fios (HLK +5V/GND + rele VCC/GND/IN): rasgo na DIVISORIA de
  policarbonato, nao na chapa. A divisoria nao esta modelada.
"""

NOTAS_FUROS_P2 = """\
CATALOGO DE FUROS - PECA 2 (fundo, tampa por baixo). Ver tambem mechanical/FUROS.md.

O fundo e' parafusado na FACE INFERIOR das abas de retorno da Peca 1 e sai por
baixo. Rente a' face externa das saias (pegada_x x pegada_y); protrai
espessura_fundo abaixo da borda das saias.

  fundo_furo_1..8   Ø3,2 (M3) eixo Z   FIXACAO DO FUNDO. Furo de passagem: o
                    parafuso entra por baixo e rosqueia na porca-rebite pr_* da
                    aba da Peca 1 (_env.porca_rebite_holes; check_peca2.py
                    confirma a coincidencia). Cabeca panela no vao entre os pes.
  cant_furo_1..2    Ø3,2 (M3) eixo Z   Fixam as cantoneiras da divisoria
                    (policarbonato/acrilico, altura plena) na linha
                    y = espessura + faixa_baixa, entre a faixa de rede e a de
                    baixa tensao.
"""


def apply_descriptions(doc):
    """Escreve Label legivel + Label2 (comentario) nos objetos do documento."""
    for obj in doc.Objects:
        for key, (label, desc) in DESCRIPTIONS.items():
            exato = obj.Name == key
            if exato or (key.endswith("_") and obj.Name.startswith(key)):
                # multi-instancia (match por prefixo): sufixa o Name para o Label
                # ficar unico e rastreavel no arvore.
                obj.Label = label if exato else "%s [%s]" % (label, obj.Name)
                try:
                    obj.Label2 = desc
                except AttributeError:
                    pass
                break


def bend_allowance(raio_dobra, fator_k, espessura, angulo_deg=90.0):
    """BA de uma dobra. Linha neutra deslocada por fator K."""
    return math.radians(angulo_deg) * (raio_dobra + fator_k * espessura)


def setback(raio_dobra, espessura, angulo_deg=90.0):
    """Setback (OSSB) para calcular comprimento plano a partir de cotas externas."""
    return math.tan(math.radians(angulo_deg) / 2.0) * (raio_dobra + espessura)


def params_dict():
    """Alias -> valor numerico, incluindo as celulas derivadas (DERIVED)."""
    g = {a: v for a, v, _, _ in load_params() if v is not None}
    for alias, formula in DERIVED.items():
        g[alias] = eval(formula, {"__builtins__": {}}, g)  # formulas fixas do modulo
    return g


def porca_rebite_holes(g):
    """8 centros (x, y) dos furos de porca-rebite nas abas do fundo da Peca 1.

    Espelha as formulas de build_peca1.py (passo 10). A Peca 2 e a validacao
    consomem esta mesma funcao para garantir coincidencia.
    """
    t, af = g["espessura"], g["aba_fundo"]
    xs = [g["pegada_x"] / 3.0, 2.0 * g["pegada_x"] / 3.0]
    ys = [g["pegada_y"] / 3.0, 2.0 * g["pegada_y"] / 3.0]
    pts = []
    for x in xs:  # abas frontal e traseira (furos distribuidos em X)
        pts.append((x, t + af / 2.0))
        pts.append((x, g["pegada_y"] - t - af / 2.0))
    for y in ys:  # abas esquerda e direita (furos distribuidos em Y)
        pts.append((t + af / 2.0, y))
        pts.append((g["pegada_x"] - t - af / 2.0, y))
    return pts


def flat_segments(g):
    """Segmentos do blank plano da Peca 1 nas direcoes X e Y (cotas nominais).

    Ordem por direcao: aba - sb | BA | saia - 2sb | BA | tampo - 2sb | BA |
    saia - 2sb | BA | aba - sb. Devolve dict com cumX, cumY (limites
    acumulados) e os indices notaveis.
    """
    ba = bend_allowance(g["raio_dobra"], g["fator_k"], g["espessura"])
    sb = setback(g["raio_dobra"], g["espessura"])
    saia, aba = g["altura_externa"], g["aba_fundo"]

    def cum(centro):
        segs = [aba - sb, ba, saia - 2 * sb, ba, centro - 2 * sb,
                ba, saia - 2 * sb, ba, aba - sb]
        out, acc = [0.0], 0.0
        for s in segs:
            acc += s
            out.append(acc)
        return out

    return {
        "ba": ba, "sb": sb,
        "cumX": cum(g["pegada_x"]),
        "cumY": cum(g["pegada_y"]),
        # bandas: tampo = [4,5]; saia frontal/esq = [2,3]; saia traseira/dir = [6,7]
    }

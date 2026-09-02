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
    "janela_rf": ("Janela de RF 40 x 28 (x=75)",
                  "Antena do ESP32 a ~5 mm, sem metal na frente. Valida no ensaio 11."),
    "furo_led": ("Furo do LED RGB Ø5 (x=150)", "Centro a 22 mm do tampo."),
    "furo_botao": ("Furo do botao de painel Ø12 (x=195)", "Centro a 22 mm do tampo."),
    "prensa_cabo": ("Prensa-cabo PG9 Ø15 (x=70)",
                    "Alivio de tracao normalizado; menor recorte na saia da rede."),
    "tomada_j1": ("Recorte da tomada J1 45,5 x 23 (x=185)",
                  "Modulo 2P+T girado 90 graus: 45 mm numa saia de 45 nao deixa material."),
    "rasgo_e_": ("Rasgo de ventilacao (esquerda)",
                 "25 x 3 mm, 15 mm da borda inferior. 3 mm bloqueia dedo, nao arame: "
                 "bornes com tampa na faixa de rede sao obrigatorios."),
    "rasgo_d_": ("Rasgo de ventilacao (direita)",
                 "25 x 3 mm, 15 mm da borda inferior."),
    "recorte_usb": ("Recorte do USB de painel (lateral esquerda)",
                    "Placeholder - medir flange do conector (item 16). Ocupa a posicao "
                    "de um rasgo; assimetria proposital, nao corrigir."),
    "fix_": ("Furo M3 de fixacao de canto",
             "2 por canto, 8 no total. Parafuso inox (premissa); rebite e' alternativa "
             "parametrica. Posicao a 1/3 e 2/3 da altura da saia - provisoria."),
    "pr_": ("Furo de porca-rebite M3 - aba do fundo",
            "8 no total. Furo nominal ~4,5 mm para chapa de 1,2 mm - confirmar item 17."),
    # --- Peca 2 (fundo) e montagem ---
    "fundo": ("Peca 2 - fundo plano removivel",
              "Chapa plana, sem nenhuma dobra. Apoia sobre as quatro abas de "
              "retorno da Peca 1. Troca da so' abrindo o fundo - razao a mais "
              "para acertar o valor do fusivel."),
    "notch_": ("Recorte de canto do fundo",
               "Alivio para a aba de canto da Peca 1, que desce pelo interior."),
    "fundo_furo_": ("Furo M3 do fundo",
                    "8 no total, coincidentes com as porcas-rebite das abas da Peca 1."),
    "cant_furo_": ("Furo de cantoneira da divisoria",
                   "Fixa a cantoneira que segura a divisoria (policarbonato/acrilico, "
                   "altura plena) entre a faixa de rede e a de baixa tensao."),
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


def apply_descriptions(doc):
    """Escreve Label legivel + Label2 (comentario) nos objetos do documento."""
    for obj in doc.Objects:
        for key, (label, desc) in DESCRIPTIONS.items():
            if obj.Name == key or (key.endswith("_") and obj.Name.startswith(key)):
                obj.Label = label
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

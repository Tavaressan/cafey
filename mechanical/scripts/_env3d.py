"""Helpers compartilhados pelos scripts de modelagem da variante 3D (FDM).

Peca unica impressa (tampo + 4 paredes continuas, sem dobra) mais fundo plano
removivel. Fixacao Peca1/Peca2 por insert termico rosqueado M3, em vez da
porca-rebite da chapa dobrada. Reusa os mesmos helpers de geometria de
_build.py (box/cyl) e o mesmo padrao de Spreadsheet parametrico de _env.py -
so os aliases e os arquivos de saida mudam.
"""

import csv
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PARAMS_CSV = os.path.join(ROOT, "params", "parametros_3d.csv")
COMPONENTES_CSV = os.path.join(ROOT, "params", "componentes_3d.csv")
BUILD_DIR = os.path.join(ROOT, "build")

FCSTD = os.path.join(BUILD_DIR, "peca1_3d.FCStd")
STEP = os.path.join(BUILD_DIR, "peca1_3d.step")
STL = os.path.join(BUILD_DIR, "peca1_3d.stl")
FCSTD2 = os.path.join(BUILD_DIR, "peca2_3d.FCStd")
STEP2 = os.path.join(BUILD_DIR, "peca2_3d.step")
STL2 = os.path.join(BUILD_DIR, "peca2_3d.stl")
ARRANJO_FCSTD = os.path.join(BUILD_DIR, "arranjo_3d.FCStd")
ARRANJO_STEP = os.path.join(BUILD_DIR, "arranjo_3d.step")

# Expressoes das celulas derivadas (alias -> formula em termos de outros alias).
DERIVED = {
    "elevacao_total": "altura_externa + pe_altura + apoio_altura",
}

# Premissa de material assumida nesta issue (#116): PETG. Motivo documentado em
# docs/status-modulo-fisico.md - resiste melhor que PLA ao calor irradiado pela
# base da cafeteira (>= ~70 degC de HDT) e nao e' tao higroscopico/dificil de
# imprimir quanto ABS. NAO validado por ensaio (item 10 da lista de verificacao
# segue pendente); se o ensaio de temperatura mostrar valor mais alto, reavaliar
# para ABS ou PC.
MATERIAL_TXT = "PETG (premissa desta issue; a confirmar com o ensaio de temperatura #10)"
DENSIDADE_PETG = 1.27e-9  # tonne/mm^3 (PETG solido ~1270 kg/m3; a peca impressa
# tem infill parcial, entao a massa real fica ABAIXO da estimativa por este
# numero - ver nota em check_peca1_3d.py)


def load_params():
    """Le parametros_3d.csv -> lista de (alias, value_float_or_None, unit, origin)."""
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


DESCRIPTIONS = {
    "params": ("Parametros do modelo (impressao 3D)",
               "parede, aba_fundo e a fixacao por insert sao as celulas que um "
               "slicer/prototipo corrige. Toda a geometria depende destas "
               "celulas por expressao, nunca por numero digitado."),
    "tampo": ("Tampo", "A cafeteira apoia aqui. Sem aberturas (regra de derrame)."),
    "parede_frontal": ("Parede frontal (Y=0)",
                       "Face da baixa tensao: janela de RF, LED, botao."),
    "parede_traseira": ("Parede traseira (Y=pegada_y)",
                        "Face da rede: prensa-cabo PG9 e tomada J1."),
    "parede_esquerda": ("Parede lateral esquerda (X=0)",
                        "4 rasgos de ventilacao; o recorte do USB de painel ocupa a 5a posicao."),
    "parede_direita": ("Parede lateral direita (X=pegada_x)", "5 rasgos de ventilacao."),
    "flange_frontal": ("Flange de fixacao - frontal",
                       "Continua com as paredes (peca unica impressa); apoia o "
                       "fundo removivel e aloja os bosses de insert M3."),
    "flange_traseira": ("Flange de fixacao - traseira", "Ver flange_frontal."),
    "flange_esquerda": ("Flange de fixacao - esquerda", "Ver flange_frontal."),
    "flange_direita": ("Flange de fixacao - direita", "Ver flange_frontal."),
    "nervura_perimetral_": ("Nervura perimetral sob o tampo",
                           "Moldura de nervura_h x nervura_w pendurada na face "
                           "inferior do tampo, junto as paredes. Enrijece a "
                           "borda do tampo e o topo das paredes. Imprime sem "
                           "suporte (peca de cabeca p/ baixo: a nervura cresce "
                           "a partir do tampo)."),
    "nervura_transversal": ("Nervura transversal (linha da divisoria)",
                       "Nervura em y = parede + faixa_baixa, dividindo o "
                       "vao do tampo em dois paineis apoiados - reduz a "
                       "flecha de longo prazo do tampo sob 3 kg. Rasgo "
                       "central p/ a travessia de 5 fios."),
    "coluna_": ("Coluna de canto (caminho de carga tampo->fundo)",
                "Prisma coluna_lado x coluna_lado do tampo ate o plano do "
                "fundo. Leva a carga vertical direto aos pes e trava o corpo "
                "contra racking. 4 colunas (coluna_qtd); some com coluna_qtd=0."),
    "boss_": ("Boss de insert termico M3",
             "Reforco cilindrico local na flange, ate insert_prof de altura, "
             "onde o insert termico M3 e' fundido a quente depois da impressao. "
             "8 bosses, mesmas posicoes da porca-rebite da versao em chapa."),
    "insert_": ("Furo do insert termico M3 (cego, pela face inferior)",
               "Aloja o insert termico M3x5.7 fundido a quente no boss "
               "correspondente. O parafuso da Peca 2 entra por baixo e rosqueia "
               "aqui - mesma logica de montagem da porca-rebite da chapa, so' "
               "a peca fixadora muda."),
    "janela_rf": ("Janela de RF 40 x 28 (x=75, z=-22)",
                  "SAIDA DE RADIO. Deixa a antena Wi-Fi do ESP32 (encostada por "
                  "dentro, ~5 mm de folga, sem metal/impressao na frente) "
                  "enxergar para fora. Nao e' acesso a componente. Valida no "
                  "ensaio 11 (atenuacao)."),
    "furo_led": ("Furo do LED RGB Ø5 (x=150, z=-22)",
                 "SINALIZACAO. Passagem do LED RGB de status. Conferir Ø real "
                 "contra o LED/porta-LED comprado (item 5)."),
    "furo_botao": ("Furo do botao de painel Ø12 (x=195, z=-22)",
                   "COMANDO. Passagem do botao de painel. Conferir Ø real (item 5)."),
    "prensa_cabo": ("Prensa-cabo PG9 Ø15 (x=70, z=-22)",
                    "ENTRADA DE ENERGIA DO MODULO (rede 127 V vinda da parede). "
                    "Recebe o prensa-cabo PG9. Faixa de aperto 4-8 mm nominal "
                    "vs cabo real (item 14)."),
    "tomada_j1": ("Recorte da tomada J1 45,5 x 23 (x=185, z=-22)",
                  "SAIDA DE ENERGIA. Recorte do modulo de tomada 2P+T (NBR "
                  "14136) onde a CAFETEIRA e' plugada - comutada pelo rele. "
                  "Girado 90 graus, mesma razao da versao em chapa."),
    "rasgo_e_": ("Rasgo de ventilacao (lateral esquerda)",
                 "TERMICA. 25 x 3 mm, centro a 15 mm da borda inferior da "
                 "parede. Esquerda tem 4: o recorte do USB ocupa a 5a posicao."),
    "rasgo_d_": ("Rasgo de ventilacao (lateral direita)",
                 "TERMICA. 25 x 3 mm, centro a 15 mm da borda inferior. "
                 "Direita tem os 5 da grade (assimetria proposital)."),
    "recorte_usb": ("Recorte do USB de painel (lateral esquerda, y=grade)",
                    "GRAVACAO / DEBUG. Passagem do conector USB de painel, "
                    "ligado ao micro-USB do ESP32 por cabo de painel. "
                    "Dimensao placeholder - medir flange real (item 16)."),
    "peca1": ("Peca 1 - pedestal impresso (FDM)",
             "Tampo + 4 paredes + flange de fixacao continua, uma peca unica. "
             "Substitui as 8 dobras da versao em chapa: sem linha neutra, sem "
             "planificacao, sem abas de canto (a peca ja' nasce fechada nos "
             "4 cantos)."),
    "fundo": ("Peca 2 - fundo plano removivel (tampa por baixo)",
              "Placa plana impressa, parede_fundo de espessura, rente a face "
              "externa das paredes (pegada_x x pegada_y). Parafusada na face "
              "inferior da flange da Peca 1, mesma logica de montagem/desmontagem "
              "da versao em chapa (sai por baixo p/ manutencao)."),
    "fundo_furo_": ("Furo de passagem M3 do fundo (Ø furo_passagem, eixo Z)",
                    "FIXACAO DO FUNDO (lado Peca 2). 8 no total, coincidentes "
                    "com os bosses/inserts da Peca 1. O parafuso entra por "
                    "baixo e rosqueia no insert."),
    "pe_": ("Assento de pe (Ø pe_d, face inferior do fundo)",
            "Rebaixo raso p/ o pe adesivo EPDM/silicone, nos 4 cantos, "
            "alinhado com a coluna de canto da Peca 1 - fecha o caminho de "
            "carga tampo -> coluna -> fundo -> pe -> bancada."),
    "cant_furo_": ("Furo de cantoneira da divisoria (Ø furo_passagem, eixo Z)",
                   "SEPARACAO ELETRICA. 2 furos na linha y = parede + "
                   "faixa_baixa. Fixam as cantoneiras que seguram a divisoria "
                   "entre a faixa de rede (127 V, tras) e a de baixa tensao "
                   "(5 V, frente). Nenhum condutor de rede a menos de 10 mm da "
                   "divisoria - valida com o eletricista (marco 02/10)."),
    "peca2": ("Peca 2 - fundo plano impresso",
              "Fundo removivel + 8 furos M3 de passagem + furos das "
              "cantoneiras da divisoria."),
}

NOTAS_FABRICACAO = """\
Peca 1 - pedestal impresso em FDM (PETG, premissa - ver DESCRICOES/status-modulo-fisico.md).
NAO E' GEOMETRIA.

Por que mudou (issue #116)
  A versao anterior era chapa de inox dobrada (8 dobras, revisor de dobra
  externo). Esta versao e' um solido unico impresso: sem fator K, sem linha
  neutra, sem planificacao. Corners nascem fechados - nao ha' mais abas de
  canto nem parafuso de canto (fix_*) prendendo a caixa.

Parede
  2,4 mm (3 perimetros de bico 0,4 mm), contra 1,2 mm da chapa. FDM em parede
  fina isolada (1,2 mm) tende a delaminar/flexionar sob a carga de ~2,9 kg do
  conjunto cafeteira + agua; 2,4 mm e' o minimo pratico recomendado para uma
  parede que atua como estrutura primaria (nao so' involucro).

Fixacao Peca1/Peca2
  Insert termico rosqueado M3 (Ø furo insert_furo, profundidade insert_prof),
  fundido a quente em 8 bosses da flange apos a impressao. Escolhido em vez de
  parafuso auto-atarraxante direto no PETG: o auto-atarraxante degrada a rosca
  em poucas desmontagens (o fundo abre para trocar o fusivel - item 15), o
  insert nao. Mesma logica de montagem da porca-rebite da chapa: parafuso
  entra por baixo, na Peca 2, e rosqueia no insert da Peca 1.

Reforco estrutural sob o tampo (audit da carga de 3 kg)
  O modelo inicial (#118) so' checava a flecha ELASTICA sob 2,9 kg e passava
  raspando (0,57 mm vs limite 0,70 mm). Faltava a fluencia (creep): PETG sob
  carga continua + calor irradiado deforma 3-4x a flecha elastica ao longo de
  meses. Com fator_fluencia = 4 e a carga de projeto de 3,0 kgf, o tampo LISO
  estouraria o limite L/300. Reforco adicionado, todo parametrico:
    - 4 colunas de canto (coluna_qtd) do tampo ao plano do fundo: caminho de
      carga vertical direto aos pes, e travam o corpo contra racking.
    - nervura perimetral sob o tampo: enrijece a borda e o topo das paredes.
    - nervura transversal (nervura_transversal) na linha da divisoria:
      divide o vao do tampo em dois paineis -> flecha cai ~10x.
  coluna_qtd=0 e nervura_transversal=0 voltam ao modelo do #118 (nesse caso
  rebaixar altura_externa de 53 p/ 45). Memoria de calculo em
  check_peca1_3d.py. ESTIMATIVA de engenharia, nao ensaio - o ensaio de
  bancada (item 8 adaptado) com a peca impressa e a carga real e' o que
  confirma; a fluencia so' aparece num teste de semanas.

Ordem de impressao: peca de cabeca p/ baixo (tampo na mesa, 1a camada). As
nervuras e colunas crescem a partir do tampo -> imprimem sem suporte. A
flange na base fica por ultimo (ponte de aba_fundo mm p/ dentro) - chanfro
45deg da flange fica como melhoria se o ensaio de ponte reprovar.

Sem raios de dobra: nao se aplica (peca unica). Fillet dos cantos verticais
fica como melhoria futura (YAGNI - nenhum requisito atual exige).
"""

NOTAS_FUROS_P1 = """\
CATALOGO DE FUROS E ABERTURAS - PECA 1 (impressa). Centro Z das aberturas de
parede a 22 mm abaixo do tampo. x medido de X=0 (lateral esquerda). Cobertura
1:1 com o catalogo da chapa dobrada em mechanical/FUROS.md (aba_canto e
fix_* nao existem mais - a peca ja' nasce fechada nos 4 cantos).

ABERTURAS DE FACE
  janela_rf     retangulo 40x28   parede frontal  x=75    janela de radio
  furo_led      Ø5                parede frontal  x=150   LED RGB de status
  furo_botao    Ø12               parede frontal  x=195   botao de painel
  prensa_cabo   Ø15               parede traseira x=70    entrada de energia
  tomada_j1     retangulo 45,5x23 parede traseira x=185   saida de energia
  recorte_usb   ~16x9 (placeholder) parede esq   grade    USB de painel

RASGOS DE VENTILACAO (25x3 mm, centro 15 mm acima da borda inferior)
  rasgo_e_1..4  parede esquerda   4 unidades (USB ocupa a 5a posicao da grade)
  rasgo_d_0..4  parede direita    5 unidades

FIXACAO (novo - substitui fix_* da chapa)
  boss_<f|t|e|d><a|b>    Ø boss_d       8 bosses na flange, insert M3 fundido
  insert_<f|t|e|d><a|b>  Ø insert_furo  furo cego, profundidade insert_prof

SEM FURO
  Tampo: nenhuma abertura (regra de derrame).
  Fusivel: porta-fusivel em linha preso por abracadeira - troca abrindo o fundo.
  Travessia de 5 fios: rasgo na DIVISORIA de policarbonato, nao na parede
  impressa. A divisoria nao esta modelada.
"""

NOTAS_FUROS_P2 = """\
CATALOGO DE FUROS - PECA 2 (fundo impresso, tampa por baixo).

  fundo_furo_1..8   Ø furo_passagem, eixo Z   FIXACAO DO FUNDO. Furo de
                    passagem: o parafuso entra por baixo e rosqueia no insert
                    da Peca 1 (mesmas posicoes de _env3d.insert_holes;
                    check_peca2_3d.py confirma a coincidencia).
  cant_furo_1..2    Ø furo_passagem, eixo Z   Fixam as cantoneiras da
                    divisoria na linha y = parede + faixa_baixa.
"""


def apply_descriptions(doc):
    for obj in doc.Objects:
        for key, (label, desc) in DESCRIPTIONS.items():
            exato = obj.Name == key
            if exato or (key.endswith("_") and obj.Name.startswith(key)):
                obj.Label = label if exato else "%s [%s]" % (label, obj.Name)
                try:
                    obj.Label2 = desc
                except AttributeError:
                    pass
                break


def load_componentes():
    """Le componentes.csv (mesmo arranjo interno da versao em chapa)."""
    out = []
    with open(COMPONENTES_CSV, newline="") as fh:
        reader = csv.DictReader(fh)
        for r in reader:
            if not r.get("nome") or r["nome"].strip().startswith("#"):
                continue
            for k in ("dx", "dy", "dz", "x", "y", "z"):
                r[k] = float(r[k])
            r["nome"] = r["nome"].strip()
            out.append(r)
    return out


def params_dict():
    g = {a: v for a, v, _, _ in load_params() if v is not None}
    for alias, formula in DERIVED.items():
        g[alias] = eval(formula, {"__builtins__": {}}, g)
    return g


def insert_holes(g):
    """8 centros (x, y) dos bosses/inserts M3 na flange da Peca 1.

    Mesmo layout da porca_rebite_holes da versao em chapa (thirds de
    pegada_x/pegada_y). A Peca 2 e a validacao consomem esta mesma funcao
    para garantir coincidencia.
    """
    t, af = g["parede"], g["aba_fundo"]
    xs = [g["pegada_x"] / 3.0, 2.0 * g["pegada_x"] / 3.0]
    ys = [g["pegada_y"] / 3.0, 2.0 * g["pegada_y"] / 3.0]
    pts = []
    for x in xs:
        pts.append((x, t + af / 2.0))
        pts.append((x, g["pegada_y"] - t - af / 2.0))
    for y in ys:
        pts.append((t + af / 2.0, y))
        pts.append((g["pegada_x"] - t - af / 2.0, y))
    return pts


def build_spreadsheet(doc):
    """Cria o Spreadsheet `params` a partir de parametros_3d.csv."""
    sheet = doc.addObject("Spreadsheet::Sheet", "params")
    sheet.Label = "params"
    row = 1
    sheet.set("A%d" % row, "material")
    sheet.set("B%d" % row, MATERIAL_TXT)
    sheet.setAlias("B%d" % row, "material_txt")
    row += 1
    for alias, value, _unit, origin in load_params():
        a, b, c = "A%d" % row, "B%d" % row, "C%d" % row
        sheet.set(a, alias)
        if alias in DERIVED:
            sheet.set(b, "=" + DERIVED[alias])
        else:
            sheet.set(b, repr(value))
        sheet.setAlias(b, alias)
        if origin:
            sheet.set(c, origin)
        row += 1
    doc.recompute()
    return sheet


def coluna_positions(g):
    """4 centros (x, y) das colunas de canto (dentro das paredes)."""
    m = g["parede"] + g["coluna_lado"] / 2.0
    return [(m, m), (g["pegada_x"] - m, m),
            (m, g["pegada_y"] - m), (g["pegada_x"] - m, g["pegada_y"] - m)]


def pe_positions(g):
    """4 centros (x, y) dos assentos de pe no fundo.

    Coaxiais com as colunas de canto quando elas existem (caminho de carga
    fechado); senao recuados pe_inset dos cantos.
    """
    if g.get("coluna_qtd", 0) >= 4:
        return coluna_positions(g)
    m = g["pe_inset"]
    return [(m, m), (g["pegada_x"] - m, m),
            (m, g["pegada_y"] - m), (g["pegada_x"] - m, g["pegada_y"] - m)]


def y_nervura_transversal(g):
    """Linha Y da nervura transversal = plano da divisoria."""
    return g["parede"] + g["faixa_baixa"]


def vao_menor_tampo_mm(g, com_reforco=True):
    """Menor vao do maior painel do tampo.

    Sem reforco: o menor lado da pegada (260 x 210 -> 210). Com a nervura
    transversal na linha da divisoria, o tampo vira dois paineis apoiados;
    governa o mais fundo (traseiro, faixa da rede).
    """
    if com_reforco and g.get("nervura_transversal"):
        yd = y_nervura_transversal(g)
        frontal = yd - g["parede"]
        traseiro = (g["pegada_y"] - g["parede"]) - (yd + g["nervura_w"])
        return max(frontal, traseiro)
    return min(g["pegada_x"], g["pegada_y"])


def _flecha_elastica_mm(g, b, massa_kgf, e_gpa=1.8):
    """Flecha elastica de placa ret. simplesmente apoiada, carga uniforme.

        w_max = alpha * q * b^4 / (E * t^3)     (Roark's Formulas for Stress
        and Strain, placa ret. simpl. apoiada)

    alpha ~ 0.0140 cobre de a/b ~ 1.24 (260x210 sem reforco) ate a/b > 2
    (paineis estreitos com reforco; alpha satura em ~0.0142). Apoio simples
    e' o pior caso realista - o encaixe com as paredes da' mais engaste, o
    que reduziria a flecha.
    """
    q = massa_kgf * 9.81 / (g["pegada_x"] * g["pegada_y"])  # N/mm^2
    e_mpa = e_gpa * 1000.0  # GPa -> MPa (N/mm^2); PETG ~ 1.8-2.2 GPa
    t = g["parede"]
    alpha = 0.0140
    return alpha * q * (b ** 4) / (e_mpa * (t ** 3))


def deflexao_tampo_mm(g, com_reforco=True):
    """Flecha de LONGO PRAZO do tampo sob a carga de operacao.

    = flecha elastica (formula de placa) x fator_fluencia. O fator de fluencia
    (creep viscoelastico do PETG sob carga continua + calor irradiado pela
    base da cafeteira) e' o que transforma uma flecha elastica pequena numa
    deformacao permanente relevante ao longo de meses - e' por isso que o
    modelo do #118 (so' elastico, so' 2.9 kg) subestimava o problema.

    ESTIMATIVA DE ENGENHARIA, nao ensaio (item 8/10 da lista de verificacao).
    """
    b = vao_menor_tampo_mm(g, com_reforco)
    return _flecha_elastica_mm(g, b, g["carga_operacao"]) * g["fator_fluencia"]

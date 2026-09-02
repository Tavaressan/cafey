"""Validacao do arranjo interno (arranjo.FCStd).

Confere as faixas, a altura livre, a separacao rede/baixa tensao e conflitos
grosseiros entre os volumes de referencia. Todos os tamanhos sao ESTIMADOS;
esta checagem so' faz sentido depois de o usuario confirmar o CSV.

Rode build_peca1.py, build_peca2.py e build_arranjo.py antes.
Uso: freecadcmd mechanical/scripts/check_arranjo.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App

falhas = []
avisos = []


def check(cond, msg, hard=True):
    tag = "  OK  " if cond else ("FALHA " if hard else "AVISO ")
    print(tag + msg)
    if not cond:
        (falhas if hard else avisos).append(msg)


def main():
    g = _env.params_dict()
    comps = {c["nome"]: c for c in _env.load_componentes()}
    d = App.openDocument(_env.ARRANJO_FCSTD)
    d1 = App.openDocument(_env.FCSTD)
    peca1 = d1.getObject("peca1").Shape

    piso = -g["altura_externa"]
    teto = -g["espessura"]
    div = comps["divisoria"]
    div_frente = div["y"]
    div_tras = div["y"] + div["dy"]
    GAP_REDE = 10.0

    tallest = piso
    for nome, c in comps.items():
        o = d.getObject(nome)
        b = o.Shape.BoundBox
        tallest = max(tallest, b.ZMax)

        # envelope vertical (todos)
        check(b.ZMin >= piso - 0.05 and b.ZMax <= teto + 0.05,
              "%s: Z [%.1f, %.1f] dentro de [%.1f, %.1f]"
              % (nome, b.ZMin, b.ZMax, piso, teto))

        if c["apoio"] != "parede":
            # dentro das paredes em planta
            check(b.XMin >= g["espessura"] - 0.05 and b.XMax <= g["pegada_x"] - g["espessura"] + 0.05
                  and b.YMin >= g["espessura"] - 0.05 and b.YMax <= g["pegada_y"] - g["espessura"] + 0.05,
                  "%s: dentro das paredes em planta" % nome)
            # nao penetra a Peca 1 (abas / saias / tampo)
            inter = o.Shape.common(peca1).Volume
            check(inter < 1.0, "%s: sem penetrar a chapa (%.2f mm3)" % (nome, inter))

        # separacao de faixa
        if c["faixa"] == "rede":
            check(b.YMin >= div_tras + GAP_REDE - 0.05,
                  "%s: %.1f mm da divisoria (>= %.0f)" % (nome, b.YMin - div_tras, GAP_REDE))
        elif c["faixa"] == "baixa":
            check(b.YMax <= div_frente + 0.05,
                  "%s: nao passa da divisoria (Ymax %.1f <= %.1f)" % (nome, b.YMax, div_frente))

        # so' a travessia cruza a divisoria
        if nome not in ("divisoria", "travessia_fios"):
            cruza = b.YMin < div_tras and b.YMax > div_frente
            check(not cruza, "%s: nao cruza o plano da divisoria" % nome)

    # travessia passa pelo rasgo (divisoria nao a intercepta)
    tr = d.getObject("travessia_fios").Shape
    dv = d.getObject("divisoria").Shape
    check(tr.common(dv).Volume < 0.5,
          "travessia_fios passa pelo rasgo da divisoria (%.2f mm3)" % tr.common(dv).Volume)

    # divisoria cobre a largura interna
    bd = dv.BoundBox
    check(bd.XLength >= g["pegada_x"] - 2 * g["espessura"] - 1,
          "divisoria cobre a largura interna (%.1f mm)" % bd.XLength)

    # altura livre
    folga = teto - tallest
    check(folga >= 0, "altura livre: folga %.1f mm sob o tampo (mais alto: %.1f)"
          % (folga, tallest))
    print("       altura interna util ~ %.1f mm" % (teto - piso))

    # antena do ESP32 alinhada com a janela de RF
    e = d.getObject("esp32").Shape.BoundBox
    jrf = d1.getObject("janela_rf").Shape.BoundBox
    check(e.XMin <= jrf.XMax and e.XMax >= jrf.XMin,
          "ESP32 cobre a janela de RF em X (esp32 %.0f-%.0f, janela %.0f-%.0f)"
          % (e.XMin, e.XMax, jrf.XMin, jrf.XMax))
    check(e.YMin <= g["espessura"] + 8,
          "ESP32 com a antena junto a' saia frontal (Ymin %.1f)" % e.YMin)

    # conflitos entre volumes (informativo)
    print("--- interferencias entre volumes (informativo) ---")
    nomes = [n for n in comps if n != "divisoria"]
    for i, a in enumerate(nomes):
        for bn in nomes[i + 1:]:
            v = d.getObject(a).Shape.common(d.getObject(bn).Shape).Volume
            if v > 1e-6:
                print("    %s x %s : %.1f mm3" % (a, bn, v))

    print()
    for a in avisos:
        print("AVISO:", a)
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("arranjo OK (tamanhos ainda estimados)")


main()

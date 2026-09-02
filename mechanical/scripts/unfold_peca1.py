"""Planificacao analitica da Peca 1 e gravacao no FCStd.

Sem geometria de unfold: a chapa e' plana antes de dobrar, entao o comprimento
plano de cada direcao e' a soma dos trechos e as dobras entram por bend deduction
(BD = 2*setback - BA). Cada direcao (X e Y) atravessa 4 dobras de 90 graus:
aba<->saia, saia<->tampo, tampo<->saia, saia<->aba.

Uso: freecadcmd mechanical/scripts/unfold_peca1.py   (depois de build_peca1.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App

DOC_ESPERADO = {"X": 366.0, "Y": 316.0}  # ~ do documento; chapa bruta ~400 x 350
TOL = 1.0


def main():
    doc = App.openDocument(_env.FCSTD)
    g = {a: v for a, v, _, _ in _env.load_params() if v is not None}

    t, r, k = g["espessura"], g["raio_dobra"], g["fator_k"]
    ba = _env.bend_allowance(r, k, t)
    sb = _env.setback(r, t)
    bd = 2 * sb - ba

    print("BA (90 graus) = %.3f mm" % ba)
    print("setback       = %.3f mm" % sb)
    print("bend deduction= %.3f mm" % bd)
    print()

    legs = {
        "X": [("aba_fundo", g["aba_fundo"]), ("saia", g["altura_externa"]),
              ("tampo (pegada_x)", g["pegada_x"]), ("saia", g["altura_externa"]),
              ("aba_fundo", g["aba_fundo"])],
        "Y": [("aba_fundo", g["aba_fundo"]), ("saia", g["altura_externa"]),
              ("tampo (pegada_y)", g["pegada_y"]), ("saia", g["altura_externa"]),
              ("aba_fundo", g["aba_fundo"])],
    }

    sheet = doc.getObject("planificacao") or doc.addObject("Spreadsheet::Sheet", "planificacao")
    sheet.set("A1", "direcao"); sheet.set("B1", "soma trechos"); sheet.set("C1", "n dobras")
    sheet.set("D1", "comprimento plano"); sheet.set("E1", "esperado doc")

    row = 2
    ok = True
    for eixo in ("X", "Y"):
        soma = sum(v for _, v in legs[eixo])
        flat = soma - 4 * bd
        sheet.set("A%d" % row, eixo)
        sheet.set("B%d" % row, "%.2f" % soma)
        sheet.set("C%d" % row, "4")
        sheet.set("D%d" % row, "%.2f" % flat)
        sheet.set("E%d" % row, "%.0f" % DOC_ESPERADO[eixo])
        delta = abs(flat - DOC_ESPERADO[eixo])
        flag = "OK" if delta <= TOL else "DIVERGE %.2f" % delta
        if delta > TOL:
            ok = False
        print("%s: trechos %.2f  ->  plano %.2f mm  (doc ~%.0f)  %s"
              % (eixo, soma, flat, DOC_ESPERADO[eixo], flag))
        row += 1

    sheet.set("A5", "BA_90"); sheet.set("B5", "%.4f" % ba)
    sheet.set("A6", "bend_deduction_90"); sheet.set("B6", "%.4f" % bd)
    sheet.set("A7", "chapa bruta sugerida"); sheet.set("B7", "~400 x 350 mm")

    _env.apply_descriptions(doc)
    doc.recompute()
    doc.save()
    print("\ngravado em", _env.FCSTD)
    if not ok:
        raise SystemExit("planificacao diverge do documento alem de %.1f mm" % TOL)


main()

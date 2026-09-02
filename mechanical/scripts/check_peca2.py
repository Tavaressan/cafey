"""Validacao da Peca 2 e da coincidencia de furos com a Peca 1.

Uso: freecadcmd mechanical/scripts/check_peca2.py   (depois de build_peca1/2.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App

falhas = []


def check(cond, msg):
    print(("  OK  " if cond else "FALHA ") + msg)
    if not cond:
        falhas.append(msg)


def main():
    g = _env.params_dict()
    d2 = App.openDocument(_env.FCSTD2)
    peca2 = d2.getObject("peca2")
    shp = peca2.Shape
    bb = shp.BoundBox

    check(shp.isValid(), "Shape.isValid()")
    check(len(shp.Solids) == 1, "solido unico (achou %d)" % len(shp.Solids))

    # tampa por baixo: rente a' face externa das saias
    check(abs(bb.XLength - g["pegada_x"]) < 0.01, "largura %.2f = pegada_x" % bb.XLength)
    check(abs(bb.YLength - g["pegada_y"]) < 0.01, "profundidade %.2f = pegada_y" % bb.YLength)
    check(abs(bb.ZLength - g["espessura_fundo"]) < 0.01, "espessura %.2f" % bb.ZLength)

    # face superior do fundo coplanar com a face inferior das abas (Z=-altura_externa);
    # o fundo protrai espessura_fundo abaixo da borda das saias.
    check(abs(bb.ZMax - (-g["altura_externa"])) < 0.01,
          "topo do fundo em Z=%.2f (face inferior das abas)" % bb.ZMax)
    check(abs(bb.ZMin - (-g["altura_externa"] - g["espessura_fundo"])) < 0.01,
          "fundo protrai %.1f mm abaixo da saia" % g["espessura_fundo"])

    # coincidencia dos 8 furos com as porcas-rebite das abas da Peca 1
    alvo = _env.porca_rebite_holes(g)
    furos2 = []
    for o in d2.Objects:
        if o.Name.startswith("fundo_furo_"):
            c = o.Shape.BoundBox.Center
            furos2.append((c.x, c.y))
    check(len(furos2) == 8, "8 furos M3 no fundo (achou %d)" % len(furos2))
    for ax, ay in alvo:
        perto = min((abs(fx - ax) + abs(fy - ay) for fx, fy in furos2), default=1e9)
        check(perto < 0.05,
              "furo do fundo casa com porca-rebite em (%.1f, %.1f)" % (ax, ay))

    # divisoria: linha da cantoneira dentro da faixa util
    y_div = g["espessura"] + g["faixa_baixa"]
    check(g["espessura"] < y_div < g["pegada_y"] - g["espessura"],
          "linha da divisoria (y=%.1f) dentro do interior" % y_div)
    check(abs((g["faixa_baixa"] + g["divisoria"] + g["faixa_rede"])
              - (g["pegada_y"] - 2 * g["espessura"])) < 5.0,
          "faixas (baixa+divisoria+rede) ~ profundidade interna")

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("todos os criterios OK")


main()

"""Validacao da Peca 2 (variante 3D) e da coincidencia de furos com a Peca 1.

Uso: freecadcmd mechanical/scripts/check_peca2_3d.py
     (depois de build_peca1_3d.py e build_peca2_3d.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

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

    check(abs(bb.XLength - g["pegada_x"]) < 0.01, "largura %.2f = pegada_x" % bb.XLength)
    check(abs(bb.YLength - g["pegada_y"]) < 0.01, "profundidade %.2f = pegada_y" % bb.YLength)
    check(abs(bb.ZLength - g["parede_fundo"]) < 0.01, "espessura %.2f" % bb.ZLength)

    check(abs(bb.ZMax - (-g["altura_externa"])) < 0.01,
          "topo do fundo em Z=%.2f (face inferior da flange)" % bb.ZMax)
    check(abs(bb.ZMin - (-g["altura_externa"] - g["parede_fundo"])) < 0.01,
          "fundo protrai %.1f mm abaixo da parede" % g["parede_fundo"])

    check(g["parede_fundo"] >= 2.0,
          "parede_fundo %.1f mm >= 2.0 mm (minimo FDM estrutural)" % g["parede_fundo"])
    # furo_passagem e' folga do FUSO M3 (Ø nominal 3.0 mm) na Peca 2, nao tem
    # relacao direta com insert_furo (bore do insert termico na Peca 1) - sao
    # furos em pecas e com funcoes diferentes.
    check(g["furo_passagem"] > 3.0,
          "furo_passagem %.1f > 3.0 mm (folga real sobre o fuso M3)" % g["furo_passagem"])

    # coincidencia dos 8 furos com os bosses/inserts da Peca 1
    alvo = _env.insert_holes(g)
    furos2 = []
    for o in d2.Objects:
        if o.Name.startswith("fundo_furo_"):
            c = o.Shape.BoundBox.Center
            furos2.append((c.x, c.y))
    check(len(furos2) == 8, "8 furos M3 no fundo (achou %d)" % len(furos2))
    for ax, ay in alvo:
        perto = min((abs(fx - ax) + abs(fy - ay) for fx, fy in furos2), default=1e9)
        check(perto < 0.05,
              "furo do fundo casa com insert em (%.1f, %.1f)" % (ax, ay))

    # divisoria: linha da cantoneira dentro da faixa util
    y_div = g["parede"] + g["faixa_baixa"]
    check(g["parede"] < y_div < g["pegada_y"] - g["parede"],
          "linha da divisoria (y=%.1f) dentro do interior" % y_div)
    check(abs((g["faixa_baixa"] + g["divisoria"] + g["faixa_rede"])
              - (g["pegada_y"] - 2 * g["parede"])) < 5.0,
          "faixas (baixa+divisoria+rede) ~ profundidade interna")

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("todos os criterios OK")


main()

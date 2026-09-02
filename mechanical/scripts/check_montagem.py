"""Validacao de conjunto: Peca 1 + Peca 2 montadas.

Confere o que so' aparece com as duas pecas juntas: nao-interferencia, pilha de
parafuso do fundo (fundo_furo -> aba -> porca-rebite), cobertura das abas pelo
fundo, e a elevacao total.

Rode build_peca1.py, build_peca2.py e build_montagem.py antes.
Uso: freecadcmd mechanical/scripts/check_montagem.py
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
    d1 = App.openDocument(_env.FCSTD)
    d2 = App.openDocument(_env.FCSTD2)
    s1 = d1.getObject("peca1").Shape
    s2 = d2.getObject("peca2").Shape

    check(s1.isValid() and s2.isValid(), "as duas pecas sao solidos validos")
    check(len(s1.Solids) == 1 and len(s2.Solids) == 1, "um solido por peca")

    inter = s1.common(s2).Volume
    check(inter < 1.0, "Peca 1 x Peca 2 sem interferencia (%.3f mm3)" % inter)

    # contato: a face superior do fundo encosta na face inferior das abas
    gap = -_env.params_dict()["altura_externa"] - s2.BoundBox.ZMax
    check(abs(gap) < 0.01, "fundo encosta na face inferior das abas (folga %.3f)" % gap)

    # pilha do parafuso: cada fundo_furo alinhado com uma porca-rebite pr_*,
    # e a aba tem material em volta do furo da porca-rebite (onde a porca crava).
    pr = {(round(o.Shape.BoundBox.Center.x, 1), round(o.Shape.BoundBox.Center.y, 1))
          for o in d1.Objects if o.Name.startswith("pr_")}
    ff = {(round(o.Shape.BoundBox.Center.x, 1), round(o.Shape.BoundBox.Center.y, 1))
          for o in d2.Objects if o.Name.startswith("fundo_furo_")}
    check(pr == ff, "8 furos do fundo alinhados com as 8 porcas-rebite")

    import Part
    faltou = 0
    for x, y in pr:
        anel = Part.makeCylinder(g["furo_porca_rebite"], g["espessura"],
                                 App.Vector(x, y, -g["altura_externa"] - 0.1))
        anel = anel.cut(Part.makeCylinder(g["furo_porca_rebite"] / 2 + 0.1,
                                          g["espessura"] * 2,
                                          App.Vector(x, y, -g["altura_externa"] - 0.5)))
        if s1.common(anel).Volume < 0.5 * anel.Volume:
            faltou += 1
    check(faltou == 0, "aba tem material em volta de cada porca-rebite (%d sem)" % faltou)

    # fundo cobre as 4 abas em planta
    fb = s2.BoundBox
    for nm in ("aba_frontal", "aba_traseira", "aba_esquerda", "aba_direita"):
        ab = d1.getObject(nm).Shape.BoundBox
        dentro = (fb.XMin - 0.01 <= ab.XMin and ab.XMax <= fb.XMax + 0.01
                  and fb.YMin - 0.01 <= ab.YMin and ab.YMax <= fb.YMax + 0.01)
        check(dentro, "fundo cobre %s" % nm)

    # elevacao total (surface -> topo do tampo), agora com o fundo por baixo
    elev = g["altura_externa"] + g["espessura_fundo"] + g["pe_altura"] + g["apoio_altura"]
    check(elev <= 60.5, "elevacao total %.1f mm <= ~60 (doc: 55 a 60)" % elev)
    print("       elevacao com apoio_altura no maximo (5 mm): %.1f mm"
          % (elev + 5 - g["apoio_altura"]))

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("montagem OK")


main()

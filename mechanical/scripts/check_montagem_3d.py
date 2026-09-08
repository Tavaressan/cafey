"""Validacao de conjunto (variante 3D): Peca 1 + Peca 2 montadas.

Confere o que so' aparece com as duas pecas juntas: nao-interferencia, pilha
do parafuso (fundo_furo -> boss -> insert), cobertura da flange pelo fundo, e
a elevacao total.

Rode build_peca1_3d.py e build_peca2_3d.py antes.
Uso: freecadcmd mechanical/scripts/check_montagem_3d.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

import FreeCAD as App
import Part

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

    # contato: a face superior do fundo encosta na face inferior da flange
    gap = -g["altura_externa"] - s2.BoundBox.ZMax
    check(abs(gap) < 0.01, "fundo encosta na face inferior da flange (folga %.3f)" % gap)

    # pilha do parafuso: cada fundo_furo alinhado com um boss/insert, e o
    # boss tem material em volta do furo do insert (onde o insert e' fundido).
    bosses = {(round(o.Shape.BoundBox.Center.x, 1), round(o.Shape.BoundBox.Center.y, 1))
              for o in d1.Objects if o.Name.startswith("boss_")}
    ff = {(round(o.Shape.BoundBox.Center.x, 1), round(o.Shape.BoundBox.Center.y, 1))
          for o in d2.Objects if o.Name.startswith("fundo_furo_")}
    check(bosses == ff, "8 furos do fundo alinhados com os 8 bosses de insert")

    faltou = 0
    for x, y in bosses:
        anel = Part.makeCylinder(g["boss_d"] / 2, g["parede"],
                                 App.Vector(x, y, -g["altura_externa"] - 0.1))
        anel = anel.cut(Part.makeCylinder(g["insert_furo"] / 2 + 0.1,
                                          g["parede"] * 2,
                                          App.Vector(x, y, -g["altura_externa"] - 0.5)))
        if s1.common(anel).Volume < 0.5 * anel.Volume:
            faltou += 1
    check(faltou == 0, "boss tem material em volta de cada insert (%d sem)" % faltou)

    # fundo cobre as 4 faixas da flange em planta
    fb = s2.BoundBox
    for nm in ("flange_frontal", "flange_traseira", "flange_esquerda", "flange_direita"):
        fl = d1.getObject(nm).Shape.BoundBox
        dentro = (fb.XMin - 0.01 <= fl.XMin and fl.XMax <= fb.XMax + 0.01
                  and fb.YMin - 0.01 <= fl.YMin and fl.YMax <= fb.YMax + 0.01)
        check(dentro, "fundo cobre %s" % nm)

    # elevacao total (superficie -> topo do tampo), agora com o fundo por baixo
    elev = g["altura_externa"] + g["parede_fundo"] + g["pe_altura"] + g["apoio_altura"]
    check(elev <= 62.0, "elevacao total %.1f mm <= ~60-62 (doc: 55 a 60 na chapa)" % elev)
    print("       elevacao com apoio_altura no maximo (5 mm): %.1f mm"
          % (elev + 5 - g["apoio_altura"]))

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("montagem OK")


main()

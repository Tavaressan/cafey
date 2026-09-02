"""Validacao geometrica da Peca 1. Sai != 0 se algum criterio falhar.

Uso: freecadcmd mechanical/scripts/check_peca1.py   (depois de build_peca1.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part

falhas = []


def check(cond, msg):
    print(("  OK  " if cond else "FALHA ") + msg)
    if not cond:
        falhas.append(msg)


def main():
    doc = App.openDocument(_env.FCSTD)
    g = {a: v for a, v, _, _ in _env.load_params() if v is not None}
    peca1 = doc.getObject("peca1")
    shp = peca1.Shape
    bb = shp.BoundBox
    tol = g["raio_dobra"]

    check(shp.isValid(), "Shape.isValid()")
    check(len(shp.Solids) == 1, "solido unico (achou %d)" % len(shp.Solids))
    check(abs(bb.XLength - g["pegada_x"]) <= tol,
          "bbox X = %.2f ~ pegada_x %.1f" % (bb.XLength, g["pegada_x"]))
    check(abs(bb.YLength - g["pegada_y"]) <= tol,
          "bbox Y = %.2f ~ pegada_y %.1f" % (bb.YLength, g["pegada_y"]))
    check(abs(bb.ZLength - g["altura_externa"]) <= tol,
          "bbox Z = %.2f ~ altura_externa %.1f" % (bb.ZLength, g["altura_externa"]))

    # massa da chapa plausivel (sub-2 kg para ~0.11 m2 de inox 1.2 mm)
    massa_g = shp.Volume * _env.DENSIDADE_INOX * 1e6
    check(0.3 < massa_g / 1000 < 2.0, "massa chapa %.0f g em faixa esperada" % massa_g)

    # --- margens de projeto das aberturas (a partir dos alias) ---
    md, me = g["margem_dobra"], g["margem_extremidade"]
    saia_top = g["altura_externa"] - g["espessura"]          # dist. do topo a' dobra inferior
    centro = g["abertura_centro_z"]

    # aberturas frontais/traseiras: >= md da dobra superior e da inferior
    for nome, alt in [("janela_rf", g["janela_rf_h"]), ("tomada_j1", g["tomada_j1_h"])]:
        folga_topo = centro - alt / 2
        folga_base = (saia_top - centro) - alt / 2
        check(folga_topo >= md, "%s: folga p/ dobra superior %.1f >= %.0f" % (nome, folga_topo, md))
        check(folga_base >= md, "%s: folga p/ dobra inferior %.1f >= %.0f" % (nome, folga_base, md))

    # extremidades em X das aberturas frontais/traseiras
    for nome, x, w in [("janela_rf", g["janela_rf_x"], g["janela_rf_w"]),
                       ("furo_led", g["furo_led_x"], g["furo_led"]),
                       ("furo_botao", g["furo_botao_x"], g["furo_botao"]),
                       ("prensa_cabo", g["prensa_cabo_x"], g["prensa_cabo_d"]),
                       ("tomada_j1", g["tomada_j1_x"], g["tomada_j1_w"])]:
        check(x - w / 2 >= me, "%s: extremidade esq %.1f >= %.0f" % (nome, x - w / 2, me))
        check(g["pegada_x"] - (x + w / 2) >= me,
              "%s: extremidade dir %.1f >= %.0f" % (nome, g["pegada_x"] - (x + w / 2), me))

    # rasgos laterais: 3 mm de largura, borda inferior a >= rasgo_altura - w/2 da dobra
    check(g["rasgo_w"] <= 3.0, "rasgo_w %.1f <= 3 (bloqueia dedo)" % g["rasgo_w"])
    check(g["rasgo_altura"] - g["rasgo_w"] / 2 >= 0,
          "rasgo nao ultrapassa a borda inferior")

    # rasgos laterais: nenhuma peca de corte a menos de me das extremidades em Y,
    # e nenhuma sobreposicao com os furos de canto fix_* (inspecao 3D real).
    rasgos = [o for o in doc.Objects if o.Name.startswith(("rasgo_e", "rasgo_d"))]
    for o in rasgos:
        b = o.Shape.BoundBox
        check(b.YMin >= me - 0.01 and b.YMax <= g["pegada_y"] - me + 0.01,
              "%s: Y [%.1f, %.1f] dentro de [%.0f, %.0f]"
              % (o.Name, b.YMin, b.YMax, me, g["pegada_y"] - me))
    furos_canto = [o for o in doc.Objects if o.Name.startswith("fix_")]
    pior = 0.0
    for a in rasgos:
        for f in furos_canto:
            pior = max(pior, a.Shape.common(f.Shape).Volume)
    check(pior < 1e-6, "rasgos x furos de canto sem sobreposicao (max %.3f mm3)" % pior)

    # --- toda abertura de face atravessa a chapa (nao ficou pele) ---
    # sonda fina no centro de cada corte, ao longo da normal da face; se sobrar
    # material a abertura esta "tapada".
    aberturas = ["janela_rf", "furo_led", "furo_botao", "prensa_cabo",
                 "tomada_j1", "recorte_usb"]
    for nome in aberturas:
        o = doc.getObject(nome)
        c = o.Shape.BoundBox.Center
        if o.Shape.BoundBox.YLength < 5:            # face frontal/traseira: normal Y
            probe = Part.makeBox(2, 4 * g["espessura"], 2,
                                 App.Vector(c.x - 1, c.y - 2 * g["espessura"], c.z - 1))
        else:                                       # lateral: normal X
            probe = Part.makeBox(4 * g["espessura"], 2, 2,
                                 App.Vector(c.x - 2 * g["espessura"], c.y - 1, c.z - 1))
        resto = shp.common(probe).Volume
        check(resto < 1e-6, "%s atravessa a chapa (resto %.2f mm3)" % (nome, resto))

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("todos os criterios OK")


main()

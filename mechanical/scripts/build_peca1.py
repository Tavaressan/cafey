"""Constroi mechanical/build/peca1.FCStd do zero.

Peca 1 do pedestal: tampo + 4 saias + 4 abas de fundo + 4 abas de canto, com
alivios de canto, furacao e todas as aberturas por face. Solido Part unico,
inteiramente dirigido pelo Spreadsheet `params` via expressao.

Sistema de coordenadas: face superior do tampo em Z=0; a peca desce para Z<0.
X ao longo de pegada_x, Y ao longo de pegada_y. Y=0 e' a saia frontal (baixa
tensao), Y=pegada_y e' a traseira (rede). X=0 e' a lateral esquerda.

Uso: freecadcmd mechanical/scripts/build_peca1.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env
from _build import build_spreadsheet, box, cyl

import FreeCAD as App


def build(doc):
    adds, cuts = [], []
    P = "params."  # prefixo de alias

    # --- 1. tampo ---
    adds.append(box(doc, "tampo",
                    P + "pegada_x", P + "pegada_y", P + "espessura",
                    "0", "0", "-" + P + "espessura"))

    saia_h = "%saltura_externa - %sespessura" % (P, P)
    z_saia = "-" + P + "altura_externa"

    # --- 2. saias ---
    adds.append(box(doc, "saia_frontal",
                    P + "pegada_x", P + "espessura", saia_h, "0", "0", z_saia))
    adds.append(box(doc, "saia_traseira",
                    P + "pegada_x", P + "espessura", saia_h,
                    "0", "%spegada_y - %sespessura" % (P, P), z_saia))
    adds.append(box(doc, "saia_esquerda",
                    P + "espessura", P + "pegada_y", saia_h, "0", "0", z_saia))
    adds.append(box(doc, "saia_direita",
                    P + "espessura", P + "pegada_y", saia_h,
                    "%spegada_x - %sespessura" % (P, P), "0", z_saia))

    # --- 3. abas do fundo (dobradas para dentro na borda inferior) ---
    adds.append(box(doc, "aba_frontal",
                    P + "pegada_x", P + "aba_fundo", P + "espessura",
                    "0", P + "espessura", z_saia))
    adds.append(box(doc, "aba_traseira",
                    P + "pegada_x", P + "aba_fundo", P + "espessura",
                    "0", "%spegada_y - %sespessura - %saba_fundo" % (P, P, P), z_saia))
    adds.append(box(doc, "aba_esquerda",
                    P + "aba_fundo", P + "pegada_y", P + "espessura",
                    P + "espessura", "0", z_saia))
    adds.append(box(doc, "aba_direita",
                    P + "aba_fundo", P + "pegada_y", P + "espessura",
                    "%spegada_x - %sespessura - %saba_fundo" % (P, P, P), "0", z_saia))

    # --- 4. abas de canto (saem das saias frontal/traseira, lapam a lateral) ---
    x_esq = P + "espessura"
    x_dir = "%spegada_x - 2 * %sespessura" % (P, P)
    y_front = P + "espessura"
    y_tras = "%spegada_y - %sespessura - %saba_canto" % (P, P, P)
    for nm, xx, yy in [("canto_fl", x_esq, y_front), ("canto_fr", x_dir, y_front),
                       ("canto_tl", x_esq, y_tras), ("canto_tr", x_dir, y_tras)]:
        adds.append(box(doc, nm,
                        P + "espessura", P + "aba_canto", saia_h, xx, yy, z_saia))

    # --- 5. alivios de canto (entalhe no encontro das dobras verticais) ---
    # some do fundo do tampo (-espessura) ate a borda inferior externa.
    relief_h = "%saltura_externa - %sespessura" % (P, P)
    ac = P + "alivio_canto"
    for nm, xx, yy in [
        ("relief_00", "0", "0"),
        ("relief_x0", "%spegada_x - %salivio_canto" % (P, P), "0"),
        ("relief_0y", "0", "%spegada_y - %salivio_canto" % (P, P)),
        ("relief_xy", "%spegada_x - %salivio_canto" % (P, P),
         "%spegada_y - %salivio_canto" % (P, P))]:
        cuts.append(box(doc, nm, ac, ac, relief_h, xx, yy, z_saia))

    # --- 6. aberturas frontais (Y=0, eixo de corte +Y) ---
    zc = "-" + P + "abertura_centro_z"           # centro Z das aberturas de saia
    thru_y = ("-1", "%sespessura + 2" % P)       # base.y, height p/ atravessar
    cuts.append(box(doc, "janela_rf",
                    P + "janela_rf_w", thru_y[1], P + "janela_rf_h",
                    "%sjanela_rf_x - %sjanela_rf_w / 2" % (P, P), thru_y[0],
                    "%s - %sjanela_rf_h / 2" % (zc, P)))
    cuts.append(cyl(doc, "furo_led", P + "furo_led / 2", "%sespessura + 2" % P,
                    (P + "furo_led_x", "-1", zc), "Y"))
    cuts.append(cyl(doc, "furo_botao", P + "furo_botao / 2", "%sespessura + 2" % P,
                    (P + "furo_botao_x", "-1", zc), "Y"))

    # --- 7. aberturas traseiras (Y=pegada_y) ---
    # base antes da face interna da saia traseira (pegada_y - espessura), com
    # +2 de altura para atravessar toda a chapa.
    y_back_base = "%spegada_y - %sespessura - 1" % (P, P)
    cuts.append(cyl(doc, "prensa_cabo", P + "prensa_cabo_d / 2",
                    "%sespessura + 2" % P,
                    (P + "prensa_cabo_x", y_back_base, zc), "Y"))
    cuts.append(box(doc, "tomada_j1",
                    P + "tomada_j1_w", "%sespessura + 2" % P, P + "tomada_j1_h",
                    "%stomada_j1_x - %stomada_j1_w / 2" % (P, P), y_back_base,
                    "%s - %stomada_j1_h / 2" % (zc, P)))

    # --- 8. rasgos laterais + recorte USB ---
    # grade de 5 posicoes em Y. O centro e' recuado de metade do comprimento do
    # rasgo alem da margem de extremidade, para que a BORDA do rasgo (nao o
    # centro) respeite os >= 25 mm das extremidades e nao invada os furos de
    # canto (fix_*t_lo / fix_*f_lo).
    params = _env.params_dict()
    meia = params["rasgo_l"] / 2.0
    ymin = params["margem_extremidade"] + meia
    ymax = params["pegada_y"] - params["margem_extremidade"] - meia
    grid = [ymin + i * (ymax - ymin) / 4.0 for i in range(5)]
    usb_y = params["pegada_y"] - params["usb_y"]
    drop = min(range(5), key=lambda i: abs(grid[i] - usb_y))  # USB ocupa esta

    z_rasgo = "-%saltura_externa + %srasgo_altura - %srasgo_w / 2" % (P, P, P)
    # esquerda (X=0), eixo +X
    for i, yc in enumerate(grid):
        if i == drop:
            continue
        cuts.append(box(doc, "rasgo_e_%d" % i,
                        "%sespessura + 2" % P, P + "rasgo_l", P + "rasgo_w",
                        "-1", "%f - %srasgo_l / 2" % (yc, P), z_rasgo))
    # recorte USB na posicao 'drop' da esquerda
    cuts.append(box(doc, "recorte_usb",
                    "%sespessura + 2" % P, P + "usb_w", P + "usb_h",
                    "-1", "%f - %susb_w / 2" % (grid[drop], P),
                    "%s - %susb_h / 2" % (zc, P)))
    # direita (X=pegada_x), 5 rasgos
    for i, yc in enumerate(grid):
        cuts.append(box(doc, "rasgo_d_%d" % i,
                        "%sespessura + 2" % P, P + "rasgo_l", P + "rasgo_w",
                        "%spegada_x - 1" % P, "%f - %srasgo_l / 2" % (yc, P), z_rasgo))

    # --- 9. furacao dos cantos (2 por canto, eixo +X atravessando lateral+aba) ---
    z_lo = "-%saltura_externa + (%saltura_externa - %sespessura) / 3" % (P, P, P)
    z_hi = "-%saltura_externa + 2 * (%saltura_externa - %sespessura) / 3" % (P, P, P)
    for lado, xb in [("e", "-1"), ("d", "%spegada_x - 2 * %sespessura - 1" % (P, P))]:
        for canto, yc in [("f", "%sespessura + %saba_canto / 2" % (P, P)),
                          ("t", "%spegada_y - %sespessura - %saba_canto / 2" % (P, P, P))]:
            for j, zz in [("lo", z_lo), ("hi", z_hi)]:
                cuts.append(cyl(doc, "fix_%s%s_%s" % (lado, canto, j),
                                P + "furo_fixacao / 2",
                                "3 * %sespessura + 2" % P, (xb, yc, zz), "X"))

    # --- 10. furacao do fundo (8 x porca-rebite nas abas, eixo +Z) ---
    z_fundo = "-%saltura_externa - 1" % P
    h_fundo = "%sespessura + 2" % P
    for lado, xc in [("a", "%spegada_x / 3" % P), ("b", "2 * %spegada_x / 3" % P)]:
        for aba, yc in [
            ("f", "%sespessura + %saba_fundo / 2" % (P, P)),
            ("t", "%spegada_y - %sespessura - %saba_fundo / 2" % (P, P, P)),
            ("e", "%sespessura + %saba_fundo / 2" % (P, P)),
            ("d", "%spegada_y - %sespessura - %saba_fundo / 2" % (P, P, P))]:
            # abas e/d correm ao longo de Y: troca os papeis de x e y
            if aba in ("e", "d"):
                bx = ("%sespessura + %saba_fundo / 2" % (P, P)) if aba == "e" \
                    else ("%spegada_x - %sespessura - %saba_fundo / 2" % (P, P, P))
                by = xc.replace("pegada_x", "pegada_y")
            else:
                bx, by = xc, yc
            cuts.append(cyl(doc, "pr_%s%s" % (aba, lado),
                            P + "furo_porca_rebite / 2", h_fundo,
                            (bx, by, z_fundo), "Z"))

    doc.recompute()

    fuse_add = doc.addObject("Part::MultiFuse", "corpo")
    fuse_add.Shapes = adds
    fuse_cut = doc.addObject("Part::MultiFuse", "recortes")
    fuse_cut.Shapes = cuts
    doc.recompute()

    peca1 = doc.addObject("Part::Cut", "peca1")
    peca1.Base = fuse_add
    peca1.Tool = fuse_cut
    doc.recompute()

    _env.apply_descriptions(doc)
    notas = doc.addObject("App::TextDocument", "notas_fabricacao")
    notas.Label = "notas de fabricacao"
    notas.Text = _env.NOTAS_FABRICACAO
    furos = doc.addObject("App::TextDocument", "notas_furos")
    furos.Label = "catalogo de furos"
    furos.Text = _env.NOTAS_FUROS_P1
    doc.recompute()
    return peca1


def main():
    if os.path.exists(_env.FCSTD):
        os.remove(_env.FCSTD)
    doc = App.newDocument("peca1")
    build_spreadsheet(doc)
    peca1 = build(doc)
    doc.recompute()

    shp = peca1.Shape
    ok = shp.isValid()
    bb = shp.BoundBox
    print("solidos:", len(shp.Solids))
    print("valido :", ok)
    print("bbox   : %.1f x %.1f x %.1f mm" % (bb.XLength, bb.YLength, bb.ZLength))
    print("volume : %.0f mm3   massa chapa ~ %.0f g"
          % (shp.Volume, shp.Volume * _env.DENSIDADE_INOX * 1e6))

    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.FCSTD)
    print("salvo  :", _env.FCSTD)
    if not ok or len(shp.Solids) != 1:
        raise SystemExit("geometria invalida ou fragmentada")


main()

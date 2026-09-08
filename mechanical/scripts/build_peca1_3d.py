"""Constroi mechanical/build/peca1_3d.FCStd do zero (variante para impressao 3D).

Peca 1 do pedestal, versao FDM: tampo + 4 paredes continuas (sem dobra, sem
abas de canto - a peca ja' nasce fechada nos 4 cantos) + flange interna
continua na base, com 8 bosses para insert termico M3. Substitui a Peca 1 em
chapa dobrada de build_peca1.py (issue #116). Solido Part unico, dirigido pelo
Spreadsheet `params` via expressao.

Sistema de coordenadas identico ao da versao em chapa: face superior do tampo
em Z=0; a peca desce para Z<0. X ao longo de pegada_x, Y ao longo de
pegada_y. Y=0 e' a parede frontal (baixa tensao), Y=pegada_y e' a traseira
(rede). X=0 e' a parede esquerda.

Uso: freecadcmd mechanical/scripts/build_peca1_3d.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env
from _build import box, cyl

import FreeCAD as App


def build(doc):
    adds, cuts = [], []
    P = "params."  # prefixo de alias

    # --- 1. tampo ---
    adds.append(box(doc, "tampo",
                    P + "pegada_x", P + "pegada_y", P + "parede",
                    "0", "0", "-" + P + "parede"))

    parede_h = "%saltura_externa - %sparede" % (P, P)
    z_base = "-" + P + "altura_externa"

    # --- 2. paredes (continuas - sem dobra, os 4 cantos ja' nascem fechados) ---
    adds.append(box(doc, "parede_frontal",
                    P + "pegada_x", P + "parede", parede_h, "0", "0", z_base))
    adds.append(box(doc, "parede_traseira",
                    P + "pegada_x", P + "parede", parede_h,
                    "0", "%spegada_y - %sparede" % (P, P), z_base))
    adds.append(box(doc, "parede_esquerda",
                    P + "parede", P + "pegada_y", parede_h, "0", "0", z_base))
    adds.append(box(doc, "parede_direita",
                    P + "parede", P + "pegada_y", parede_h,
                    "%spegada_x - %sparede" % (P, P), "0", z_base))

    # --- 3. flange interna continua na base (apoia o fundo removivel) ---
    adds.append(box(doc, "flange_frontal",
                    P + "pegada_x", P + "aba_fundo", P + "parede",
                    "0", P + "parede", z_base))
    adds.append(box(doc, "flange_traseira",
                    P + "pegada_x", P + "aba_fundo", P + "parede",
                    "0", "%spegada_y - %sparede - %saba_fundo" % (P, P, P), z_base))
    adds.append(box(doc, "flange_esquerda",
                    P + "aba_fundo", P + "pegada_y", P + "parede",
                    P + "parede", "0", z_base))
    adds.append(box(doc, "flange_direita",
                    P + "aba_fundo", P + "pegada_y", P + "parede",
                    "%spegada_x - %sparede - %saba_fundo" % (P, P, P), "0", z_base))

    # --- 4. bosses de insert M3 (8, mesmas posicoes da porca-rebite da chapa) ---
    params = _env.params_dict()
    pontos = _env.insert_holes(params)
    nomes = []
    for xc, yc in pontos:
        if abs(yc - params["parede"] - params["aba_fundo"] / 2.0) < 1e-6:
            nomes.append(("f", "a" if xc < params["pegada_x"] / 2 else "b"))
        elif abs(yc - (params["pegada_y"] - params["parede"] - params["aba_fundo"] / 2.0)) < 1e-6:
            nomes.append(("t", "a" if xc < params["pegada_x"] / 2 else "b"))
        elif abs(xc - params["parede"] - params["aba_fundo"] / 2.0) < 1e-6:
            nomes.append(("e", "a" if yc < params["pegada_y"] / 2 else "b"))
        else:
            nomes.append(("d", "a" if yc < params["pegada_y"] / 2 else "b"))

    for (xc, yc), (lado, col) in zip(pontos, nomes):
        adds.append(cyl(doc, "boss_%s%s" % (lado, col),
                        P + "boss_d / 2", P + "insert_prof",
                        ("%f" % xc, "%f" % yc, z_base), "Z"))

    # --- 4b. reforco estrutural sob o tampo (audit da carga de 3 kg) ---
    # Peca impressa de cabeca p/ baixo: nervuras e colunas crescem a partir
    # do tampo, imprimem sem suporte. Topo das nervuras coincide com a face
    # inferior do tampo (fundem no MultiFuse).
    nerv_bot = "-%sparede - %snervura_h" % (P, P)
    nerv_h = P + "nervura_h"
    w = P + "nervura_w"
    inner_x0 = P + "parede"
    inner_x1 = "%spegada_x - %sparede" % (P, P)
    span_x = "%spegada_x - 2 * %sparede" % (P, P)

    # nervura perimetral: 4 barras (frontal/traseira cobrem toda a largura
    # interna; esquerda/direita ficam entre elas).
    adds.append(box(doc, "nervura_perimetral_f",
                    span_x, w, nerv_h, inner_x0, P + "parede", nerv_bot))
    adds.append(box(doc, "nervura_perimetral_t",
                    span_x, w, nerv_h, inner_x0,
                    "%spegada_y - %sparede - %snervura_w" % (P, P, P), nerv_bot))
    lat_y0 = "%sparede + %snervura_w" % (P, P)
    lat_span_y = "%spegada_y - 2 * %sparede - 2 * %snervura_w" % (P, P, P)
    adds.append(box(doc, "nervura_perimetral_e",
                    w, lat_span_y, nerv_h, inner_x0, lat_y0, nerv_bot))
    adds.append(box(doc, "nervura_perimetral_d",
                    w, lat_span_y, nerv_h,
                    "%spegada_x - %sparede - %snervura_w" % (P, P, P),
                    lat_y0, nerv_bot))

    # nervura transversal na linha da divisoria (divide o vao do tampo)
    if params.get("nervura_transversal"):
        yd = _env.y_nervura_transversal(params)
        adds.append(box(doc, "nervura_transversal",
                        span_x, w, nerv_h, inner_x0,
                        "%f - %snervura_w / 2" % (yd, P), nerv_bot))
        # rasgo p/ a travessia de 5 fios (componente travessia_fios)
        comps = {c["nome"]: c for c in _env.load_componentes()}
        tv = comps["travessia_fios"]
        adds_cut_slot_x0 = tv["x"] - params["travessia_folga"]
        adds_cut_slot_w = tv["dx"] + 2 * params["travessia_folga"]
        cuts.append(box(doc, "nervura_transv_rasgo",
                        "%f" % adds_cut_slot_w, "%snervura_w + 2" % P,
                        "%snervura_h / 2" % P,
                        "%f" % adds_cut_slot_x0,
                        "%f - %snervura_w / 2 - 1" % (yd, P), nerv_bot))

    # colunas de canto: caminho de carga vertical tampo -> plano do fundo
    if params.get("coluna_qtd", 0) >= 4:
        col_h = "%saltura_externa - %sparede" % (P, P)
        for i, (xc, yc) in enumerate(_env.coluna_positions(params)):
            adds.append(box(doc, "coluna_%d" % i,
                            P + "coluna_lado", P + "coluna_lado", col_h,
                            "%f - %scoluna_lado / 2" % (xc, P),
                            "%f - %scoluna_lado / 2" % (yc, P), z_base))

    # --- 5. aberturas frontais (Y=0, eixo de corte +Y) ---
    zc = "-" + P + "abertura_centro_z"
    thru_y = ("-1", "%sparede + 2" % P)
    cuts.append(box(doc, "janela_rf",
                    P + "janela_rf_w", thru_y[1], P + "janela_rf_h",
                    "%sjanela_rf_x - %sjanela_rf_w / 2" % (P, P), thru_y[0],
                    "%s - %sjanela_rf_h / 2" % (zc, P)))
    cuts.append(cyl(doc, "furo_led", P + "furo_led / 2", "%sparede + 2" % P,
                    (P + "furo_led_x", "-1", zc), "Y"))
    cuts.append(cyl(doc, "furo_botao", P + "furo_botao / 2", "%sparede + 2" % P,
                    (P + "furo_botao_x", "-1", zc), "Y"))

    # --- 6. aberturas traseiras (Y=pegada_y) ---
    y_back_base = "%spegada_y - %sparede - 1" % (P, P)
    cuts.append(cyl(doc, "prensa_cabo", P + "prensa_cabo_d / 2",
                    "%sparede + 2" % P,
                    (P + "prensa_cabo_x", y_back_base, zc), "Y"))
    cuts.append(box(doc, "tomada_j1",
                    P + "tomada_j1_w", "%sparede + 2" % P, P + "tomada_j1_h",
                    "%stomada_j1_x - %stomada_j1_w / 2" % (P, P), y_back_base,
                    "%s - %stomada_j1_h / 2" % (zc, P)))

    # --- 7. rasgos laterais + recorte USB (mesma grade da versao em chapa) ---
    meia = params["rasgo_l"] / 2.0
    ymin = params["margem_extremidade"] + meia
    ymax = params["pegada_y"] - params["margem_extremidade"] - meia
    grid = [ymin + i * (ymax - ymin) / 4.0 for i in range(5)]
    usb_y = params["pegada_y"] - params["usb_y"]
    drop = min(range(5), key=lambda i: abs(grid[i] - usb_y))

    z_rasgo = "-%saltura_externa + %srasgo_altura - %srasgo_w / 2" % (P, P, P)
    for i, yc in enumerate(grid):
        if i == drop:
            continue
        cuts.append(box(doc, "rasgo_e_%d" % i,
                        "%sparede + 2" % P, P + "rasgo_l", P + "rasgo_w",
                        "-1", "%f - %srasgo_l / 2" % (yc, P), z_rasgo))
    cuts.append(box(doc, "recorte_usb",
                    "%sparede + 2" % P, P + "usb_w", P + "usb_h",
                    "-1", "%f - %susb_w / 2" % (grid[drop], P),
                    "%s - %susb_h / 2" % (zc, P)))
    for i, yc in enumerate(grid):
        cuts.append(box(doc, "rasgo_d_%d" % i,
                        "%sparede + 2" % P, P + "rasgo_l", P + "rasgo_w",
                        "%spegada_x - %sparede - 1" % (P, P),
                        "%f - %srasgo_l / 2" % (yc, P), z_rasgo))

    # --- 8. furos cegos dos inserts M3 (pela face inferior, dentro dos bosses) ---
    z_insert = "-%saltura_externa - 0.5" % P
    h_insert = "%sinsert_prof + 0.5" % P
    for (xc, yc), (lado, col) in zip(pontos, nomes):
        cuts.append(cyl(doc, "insert_%s%s" % (lado, col),
                        P + "insert_furo / 2", h_insert,
                        ("%f" % xc, "%f" % yc, z_insert), "Z"))

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
    doc = App.newDocument("peca1_3d")
    _env.build_spreadsheet(doc)
    peca1 = build(doc)
    doc.recompute()

    shp = peca1.Shape
    ok = shp.isValid()
    bb = shp.BoundBox
    print("solidos:", len(shp.Solids))
    print("valido :", ok)
    print("bbox   : %.1f x %.1f x %.1f mm" % (bb.XLength, bb.YLength, bb.ZLength))
    print("volume : %.0f mm3   massa (solido cheio, PETG) ~ %.0f g"
          % (shp.Volume, shp.Volume * _env.DENSIDADE_PETG * 1e6))

    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.FCSTD)
    print("salvo  :", _env.FCSTD)
    if not ok or len(shp.Solids) != 1:
        raise SystemExit("geometria invalida ou fragmentada")


main()

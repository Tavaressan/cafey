"""Peca 1 — pedestal impresso (FDM): tampo + 4 paredes + flange continua +
bosses de insert M3 + reforco (nervura perimetral, nervura transversal,
4 colunas de canto) + aberturas de face + rasgos de ventilacao.

Equivalente cadgen/build123d de mechanical/scripts/build_peca1_3d.py (FreeCAD).
Mesmo sistema de coordenadas: canto em (0,0), tampo em Z=0, peca em Z<0.
"""

from cadgen import build123d as bd
from cadgen import step

from lib import params as P
from lib.geometry import cbox, ccyl_y, ccyl_z, insert_xy, column_xy, vent_grid


def _corpo():
    t = P.PAREDE
    zb = P.Z_BASE
    wh = P.ALTURA_PAREDE

    adds = []

    # 1. tampo
    adds.append(cbox(P.PEGADA_X, P.PEGADA_Y, t, 0, 0, -t))

    # 2. paredes continuas (cantos ja' nascem fechados)
    adds.append(cbox(P.PEGADA_X, t, wh, 0, 0, zb))
    adds.append(cbox(P.PEGADA_X, t, wh, 0, P.PEGADA_Y - t, zb))
    adds.append(cbox(t, P.PEGADA_Y, wh, 0, 0, zb))
    adds.append(cbox(t, P.PEGADA_Y, wh, P.PEGADA_X - t, 0, zb))

    # 3. flange interna continua na base
    af = P.ABA_FUNDO
    adds.append(cbox(P.PEGADA_X, af, t, 0, t, zb))
    adds.append(cbox(P.PEGADA_X, af, t, 0, P.PEGADA_Y - t - af, zb))
    adds.append(cbox(af, P.PEGADA_Y, t, t, 0, zb))
    adds.append(cbox(af, P.PEGADA_Y, t, P.PEGADA_X - t - af, 0, zb))

    # 4. bosses de insert M3 (Ø BOSS_D, altura INSERT_PROF, subindo da base)
    for x, y in insert_xy():
        adds.append(ccyl_z(P.BOSS_D, P.INSERT_PROF, x, y, zb + P.INSERT_PROF / 2.0))

    # 4b. reforco — nervura perimetral (moldura sob o tampo)
    nb = P.Z_NERVURA_BOT
    spanx = P.PEGADA_X - 2 * t
    laty = P.PEGADA_Y - 2 * t - 2 * P.NERVURA_W
    adds.append(cbox(spanx, P.NERVURA_W, P.NERVURA_H, t, t, nb))
    adds.append(cbox(spanx, P.NERVURA_W, P.NERVURA_H, t, P.PEGADA_Y - t - P.NERVURA_W, nb))
    adds.append(cbox(P.NERVURA_W, laty, P.NERVURA_H, t, t + P.NERVURA_W, nb))
    adds.append(cbox(P.NERVURA_W, laty, P.NERVURA_H, P.PEGADA_X - t - P.NERVURA_W, t + P.NERVURA_W, nb))

    # 4c. nervura transversal (linha da divisoria) com rasgo para a travessia
    if P.NERVURA_TRANSVERSAL:
        yd = P.Y_DIVISORIA
        trans = cbox(spanx, P.NERVURA_W, P.NERVURA_H, t, yd - P.NERVURA_W / 2.0, nb)
        slot = cbox(P.TRAVESSIA_DX + 2 * P.TRAVESSIA_FOLGA, P.NERVURA_W + 2, P.NERVURA_H / 2.0,
                    P.TRAVESSIA_X - P.TRAVESSIA_FOLGA, yd - P.NERVURA_W / 2.0 - 1, nb)
        adds.append(trans - slot)

    # 4d. colunas de canto (tampo -> plano do fundo)
    if P.COLUNA_QTD >= 4:
        for x, y in column_xy():
            adds.append(cbox(P.COLUNA_LADO, P.COLUNA_LADO, wh,
                             x - P.COLUNA_LADO / 2.0, y - P.COLUNA_LADO / 2.0, zb))

    body = adds[0] + adds[1:]

    # --- recortes ---
    zc = P.ABERTURA_Z
    cuts = []
    # aberturas frontais (parede Y=0)
    cuts.append(cbox(P.JANELA_RF_W, t + 4, P.JANELA_RF_H,
                     P.JANELA_RF_X - P.JANELA_RF_W / 2.0, -2, zc - P.JANELA_RF_H / 2.0))
    cuts.append(ccyl_y(P.FURO_LED, t + 4, P.FURO_LED_X, t / 2.0, zc))
    cuts.append(ccyl_y(P.FURO_BOTAO, t + 4, P.FURO_BOTAO_X, t / 2.0, zc))
    # aberturas traseiras (parede Y=pegada_y)
    cuts.append(ccyl_y(P.PRENSA_CABO_D, t + 4, P.PRENSA_CABO_X, P.PEGADA_Y - t / 2.0, zc))
    cuts.append(cbox(P.TOMADA_J1_W, t + 4, P.TOMADA_J1_H,
                     P.TOMADA_J1_X - P.TOMADA_J1_W / 2.0, P.PEGADA_Y - t - 2, zc - P.TOMADA_J1_H / 2.0))
    # rasgos laterais + recorte USB
    grid = vent_grid()
    drop = min(range(5), key=lambda i: abs(grid[i] - (P.PEGADA_Y - P.USB_Y)))
    z_rasgo = -P.ALTURA_EXTERNA + P.RASGO_ALTURA - P.RASGO_W / 2.0
    for i, yc in enumerate(grid):
        if i != drop:
            cuts.append(cbox(t + 4, P.RASGO_L, P.RASGO_W, -2, yc - P.RASGO_L / 2.0, z_rasgo))
    cuts.append(cbox(t + 4, P.USB_W, P.USB_H, -2, grid[drop] - P.USB_W / 2.0, zc - P.USB_H / 2.0))
    for yc in grid:
        cuts.append(cbox(t + 4, P.RASGO_L, P.RASGO_W, P.PEGADA_X - t - 2, yc - P.RASGO_L / 2.0, z_rasgo))
    # furos cegos dos inserts (pela face inferior)
    for x, y in insert_xy():
        cuts.append(ccyl_z(P.INSERT_FURO, P.INSERT_PROF + 1.0, x, y, zb + P.INSERT_PROF / 2.0 - 0.5))

    result = body - cuts
    result.label = "corpo"
    return result


@step(out="../STEP/corpo.step")
def corpo():
    return _corpo()


if __name__ == "__main__":
    corpo()

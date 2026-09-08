"""Peca 2 — fundo plano removivel impresso: placa + 8 furos de passagem M3
(coincidentes com os inserts da Peca 1) + 2 furos de cantoneira da divisoria
+ 4 assentos de pe coaxiais com as colunas de canto.

Equivalente cadgen/build123d de mechanical/scripts/build_peca2_3d.py.
"""

from cadgen import step

from lib import params as P
from lib.geometry import cbox, ccyl_z, insert_xy, column_xy


def _tampa():
    top = -P.ALTURA_EXTERNA               # face superior do fundo (coplanar com a base da flange)
    z0 = top - P.PAREDE_FUNDO             # face inferior

    plate = cbox(P.PEGADA_X, P.PEGADA_Y, P.PAREDE_FUNDO, 0, 0, z0)

    cuts = []
    # 8 furos de passagem M3
    for x, y in insert_xy():
        cuts.append(ccyl_z(P.FURO_PASSAGEM, P.PAREDE_FUNDO + 2, x, y, top - P.PAREDE_FUNDO / 2.0))
    # 2 furos de cantoneira da divisoria (linha y = parede + faixa_baixa)
    for x in (P.PEGADA_X / 4.0, 3.0 * P.PEGADA_X / 4.0):
        cuts.append(ccyl_z(P.FURO_PASSAGEM, P.PAREDE_FUNDO + 2, x, P.Y_DIVISORIA, top - P.PAREDE_FUNDO / 2.0))
    # 4 assentos de pe (rebaixo de 1.2 mm na face inferior), coaxiais com as colunas
    for x, y in column_xy():
        cuts.append(ccyl_z(P.PE_D, 1.7, x, y, z0 + 0.35))

    result = plate - cuts
    result.label = "tampa"
    return result


@step(out="../STEP/tampa.step")
def tampa():
    return _tampa()


if __name__ == "__main__":
    tampa()

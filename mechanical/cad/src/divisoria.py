"""Divisoria de policarbonato/acrilico entre a faixa de rede e a de baixa
tensao, com rasgo de passa-fio para a travessia de 5 fios. Volume de
referencia (nao impresso). Espelha build_arranjo_3d.py.
"""

from cadgen import step

from lib import params as P
from lib.geometry import cbox


def _divisoria():
    painel = cbox(P.DIVISORIA_DX, P.DIVISORIA_DY, P.DIVISORIA_DZ,
                  P.DIVISORIA_X, P.DIVISORIA_Y, P.DIVISORIA_Z)
    # rasgo de passa-fio (build_arranjo_3d.py): tv.dx+6 x dv.dy+2 x tv.dz+4,
    # em (tv.x-3, dv.y-1, tv.z-2)
    rasgo = cbox(P.TRAVESSIA_DX + 6, P.DIVISORIA_DY + 2, P.TRAVESSIA_DZ + 4,
                 P.TRAVESSIA_X - 3, P.DIVISORIA_Y - 1, P.TRAVESSIA_Z - 2)
    result = painel - rasgo
    result.label = "divisoria"
    return result


@step(out="../STEP/divisoria.step")
def divisoria():
    return _divisoria()


if __name__ == "__main__":
    divisoria()

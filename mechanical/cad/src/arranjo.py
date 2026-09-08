"""Arranjo interno do pedestal impresso — modelo raiz.

Peca 1 (corpo) + Peca 2 (tampa) + divisoria + 13 volumes de referencia dos
componentes (caixas envolventes ESTIMADAS, lidas de lib/params.COMPONENTES,
transcritas de mechanical/params/componentes_3d.csv).

Equivalente cadgen/build123d de mechanical/scripts/build_arranjo_3d.py. Serve
para comparar contra mechanical/build/arranjo_3d.step (FreeCAD).
"""

from cadgen import build123d as bd
from cadgen import step

from lib import params as P
from lib.geometry import cbox
from corpo import corpo
from tampa import tampa
from divisoria import divisoria


@step(out="../STEP/arranjo.step")
def arranjo():
    children = []

    c = corpo()
    c.label = "corpo"
    children.append(c)

    t = tampa()
    t.label = "tampa"
    children.append(t)

    d = divisoria()
    d.label = "divisoria"
    children.append(d)

    for nome, faixa, dx, dy, dz, x, y, z in P.COMPONENTES:
        box = cbox(dx, dy, dz, x, y, z, label="%s:%s" % (nome, faixa))
        children.append(box)

    return bd.Compound(children=children, label="arranjo_3d_cad")


if __name__ == "__main__":
    arranjo()

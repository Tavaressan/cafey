"""Helpers de geometria (build123d) — box ancorado no canto, cilindro por eixo,
e as posicoes de furo/coluna/rasgo, espelhando _env3d.py e build_peca1_3d.py
do projeto FreeCAD."""

from cadgen import build123d as bd

from . import params as P


def cbox(length, width, height, x, y, z, label=None):
    """Box com o CANTO MINIMO em (x, y, z) — como o Part::Box do FreeCAD."""
    s = bd.Pos(x + length / 2.0, y + width / 2.0, z + height / 2.0) * bd.Box(length, width, height)
    if label:
        s.label = label
    return s


def ccyl_y(diameter, length, x, yc, zc):
    """Cilindro de eixo Y, centrado em (x, yc, zc), comprimento total 'length'."""
    return bd.Pos(x, yc, zc) * bd.Rot(90, 0, 0) * bd.Cylinder(diameter / 2.0, length)


def ccyl_z(diameter, length, x, y, zc):
    """Cilindro de eixo Z, centrado em (x, y, zc)."""
    return bd.Pos(x, y, zc) * bd.Cylinder(diameter / 2.0, length)


def insert_xy():
    """8 centros (x, y) dos bosses/inserts M3 — _env3d.insert_holes()."""
    xs = [P.PEGADA_X / 3.0, 2.0 * P.PEGADA_X / 3.0]
    ys = [P.PEGADA_Y / 3.0, 2.0 * P.PEGADA_Y / 3.0]
    m = P.PAREDE + P.ABA_FUNDO / 2.0
    pts = []
    for x in xs:
        pts.append((x, m))
        pts.append((x, P.PEGADA_Y - m))
    for y in ys:
        pts.append((m, y))
        pts.append((P.PEGADA_X - m, y))
    return pts


def column_xy():
    """4 centros (x, y) das colunas de canto — _env3d.coluna_positions()."""
    m = P.PAREDE + P.COLUNA_LADO / 2.0
    return [(m, m), (P.PEGADA_X - m, m),
            (m, P.PEGADA_Y - m), (P.PEGADA_X - m, P.PEGADA_Y - m)]


def vent_grid():
    """5 centros Y da grade de rasgos — build_peca1_3d.py passo 7."""
    meia = P.RASGO_L / 2.0
    ymin = P.MARGEM_EXTREMIDADE + meia
    ymax = P.PEGADA_Y - P.MARGEM_EXTREMIDADE - meia
    return [ymin + i * (ymax - ymin) / 4.0 for i in range(5)]

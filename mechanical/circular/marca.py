# /// script
# requires-python = ">=3.10"
# dependencies = ["shapely>=2.1", "fonttools>=4.40", "uharfbuzz>=0.39", "numpy", "trimesh", "manifold3d"]
# ///
"""Grava a marca [simbolo] Caféy na parede cilindrica do corpo (#303, #302).

    uv run mechanical/circular/marca.py

Le build/circular/corpo_sem_marca.stl (gerado por build.py) e escreve
build/circular/corpo.stl com a composicao gravada (baixo-relevo), enrolada
no cilindro com profundidade constante. Posicao e tamanho vem de aberturas.csv
(linha "marca") e parametros.csv (marca_altura_simbolo, marca_prof).
"""
import math
import os
import sys

import manifold3d as m3d
import numpy as np
import shapely
import trimesh
from shapely import affinity
from shapely.geometry import MultiPolygon
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "docs", "docs_interface", "logo"))
import gen_lockup  # noqa: E402
from layout import Layout  # noqa: E402

L = Layout()
p = L.p
OUT = os.path.join(ROOT, "mechanical", "build", "circular")
marca = next(a for a in L.aberturas if a.forma == "marca")


def lockup_uv():
    """Composicao em mm no plano (u, v): u para a direita, v para cima, centrada."""
    b = gen_lockup.build("Caféy")
    g = unary_union([b["symbol"], b["text"]])
    s = p["marca_altura_simbolo"] / b["art_h"]
    g = affinity.scale(g, xfact=s, yfact=-s, origin=(0, 0))
    x0, y0, x1, y1 = g.bounds
    g = affinity.translate(g, -(x0 + x1) / 2, -(y0 + y1) / 2)
    return shapely.segmentize(g, 1.0)


def cutter(geom, R, depth, over=1.0):
    """Prisma (u, v, r) com r de R - depth a R + over, mapeado no cilindro."""
    phi0 = math.radians(marca.ang)
    zc = marca.zc

    def P(u, v, r):
        phi = phi0 + u / R
        return (r * math.cos(phi), r * math.sin(phi), zc + v)

    r0, r1 = R - depth, R + over
    verts, faces = [], []

    def tri(a, b, c):
        n = len(verts)
        verts.extend([a, b, c])
        faces.append((n, n + 1, n + 2))

    polys = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    for poly in polys:
        poly = shapely.orient_polygons(poly, exterior_cw=False)
        for t in shapely.constrained_delaunay_triangles(poly).geoms:
            a, b, c = list(t.exterior.coords)[:3]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) < 0:
                b, c = c, b
            tri(P(*a, r1), P(*b, r1), P(*c, r1))   # face externa (+r)
            tri(P(*a, r0), P(*c, r0), P(*b, r0))   # fundo da gravacao (-r)
        for ring in [poly.exterior, *poly.interiors]:
            cs = list(ring.coords)
            for (au, av), (bu, bv) in zip(cs[:-1], cs[1:]):
                tri(P(au, av, r0), P(bu, bv, r0), P(bu, bv, r1))
                tri(P(au, av, r0), P(bu, bv, r1), P(au, av, r1))
    m = trimesh.Trimesh(np.array(verts), np.array(faces), process=True)
    return m


def to_manifold(tm):
    mesh = m3d.Mesh(vert_properties=np.asarray(tm.vertices, dtype=np.float32),
                    tri_verts=np.asarray(tm.faces, dtype=np.uint32))
    man = m3d.Manifold(mesh)
    if man.status() != m3d.Error.NoError:
        raise SystemExit("malha nao-manifold: %s" % man.status())
    return man


def main():
    body_tm = trimesh.load(os.path.join(OUT, "corpo_sem_marca.stl"), process=True)
    geom = lockup_uv()
    cut_tm = cutter(geom, L.R, p["marca_prof"])
    if not cut_tm.is_watertight:
        raise SystemExit("cortador da marca nao e' estanque")
    res = to_manifold(body_tm) - to_manifold(cut_tm)
    out = res.to_mesh()
    tm = trimesh.Trimesh(out.vert_properties[:, :3], out.tri_verts, process=True)
    path = os.path.join(OUT, "corpo.stl")
    tm.export(path)
    w = geom.bounds[2] - geom.bounds[0]
    removido = body_tm.volume - tm.volume
    print("marca: %.1f x %.1f mm em %.0f graus, z = %.0f, profundidade %.1f mm"
          % (w, geom.bounds[3] - geom.bounds[1], marca.ang, marca.zc, p["marca_prof"]))
    print("volume removido %.0f mm3 (area da marca ~%.0f mm2); estanque: %s"
          % (removido, geom.area, tm.is_watertight))
    print("escrito %s (%d triangulos)" % (os.path.relpath(path, ROOT), len(tm.faces)))


if __name__ == "__main__":
    main()

# /// script
# requires-python = ">=3.10"
# dependencies = ["shapely>=2.1", "fonttools>=4.40", "uharfbuzz>=0.39", "numpy"]
# ///
"""Corpo de prova da marca em relevo (#303): placa com a composicao
[simbolo] Caféy em tres escalas, nas versoes gravada e em alto-relevo.

Serve para medir na impressora real qual altura de simbolo fica legivel e
quais detalhes fecham, antes de fixar os parametros no CAD da carcaca (#302).

    uv run mechanical/scripts/build_corpo_prova_marca.py
    -> mechanical/build/corpo_prova_marca_gravado.stl
    -> mechanical/build/corpo_prova_marca_relevo.stl

Ordem das linhas na placa, de cima para baixo: simbolo de 25, 20 e 15 mm.
Imprimir **em pe** (placa na vertical) reproduz a parede da carcaca; deitada
reproduz uma peca encaixada impressa plana.
"""
import os
import struct
import sys

import numpy as np
import shapely
from shapely import affinity
from shapely.geometry import MultiPolygon, Polygon, box
from shapely.ops import unary_union

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "docs", "docs_interface", "logo"))
import gen_lockup  # noqa: E402

ALTURAS_SIMBOLO_MM = (25.0, 20.0, 15.0)
ESPESSURA_MM = 3.0       # placa
PROFUNDIDADE_MM = 0.8    # gravacao ou altura do relevo
MARGEM_MM = 5.0
ESPACO_MM = 6.0          # entre linhas
OUT = os.path.join(ROOT, "mechanical", "build")


def lockup_mm(h_simbolo):
    """Composicao em mm, y para cima, canto inferior esquerdo da arte em (0, 0)."""
    b = gen_lockup.build("Caféy")  # grafia decidida em 09/11/2026
    geom = unary_union([b["symbol"], b["text"]])
    s = h_simbolo / b["art_h"]
    geom = affinity.scale(geom, xfact=s, yfact=-s, origin=(0, 0))  # SVG y para baixo -> CAD y para cima
    x0, y0, _, _ = geom.bounds
    return affinity.translate(geom, -x0, -y0)


def polys(geom):
    if geom.is_empty:
        return []
    return list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]


def extrude(geom, z0, z1):
    """Triangulos de um prisma reto sobre (multi)poligono com furos."""
    tris = []
    for p in polys(geom):
        p = shapely.orient_polygons(p, exterior_cw=False)  # externo anti-horario, furos horario
        for t in shapely.constrained_delaunay_triangles(p).geoms:
            a, b, c = list(t.exterior.coords)[:3]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) < 0:
                b, c = c, b
            tris.append(((a[0], a[1], z1), (b[0], b[1], z1), (c[0], c[1], z1)))  # topo (+z)
            tris.append(((a[0], a[1], z0), (c[0], c[1], z0), (b[0], b[1], z0)))  # fundo (-z)
        for ring in [p.exterior, *p.interiors]:
            c = list(ring.coords)
            for (ax, ay), (bx, by) in zip(c[:-1], c[1:]):
                # Material a esquerda da aresta: a normal (a direita) aponta para fora.
                tris.append(((ax, ay, z0), (bx, by, z0), (bx, by, z1)))
                tris.append(((ax, ay, z0), (bx, by, z1), (ax, ay, z1)))
    return tris


def faces(geom, z, up):
    tris = []
    for p in polys(geom):
        p = shapely.orient_polygons(p, exterior_cw=False)
        for t in shapely.constrained_delaunay_triangles(p).geoms:
            a, b, c = list(t.exterior.coords)[:3]
            if ((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) < 0) == up:
                b, c = c, b
            tris.append(((a[0], a[1], z), (b[0], b[1], z), (c[0], c[1], z)))
    return tris


def walls(geom, z0, z1, outward=True):
    tris = []
    for p in polys(geom):
        p = shapely.orient_polygons(p, exterior_cw=False)
        for ring in [p.exterior, *p.interiors]:
            c = list(ring.coords)
            for (ax, ay), (bx, by) in zip(c[:-1], c[1:]):
                if outward:
                    tris.append(((ax, ay, z0), (bx, by, z0), (bx, by, z1)))
                    tris.append(((ax, ay, z0), (bx, by, z1), (ax, ay, z1)))
                else:
                    tris.append(((ax, ay, z0), (bx, by, z1), (bx, by, z0)))
                    tris.append(((ax, ay, z0), (ax, ay, z1), (bx, by, z1)))
    return tris


def pocketed(plate, pocket, t, d):
    """Placa com bolsoes gravados, como um unico solido fechado."""
    return (faces(plate, 0, up=False) + walls(plate, 0, t)
            + faces(plate.difference(pocket), t, up=True)
            + faces(pocket, t - d, up=True) + walls(pocket, t - d, t, outward=False))


def write_stl(path, tris):
    with open(path, "wb") as fh:
        fh.write(b"Cafey corpo de prova da marca (#303)".ljust(80, b" "))
        fh.write(struct.pack("<I", len(tris)))
        for a, b, c in tris:
            a, b, c = np.array(a), np.array(b), np.array(c)
            n = np.cross(b - a, c - a)
            ln = np.linalg.norm(n)
            n = n / ln if ln else n
            fh.write(struct.pack("<12fH", *n, *a, *b, *c, 0))
    print("%-48s %7d triangulos" % (os.path.relpath(path, ROOT), len(tris)))


def main():
    linhas = [lockup_mm(h) for h in ALTURAS_SIMBOLO_MM]
    largura = max(g.bounds[2] for g in linhas) + 2 * MARGEM_MM
    altura = sum(g.bounds[3] for g in linhas) + ESPACO_MM * (len(linhas) - 1) + 2 * MARGEM_MM
    placa = box(0, 0, largura, altura)

    y = altura - MARGEM_MM
    marcas = []
    for g in linhas:
        y -= g.bounds[3]
        marcas.append(affinity.translate(g, MARGEM_MM, y))
        y -= ESPACO_MM
    marca = unary_union(marcas)

    os.makedirs(OUT, exist_ok=True)
    t, d = ESPESSURA_MM, PROFUNDIDADE_MM
    write_stl(os.path.join(OUT, "corpo_prova_marca_gravado.stl"),
              pocketed(placa, marca, t, d))
    write_stl(os.path.join(OUT, "corpo_prova_marca_relevo.stl"),
              extrude(placa, 0, t) + extrude(marca, t, t + d))
    print("placa %.1f x %.1f mm, espessura %.1f mm, profundidade/relevo %.1f mm"
          % (largura, altura, t, d))
    for h, g in zip(ALTURAS_SIMBOLO_MM, linhas):
        print("  simbolo %4.1f mm -> composicao %5.1f x %4.1f mm" % (h, g.bounds[2], g.bounds[3]))


if __name__ == "__main__":
    main()

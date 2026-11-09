"""Gera a carcaca circular (#302) no FreeCAD, a partir dos CSVs desta pasta.

    freecadcmd mechanical/circular/build.py

Saidas em mechanical/build/circular/ (pasta nao versionada):
- corpo_sem_marca.stl  Peca 1: tampo + parede cilindrica + divisoria + colunas + bosses
                       (a marca e' gravada depois por marca.py -> corpo.stl)
- fundo.stl            Peca 2: fundo removivel com berco da placa, berco da fonte e ventilacao
- carcaca.step         conjunto: corpo + fundo + volumes de referencia dos componentes
- carcaca.FCStd        mesmo conjunto, para abrir no FreeCAD

O freecadcmd executa o arquivo como modulo, entao o codigo roda no topo.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import FreeCAD as App  # noqa: E402
import Mesh  # noqa: E402
import Part  # noqa: E402

from layout import Layout  # noqa: E402

V = App.Vector
L = Layout()
p = L.p
OUT = os.path.join(os.path.dirname(HERE), "build", "circular")
os.makedirs(OUT, exist_ok=True)


def cyl_z(r, z0, z1, x=0.0, y=0.0):
    return Part.makeCylinder(r, z1 - z0, V(x, y, z0), V(0, 0, 1))


def box(x0, y0, z0, x1, y1, z1):
    return Part.makeBox(x1 - x0, y1 - y0, z1 - z0, V(x0, y0, z0))


def rot_z(shape, ang):
    s = shape.copy()
    s.rotate(V(0, 0, 0), V(0, 0, 1), ang)
    return s


def wall_rect(ang, zc, w, h):
    """Recorte retangular atravessando a parede no angulo dado."""
    d = 2 * p["parede"] + 6
    b = box(L.R - d + 2, -w / 2, zc - h / 2, L.R + 2, w / 2, zc + h / 2)
    return rot_z(b, ang)


def wall_hole(ang, zc, dia, ts=0.0, zs=0.0):
    """Furo radial; ts/zs deslocam o centro ao longo da parede (mm) e em z."""
    c = Part.makeCylinder(dia / 2, 2 * p["parede"] + 6, V(L.R - 2 * p["parede"] - 4, ts, zc + zs), V(1, 0, 0))
    return rot_z(c, ang)


def fuse_all(shapes):
    base = shapes[0]
    if len(shapes) > 1:
        base = base.fuse(shapes[1:])
    return base.removeSplitter()


# --- Peca 1: corpo -------------------------------------------------------------
z_f, z_t = L.z_floor, L.z_teto
corpo = cyl_z(L.R, z_f, 0).cut(cyl_z(L.Ri, z_f - 1, z_t))

adds = []
# Divisoria: corda do piso ao tampo, colada na parede; 0,3 mm acima do piso
# para o fundo assentar na borda da parede e nos bosses, nao na divisoria.
div = box(-L.R, L.div_y0, z_f + 0.3, L.R, L.div_y1, z_t + 0.1).common(cyl_z(L.Ri + 0.5, z_f, z_t + 0.1))
tw, tx = p["travessia_w"], p["travessia_x"]
div = div.cut(box(tx - tw / 2, L.div_y0 - 1, z_f - 1, tx + tw / 2, L.div_y1 + 1, z_f + p["travessia_h"]))
adds.append(div)

furos_insert = []
for x, y in L.bosses():
    adds.append(cyl_z(p["boss_d"] / 2, z_f, z_t + 0.1, x, y).common(cyl_z(L.Ri + 0.5, z_f, z_t + 0.1)))
    furos_insert.append((x, y))
for x, y in L.colunas():
    adds.append(cyl_z(p["coluna_d"] / 2, z_f, z_t + 0.1, x, y))  # so apoio, sem insert
corpo = fuse_all([corpo] + adds)

cuts = []
for x, y in furos_insert:
    cuts.append(cyl_z(p["insert_furo"] / 2, z_f - 1, z_f + p["insert_prof"], x, y))
for a in L.aberturas:
    if a.forma == "ret":
        cuts.append(wall_rect(a.ang, a.zc, a.w, a.h))
    elif a.forma == "circ":
        cuts.append(wall_hole(a.ang, a.zc, a.w))
    elif a.forma == "furos":
        cuts.append(wall_hole(a.ang, a.zc, 2.5))
        for k in range(6):
            t = math.radians(60 * k)
            cuts.append(wall_hole(a.ang, a.zc, 2.5, 4.0 * math.cos(t), 4.0 * math.sin(t)))
for ang in L.vents():
    for zc in L.vent_zs():
        cuts.append(wall_rect(ang, zc, p["vent_l"], p["vent_w"]))
corpo = corpo.cut(cuts).removeSplitter()

# --- Peca 2: fundo ---------------------------------------------------------------
z_b = -L.H
fundo = cyl_z(L.R, z_b, z_f)
f_adds, f_cuts = [], []
for x, y in furos_insert:
    f_cuts.append(cyl_z(p["furo_passagem"] / 2, z_b - 1, z_f + 1, x, y))
    f_cuts.append(cyl_z(p["cabeca_d"] / 2, z_b - 1, z_b + p["cabeca_prof"], x, y))
for x, y in L.pes():
    f_cuts.append(cyl_z(p["pe_d"] / 2 + 0.25, z_b - 1, z_b + p["pe_rebaixo"], x, y))

comp = {c.nome: c for c in L.comps}
# Ventilacao sob o SSR: rasgos ao longo de y.
s = comp["ssr_dissipador"]
x0, y0, _, x1, y1, _ = s.box
yc, lv = (y0 + y1) / 2, p["vent_fundo_l"]
x = x0 + 5
while x <= x1 - 5:
    f_cuts.append(box(x - p["vent_fundo_w"] / 2, yc - lv / 2, z_b - 1, x + p["vent_fundo_w"] / 2, yc + lv / 2, z_f + 1))
    x += p["vent_fundo_passo"]

# Berco da placa base: dois trilhos com ranhura, batente no lado da antena (x min).
pb = comp["placa_base"]
bx0, by0, _, bx1, by1, _ = pb.box
tw_, th_, rz, rh, rp = (p["berco_trilho_w"], p["berco_trilho_h"], p["berco_ranhura_z"],
                         p["berco_ranhura_h"], p["berco_ranhura_p"])
fol = 0.3
for side in (-1, 1):
    if side < 0:
        ya, yb = by0 - fol - (tw_ - rp), by0 - fol + rp
        g0, g1 = by0 - fol, yb + 1
    else:
        ya, yb = by1 + fol - rp, by1 + fol + (tw_ - rp)
        g0, g1 = ya - 1, by1 + fol
    rail = box(bx0 - fol - 3, ya, z_f - 0.1, bx1, yb, z_f + th_)
    rail = rail.cut(box(bx0 - fol, g0, z_f + rz - 0.2, bx1 + 1, g1, z_f + rz - 0.2 + rh))
    f_adds.append(rail)
f_adds.append(box(bx0 - fol - 3, by0 - fol - (tw_ - rp), z_f - 0.1, bx0 - fol, by1 + fol + (tw_ - rp), z_f + th_))

# Berco da fonte A1385: duas paredes laterais + rasgos para abracadeira.
fz = comp["a1385"]
ax0, ay0, _, ax1, ay1, _ = fz.box
corpo_x1 = ax0 + 38.1 + p["fonte_folga"]  # corpo sem os pinos
fp, fh, ff = p["fonte_berco_parede"], p["fonte_berco_h"], p["fonte_folga"]
for ya, yb in ((ay0 - ff - fp, ay0 - ff), (ay1 + ff, ay1 + ff + fp)):
    f_adds.append(box(ax0, ya, z_f - 0.1, corpo_x1, yb, z_f + fh))
xm = (ax0 + corpo_x1) / 2
for yy in (ay0 - ff - fp - 3, ay1 + ff + fp + 1):
    f_cuts.append(box(xm - 2.5, yy, z_b - 1, xm + 2.5, yy + 2, z_f + 1))

fundo = fuse_all([fundo] + f_adds).cut(f_cuts).removeSplitter()

# --- validacao e exportacao ---------------------------------------------------------
for nome, shp in (("corpo", corpo), ("fundo", fundo)):
    if not shp.isValid():
        raise SystemExit("%s: solido invalido" % nome)
    print("%-6s valido, %d solido(s), volume %.0f cm3, caixa %s"
          % (nome, len(shp.Solids), shp.Volume / 1000, shp.BoundBox))
    if len(shp.Solids) != 1:
        raise SystemExit("%s: esperado 1 solido" % nome)

doc = App.newDocument("carcaca")
g_imp = doc.addObject("App::DocumentObjectGroup", "Impressao")
g_imp.Label = "Pecas impressas"
g_cmp = doc.addObject("App::DocumentObjectGroup", "Componentes")
g_cmp.Label = "Componentes internos (volumes de referencia)"
g_par = doc.addObject("App::DocumentObjectGroup", "Parede")
g_par.Label = "Componentes de parede (volumes de referencia)"
o1 = doc.addObject("Part::Feature", "Corpo")
o1.Shape = corpo
o1.Label = "Peca 1 - corpo (sem marca)"
o2 = doc.addObject("Part::Feature", "Fundo")
o2.Shape = fundo
o2.Label = "Peca 2 - fundo"
g_imp.addObjects([o1, o2])
refs = []
for c in L.comps:
    x0, y0, z0, x1, y1, z1 = c.box
    o = doc.addObject("Part::Feature", "ref_" + c.nome)
    o.Shape = box(x0, y0, z0, x1, y1, z1)
    o.Label = c.nome + (" (PROVISORIO)" if "PROVISORIO" in c.fonte else "")
    g_cmp.addObject(o)
    refs.append(o)
for a in L.aberturas:
    if a.cp <= 0:
        continue
    z0, z1 = a.corpo_z()
    b = box(L.Ri - a.cp, -a.cw / 2, z0, L.Ri, a.cw / 2, z1)
    o = doc.addObject("Part::Feature", "ref_" + a.nome)
    o.Shape = rot_z(b, a.ang)
    o.Label = a.nome + (" (PROVISORIO)" if "PROVISORIO" in a.fonte else "")
    g_par.addObject(o)
    refs.append(o)
# Sem interface, o FreeCAD grava os objetos como ocultos: ligar a visibilidade.
for o in doc.Objects:
    o.Visibility = True
doc.recompute()
doc.saveAs(os.path.join(OUT, "carcaca.FCStd"))
Part.export([o1, o2] + refs, os.path.join(OUT, "carcaca.step"))


def stl(shape, path):
    m = Mesh.Mesh()
    m.addFacets(shape.tessellate(0.05))
    m.write(path)
    print("escrito %s (%d triangulos)" % (os.path.relpath(path, os.path.dirname(OUT)), m.CountFacets))


stl(corpo, os.path.join(OUT, "corpo_sem_marca.stl"))
stl(fundo, os.path.join(OUT, "fundo.stl"))
print("ok")

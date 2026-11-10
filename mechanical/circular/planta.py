# /// script
# requires-python = ">=3.10"
# dependencies = ["matplotlib"]
# ///
"""Desenha a planta do arranjo interno (vista de cima, tampo removido).

    uv run mechanical/circular/planta.py   ->  mechanical/circular/planta.svg
"""
import math
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import Circle, Polygon, Rectangle  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from layout import Layout  # noqa: E402

L = Layout()
p = L.p
COR = {"baixa": "#4c8bd6", "rede": "#d65a4c", "estrutura": "#888888"}

fig, ax = plt.subplots(figsize=(9, 9))
ax.add_patch(Circle((0, 0), L.R, fill=False, lw=2, color="#333"))
ax.add_patch(Circle((0, 0), L.Ri, fill=False, lw=0.8, color="#333"))
xr = math.sqrt(L.Ri ** 2 - p["divisoria_y"] ** 2)
ax.add_patch(Rectangle((-xr, L.div_y0), 2 * xr, p["divisoria_t"], color=COR["estrutura"]))
tw = p["travessia_w"]
ax.add_patch(Rectangle((p["travessia_x"] - tw / 2, L.div_y0 - 0.5), tw, p["divisoria_t"] + 1, color="white"))
ax.annotate("travessia", (p["travessia_x"], L.div_y1), (p["travessia_x"] + 20, L.div_y1 + 12),
            fontsize=7, arrowprops=dict(arrowstyle="-", lw=0.5))

for c in L.comps:
    x0, y0, _, x1, y1, _ = c.box
    prov = "PROVISORIO" in c.fonte
    ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fill=True, alpha=0.18, color=COR[c.zona]))
    ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fill=False, lw=1, color=COR[c.zona],
                           ls="--" if prov else "-"))
    ax.text((x0 + x1) / 2, (y0 + y1) / 2, c.nome + ("\n(provisorio)" if prov else ""),
            ha="center", va="center", fontsize=7)
for a in L.aberturas:
    t = math.radians(a.ang)
    lx, ly = (L.R + 9) * math.cos(t), (L.R + 9) * math.sin(t)
    ax.text(lx, ly, a.nome, ha="center", va="center", fontsize=7, rotation=a.ang - 90 if abs(a.ang) > 0 else 0)
    if a.cp > 0:
        ax.add_patch(Polygon(a.corpo_xy(L.Ri)[:2] + a.corpo_xy(L.Ri)[2:][::-1], closed=True,
                             alpha=0.25, color=COR[a.zona]))
    if a.forma == "marca":
        half = (a.w / 2) / L.R
        th = [t - half + k * 2 * half / 30 for k in range(31)]
        ax.plot([(L.R + 2) * math.cos(v) for v in th], [(L.R + 2) * math.sin(v) for v in th], lw=3, color=COR["baixa"])
for ang in L.vents():
    t = math.radians(ang)
    ax.plot([L.Ri * math.cos(t), L.R * math.cos(t)], [L.Ri * math.sin(t), L.R * math.sin(t)], color="#555", lw=2)
for x, y in L.bosses():
    ax.add_patch(Circle((x, y), p["boss_d"] / 2, color=COR["estrutura"]))
for k, (x, y) in enumerate(L.colunas(), 1):
    ax.add_patch(Circle((x, y), p["coluna_d"] / 2, color=COR["estrutura"]))
    ax.text(x, y, "C%d" % k, ha="center", va="center", fontsize=6, color="white")
for x, y in L.pes():
    ax.add_patch(Circle((x, y), p["pe_d"] / 2, fill=False, ls=":", color="#333"))

ax.text(0, -L.R - 22, "FRENTE (baixa tensao)", ha="center", fontsize=9, color=COR["baixa"])
ax.text(0, L.R + 20, "TRAS (rede)", ha="center", fontsize=9, color=COR["rede"])
ax.set_title("Carcaca circular — planta (D = %.0f mm, altura %.0f mm)  |  cinza: divisoria, colunas, bosses;"
             " pontilhado: pes; tracejado: medida provisoria" % (2 * L.R, L.H), fontsize=8)
ax.set_xlim(-L.R - 30, L.R + 30)
ax.set_ylim(-L.R - 30, L.R + 30)
ax.set_aspect("equal")
ax.grid(True, lw=0.3, alpha=0.5)
out = os.path.join(HERE, "planta.svg")
fig.savefig(out, bbox_inches="tight")
fig.savefig(os.path.join(os.path.dirname(HERE), "build", "circular", "planta.png"), dpi=80, bbox_inches="tight")
print("escrito", os.path.relpath(out, os.path.dirname(os.path.dirname(HERE))))

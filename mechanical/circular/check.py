"""Verificacao do layout da carcaca circular (#302). Python puro, sem FreeCAD.

    python3 mechanical/circular/check.py

Sai com codigo 1 se alguma verificacao falhar. O que confere:
- cabe na impressora;
- componentes dentro do cilindro e entre o piso e o tampo;
- separacao rede / baixa tensao pela divisoria;
- sem colisao entre componentes, corpos das aberturas, colunas, bosses e divisoria;
- recortes com margem do tampo, do piso e entre si ao longo da parede;
- flecha de longo prazo do tampo com a cafeteira em cima.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout import Layout  # noqa: E402

L = Layout()
p = L.p
falhas = []


def ok(cond, msg):
    print(("  ok    " if cond else "  FALHA ") + msg)
    if not cond:
        falhas.append(msg)


# --- geometria 2D ----------------------------------------------------------
def circle_poly(cx, cy, r, n=24):
    return [(cx + r * math.cos(2 * math.pi * k / n), cy + r * math.sin(2 * math.pi * k / n)) for k in range(n)]


def convex_hull(pts):
    pts = sorted(set(pts))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for q in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], q) <= 0:
            lower.pop()
        lower.append(q)
    for q in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], q) <= 0:
            upper.pop()
        upper.append(q)
    return lower[:-1] + upper[:-1]


def _seg_dist(p0, a, b):
    ax, ay = b[0] - a[0], b[1] - a[1]
    t = max(0.0, min(1.0, ((p0[0] - a[0]) * ax + (p0[1] - a[1]) * ay) / (ax * ax + ay * ay or 1)))
    return math.hypot(p0[0] - a[0] - t * ax, p0[1] - a[1] - t * ay)


def _sat_overlap(A, B):
    for poly in (A, B):
        for i in range(len(poly)):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % len(poly)]
            nx, ny = y2 - y1, x1 - x2
            pa = [nx * x + ny * y for x, y in A]
            pb = [nx * x + ny * y for x, y in B]
            if max(pa) < min(pb) or max(pb) < min(pa):
                return False
    return True


def poly_dist(A, B):
    if _sat_overlap(A, B):
        return 0.0
    d = float("inf")
    for P, Q in ((A, B), (B, A)):
        for v in P:
            for i in range(len(Q)):
                d = min(d, _seg_dist(v, Q[i], Q[(i + 1) % len(Q)]))
    return d


# --- obstaculos: (nome, zona, poligono em planta, z0, z1) ------------------
obst = []
for c in L.comps:
    x0, y0, z0, x1, y1, z1 = c.box
    obst.append((c.nome, c.zona, [(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1))
for a in L.aberturas:
    if a.cp > 0:
        z0, z1 = a.corpo_z()
        obst.append((a.nome, a.zona, convex_hull(a.corpo_xy(L.Ri)), z0, z1))
for k, (x, y) in enumerate(L.colunas(), 1):
    obst.append(("coluna_%d" % k, "estrutura", circle_poly(x, y, p["coluna_d"] / 2), L.z_floor, L.z_teto))
for k, (x, y) in enumerate(L.bosses(), 1):
    obst.append(("boss_%d" % k, "estrutura", circle_poly(x, y, p["boss_d"] / 2), L.z_floor, L.z_teto))
# divisoria: retangulo de corda (o rasgo de travessia nao conta como livre para componentes)
xr = math.sqrt(L.Ri ** 2 - p["divisoria_y"] ** 2)
obst.append(("divisoria", "estrutura",
             [(-xr, L.div_y0), (xr, L.div_y0), (xr, L.div_y1), (-xr, L.div_y1)], L.z_floor, L.z_teto))

PARES_PERMITIDOS = {frozenset(s) for s in (("esp32", "placa_base"), ("esp32", "esp32_usb"),
                                           ("esp32_usb", "placa_base"), ("buzzer", "placa_base"))}

print("Carcaca circular: D = %.0f mm, altura = %.0f mm (corpo %.0f + fundo %.0f)"
      % (p["diametro_externo"], L.H, L.H_corpo, p["fundo"]))
print("Cavidade util: piso z = %.1f, tampo z = %.1f -> %.1f mm\n" % (L.z_floor, L.z_teto, L.z_teto - L.z_floor))

print("Impressora")
ok(p["diametro_externo"] <= p["impressora_xy"] - 5,
   "diametro %.0f mm <= mesa %.0f mm - 5 mm" % (p["diametro_externo"], p["impressora_xy"]))
ok(L.H_corpo <= p["impressora_z"], "altura do corpo %.0f mm <= %.0f mm" % (L.H_corpo, p["impressora_z"]))

print("\nComponentes dentro do cilindro, entre piso e tampo, na zona certa")
for c in L.comps:
    rmax = max(math.hypot(x, y) for x, y in c.corners_xy())
    ok(rmax <= L.Ri - 1, "%-15s raio max %.1f <= %.1f" % (c.nome, rmax, L.Ri - 1))
    ok(c.z >= L.z_floor - 1e-6 and c.box[5] <= L.z_teto - p["folga_teto"],
       "%-15s z %.1f..%.1f dentro de %.1f..%.1f" % (c.nome, c.z, c.box[5], L.z_floor, L.z_teto - p["folga_teto"]))
for nome, zona, poly, z0, z1 in obst:
    if zona == "baixa":
        ymax = max(y for _, y in poly)
        ok(ymax <= L.div_y0, "%-15s baixa tensao: y max %.1f <= divisoria %.1f" % (nome, ymax, L.div_y0))
    elif zona == "rede":
        ymin = min(y for _, y in poly)
        lim = L.div_y1 + p["divisoria_folga_rede"]
        ok(ymin >= lim, "%-15s rede: y min %.1f >= divisoria + folga %.1f" % (nome, ymin, lim))

print("\nColisoes (folga minima %.1f mm em planta quando as alturas se sobrepoem)" % p["folga_comp"])
n_col = 0
for i in range(len(obst)):
    for j in range(i + 1, len(obst)):
        a, b = obst[i], obst[j]
        if frozenset((a[0], b[0])) in PARES_PERMITIDOS:
            continue
        if a[2 - 2] == "divisoria" or b[0] == "divisoria":
            pass
        if min(a[4], b[4]) - max(a[3], b[3]) <= 0:
            continue  # alturas nao se sobrepoem
        if a[1] == "estrutura" and b[1] == "estrutura":
            continue
        d = poly_dist(a[2], b[2])
        need = 0.0 if "divisoria" in (a[0], b[0]) else p["folga_comp"]
        if d < need - 1e-6:
            ok(False, "%s x %s: distancia %.1f < %.1f" % (a[0], b[0], d, need))
            n_col += 1
ok(n_col == 0, "nenhuma colisao entre %d volumes" % len(obst))

print("\nRecortes na parede")
feats = []  # (nome, angulo, meia-largura em graus, z0, z1)
for a in L.aberturas:
    z0, z1 = a.zc - a.h / 2, a.zc + a.h / 2
    if a.forma != "marca":
        ok(z0 >= L.z_floor + p["margem_abertura_z"] and z1 <= L.z_teto - p["margem_abertura_z"],
           "%-12s z %.1f..%.1f com margem de %.0f mm do piso e do tampo" % (a.nome, z0, z1, p["margem_abertura_z"]))
    feats.append((a.nome, a.ang, math.degrees((a.w / 2) / L.R), z0, z1, a.forma))
for k, ang in enumerate(L.bosses(), 1):
    pass
for k in range(int(p["boss_qtd"])):
    feats.append(("boss_%d" % (k + 1), p["boss_ang_%d" % (k + 1)], math.degrees((p["boss_d"] / 2) / L.Ri),
                  L.z_floor, L.z_teto, "boss"))
vz = L.vent_zs()
feats.append(("ventilacao", (p["vent_ang_ini"] + p["vent_ang_fim"]) / 2,
              (p["vent_ang_fim"] - p["vent_ang_ini"]) / 2 + math.degrees((p["vent_l"] / 2) / L.R),
              vz[0] - p["vent_w"], vz[1] + p["vent_w"], "vent"))


def ang_gap(a1, a2):
    d = abs((a1 - a2 + 180) % 360 - 180)
    return d


for i in range(len(feats)):
    for j in range(i + 1, len(feats)):
        A, B = feats[i], feats[j]
        if {A[5], B[5]} == {"marca", "boss"} or (A[5] == "boss" and B[5] == "boss"):
            continue  # gravacao rasa nao conflita com boss interno
        if min(A[4], B[4]) - max(A[3], B[3]) <= 0:
            continue
        gap_mm = math.radians(ang_gap(A[1], B[1]) - A[2] - B[2]) * L.R
        if gap_mm < p["margem_parede"]:
            ok(False, "%s x %s: so %.1f mm de parede entre eles" % (A[0], B[0], gap_mm))
ok(True, "espacamento entre %d recortes, bosses e ventilacao conferido" % len(feats))

print("\nPes de borracha")
cab = [(x, y, p["cabeca_d"] / 2) for x, y in L.bosses()]
for k, (x, y) in enumerate(L.pes(), 1):
    rr = math.hypot(x, y) + p["pe_d"] / 2
    ok(rr <= L.R - 1, "pe %d dentro do fundo (raio externo %.1f)" % (k, rr))
    dmin = min(math.hypot(x - cx, y - cy) - p["pe_d"] / 2 - cr for cx, cy, cr in cab)
    ok(dmin >= 1, "pe %d longe das cabecas dos parafusos (%.1f mm)" % (k, dmin))
cols = L.colunas()
sob = sum(1 for cx, cy in cols if any(math.hypot(cx - x, cy - y) < 1 for x, y in L.pes()))
ok(sob == len(cols), "%d de %d colunas com pe logo abaixo" % (sob, len(cols)))

print("\nTampo com a cafeteira em cima (estimativa analitica, nao FEA)")
# Maior circulo vazio entre apoios (parede, divisoria, colunas): placa circular
# simplesmente apoiada sob carga uniforme (Roark). Nervuras sao ignoradas (conservador).
step = 2.0
a_max, at = 0.0, (0, 0)
cols = L.colunas()
y = -L.Ri
while y <= L.Ri:
    x = -L.Ri
    while x <= L.Ri:
        r = math.hypot(x, y)
        if r < L.Ri:
            d = L.Ri - r
            d = min(d, max(0.0, abs(y - p["divisoria_y"]) - p["divisoria_t"] / 2))
            for cx, cy in cols:
                d = min(d, max(0.0, math.hypot(x - cx, y - cy) - p["coluna_d"] / 2))
            if d > a_max:
                a_max, at = d, (x, y)
        x += step
    y += step
q = p["carga_operacao"] * 9.81 / (math.pi * p["carga_raio_eq"] ** 2)  # N/mm2
E, nu, t = p["E_petg"], p["nu_petg"], p["tampo"]
Df = E * t ** 3 / (12 * (1 - nu ** 2))
w_el = (5 + nu) / (1 + nu) * q * a_max ** 4 / (64 * Df)
w_lp = w_el * p["fator_fluencia"]
lim = 2 * a_max / 300
print("  maior vao sem apoio: circulo de raio %.0f mm centrado em (%.0f, %.0f)" % (a_max, at[0], at[1]))
print("  flecha elastica %.3f mm; de longo prazo (x%.0f) %.3f mm; limite L/300 = %.3f mm"
      % (w_el, p["fator_fluencia"], w_lp, lim))
ok(w_lp <= lim, "flecha de longo prazo do tampo dentro de L/300")

print("\nAltura")
topo = max(c.box[5] for c in L.comps)
alto = max(L.comps, key=lambda c: c.box[5])
print("  componente mais alto: %s, topo em z = %.1f; folga ate o tampo %.1f mm"
      % (alto.nome, topo, L.z_teto - topo))
prov = [c.nome for c in L.comps if "PROVISORIO" in c.fonte] + \
       [a.nome for a in L.aberturas if "PROVISORIO" in a.fonte]
if prov:
    print("  ATENCAO: medidas provisorias em: %s" % ", ".join(prov))

print("\n%s" % ("TUDO OK" if not falhas else "%d FALHA(S)" % len(falhas)))
sys.exit(1 if falhas else 0)

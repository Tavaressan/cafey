"""Modelo de layout da carcaca circular (#302), sem dependencia do FreeCAD.

Le os tres CSVs desta pasta e calcula a geometria derivada. E' usado pelo
build (FreeCAD) e pela verificacao (python puro), para os dois enxergarem
exatamente os mesmos numeros.

Sistema de coordenadas
----------------------
- Origem no centro da face SUPERIOR do tampo; o corpo fica em z < 0.
- y < 0: frente (baixa tensao: botao, marca, ESP32).
- y > 0: tras (rede: cabo de forca, tomada, disjuntor, SSR, fonte).
- x > 0: direita de quem olha a frente.
- Angulos em graus, a partir de +x, sentido anti-horario visto de cima:
  frente = -90, tras = +90.
"""
import csv
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def _rows(name):
    with open(os.path.join(HERE, name), newline="", encoding="utf-8") as fh:
        for r in csv.reader(fh):
            if not r or r[0].strip().startswith("#"):
                continue
            yield [c.strip() for c in r]


def load_params():
    rows = list(_rows("parametros.csv"))
    head, body = rows[0], rows[1:]
    assert head[:2] == ["alias", "value"], head
    p = {}
    for r in body:
        p[r[0]] = float(r[1])
    return p


class Comp:
    """Volume de referencia (caixa) de um componente interno."""

    def __init__(self, nome, zona, apoio, dx, dy, dz, x, y, z, fonte, z_floor):
        self.nome, self.zona, self.apoio, self.fonte = nome, zona, apoio, fonte
        self.dx, self.dy, self.dz = dx, dy, dz
        self.x, self.y = x, y
        self.z = z_floor + z  # CSV: z relativo ao piso (topo do fundo)

    @property
    def box(self):
        return (self.x, self.y, self.z, self.x + self.dx, self.y + self.dy, self.z + self.dz)

    def corners_xy(self):
        x0, y0, _, x1, y1, _ = self.box
        return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


class Abertura:
    """Furo/recorte na parede cilindrica, com o volume interno do componente."""

    def __init__(self, r, p):
        (self.nome, self.zona, self.forma, ang, zc, w, h, cw, ch, cp) = r[:10]
        self.fonte = ", ".join(r[10:])
        self.ang = float(ang)
        self.zc = float(zc)              # centro, relativo a face superior (negativo)
        self.w, self.h = float(w), float(h)  # recorte: largura (ao longo da parede) e altura
        self.cw, self.ch, self.cp = float(cw), float(ch), float(cp)  # corpo interno
        self.p = p

    def corpo_xy(self, ri):
        """Cantos (planta) do corpo interno: retangulo radial de largura cw e
        profundidade cp a partir da face interna da parede."""
        a = math.radians(self.ang)
        ur = (math.cos(a), math.sin(a))      # radial para fora
        ut = (-math.sin(a), math.cos(a))     # tangente
        pts = []
        for d in (ri, ri - self.cp):
            for s in (-self.cw / 2, self.cw / 2):
                pts.append((ur[0] * d + ut[0] * s, ur[1] * d + ut[1] * s))
        return pts

    def corpo_z(self):
        return (self.zc - self.ch / 2, self.zc + self.ch / 2)

    def meia_largura_ang(self, r):
        return math.degrees((max(self.w, self.cw) / 2) / r)


class Layout:
    def __init__(self):
        p = self.p = load_params()
        self.R = p["diametro_externo"] / 2
        self.Ri = self.R - p["parede"]
        self.H = p["altura_externa"]
        self.H_corpo = self.H - p["fundo"]       # Peca 1 (corpo) vai de z=-H_corpo a 0
        self.z_floor = -self.H_corpo             # topo do fundo = piso interno
        self.z_teto = -p["tampo"]                # face inferior do tampo
        self.div_y0 = p["divisoria_y"] - p["divisoria_t"] / 2
        self.div_y1 = p["divisoria_y"] + p["divisoria_t"] / 2

        self.comps = []
        rows = list(_rows("componentes.csv"))
        for r in rows[1:]:
            nome, zona, apoio = r[0], r[1], r[2]
            dx, dy, dz, x, y, z = (float(v) for v in r[3:9])
            self.comps.append(Comp(nome, zona, apoio, dx, dy, dz, x, y, z, ", ".join(r[9:]), self.z_floor))

        self.aberturas = [Abertura(r, p) for r in list(_rows("aberturas.csv"))[1:]]

    # --- elementos estruturais ------------------------------------------------
    def bosses(self):
        """(x, y) dos bosses de insert colados na parede."""
        r = self.Ri - self.p["boss_d"] / 2 + 0.5
        out = []
        for k in range(int(self.p["boss_qtd"])):
            a = math.radians(self.p["boss_ang_%d" % (k + 1)])
            out.append((r * math.cos(a), r * math.sin(a)))
        return out

    def colunas(self):
        out = []
        k = 1
        while "coluna_%d_x" % k in self.p:
            out.append((self.p["coluna_%d_x" % k], self.p["coluna_%d_y" % k]))
            k += 1
        return out

    def vents(self):
        """Angulos dos rasgos de ventilacao da parede (perto do SSR)."""
        a0, a1, st = self.p["vent_ang_ini"], self.p["vent_ang_fim"], self.p["vent_passo"]
        n = int(round((a1 - a0) / st)) + 1
        return [a0 + i * st for i in range(n)]

    def vent_zs(self):
        """Centros (z) das duas fileiras de rasgos: baixa (entrada) e alta (saida)."""
        return (self.z_floor + self.p["vent_z_piso"], self.z_teto - self.p["vent_z_teto"])

    def pes(self):
        out = []
        k = 1
        while "pe_%d_x" % k in self.p:
            out.append((self.p["pe_%d_x" % k], self.p["pe_%d_y" % k]))
            k += 1
        return out

    def marca_largura(self):
        """Largura da composicao [simbolo] Caféy: ~3,90 x a altura do simbolo (#303)."""
        return 3.90 * self.p["marca_altura_simbolo"]

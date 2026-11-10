# /// script
# requires-python = ">=3.10"
# dependencies = ["shapely>=2.0", "fonttools>=4.40", "uharfbuzz>=0.39"]
# ///
"""Gera a composicao horizontal monocromatica da marca para a peca fisica (#303).

    [SIMBOLO]  Caféy

- O simbolo vem da mesma geometria de gen_logo.py (importada, nao redesenhada).
  Os tracos (stroke) viram contornos preenchidos com pontas e juncoes
  arredondadas, como no SVG original.
- O texto vem da Space Grotesk variavel dos apps, moldada pelo HarfBuzz
  (kerning da fonte), com peso e tracking ajustados para FDM.
- A saida e um unico <path> preenchido, pronto para importar no CAD.

Uso (nesta pasta):  uv run gen_lockup.py
"""
import math
import os
import sys
from functools import reduce

import uharfbuzz as hb
from fontTools.pens.basePen import BasePen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from shapely.geometry import LineString, MultiPolygon, Polygon
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_logo as logo  # noqa: E402  (geometria original do simbolo)

FONT = os.path.join(HERE, "..", "..", "..", "apps", "shared", "src", "commonMain",
                    "composeResources", "font", "space_grotesk_variable.ttf")

# --- parametros do lettering para FDM ---------------------------------------
WEIGHT = 700          # apps usam 600 (SemiBold); 700 engrossa as hastes
TRACKING_EM = 0.02    # apps usam -0.03 em; positivo abre o espaco entre letras
TEXT_TO_ART = 0.9375  # tamanho da fonte / altura do simbolo (27 sp / 28,8 dp nos apps)
GAP_TO_ART = 0.62     # espaco simbolo -> texto / altura do simbolo (~18 dp / 28,8 dp)
MARGIN = 0.08         # margem em volta, fracao da altura do simbolo
CURVE_STEPS = 48      # segmentos por curva ao achatar Beziers

ART_H = logo.ART[3]   # 356 unidades: altura visual do simbolo


# --- simbolo: tracos -> areas ---------------------------------------------------
def _cubic(p0, p1, p2, p3, n):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t ** 2, t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0],
                    a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def _quad(p0, p1, p2, n):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t ** 2
        out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return out


def flatten_path(d):
    """Achata um path SVG absoluto (M, C, Q, Z). Retorna (pontos, fechado)."""
    tok = d.replace(",", " ").split()
    pts, closed, i, cmd = [], False, 0, None
    while i < len(tok):
        if tok[i] in "MCQZ":
            cmd = tok[i]
            i += 1
            if cmd == "Z":
                closed = True
                continue
        nums = lambda k: [float(x) for x in tok[i:i + k]]  # noqa: E731
        if cmd == "M":
            x, y = nums(2)
            pts.append((x, y))
            i += 2
        elif cmd == "C":
            x1, y1, x2, y2, x, y = nums(6)
            pts += _cubic(pts[-1], (x1, y1), (x2, y2), (x, y), CURVE_STEPS)
            i += 6
        elif cmd == "Q":
            x1, y1, x, y = nums(4)
            pts += _quad(pts[-1], (x1, y1), (x, y), CURVE_STEPS)
            i += 4
        else:
            raise ValueError("comando SVG nao suportado: %s" % cmd)
    return pts, closed


def stroke(d, width):
    pts, closed = flatten_path(d)
    line = LineString(pts + ([pts[0]] if closed else []))
    return line.buffer(width / 2.0, quad_segs=32, cap_style="round", join_style="round")


def symbol_parts():
    outline = stroke(logo.OUTLINE, logo.STROKE_OUTLINE)
    inner = unary_union([stroke(logo.s_curve(-1), logo.STROKE_INNER),
                         stroke(logo.s_curve(+1), logo.STROKE_INNER)])
    star = Polygon(flatten_path(logo.star_path())[0])
    return outline, inner, star


# --- texto: Space Grotesk -> areas ----------------------------------------------
class FlattenPen(BasePen):
    def __init__(self, glyphset):
        super().__init__(glyphset)
        self.contours, self.cur = [], []

    def _moveTo(self, p):
        self.cur = [p]

    def _lineTo(self, p):
        self.cur.append(p)

    def _curveToOne(self, p1, p2, p3):
        self.cur += _cubic(self.cur[-1], p1, p2, p3, CURVE_STEPS // 2)

    def _qCurveToOne(self, p1, p2):
        self.cur += _quad(self.cur[-1], p1, p2, CURVE_STEPS // 2)

    def _closePath(self):
        if len(self.cur) > 2:
            self.contours.append(self.cur)
        self.cur = []

    _endPath = _closePath


def text_geometry(text):
    """Texto moldado pelo HarfBuzz; coordenadas em unidades da fonte, y para cima."""
    blob = hb.Blob.from_file_path(FONT)
    face = hb.Face(blob)
    font = hb.Font(face)
    font.set_variations({"wght": WEIGHT})
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf, {"kern": True, "liga": False})

    tt = instantiateVariableFont(TTFont(FONT), {"wght": WEIGHT}, inplace=False)
    upem = tt["head"].unitsPerEm
    order = tt.getGlyphOrder()
    gs = tt.getGlyphSet()
    track = TRACKING_EM * upem

    x, polys = 0.0, []
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        pen = FlattenPen(gs)
        gs[order[info.codepoint]].draw(pen)
        rings = [Polygon([(px + x + pos.x_offset, py + pos.y_offset) for px, py in c]).buffer(0)
                 for c in pen.contours]
        if rings:
            polys.append(reduce(lambda a, b: a.symmetric_difference(b), rings))
        x += pos.x_advance + track
    cap = tt["OS/2"].sCapHeight
    return unary_union(polys), upem, cap


# --- composicao -----------------------------------------------------------------
def transform(geom, fn):
    from shapely import transform as shp_transform
    import numpy as np
    return shp_transform(geom, lambda c: np.array([fn(x, y) for x, y in c]))


def build(text):
    outline, inner, star = symbol_parts()
    symbol = unary_union([outline, inner, star])
    sx0, sy0, sx1, sy1 = symbol.bounds

    glyphs, upem, cap = text_geometry(text)
    scale = TEXT_TO_ART * ART_H / upem
    # Centro da altura das maiusculas alinhado ao centro do simbolo.
    sym_cy = (sy0 + sy1) / 2.0
    gx0 = glyphs.bounds[0]
    tx = sx1 + GAP_TO_ART * ART_H - gx0 * scale
    ty = sym_cy + (cap / 2.0) * scale
    text_geom = transform(glyphs, lambda x, y: (tx + x * scale, ty - y * scale))

    lockup = unary_union([symbol, text_geom])
    m = MARGIN * ART_H
    x0, y0, x1, y1 = lockup.bounds
    shifted = transform(lockup, lambda x, y: (x - x0 + m, y - y0 + m))
    w, h = x1 - x0 + 2 * m, y1 - y0 + 2 * m
    return dict(lockup=shifted, w=w, h=h, symbol=symbol, outline=outline, inner=inner,
                star=star, text=text_geom, cap_px=cap * scale, art_h=sy1 - sy0)


def to_path(geom):
    polys = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    parts = []
    for p in polys:
        for ring in [p.exterior, *p.interiors]:
            c = list(ring.coords)[:-1]
            parts.append("M " + " L ".join("%.2f %.2f" % xy for xy in c) + " Z")
    return " ".join(parts)


def write_svg(path, geom, w, h, title):
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.2f} {h:.2f}" '
           'width="{w:.0f}" height="{h:.0f}">\n  <title>{t}</title>\n'
           '  <path fill="#000000" fill-rule="evenodd" d="{d}"/>\n</svg>\n').format(
        w=w, h=h, t=title, d=to_path(geom))
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(svg)
    print("%-34s %7d bytes  viewBox %.1f x %.1f" % (os.path.basename(path), len(svg), w, h))


# --- metricas para FDM ------------------------------------------------------------
def thinnest(geom, lo=0.0, hi=40.0, loss=0.01):
    """Largura do trecho mais fino: menor 2r cuja abertura (erosao + dilatacao)
    remove mais que `loss` da area."""
    area = geom.area
    for _ in range(30):
        r = (lo + hi) / 2
        opened = geom.buffer(-r, quad_segs=16).buffer(r, quad_segs=16)
        if area - opened.area > loss * area:
            hi = r
        else:
            lo = r
    return 2 * hi


def narrowest_gap(geom, lo=0.0, hi=40.0, gain=0.002):
    """Abertura mais estreita entre partes/dentro das letras: menor 2r cujo
    fechamento (dilatacao + erosao) acrescenta mais que `gain` da area."""
    area = geom.area
    for _ in range(30):
        r = (lo + hi) / 2
        closed = geom.buffer(r, quad_segs=16).buffer(-r, quad_segs=16)
        if closed.area - area > gain * area:
            hi = r
        else:
            lo = r
    return 2 * hi


def report(b):
    art = b["art_h"]
    rows = [
        ("Simbolo: traco do contorno", logo.STROKE_OUTLINE),
        ("Simbolo: traco das curvas internas", logo.STROKE_INNER),
        ("Simbolo: folga contorno x curvas", b["outline"].distance(b["inner"])),
        ("Simbolo: folga estrela x curvas", b["star"].distance(b["inner"])),
        ("Texto: trecho mais fino", thinnest(b["text"])),
        ("Texto: abertura mais estreita", narrowest_gap(b["text"])),
    ]
    print("\nAltura do simbolo = %.1f u; altura das maiusculas = %.1f u (%.2f do simbolo)"
          % (art, b["cap_px"], b["cap_px"] / art))
    print("%-38s %8s %8s   %s" % ("Detalhe", "u", "/simb.", "mm com simbolo de 15 / 20 / 25 / 30 mm"))
    for name, v in rows:
        f = v / art
        print("%-38s %8.1f %8.4f   %s" % (name, v, f, " / ".join("%.2f" % (f * hmm) for hmm in (15, 20, 25, 30))))
    return rows


if __name__ == "__main__":
    # Grafia decidida pelo responsavel em 09/11/2026: "Caféy" (igual aos apps).
    # A versao sem acento fica como alternativa.
    for text, name in (("Caféy", "cafey-lockup-mono.svg"), ("Cafey", "cafey-lockup-mono-sem-acento.svg")):
        b = build(text)
        write_svg(os.path.join(HERE, name), b["lockup"], b["w"], b["h"], "%s — composição monocromática" % text)
        if text == "Caféy":
            report(b)
            sym = b["symbol"]
            x0, y0, x1, y1 = sym.bounds
            m = MARGIN * ART_H
            write_svg(os.path.join(HERE, "cafey-symbol-mono.svg"),
                      transform(sym, lambda x, y: (x - x0 + m, y - y0 + m)),
                      x1 - x0 + 2 * m, y1 - y0 + 2 * m, "Cafey — símbolo monocromático")

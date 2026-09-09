"""Gera os SVGs da marca Cafey a partir de parametros geometricos.

O desenho e' um grao de cafe: contorno eliptico, duas curvas em S internas
(mesma forma transladada, nao espelhada) e uma estrela de quatro pontas ao
centro. Cores extraidas do esboco original cafey-idea-logo.png.

Uso:  python gen_logo.py
"""
import math

# --- paleta (amostrada de cafey-idea-logo.png) ---
BEAN_COLOR = "#d2784d"   # vermelho queimado
STAR_COLOR = "#7092be"   # azul acinzentado

# --- geometria, em unidades do viewBox base de 512 ---
CX, CY = 256.0, 256.0
RX, RY = 116.0, 170.0          # semieixos do contorno
Y0, Y1 = 104.0, 408.0          # extensao vertical dos S internos
SEP = 46.0                     # meia-distancia entre as duas curvas
AMP = 21.0                     # amplitude da sinuosidade
CLEAR = 18.0                   # folga minima entre curva interna e contorno
STAR_R, STAR_CTL = 30.0, 7.0   # raio da estrela e recuo do controle (afilamento)
STROKE_OUTLINE, STROKE_INNER = 16.0, 13.0

BASE = 512.0                   # lado do viewBox base
ART = (132.0, 78.0, 248.0, 356.0)   # bbox visual medida com: inkscape -X -Y -W -H

OUTLINE = ("M 256 86 C 322 86 372 162 372 252 C 372 348 320 426 256 426 "
           "C 192 426 140 348 140 252 C 140 162 190 86 256 86 Z")


def catmull_to_bezier(points):
    """Converte polilinha em path cubico suave (Catmull-Rom uniforme)."""
    p = [points[0]] + list(points) + [points[-1]]
    d = "M %.1f %.1f" % points[0]
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
        d += " C %.1f %.1f %.1f %.1f %.1f %.1f" % (c1 + c2 + p2)
    return d


def envelope(t):
    """Afina o deslocamento perto das pontas, evitando o coto horizontal
    que o clamp contra o contorno produzia."""
    return math.sin(math.pi * t) ** 0.3


def wave(t):
    """Periodo unico: cruza zero em t=0.5, o que mantem a estrela
    equidistante das duas curvas."""
    return AMP * math.sin(2.0 * math.pi * t)


def s_curve(side, samples=15):
    """side=-1 esquerda, +1 direita. Mesma forma transladada, nao espelhada."""
    points = []
    for i in range(samples):
        t = i / (samples - 1.0)
        y = Y0 + t * (Y1 - Y0)
        offset = (side * SEP + wave(t)) * envelope(t)
        k = abs(y - CY) / RY
        limit = RX * math.sqrt(max(0.0, 1.0 - k * k)) - CLEAR
        offset = math.copysign(min(abs(offset), max(0.0, limit)), offset)
        points.append((CX + offset, y))
    return catmull_to_bezier(points)


def star_path():
    r, c = STAR_R, STAR_CTL
    return ("M {cx} {t} Q {cxp} {cyp} {r_} {cy} Q {cxp} {cyn} {cx} {b} "
            "Q {cxn} {cyn} {l_} {cy} Q {cxn} {cyp} {cx} {t} Z").format(
        cx=CX, cy=CY, t=CY - r, b=CY + r, r_=CX + r, l_=CX - r,
        cxp=CX + c, cxn=CX - c, cyp=CY - c, cyn=CY + c)


def render(path, size=BASE, fill_fraction=None):
    """Escreve um SVG quadrado. fill_fraction=None mantem o enquadramento
    da marca; um valor define a fracao da altura do quadro ocupada pelo
    desenho (0.6667 = zona segura de 72dp do icone adaptativo Android)."""
    if fill_fraction is None:
        open_g, close_g = "", ""
    else:
        scale = fill_fraction * size / ART[3]
        shift = size / 2.0 - CX * scale
        open_g = '<g transform="translate(%.3f %.3f) scale(%.5f)">' % (shift, shift, scale)
        close_g = "</g>"

    body = (
        '  <g fill="none" stroke="{bean}" stroke-linecap="round" stroke-linejoin="round">\n'
        '    <path d="{outline}" stroke-width="{sw_out}"/>\n'
        '    <path d="{left}" stroke-width="{sw_in}"/>\n'
        '    <path d="{right}" stroke-width="{sw_in}"/>\n'
        '  </g>\n'
        '  <path d="{star}" fill="{star_color}"/>\n'
    ).format(bean=BEAN_COLOR, outline=OUTLINE, left=s_curve(-1), right=s_curve(+1),
             sw_out=STROKE_OUTLINE, sw_in=STROKE_INNER,
             star=star_path(), star_color=STAR_COLOR)

    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {s:.0f} {s:.0f}" '
           'width="{s:.0f}" height="{s:.0f}">\n{o}{b}{c}</svg>\n').format(
        s=size, o=open_g + "\n" if open_g else "", b=body, c=close_g + "\n" if close_g else "")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(svg)
    print("%-32s %5d bytes" % (path, len(svg)))


if __name__ == "__main__":
    render("cafey-logo.svg")                             # marca, com respiro
    render("cafey-icon.svg", fill_fraction=0.80)         # icone generico
    render("cafey-icon-foreground.svg", fill_fraction=72.0 / 108.0)  # zona segura Android

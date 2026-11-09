#!/usr/bin/env python3
"""Gera os ícones do launcher Android a partir da geometria da marca Cafey.

Produz, de forma reprodutível:
- `apps/androidApp/src/main/res/drawable/ic_launcher_foreground.xml` (vector, zona segura)
- `apps/androidApp/src/main/res/drawable/ic_launcher_monochrome.xml` (silhueta, Android 13+)
- PNGs legados em `apps/androidApp/src/main/res/mipmap-*/` (API < 26)
- `docs/docs_interface/logo/cafey-icon-foreground.svg` atualizado (zona segura 60/108 dp)
- Prévia opcional com máscaras em `--preview-dir` (fora do git, ex.: apps/androidApp/build/icon-preview)

Requer: python3, rsvg-convert, magick (ImageMagick).

Uso (a partir da raiz do repositório):
  python3 docs/docs_interface/logo/generate_android_icons.py
  python3 docs/docs_interface/logo/generate_android_icons.py --preview-dir apps/androidApp/build/icon-preview
"""
from __future__ import annotations

import argparse
import math
import os
import subprocess
import sys
from pathlib import Path

# --- paleta (amostrada de cafey-idea-logo.png) ---
BEAN_COLOR = "#d2784d"   # vermelho queimado
STAR_COLOR = "#7092be"   # azul acinzentado
BG_COLOR = "#FAF8F5"     # ic_launcher_background

# --- geometria, em unidades do viewBox base de 512 ---
CX, CY = 256.0, 256.0
RX, RY = 116.0, 170.0
Y0, Y1 = 104.0, 408.0
SEP = 46.0
AMP = 21.0
CLEAR = 18.0
STAR_R, STAR_CTL = 30.0, 7.0
STROKE_OUTLINE, STROKE_INNER = 16.0, 13.0
BASE = 512.0
# bbox visual medida com inkscape -X -Y -W -H (usada por gen_logo.py)
ART = (132.0, 78.0, 248.0, 356.0)

OUTLINE = (
    "M 256 86 C 322 86 372 162 372 252 C 372 348 320 426 256 426 "
    "C 192 426 140 348 140 252 C 140 162 190 86 256 86 Z"
)

# Zona segura do ícone adaptativo = 66 dp num canvas de 108 dp.
# Usamos 60 dp de altura para o grão, centralizado (folga confortável).
SAFE_FILL_FRACTION = 60.0 / 108.0

DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def catmull_to_bezier(points):
    p = [points[0]] + list(points) + [points[-1]]
    d = "M %.1f %.1f" % points[0]
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6.0, p1[1] + (p2[1] - p0[1]) / 6.0)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6.0, p2[1] - (p3[1] - p1[1]) / 6.0)
        d += " C %.1f %.1f %.1f %.1f %.1f %.1f" % (c1 + c2 + p2)
    return d


def envelope(t):
    return math.sin(math.pi * t) ** 0.3


def wave(t):
    return AMP * math.sin(2.0 * math.pi * t)


def s_curve(side, samples=15):
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
    return (
        "M {cx} {t} Q {cxp} {cyp} {r_} {cy} Q {cxp} {cyn} {cx} {b} "
        "Q {cxn} {cyn} {l_} {cy} Q {cxn} {cyp} {cx} {t} Z"
    ).format(
        cx=CX, cy=CY, t=CY - r, b=CY + r, r_=CX + r, l_=CX - r,
        cxp=CX + c, cxn=CX - c, cyp=CY - c, cyn=CY + c,
    )


def safe_transform(fill_fraction=SAFE_FILL_FRACTION, size=BASE):
    scale = fill_fraction * size / ART[3]
    shift = size / 2.0 - CX * scale
    return shift, scale


def tx(x, y, shift, scale, vp=108.0):
    # base(512) -> transformed(512) -> viewport(108)
    f = vp / BASE
    return (shift + x * scale) * f, (shift + y * scale) * f


def transform_path_d(d, shift, scale, vp=108.0):
    # Transforma comandos SVG absolutos M/C/Q/Z usados pela geometria da marca.
    import re

    tokens = re.findall(r"[MCQZ]|-?\d*\.?\d+", d)
    out = []
    i = 0
    cmd = None

    def fmt(v):
        return ("%.3f" % v).rstrip("0").rstrip(".")

    while i < len(tokens):
        t = tokens[i]
        if t in "MCQZ":
            cmd = t
            out.append(t)
            i += 1
            continue
        if cmd in ("M",):
            x, y = float(tokens[i]), float(tokens[i + 1])
            x2, y2 = tx(x, y, shift, scale, vp)
            out.extend([fmt(x2), fmt(y2)])
            i += 2
        elif cmd == "C":
            vals = [float(tokens[i + k]) for k in range(6)]
            for k in (0, 2, 4):
                x2, y2 = tx(vals[k], vals[k + 1], shift, scale, vp)
                out.extend([fmt(x2), fmt(y2)])
            i += 6
        elif cmd == "Q":
            vals = [float(tokens[i + k]) for k in range(4)]
            for k in (0, 2):
                x2, y2 = tx(vals[k], vals[k + 1], shift, scale, vp)
                out.extend([fmt(x2), fmt(y2)])
            i += 4
        else:
            # fallback: copia
            out.append(t)
            i += 1
    return " ".join(out)


def transform_stroke(sw, shift, scale, vp=108.0):
    # stroke aplica na escala visual do desenho
    return sw * scale * (vp / BASE)


def render_svg(path, fill_fraction=None, size=BASE):
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
    ).format(
        bean=BEAN_COLOR, outline=OUTLINE, left=s_curve(-1), right=s_curve(+1),
        sw_out=STROKE_OUTLINE, sw_in=STROKE_INNER,
        star=star_path(), star_color=STAR_COLOR,
    )
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {s:.0f} {s:.0f}" '
        'width="{s:.0f}" height="{s:.0f}">\n{o}{b}{c}</svg>\n'
    ).format(
        s=size, o=open_g + "\n" if open_g else "", b=body,
        c=close_g + "\n" if close_g else "",
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(svg, encoding="utf-8")
    print(f"  svg  {path} ({len(svg)} bytes)")


def render_monochrome_svg(path, fill_fraction=SAFE_FILL_FRACTION, size=BASE):
    # Monocromático: mesmo desenho da logo (contorno, curvas em S, estrela),
    # em uma única cor opaca. O sistema aplica o tingimento do tema ao alfa.
    scale = fill_fraction * size / ART[3]
    shift = size / 2.0 - CX * scale
    open_g = '<g transform="translate(%.3f %.3f) scale(%.5f)">' % (shift, shift, scale)
    body = (
        '  <g fill="none" stroke="#000000" stroke-linecap="round" stroke-linejoin="round">\n'
        '    <path d="{outline}" stroke-width="{sw_out}"/>\n'
        '    <path d="{left}" stroke-width="{sw_in}"/>\n'
        '    <path d="{right}" stroke-width="{sw_in}"/>\n'
        '  </g>\n'
        '  <path d="{star}" fill="#000000"/>\n'
    ).format(
        outline=OUTLINE, left=s_curve(-1), right=s_curve(+1),
        sw_out=STROKE_OUTLINE, sw_in=STROKE_INNER, star=star_path(),
    )
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {s:.0f} {s:.0f}" '
        'width="{s:.0f}" height="{s:.0f}">\n{o}\n{b}</g>\n</svg>\n'
    ).format(s=size, o=open_g, b=body)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(svg, encoding="utf-8")
    print(f"  svg  {path} ({len(svg)} bytes)")


def vector_drawable_foreground():
    shift, scale = safe_transform()
    outline_d = transform_path_d(OUTLINE, shift, scale)
    left_d = transform_path_d(s_curve(-1), shift, scale)
    right_d = transform_path_d(s_curve(+1), shift, scale)
    star_d = transform_path_d(star_path(), shift, scale)
    sw_out = "%.3f" % transform_stroke(STROKE_OUTLINE, shift, scale)
    sw_in = "%.3f" % transform_stroke(STROKE_INNER, shift, scale)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Gerado por docs/docs_interface/logo/generate_android_icons.py — não editar manualmente. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{outline_d}"
        android:strokeColor="{BEAN_COLOR}"
        android:strokeWidth="{sw_out}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{left_d}"
        android:strokeColor="{BEAN_COLOR}"
        android:strokeWidth="{sw_in}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{right_d}"
        android:strokeColor="{BEAN_COLOR}"
        android:strokeWidth="{sw_in}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{star_d}"
        android:fillColor="{STAR_COLOR}"/>
</vector>
"""


def vector_drawable_monochrome():
    shift, scale = safe_transform()
    outline_d = transform_path_d(OUTLINE, shift, scale)
    left_d = transform_path_d(s_curve(-1), shift, scale)
    right_d = transform_path_d(s_curve(+1), shift, scale)
    star_d = transform_path_d(star_path(), shift, scale)
    sw_out = "%.3f" % transform_stroke(STROKE_OUTLINE, shift, scale)
    sw_in = "%.3f" % transform_stroke(STROKE_INNER, shift, scale)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Gerado por docs/docs_interface/logo/generate_android_icons.py — não editar manualmente.
     Desenho monocromático da logo (contorno, curvas em S, estrela) para ícones temáticos (Android 13+).
     O sistema aplica o tingimento do tema ao alfa desta camada. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{outline_d}"
        android:strokeColor="#000000"
        android:strokeWidth="{sw_out}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{left_d}"
        android:strokeColor="#000000"
        android:strokeWidth="{sw_in}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{right_d}"
        android:strokeColor="#000000"
        android:strokeWidth="{sw_in}"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
    <path
        android:pathData="{star_d}"
        android:fillColor="#000000"/>
</vector>
"""


def write_text(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"  xml  {path}")


def run(cmd):
    subprocess.run(cmd, check=True)


def render_png(svg_path, out_path, size):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    run(["rsvg-convert", "-w", str(size), "-h", str(size), str(svg_path), "-o", str(out_path)])


def generate_legacy_pngs(res_dir, foreground_svg):
    print("Gerando PNGs legados (API < 26):")
    for density, size in DENSITIES.items():
        d = res_dir / density
        fg = d / "ic_launcher_foreground.png"
        render_png(foreground_svg, fg, size)
        print(f"  png  {fg} ({size}x{size})")

        # Ícone legado = fundo sólido + camada da frente.
        base = d / "ic_launcher.png"
        round_icon = d / "ic_launcher_round.png"
        run([
            "magick", "-size", f"{size}x{size}", f"xc:{BG_COLOR}",
            str(fg), "-composite", str(base),
        ])
        print(f"  png  {base} ({size}x{size})")
        # Round: máscara circular.
        run([
            "magick", str(base),
            "(", "-size", f"{size}x{size}", "xc:none", "-fill", "white",
            "-draw", f"circle {size/2},{size/2} {size/2},0", ")",
            "-alpha", "off", "-compose", "CopyOpacity", "-composite",
            str(round_icon),
        ])
        print(f"  png  {round_icon} ({size}x{size})")


def generate_previews(preview_dir, foreground_svg, monochrome_svg):
    print(f"Gerando prévias em {preview_dir}:")
    preview_dir.mkdir(parents=True, exist_ok=True)
    size = 432  # xxxhdpi
    fg = preview_dir / "_fg.png"
    mono = preview_dir / "_mono.png"
    render_png(foreground_svg, fg, size)
    render_png(monochrome_svg, mono, size)

    def mask_circle(path):
        run(["magick", "-size", f"{size}x{size}", "xc:none", "-fill", "white",
             "-draw", f"circle {size/2},{size/2} {size/2},0", str(path)])

    def mask_squircle(path):
        # superelipse aproximada (rx/ry ~ 45%)
        rx = int(size * 0.47)
        ry = int(size * 0.47)
        run(["magick", "-size", f"{size}x{size}", "xc:none", "-fill", "white",
             "-draw", f"roundrectangle 0,0 {size-1},{size-1} {rx},{ry}", str(path)])

    def mask_rounded(path):
        r = int(size * 0.18)
        run(["magick", "-size", f"{size}x{size}", "xc:none", "-fill", "white",
             "-draw", f"roundrectangle 0,0 {size-1},{size-1} {r},{r}", str(path)])

    masks = [("circle", mask_circle), ("squircle", mask_squircle), ("rounded-square", mask_rounded)]
    for name, make_mask in masks:
        mask = preview_dir / f"_mask_{name}.png"
        make_mask(mask)
        # full adaptive canvas: bg + fg, then apply mask
        base = preview_dir / f"_base_{name}.png"
        run(["magick", "-size", f"{size}x{size}", f"xc:{BG_COLOR}", str(fg), "-composite", str(base)])
        out = preview_dir / f"ic_launcher_preview_{name}.png"
        run(["magick", str(base), str(mask), "-alpha", "off", "-compose", "CopyOpacity", "-composite", str(out)])
        print(f"  preview {out}")
        base.unlink(missing_ok=True)
        mask.unlink(missing_ok=True)

    # Monocromática com tingimento simulado: desenho claro sobre fundo escuro e vice-versa.
    mask_mono = preview_dir / "_mask_mono.png"
    mask_circle(mask_mono)
    for bg, tint, label in [
        (BG_COLOR, "#3A3A3A", "light"),   # tema claro: desenho escuro
        ("#2B2B2B", "#F2EDE6", "dark"),    # tema escuro: desenho claro
    ]:
        tinted = preview_dir / f"_mono_tinted_{label}.png"
        # Tingimento: preenche com a cor do tema usando o alfa do monocromático.
        run([
            "magick", str(mono),
            "(", "+clone", "-alpha", "extract", ")",
            "-alpha", "off", "-compose", "CopyOpacity", "-composite",
            "(", "+clone", "-fill", tint, "-colorize", "100", ")",
            "+swap", "-compose", "CopyOpacity", "-composite",
            str(tinted),
        ])
        base = preview_dir / f"_mono_base_{label}.png"
        run(["magick", "-size", f"{size}x{size}", f"xc:{bg}", str(tinted), "-composite", str(base)])
        out = preview_dir / f"ic_launcher_monochrome_preview_{label}.png"
        run(["magick", str(base), str(mask_mono), "-alpha", "off", "-compose", "CopyOpacity", "-composite", str(out)])
        print(f"  preview {out}")
        base.unlink(missing_ok=True)
        tinted.unlink(missing_ok=True)
    mask_mono.unlink(missing_ok=True)

    fg.unlink(missing_ok=True)
    mono.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--preview-dir",
        help="Diretório (fora do git) para prévias com máscaras e monocromático.",
    )
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    repo = script_dir.parents[2]  # docs/docs_interface/logo -> repo root
    res_dir = repo / "apps/androidApp/src/main/res"

    print(f"Repositório: {repo}")
    print(f"Zona segura: {SAFE_FILL_FRACTION*108:.1f} dp de altura (canvas 108 dp)")

    # 1) SVG fonte do foreground (zona segura) em docs/docs_interface/logo
    foreground_svg = script_dir / "cafey-icon-foreground.svg"
    render_svg(foreground_svg, fill_fraction=SAFE_FILL_FRACTION)

    # 2) SVG monocromático temporário/fonte para rasterização de prévia
    monochrome_svg = script_dir / "cafey-icon-monochrome.svg"
    render_monochrome_svg(monochrome_svg)

    # 3) Vector drawables Android
    write_text(res_dir / "drawable/ic_launcher_foreground.xml", vector_drawable_foreground())
    write_text(res_dir / "drawable/ic_launcher_monochrome.xml", vector_drawable_monochrome())

    # 4) PNGs legados
    generate_legacy_pngs(res_dir, foreground_svg)

    # 5) Prévias
    if args.preview_dir:
        preview = Path(args.preview_dir)
        if not preview.is_absolute():
            preview = repo / preview
        generate_previews(preview, foreground_svg, monochrome_svg)

    print("Concluído.")


if __name__ == "__main__":
    main()

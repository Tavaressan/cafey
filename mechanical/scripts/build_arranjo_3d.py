"""Arranjo interno (variante 3D): Peca 1 + Peca 2 + divisoria + componentes.

Mesmo arranjo interno da versao em chapa - o componentes.csv nao muda com a
impressao 3D (issue #116 pede preservar o arranjo e a separacao rede/baixa
tensao). Cada componente tem um volume de referencia (CAIXA ENVOLVENTE
estimada, lida de mechanical/params/componentes_3d.csv), que e' o que
check_arranjo_3d.py valida. Quando ha modelo visual em mechanical/models/
(tabela MODELS abaixo; origem e licenca em models/FONTES.md), o modelo entra
como `<nome>_modelo` e o volume fica oculto.

Snapshot: copia as formas dos FCStd das pecas. Rode build_peca1_3d.py e
build_peca2_3d.py antes.

Uso: freecadcmd mechanical/scripts/build_arranjo_3d.py
     (so geometria: o freecadcmd nao le as cores dos modelos)
Com as cores dos modelos, rode pela GUI do FreeCAD sem janela:
     QT_QPA_PLATFORM=offscreen FreeCAD mechanical/scripts/build_arranjo_3d.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

import FreeCAD as App
import Part

V = App.Vector

BLACK = (0.10, 0.10, 0.10)
GREEN = (0.25, 0.65, 0.30)
BRASS = (0.80, 0.65, 0.25)
PHENOLIC = (0.72, 0.52, 0.28)
COPPER = (0.85, 0.55, 0.35)
SW_DEFAULT = (0.79, 0.82, 0.93)  # cor padrao de peca do SolidWorks (o autor nao pintou)

# axes: para onde vao os eixos X, Y, Z do modelo no arranjo.
# Sem "opening": o canto minimo do modelo girado vai ao canto minimo do volume
# de referencia. Com "opening": o ponto "seat" do modelo (centro da face que
# encosta na parede por fora) vai ao centro da abertura da Peca 1, na face
# externa da parede "wall".
MODELS = {
    # montado por make_esp32_devkit_v1.py; antena (Y max do modelo) em Y min, atras da janela_rf
    "esp32": dict(file="esp32-devkit-v1.step", axes=("-X", "-Y", "+Z")),
    # barra IN/5V/GND (X min do modelo) voltada p/ a divisoria
    "modulo_rele": dict(file="modulo-rele-v3.step", axes=("+Y", "-X", "+Z")),
    # pinos AC em X min, saida DC em X max (lado da travessia)
    "hlk_pm01": dict(file="hlk-pmxx.step", axes=("+X", "+Y", "+Z")),
    # o ImportGui descarta a barra (faces soltas no IGES) e o arquivo nao traz
    # cor: le tudo com Part.read e pinta os apoios (no piso) de verde, o resto de latao
    "terra_j3": dict(file="barramento-terra-lukma-6p.iges", axes=("+X", "+Y", "+Z"),
                     raw_color=lambda s, f: GREEN if s.BoundBox.ZMin < 1 else BRASS),
    # so a placa ilhada (sem componentes), recortada de 60 x 40 para os 45 x 32 do
    # volume - a placa da lista e' cortada no tamanho; sem cor no arquivo: fenolite
    # com ilhas de cobre (faces acima do laminado, Y > 0 do modelo)
    "placa_aux": dict(file="placa-ilhada-60x40.step", axes=("+Y", "+Z", "+X"),
                      crop=((-16, -5, -22.5), (32, 10, 45)),
                      raw_color=lambda s, f: COPPER if f.BoundBox.YMax > 0.05 else PHENOLIC),
    # corpo ao longo de Y, tampa do fusivel para cima; descarta os rabichos de 138 mm
    "porta_fusivel": dict(file="porta-fusivel-inline.step", axes=("+Y", "+Z", "+X"),
                          max_len=100),
    # ponta do domo (Z 11.6 do modelo) rente a face externa
    "led_rgb": dict(file="led-5mm-rgb.step", axes=("+X", "+Z", "-Y"),
                    opening="furo_led", wall="frontal", seat=(1.905, 0, 11.6)),
    # Z=0 do modelo = face de apoio do aro
    "botao": dict(file="adafruit-916-botao-metal-16mm.step", axes=("+X", "+Z", "-Y"),
                  opening="furo_botao", wall="frontal", seat=(0, 0, 0)),
    # flange de 5 mm por fora, orelhas de parafuso ao longo de Y
    "usb_painel": dict(file="adafruit-3258-microusb-painel.step", axes=("+Y", "-Z", "-X"),
                       opening="recorte_usb", wall="esquerda", seat=(0, 0, -5.0)),
    # vedacao (Z -0.9 do modelo) encosta na face externa; contraporca por dentro
    "prensa_cabo_corpo": dict(file="PG9 Gland.step", axes=("+X", "-Z", "+Y"),
                              opening="prensa_cabo", wall="traseira", seat=(0, 0, -0.9)),
    # Y=0 do modelo = face de apoio da moldura
    "tomada_j1_corpo": dict(file="tomada-tpa2-3-e3f.igs", axes=("+X", "+Y", "+Z"),
                            opening="tomada_j1", wall="traseira", seat=(0, 0, 0),
                            colors={SW_DEFAULT: BLACK}),
}


def _ref(doc, name, path, objname):
    src = App.openDocument(path)
    feat = doc.addObject("Part::Feature", name)
    feat.Shape = src.getObject(objname).Shape.copy()
    feat.Label = "%s (referencia)" % name
    App.closeDocument(src.Name)
    return feat


def main():
    for p in (_env.FCSTD, _env.FCSTD2):
        if not os.path.exists(p):
            raise SystemExit("falta %s - rode build_peca1_3d.py / build_peca2_3d.py" % p)

    g = _env.params_dict()
    p1 = App.openDocument(_env.FCSTD)
    openings = {m["opening"]: p1.getObject(m["opening"]).Shape.BoundBox.Center
                for m in MODELS.values() if "opening" in m}
    App.closeDocument(p1.Name)

    doc = App.newDocument("arranjo_3d")
    _ref(doc, "ref_peca1", _env.FCSTD, "peca1")
    _ref(doc, "ref_peca2", _env.FCSTD2, "peca2")

    comps = _env.load_componentes()
    by_name = {c["nome"]: c for c in comps}

    # --- divisoria com rasgo de passa-fio para a travessia ---
    dv = by_name["divisoria"]
    tv = by_name["travessia_fios"]
    painel = Part.makeBox(dv["dx"], dv["dy"], dv["dz"], V(dv["x"], dv["y"], dv["z"]))
    rasgo = Part.makeBox(tv["dx"] + 6, dv["dy"] + 2, tv["dz"] + 4,
                         V(tv["x"] - 3, dv["y"] - 1, tv["z"] - 2))
    divis = doc.addObject("Part::Feature", "divisoria")
    divis.Shape = painel.cut(rasgo)
    divis.Label = "divisoria (policarbonato/acrilico)"
    _set_desc(divis, dv)

    # --- volumes de referencia (caixas) + modelos visuais ---
    print("--- modelos visuais vs volume de referencia ---")
    hidden = []
    for c in comps:
        if c["nome"] == "divisoria":
            continue
        spec = MODELS.get(c["nome"])
        b = doc.addObject("Part::Feature", c["nome"])
        b.Shape = Part.makeBox(c["dx"], c["dy"], c["dz"], V(c["x"], c["y"], c["z"]))
        # o rotulo limpo fica para o modelo visual, quando houver
        b.Label = c["nome"] if spec is None else "%s (volume)" % c["nome"]
        _set_desc(b, c)
        if spec is None:
            print("  %-18s sem modelo - fica a caixa" % c["nome"])
            continue
        model = _import_model(doc, c["nome"], spec)
        _place(model, spec, c, openings, g)
        _report(c["nome"], model, c)
        b.Visibility = False
        hidden.append(b.Name)

    doc.recompute()

    objs = [o for o in doc.Objects
            if o.Name not in ("ref_peca1", "ref_peca2") and o.Name not in hidden]
    if App.GuiUp:
        import ImportGui
        ImportGui.export(objs, _env.ARRANJO_STEP)
    else:
        import Import
        Import.export(objs, _env.ARRANJO_STEP)
    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.ARRANJO_FCSTD)

    print("componentes:", len(comps), "| com modelo visual:", len(hidden))
    print("cores dos modelos:", "sim" if App.GuiUp else "nao (freecadcmd)")
    print("salvo :", _env.ARRANJO_FCSTD)
    print("step  :", _env.ARRANJO_STEP, "(divisoria + modelos + caixas sem modelo, sem as pecas impressas)")


def _import_model(doc, name, spec):
    """Importa o arquivo e junta tudo num unico Part::Feature `<name>_modelo`.

    Com a GUI ativa (ImportGui), as cores por face do arquivo vao para o
    ShapeAppearance do objeto; no freecadcmd so a geometria entra.
    """
    path = os.path.join(_env.MODELS_DIR, spec["file"])
    shapes, looks = [], []
    if "raw_color" in spec:
        top = Part.read(path)
        shapes = top.childShapes() if top.ShapeType == "Compound" else [top]
        shapes = [s for s in shapes if s.Faces]
        if "crop" in spec:
            base, size = spec["crop"]
            box = Part.makeBox(*size, V(*base))
            shapes = [s.common(box) for s in shapes]
        for s in shapes:
            looks += [_material(spec["raw_color"](s, f)) for f in s.Faces]
    else:
        before = set(o.Name for o in doc.Objects)
        if App.GuiUp:
            import ImportGui
            ImportGui.insert(path, doc.Name)
        else:
            import Import
            Import.insert(path, doc.Name)
        new = [o.Name for o in doc.Objects if o.Name not in before]
        for n in new:
            o = doc.getObject(n)
            if o.TypeId != "Part::Feature" or o.Shape.isNull():
                continue
            s = o.Shape.copy()
            s.Placement = o.getGlobalPlacement()
            bb = s.BoundBox
            if max(bb.XLength, bb.YLength, bb.ZLength) > spec.get("max_len", float("inf")):
                continue
            shapes.append(s)
            if App.GuiUp:
                looks += _face_looks(o, len(s.Faces), spec.get("colors", {}))
        for n in new:
            if doc.getObject(n):
                doc.removeObject(n)

    feat = doc.addObject("Part::Feature", name + "_modelo")
    feat.Shape = Part.makeCompound(shapes)
    feat.Label = name
    if App.GuiUp:
        feat.ViewObject.ShapeAppearance = looks
    feat.Label2 = "MODELO VISUAL (%s; origem em mechanical/models/FONTES.md). " \
                  "Posicionado sobre o volume de referencia '%s' - nao e' validado " \
                  "por check_arranjo_3d.py." % (spec["file"], name)
    return feat


def _face_looks(obj, nfaces, colors):
    """Aparencia por face do objeto importado, trocando as cores de origem de `colors`."""
    mats = list(obj.ViewObject.ShapeAppearance)
    if len(mats) != nfaces:
        mats = [mats[0]] * nfaces
    for m in mats:
        for src, new in colors.items():
            if all(abs(a - b) < 0.02 for a, b in zip(src, m.DiffuseColor[:3])):
                m.DiffuseColor = new
    return mats


def _material(rgb):
    m = App.Material()
    m.DiffuseColor = rgb
    return m


def _rotation(axes):
    """Rotacao que leva os eixos X, Y, Z do modelo as direcoes de `axes`."""
    unit = {"X": V(1, 0, 0), "Y": V(0, 1, 0), "Z": V(0, 0, 1)}
    cx, cy, cz = [unit[a[1]] * (1 if a[0] == "+" else -1) for a in axes]
    m = App.Matrix(cx.x, cy.x, cz.x, 0,
                   cx.y, cy.y, cz.y, 0,
                   cx.z, cy.z, cz.z, 0,
                   0, 0, 0, 1)
    return App.Placement(m).Rotation


def _place(feat, spec, c, openings, g):
    rot = _rotation(spec["axes"])
    if "opening" in spec:
        ctr = openings[spec["opening"]]
        anchor = {"frontal": V(ctr.x, 0, ctr.z),
                  "traseira": V(ctr.x, g["pegada_y"], ctr.z),
                  "esquerda": V(0, ctr.y, ctr.z)}[spec["wall"]]
        base = anchor - rot.multVec(V(*spec["seat"]))
    else:
        s = feat.Shape.copy()
        s.Placement = App.Placement(V(), rot)
        bb = s.BoundBox
        base = V(c["x"] - bb.XMin, c["y"] - bb.YMin, c["z"] - bb.ZMin)
    feat.Placement = App.Placement(base, rot)


def _report(name, feat, c):
    b = feat.Shape.BoundBox
    over = []
    for ax, lo, hi, cmin, d in (("X", b.XMin, b.XMax, c["x"], c["dx"]),
                                ("Y", b.YMin, b.YMax, c["y"], c["dy"]),
                                ("Z", b.ZMin, b.ZMax, c["z"], c["dz"])):
        if lo < cmin - 0.05:
            over.append("%s- %.1f" % (ax, cmin - lo))
        if hi > cmin + d + 0.05:
            over.append("%s+ %.1f" % (ax, hi - cmin - d))
    print("  %-18s modelo %5.1f x %5.1f x %5.1f | volume %5.1f x %5.1f x %5.1f | %s"
          % (name, b.XLength, b.YLength, b.ZLength, c["dx"], c["dy"], c["dz"],
             "excede " + ", ".join(over) if over else "dentro do volume"))


def _set_desc(obj, c):
    txt = "VOLUME DE REFERENCIA (caixa envolvente ESTIMADA - confirmar). " \
          "Faixa: %s. Apoio: %s. Tamanho %g x %g x %g. %s" % (
              c["faixa"], c["apoio"], c["dx"], c["dy"], c["dz"], c["fonte"])
    try:
        obj.Label2 = txt
    except AttributeError:
        pass


main()

# a GUI sem janela (QT_QPA_PLATFORM=offscreen) nao encerra sozinha apos o script
if App.GuiUp and os.environ.get("QT_QPA_PLATFORM") == "offscreen":
    sys.stdout.flush()
    os._exit(0)

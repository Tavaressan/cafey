"""Abre a carcaca circular no FreeCAD com cores e modelos 3D (#302).

    freecad mechanical/circular/visualizar.py      (rodar a partir da raiz do repositorio)
    sem abrir janela: CAFEY_FECHAR=1 QT_QPA_PLATFORM=offscreen freecad mechanical/circular/visualizar.py

Precisa da interface grafica (o ImportGui traz as cores dos modelos; o
freecadcmd nao). Rode antes build.py e marca.py. Abre build/circular/carcaca.FCStd,
acrescenta o corpo com a marca gravada (malha de corpo.stl), troca as caixas
pelos modelos de mechanical/models/ (tabela MODELOS; origem em models/FONTES.md)
e salva build/circular/arranjo_circular.FCStd.

Dois modelos sao gerados aqui, por falta de arquivo: a chave disjuntora ST-001
(simplificada a partir das medidas do anuncio) e o dissipador do SSR
(PROVISORIO, ocupa o envelope atual ate a medida real).
"""
import math
import os
import sys

import FreeCAD as App
import FreeCADGui as Gui
import ImportGui
import Mesh
import Part

try:
    HERE = os.path.dirname(os.path.abspath(__file__))
except NameError:
    HERE = os.path.join(os.getcwd(), "mechanical", "circular")
sys.path.insert(0, HERE)
from layout import Layout  # noqa: E402

V = App.Vector
MECH = os.path.dirname(HERE)
OUT = os.path.join(MECH, "build", "circular")
MODELS_DIR = os.path.join(MECH, "models")
L = Layout()
P = L.p
zona = {c.nome: c.zona for c in L.comps}
zona.update({a.nome: a.zona for a in L.aberturas})
comp = {c.nome: c for c in L.comps}
abert = {a.nome: a for a in L.aberturas}

AZUL, VERMELHO, LARANJA = (0.30, 0.55, 0.85), (0.85, 0.35, 0.30), (1.0, 0.60, 0.10)
CORPO, FUNDO = (0.85, 0.72, 0.55), (0.55, 0.58, 0.62)
FENOLITE, PRETO, LATAO = (0.72, 0.52, 0.28), (0.10, 0.10, 0.10), (0.80, 0.65, 0.25)
VERMELHO_LED = (0.90, 0.05, 0.05)
SW_DEFAULT = (0.79, 0.82, 0.93)  # cor padrao do SolidWorks (autor nao pintou)

# Interno: "axes" = para onde vao os eixos X, Y, Z do modelo; o canto minimo do
#   modelo girado vai ao canto minimo do volume de referencia.
#   "crop" = (canto, tamanho) no sistema do modelo: so o que fica dentro entra.
#   "copias"/"passo" = repete o modelo ao longo de x.
# Parede: "out"/"up" = eixos do modelo que apontam para fora e para cima;
#   "seat" = ponto do modelo que encosta na face externa (ou interna, com
#   face="interna"), no centro do recorte.
MODELOS = {
    "esp32": dict(file="esp32-devkit-v1.step", axes=("+Y", "-X", "+Z")),
    # pinos (Y+ do modelo) em x max; USB-A em x min, do lado da travessia
    "a1385": dict(file="apple-a1385.step", axes=("-Y", "+X", "+Z")),
    # 221-415 (5 vias) recortado em 3 vias = 221-413; dois: L_FONTE e N
    "borne_rede": dict(file="wago-221-415.step", axes=("+X", "+Y", "+Z"),
                       crop=((-1, -1, -1), (18.9, 20.2, 10.2)), copias=2, passo=21),
    # borne de terra verde-amarelo + batente + ~48 mm de trilho (sem os bornes azuis)
    "terra_j3": dict(file="bornes-metaltex-din.iges", axes=("+X", "+Y", "+Z"),
                     crop=((89.8, -40, -1), (47.7, 80, 50))),
    "botao": dict(file="adafruit-916-botao-metal-16mm.step", out="+Z", up="+Y", seat=(0, 0, 0)),
    "prensa_cabo": dict(file="PG9 Gland.step", out="+Z", up="-Y", seat=(0, 0, -0.9)),
    "tomada_j1": dict(file="tomada-tpa2-3-e3f.igs", out="+Y", up="+X", seat=(0, 0, 0),
                      colors={SW_DEFAULT: PRETO}),
    # face superior (furo de som, Z 9.6) encostada na face interna da parede
    "buzzer": dict(file="buzzer-12mm.step", out="+Z", up="+Y", seat=(0, 0, 9.6), face="interna"),
}


def eixo(s):
    return {"X": V(1, 0, 0), "Y": V(0, 1, 0), "Z": V(0, 0, 1)}[s[1]] * (1 if s[0] == "+" else -1)


def rot_from_map(m):
    """Rotacao a partir de dois pares (vetor do modelo, vetor do mundo)."""
    (a, wa), (b, wb) = m
    c, wc = a.cross(b), wa.cross(wb)
    src = App.Matrix(a.x, b.x, c.x, 0, a.y, b.y, c.y, 0, a.z, b.z, c.z, 0, 0, 0, 0, 1)
    dst = App.Matrix(wa.x, wb.x, wc.x, 0, wa.y, wb.y, wc.y, 0, wa.z, wb.z, wc.z, 0, 0, 0, 0, 1)
    return App.Placement(dst.multiply(src.inverse())).Rotation


def material(rgb):
    mt = App.Material()
    mt.DiffuseColor = rgb
    return mt


def le_modelo(doc, spec):
    """(formas, aparencias por face) do arquivo, com cores via ImportGui."""
    before = set(o.Name for o in doc.Objects)
    ImportGui.insert(os.path.join(MODELS_DIR, spec["file"]), doc.Name)
    novos = [o.Name for o in doc.Objects if o.Name not in before]
    shapes, looks = [], []
    crop = None
    if "crop" in spec:
        (cx, cy, cz), (sx, sy, sz) = spec["crop"]
        crop = Part.makeBox(sx, sy, sz, V(cx, cy, cz))
    for n in novos:
        o = doc.getObject(n)
        if o.TypeId != "Part::Feature" or o.Shape.isNull() or not o.Shape.Faces:
            continue
        s = o.Shape.copy()
        s.Placement = o.getGlobalPlacement()
        mats = list(o.ViewObject.ShapeAppearance)
        for mt in mats:
            for src, new in spec.get("colors", {}).items():
                if all(abs(x - y) < 0.02 for x, y in zip(src, mt.DiffuseColor[:3])):
                    mt.DiffuseColor = new
        if crop is not None:
            s = s.common(crop)
            if not s.Faces:
                continue
            mats = [mats[0]] * len(s.Faces)  # o recorte refaz as faces: uma cor por peca
        elif len(mats) != len(s.Faces):
            mats = [mats[0]] * len(s.Faces)
        shapes.append(s)
        looks += mats
    for n in novos:
        if doc.getObject(n):
            doc.removeObject(n)
    return shapes, looks


def feature(doc, nome, shape, looks, label, label2):
    f = doc.addObject("Part::Feature", nome)
    f.Shape = shape
    f.ViewObject.ShapeAppearance = looks
    f.Label, f.Label2 = label, label2
    return f


def interno(doc, nome, spec):
    shapes, looks = le_modelo(doc, spec)
    x_, y_, _ = (eixo(a) for a in spec["axes"])
    rot = rot_from_map([(V(1, 0, 0), x_), (V(0, 1, 0), y_)])
    base = Part.makeCompound(shapes)
    base.Placement = App.Placement(V(), rot)
    bb = base.BoundBox
    c = comp[nome]
    feats = []
    for k in range(spec.get("copias", 1)):
        s = base.copy()
        s.translate(V(c.x - bb.XMin + k * spec.get("passo", 0), c.y - bb.YMin, c.z - bb.ZMin))
        feats.append(feature(doc, "%s_modelo%s" % (nome, k or ""), s, looks,
                             nome + (" (modelo 3D)" if k == 0 else " (modelo 3D, copia %d)" % (k + 1)),
                             "Modelo visual de mechanical/models/%s" % spec["file"]))
    b = Part.makeCompound([f.Shape for f in feats]).BoundBox
    App.Console.PrintMessage("  %-14s modelo %.1f x %.1f x %.1f | volume %.1f x %.1f x %.1f\n"
                             % (nome, b.XLength, b.YLength, b.ZLength, c.dx, c.dy, c.dz))
    return feats


def frame_parede(a):
    t = math.radians(a.ang)
    return V(math.cos(t), math.sin(t), 0), t


def parede(doc, nome, spec):
    shapes, looks = le_modelo(doc, spec)
    a = abert[nome]
    u_r, t = frame_parede(a)
    rot = rot_from_map([(eixo(spec["out"]), u_r), (eixo(spec["up"]), V(0, 0, 1))])
    r = L.Ri if spec.get("face") == "interna" else L.R
    anchor = V(r * math.cos(t), r * math.sin(t), a.zc)
    s = Part.makeCompound(shapes)
    s.Placement = App.Placement(anchor - rot.multVec(V(*spec["seat"])), rot)
    f = feature(doc, nome + "_modelo", s, looks, nome + " (modelo 3D)",
                "Modelo visual de mechanical/models/%s" % spec["file"])
    prof = max(L.Ri - v.Point.dot(u_r) for v in f.Shape.Vertexes)
    App.Console.PrintMessage("  %-14s entra %.1f mm alem da face interna | volume previsto %.1f mm\n"
                             % (nome, prof, a.cp))
    return [f]


def local_para_mundo(a, shape, r):
    """Forma desenhada em (x=para fora, y=tangente, z=para cima) com origem na
    parede de raio r, no centro do recorte."""
    u_r, t = frame_parede(a)
    rot = rot_from_map([(V(1, 0, 0), u_r), (V(0, 0, 1), V(0, 0, 1))])
    s = shape.copy()
    s.Placement = App.Placement(V(r * math.cos(t), r * math.sin(t), a.zc), rot)
    return s


def chave_st001(doc):
    """ST-001 simplificada: frente 29 x 14, corpo 25 de profundidade, 3 terminais."""
    a = abert["disjuntor"]
    box = lambda x0, x1, y0, y1, z0, z1: Part.makeBox(x1 - x0, y1 - y0, z1 - z0, V(x0, y0, z0))  # noqa: E731
    pecas = [
        ("disjuntor_moldura", box(0, 2, -7, 7, -14.5, 14.5).fuse(box(-25, 0, -6.2, 6.2, -13.4, 13.4)), PRETO),
        ("disjuntor_tecla", box(2, 3.2, -5, 5, -11, 11), VERMELHO_LED),
        ("disjuntor_terminais", box(-33, -25, -0.4, 0.4, -11, -4.7).fuse(box(-33, -25, -0.4, 0.4, -3.15, 3.15))
         .fuse(box(-33, -25, -0.4, 0.4, 4.7, 11)), LATAO),
    ]
    feats = []
    for nome, s, cor in pecas:
        f = feature(doc, nome, local_para_mundo(a, s, L.R), [material(cor)] * len(s.Faces),
                    nome.replace("_", " ") + " (ST-001 simplificada)",
                    "Gerado por visualizar.py a partir do anuncio: 29 x 14 x 25 mm, 3 terminais")
        feats.append(f)
    return feats


def ssr_com_dissipador(doc):
    """SSR (modelo) sobre um dissipador de aletas PROVISORIO que ocupa o envelope."""
    c = comp["ssr_dissipador"]
    shapes, looks = le_modelo(doc, dict(file="ssr-anly-asr-40da.step"))
    s = Part.makeCompound(shapes)
    bb = s.BoundBox
    z_top_dis = c.z + c.dz - bb.ZLength
    s.translate(V(c.x + (c.dx - bb.XLength) / 2 - bb.XMin, c.y + (c.dy - bb.YLength) / 2 - bb.YMin,
                  z_top_dis - bb.ZMin))
    f_ssr = feature(doc, "ssr_modelo", s, looks, "SSR (modelo 3D: ANLY ASR-40DA, mesmo formato do Fotek)",
                    "Modelo visual de mechanical/models/ssr-anly-asr-40da.step")
    base_t, fin_t, passo = 4.0, 1.5, 5.0
    partes = [Part.makeBox(c.dx, c.dy, base_t, V(c.x, c.y, z_top_dis - base_t))]
    x = c.x + 1
    while x + fin_t <= c.x + c.dx - 1:
        partes.append(Part.makeBox(fin_t, c.dy, z_top_dis - base_t - c.z, V(x, c.y, c.z)))
        x += passo
    dis = partes[0].fuse(partes[1:]).removeSplitter()
    f_dis = feature(doc, "dissipador_provisorio", dis, [material(LARANJA)] * len(dis.Faces),
                    "dissipador (PROVISORIO: medir e atualizar ssr_dissipador)",
                    "Gerado por visualizar.py so para ocupar o envelope de componentes.csv")
    App.Console.PrintMessage("  ssr_dissipador SSR %.1f x %.1f x %.1f sobre dissipador provisorio de %.1f mm\n"
                             % (bb.XLength, bb.YLength, bb.ZLength, z_top_dis - c.z))
    return [f_ssr, f_dis]


doc = App.openDocument(os.path.join(OUT, "carcaca.FCStd"))
for o in doc.Objects:  # objetos criados pelo freecadcmd chegam ocultos
    o.ViewObject.Visibility = True

stl = os.path.join(OUT, "corpo.stl")
corpo = doc.getObject("Corpo")
if os.path.exists(stl):
    m = doc.addObject("Mesh::Feature", "CorpoComMarca")
    m.Mesh = Mesh.Mesh(stl)
    m.Label = "Peca 1 - corpo com marca (= corpo.stl)"
    doc.getObject("Impressao").addObject(m)
    corpo.ViewObject.Visibility = False
    m.ViewObject.ShapeColor = CORPO
    m.ViewObject.Transparency = 55
else:
    corpo.ViewObject.ShapeColor = CORPO
    corpo.ViewObject.Transparency = 55
doc.getObject("Fundo").ViewObject.ShapeColor = FUNDO

for o in list(doc.Objects):
    if not o.Name.startswith("ref_"):
        continue
    nome = o.Name[4:]
    cor = AZUL if zona.get(nome) == "baixa" else VERMELHO
    if "PROVISORIO" in o.Label:
        cor = LARANJA
    if nome == "placa_base":
        cor = FENOLITE
    o.ViewObject.ShapeColor = cor
    o.ViewObject.Transparency = 25 if nome in comp else 45

g_mod = doc.addObject("App::DocumentObjectGroup", "Modelos")
g_mod.Label = "Modelos 3D dos componentes"
App.Console.PrintMessage("Modelos 3D:\n")
feitos = {}
for nome, spec in MODELOS.items():
    if doc.getObject("ref_" + nome) is None:
        continue
    if not os.path.exists(os.path.join(MODELS_DIR, spec["file"])):
        App.Console.PrintWarning("  %s: falta mechanical/models/%s\n" % (nome, spec["file"]))
        continue
    feitos[nome] = interno(doc, nome, spec) if "axes" in spec else parede(doc, nome, spec)
feitos["disjuntor"] = chave_st001(doc)
if os.path.exists(os.path.join(MODELS_DIR, "ssr-anly-asr-40da.step")):
    feitos["ssr_dissipador"] = ssr_com_dissipador(doc)
for nome, feats in feitos.items():
    g_mod.addObjects(feats)
    ref = doc.getObject("ref_" + nome)
    ref.ViewObject.Visibility = False  # a caixa fica no grupo, oculta, para conferencia
    ref.Label = nome + " (volume de referencia)"

sem = [o.Name[4:] for o in doc.Objects if o.Name.startswith("ref_") and o.ViewObject.Visibility]
App.Console.PrintMessage("Sem modelo 3D (ficam como caixa): %s\n" % ", ".join(sem))

doc.recompute()
view = Gui.activeDocument().activeView()
view.viewIsometric()
Gui.SendMsgToActiveView("ViewFit")
doc.saveAs(os.path.join(OUT, "arranjo_circular.FCStd"))
App.Console.PrintMessage("Salvo %s\n" % os.path.join(OUT, "arranjo_circular.FCStd"))
if os.environ.get("CAFEY_FECHAR"):  # uso em script: o arquivo ja foi salvo, sai do FreeCAD
    sys.stdout.flush()
    os._exit(0)

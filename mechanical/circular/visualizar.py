"""Abre a carcaca circular no FreeCAD com cores e modelos 3D reais (#302).

    freecad mechanical/circular/visualizar.py      (rodar a partir da raiz do repositorio)
    sem abrir janela: CAFEY_FECHAR=1 QT_QPA_PLATFORM=offscreen freecad mechanical/circular/visualizar.py

Precisa da interface grafica (o ImportGui traz as cores dos modelos; o
freecadcmd nao). Rode antes build.py e marca.py. Abre build/circular/carcaca.FCStd,
acrescenta o corpo com a marca gravada (malha de corpo.stl), troca as caixas
pelos modelos de mechanical/models/ quando existem (tabela MODELOS) e salva
build/circular/arranjo_circular.FCStd.

Cores das caixas que ficam (componentes sem modelo): baixa tensao em azul,
rede em vermelho, medida provisoria em laranja.
"""
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
zona = {c.nome: c.zona for c in L.comps}
zona.update({a.nome: a.zona for a in L.aberturas})
comp = {c.nome: c for c in L.comps}
abert = {a.nome: a for a in L.aberturas}

AZUL, VERMELHO, LARANJA = (0.30, 0.55, 0.85), (0.85, 0.35, 0.30), (1.0, 0.60, 0.10)
CORPO, FUNDO = (0.85, 0.72, 0.55), (0.55, 0.58, 0.62)
FENOLITE = (0.72, 0.52, 0.28)
PRETO = (0.10, 0.10, 0.10)
SW_DEFAULT = (0.79, 0.82, 0.93)  # cor padrao do SolidWorks (autor nao pintou)

# Modelos de mechanical/models/ (origem e licenca em models/FONTES.md).
# Interno: "axes" = para onde vao os eixos X, Y, Z do modelo; o canto minimo do
#   modelo girado vai ao canto minimo do volume de referencia.
# Parede: "out" = eixo do modelo que aponta para fora da parede; "up" = eixo
#   que aponta para cima; "seat" = ponto do modelo que encosta na face externa,
#   no centro do recorte.
MODELOS = {
    # antena (Y max do modelo) em x min, longe do SSR; micro-USB em x max
    "esp32": dict(file="esp32-devkit-v1.step", axes=("+Y", "-X", "+Z")),
    # botao metalico 16 mm com anel de LED: mesmo formato do botao RGB inox (#308)
    "botao": dict(file="adafruit-916-botao-metal-16mm.step", out="+Z", up="+Y", seat=(0, 0, 0)),
    # vedacao (Z -0.9 do modelo) na face externa; contraporca por dentro
    "prensa_cabo": dict(file="PG9 Gland.step", out="+Z", up="-Y", seat=(0, 0, -0.9)),
    # Y=0 do modelo = face de apoio da moldura; girada para a vertical (45 mm em z)
    "tomada_j1": dict(file="tomada-tpa2-3-e3f.igs", out="+Y", up="+X", seat=(0, 0, 0),
                      colors={SW_DEFAULT: PRETO}),
}


def eixo(s):
    return {"X": V(1, 0, 0), "Y": V(0, 1, 0), "Z": V(0, 0, 1)}[s[1]] * (1 if s[0] == "+" else -1)


def rot_from_map(m):
    """Rotacao a partir de {vetor do modelo: vetor do mundo} para 2 eixos."""
    (a, wa), (b, wb) = m
    c, wc = a.cross(b), wa.cross(wb)
    src = App.Matrix(a.x, b.x, c.x, 0, a.y, b.y, c.y, 0, a.z, b.z, c.z, 0, 0, 0, 0, 1)
    dst = App.Matrix(wa.x, wb.x, wc.x, 0, wa.y, wb.y, wc.y, 0, wa.z, wb.z, wc.z, 0, 0, 0, 0, 1)
    return App.Placement(dst.multiply(src.inverse())).Rotation


def material(rgb):
    mt = App.Material()
    mt.DiffuseColor = rgb
    return mt


def importa(doc, nome, spec):
    """Importa o arquivo com as cores (ImportGui) e junta num unico Part::Feature."""
    before = set(o.Name for o in doc.Objects)
    ImportGui.insert(os.path.join(MODELS_DIR, spec["file"]), doc.Name)
    novos = [o.Name for o in doc.Objects if o.Name not in before]
    shapes, looks = [], []
    for n in novos:
        o = doc.getObject(n)
        if o.TypeId != "Part::Feature" or o.Shape.isNull():
            continue
        s = o.Shape.copy()
        s.Placement = o.getGlobalPlacement()
        shapes.append(s)
        mats = list(o.ViewObject.ShapeAppearance)
        if len(mats) != len(s.Faces):
            mats = [mats[0]] * len(s.Faces)
        for mt in mats:
            for src, new in spec.get("colors", {}).items():
                if all(abs(x - y) < 0.02 for x, y in zip(src, mt.DiffuseColor[:3])):
                    mt.DiffuseColor = new
        looks += mats
    for n in novos:
        if doc.getObject(n):
            doc.removeObject(n)
    feat = doc.addObject("Part::Feature", nome + "_modelo")
    feat.Shape = Part.makeCompound(shapes)
    feat.ViewObject.ShapeAppearance = looks
    feat.Label = nome + " (modelo 3D)"
    feat.Label2 = "Modelo visual de mechanical/models/%s; posicionado sobre o volume de referencia." % spec["file"]
    return feat


def posiciona(feat, nome, spec):
    import math
    if "axes" in spec:
        x_, y_, _ = (eixo(a) for a in spec["axes"])
        rot = rot_from_map([(V(1, 0, 0), x_), (V(0, 1, 0), y_)])
        s = feat.Shape.copy()
        s.Placement = App.Placement(V(), rot)
        bb = s.BoundBox
        c = comp[nome]
        base = V(c.x - bb.XMin, c.y - bb.YMin, c.z - bb.ZMin)
        feat.Placement = App.Placement(base, rot)
        b = feat.Shape.BoundBox
        App.Console.PrintMessage("  %-12s modelo %.1f x %.1f x %.1f | volume %.1f x %.1f x %.1f\n"
                                 % (nome, b.XLength, b.YLength, b.ZLength, c.dx, c.dy, c.dz))
        return
    a = abert[nome]
    t = math.radians(a.ang)
    u_r = V(math.cos(t), math.sin(t), 0)
    rot = rot_from_map([(eixo(spec["out"]), u_r), (eixo(spec["up"]), V(0, 0, 1))])
    anchor = V(L.R * math.cos(t), L.R * math.sin(t), a.zc)
    feat.Placement = App.Placement(anchor - rot.multVec(V(*spec["seat"])), rot)
    prof = max(L.R - v.Point.dot(u_r) for v in feat.Shape.Vertexes) - (L.R - L.Ri)
    App.Console.PrintMessage("  %-12s modelo entra %.1f mm alem da face interna | volume previsto %.1f mm\n"
                             % (nome, prof, a.cp))


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

g_mod = doc.addObject("App::DocumentObjectGroup", "Modelos")
g_mod.Label = "Modelos 3D dos componentes"
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

App.Console.PrintMessage("Modelos 3D:\n")
for nome, spec in MODELOS.items():
    ref = doc.getObject("ref_" + nome)
    if ref is None:
        continue
    feat = importa(doc, nome, spec)
    posiciona(feat, nome, spec)
    g_mod.addObject(feat)
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

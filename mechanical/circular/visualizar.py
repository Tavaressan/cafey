"""Abre a carcaca circular no FreeCAD com cores e transparencia (#302).

    freecad mechanical/circular/visualizar.py      (rodar a partir da raiz do repositorio)

Precisa da interface grafica (nao funciona no freecadcmd). Rode antes
build.py e marca.py. Abre build/circular/carcaca.FCStd, acrescenta o corpo
com a marca gravada (malha de corpo.stl), aplica cores por zona e salva
build/circular/arranjo_circular.FCStd, que guarda a aparencia.

Cores: corpo translucido; fundo cinza; baixa tensao em azul; rede em
vermelho; medidas provisorias em laranja.
"""
import os
import sys

import FreeCAD as App
import FreeCADGui as Gui
import Mesh

try:
    HERE = os.path.dirname(os.path.abspath(__file__))
except NameError:
    HERE = os.path.join(os.getcwd(), "mechanical", "circular")
sys.path.insert(0, HERE)
from layout import Layout  # noqa: E402

OUT = os.path.join(os.path.dirname(HERE), "build", "circular")
L = Layout()
zona = {c.nome: c.zona for c in L.comps}
zona.update({a.nome: a.zona for a in L.aberturas})

AZUL, VERMELHO, LARANJA = (0.30, 0.55, 0.85), (0.85, 0.35, 0.30), (1.0, 0.60, 0.10)
CORPO, FUNDO = (0.85, 0.72, 0.55), (0.55, 0.58, 0.62)

doc = App.openDocument(os.path.join(OUT, "carcaca.FCStd"))
stl = os.path.join(OUT, "corpo.stl")
if os.path.exists(stl):
    m = doc.addObject("Mesh::Feature", "CorpoComMarca")
    m.Mesh = Mesh.Mesh(stl)
    m.Label = "Peca 1 - corpo com marca (= corpo.stl)"
    doc.getObject("Impressao").addObject(m)
    doc.getObject("Corpo").ViewObject.Visibility = False
    m.ViewObject.ShapeColor = CORPO
    m.ViewObject.Transparency = 55
else:
    doc.getObject("Corpo").ViewObject.ShapeColor = CORPO
    doc.getObject("Corpo").ViewObject.Transparency = 55

doc.getObject("Fundo").ViewObject.ShapeColor = FUNDO
for o in doc.Objects:
    if not o.Name.startswith("ref_"):
        continue
    nome = o.Name[4:]
    cor = AZUL if zona.get(nome) == "baixa" else VERMELHO
    if "PROVISORIO" in o.Label:
        cor = LARANJA
    o.ViewObject.ShapeColor = cor
    o.ViewObject.Transparency = 25 if o.Name.startswith("ref_") and nome in [c.nome for c in L.comps] else 45

doc.recompute()
view = Gui.activeDocument().activeView()
view.viewIsometric()
Gui.SendMsgToActiveView("ViewFit")
doc.saveAs(os.path.join(OUT, "arranjo_circular.FCStd"))
App.Console.PrintMessage("Salvo %s\n" % os.path.join(OUT, "arranjo_circular.FCStd"))

"""Gera models/esp32-devkit-v1.step: ESP32 DevKit V1 (DOIT, 30 pinos) montado
com modelos 3D da biblioteca do KiCad, para o arranjo (build_arranjo_3d.py).

Placa, furos e fileiras de pinos seguem o footprint ESP32_30pin
(github.com/syauqibilfaqih/ESP32-DevKit-V1-DOIT, MIT): 28.3 x 51.5 mm, fileiras
a 25.4 mm, 4 furos de 3 mm, micro-USB numa ponta e WROOM-32 com a antena na
outra. Botoes EN/BOOT, AMS1117 e CP2102 estao em posicao representativa; a cor
da placa (preta) e' escolha visual.

Referencia: face de cima da placa em Z=0; X e Y do footprint, com Y invertido
(convencao 3D do KiCad) - antena em +Y, USB em -Y.

Precisa da biblioteca 3D do KiCad (KICAD10_3DMODEL_DIR ou o caminho padrao do
KiCad 10 no Windows) e da GUI, que e' quem le e grava as cores:
    QT_QPA_PLATFORM=offscreen FreeCAD mechanical/scripts/make_esp32_devkit_v1.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

import FreeCAD as App
import ImportGui
import Part

V = App.Vector
KICAD = os.environ.get("KICAD10_3DMODEL_DIR",
                       r"C:\Program Files\KiCad\10.0\share\kicad\3dmodels")
OUT = os.path.join(_env.MODELS_DIR, "esp32-devkit-v1.step")

BOARD = (-13.33, -25.1, 28.3, 51.5)  # x0, y0, largura, comprimento
HOLES = [(-10.57, 22.14), (12.43, 22.14), (-10.57, -23.86), (12.43, -23.86)]
ROW_X = (-11.93, 13.47)
PIN1_Y = 18.54
FLIP = App.Rotation(V(0, 1, 0), 180)

# (rotulo, arquivo, posicao, rotacao)
PARTS = [
    # WROOM-32 entre as fileiras, antena (Y max do modelo) rente a borda
    ("ESP32-WROOM-32", "RF_Module.3dshapes/ESP32-WROOM-32.step",
     (sum(ROW_X) / 2, 26.4 - 15.74, 0), App.Rotation()),
    # barras soldadas por baixo: corpo plastico sob a placa, pinos para baixo
    ("pinos_esq", "Connector_PinHeader_2.54mm.3dshapes/PinHeader_1x15_P2.54mm_Vertical.step",
     (ROW_X[0], PIN1_Y, -1.6), FLIP),
    ("pinos_dir", "Connector_PinHeader_2.54mm.3dshapes/PinHeader_1x15_P2.54mm_Vertical.step",
     (ROW_X[1], PIN1_Y, -1.6), FLIP),
    # linha "PCB Edge" do footprint (Y -2.67 no 3D) na borda da placa
    ("micro_usb", "Connector_USB.3dshapes/USB_Micro-B_Molex_47346-0001.step",
     (1.0, -25.1 + 2.67, 0), App.Rotation()),
    ("botao_en", "Button_Switch_SMD.3dshapes/SW_SPST_PTS810.step", (-7.5, -21.5, 0), App.Rotation()),
    ("botao_boot", "Button_Switch_SMD.3dshapes/SW_SPST_PTS810.step", (9.5, -21.5, 0), App.Rotation()),
    ("AMS1117", "Package_TO_SOT_SMD.3dshapes/SOT-223.step", (1.0, -14.0, 0), App.Rotation()),
    ("CP2102", "Package_DFN_QFN.3dshapes/QFN-28-1EP_5x5mm_P0.5mm_EP3.35x3.35mm.step",
     (1.0, -5.0, 0), App.Rotation()),
]


def main():
    doc = App.newDocument("esp32_devkit_v1")

    x0, y0, w, l = BOARD
    pcb = Part.makeBox(w, l, 1.6, V(x0, y0, -1.6))
    for hx, hy in HOLES:
        pcb = pcb.cut(Part.makeCylinder(1.5, 2, V(hx, hy, -1.8)))
    board = doc.addObject("Part::Feature", "placa")
    board.Shape = pcb
    board.ViewObject.ShapeColor = (0.10, 0.10, 0.12)
    objs = [board]

    for label, rel, pos, rot in PARTS:
        before = set(o.Name for o in doc.Objects)
        ImportGui.insert(os.path.join(KICAD, rel), doc.Name)
        new = [o for o in doc.Objects if o.Name not in before]
        tops = [o for o in new if not any(p in new for p in o.InList)]
        for o in tops:
            o.Placement = App.Placement(V(*pos), rot).multiply(o.Placement)
            o.Label = label
        objs += tops

    doc.recompute()
    ImportGui.export(objs, OUT)
    print("salvo:", OUT)


main()

# a GUI sem janela (QT_QPA_PLATFORM=offscreen) nao encerra sozinha apos o script
if os.environ.get("QT_QPA_PLATFORM") == "offscreen":
    sys.stdout.flush()
    os._exit(0)

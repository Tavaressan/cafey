"""Exporta o solido final da Peca 1 para STEP.

Uso: freecadcmd mechanical/scripts/export_step.py   (depois de build_peca1.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part


def main():
    doc = App.openDocument(_env.FCSTD)
    obj = doc.getObject("peca1")
    if obj is None:
        raise SystemExit("objeto 'peca1' nao encontrado no FCStd")
    Part.export([obj], _env.STEP)
    size = os.path.getsize(_env.STEP)
    print("exportado: %s (%d bytes)" % (_env.STEP, size))


main()

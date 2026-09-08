"""Exporta a Peca 1 e a Peca 2 (variante 3D) para STL, pronto para o fatiador.

Novo requisito da issue #116 (impressao 3D): STL alem de STEP. Malha com
tolerancia linear 0.1 mm (adequada para impressao FDM; mais fina que o
necessario deixaria o arquivo grande sem ganho pratico de qualidade).

Uso: freecadcmd mechanical/scripts/export_stl.py
     (depois de build_peca1_3d.py e build_peca2_3d.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

import FreeCAD as App
import Mesh

LINEAR_DEFLECTION = 0.1  # mm


def export_um(fcstd_path, object_name, stl_path):
    doc = App.openDocument(fcstd_path)
    obj = doc.getObject(object_name)
    if obj is None:
        raise SystemExit("objeto '%s' nao encontrado em %s" % (object_name, fcstd_path))
    mesh = doc.addObject("Mesh::Feature", "%s_mesh" % object_name)
    shape_mesh = Mesh.Mesh()
    shape_mesh.addFacets(obj.Shape.tessellate(LINEAR_DEFLECTION))
    mesh.Mesh = shape_mesh
    Mesh.export([mesh], stl_path)
    size = os.path.getsize(stl_path)
    print("exportado: %s (%d bytes, %d triangulos)" % (stl_path, size, mesh.Mesh.CountFacets))
    App.closeDocument(doc.Name)


def main():
    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    export_um(_env.FCSTD, "peca1", _env.STL)
    export_um(_env.FCSTD2, "peca2", _env.STL2)


main()

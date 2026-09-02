"""Builders compartilhados (sem execucao no import).

Reune o Spreadsheet `params` e as primitivas parametricas usadas pela Peca 1 e
pela Peca 2. `freecadcmd` roda cada arquivo como modulo (nao como __main__),
entao o codigo que executa fica so' nos scripts de topo (build_*.py).
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part  # noqa: F401  (registra Part::* no documento)


def build_spreadsheet(doc):
    """Cria o Spreadsheet `params` a partir de parametros.csv."""
    sheet = doc.addObject("Spreadsheet::Sheet", "params")
    sheet.Label = "params"
    row = 1
    sheet.set("A%d" % row, "material")
    sheet.set("B%d" % row, _env.MATERIAL_TXT)
    sheet.setAlias("B%d" % row, "material_txt")
    row += 1
    for alias, value, _unit, origin in _env.load_params():
        a, b, c = "A%d" % row, "B%d" % row, "C%d" % row
        sheet.set(a, alias)
        if alias in _env.DERIVED:
            sheet.set(b, "=" + _env.DERIVED[alias])
        else:
            sheet.set(b, repr(value))
        sheet.setAlias(b, alias)
        if origin:
            sheet.set(c, origin)
        row += 1
    doc.recompute()
    return sheet


def apply_expr(obj, exprs):
    for prop, formula in exprs.items():
        obj.setExpression(prop, formula)


def box(doc, name, length, width, height, x, y, z):
    """Part::Box com dimensoes e canto minimo dados por expressao (strings)."""
    b = doc.addObject("Part::Box", name)
    apply_expr(b, {
        "Length": length, "Width": width, "Height": height,
        "Placement.Base.x": x, "Placement.Base.y": y, "Placement.Base.z": z,
    })
    return b


def cyl(doc, name, radius, height, base, axis):
    """Part::Cylinder. base=(x,y,z) expr strings; axis in {'X','Y','Z'}."""
    c = doc.addObject("Part::Cylinder", name)
    c.Radius = 1.0
    c.Height = 1.0
    if axis == "Y":
        c.Placement.Rotation = App.Rotation(App.Vector(1, 0, 0), -90)
    elif axis == "X":
        c.Placement.Rotation = App.Rotation(App.Vector(0, 1, 0), 90)
    apply_expr(c, {
        "Radius": radius, "Height": height,
        "Placement.Base.x": base[0],
        "Placement.Base.y": base[1],
        "Placement.Base.z": base[2],
    })
    return c

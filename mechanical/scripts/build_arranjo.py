"""Arranjo interno: Peca 1 + Peca 2 + divisoria + volumes de referencia.

Cada componente e' uma CAIXA ENVOLVENTE estimada, lida de
mechanical/params/componentes.csv - o usuario confirma os tamanhos depois
editando o CSV. Serve para visualizar as faixas (rede / divisoria / baixa
tensao), conferir a altura interna livre e achar conflitos grosseiros.

Snapshot: copia as formas dos FCStd das pecas. Rode build_peca1.py e
build_peca2.py antes.

Uso: freecadcmd mechanical/scripts/build_arranjo.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part

V = App.Vector


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
            raise SystemExit("falta %s - rode build_peca1.py / build_peca2.py" % p)

    doc = App.newDocument("arranjo")
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

    # --- demais componentes como caixas ---
    for c in comps:
        if c["nome"] == "divisoria":
            continue
        b = doc.addObject("Part::Feature", c["nome"])
        b.Shape = Part.makeBox(c["dx"], c["dy"], c["dz"], V(c["x"], c["y"], c["z"]))
        b.Label = c["nome"]
        _set_desc(b, c)

    doc.recompute()

    objs = [o for o in doc.Objects if o.Name not in ("ref_peca1", "ref_peca2")]
    Part.export(objs, _env.ARRANJO_STEP)
    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.ARRANJO_FCSTD)

    print("componentes:", len(comps))
    print("salvo :", _env.ARRANJO_FCSTD)
    print("step  :", _env.ARRANJO_STEP, "(divisoria + volumes, sem as chapas)")


def _set_desc(obj, c):
    txt = "VOLUME DE REFERENCIA (caixa envolvente ESTIMADA - confirmar). " \
          "Faixa: %s. Apoio: %s. Tamanho %g x %g x %g. %s" % (
              c["faixa"], c["apoio"], c["dx"], c["dy"], c["dz"], c["fonte"])
    try:
        obj.Label2 = txt
    except AttributeError:
        pass


main()

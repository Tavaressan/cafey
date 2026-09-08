"""Validacao geometrica da Peca 1 (variante 3D). Sai != 0 se algum criterio falhar.

Uso: freecadcmd mechanical/scripts/check_peca1_3d.py   (depois de build_peca1_3d.py)
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env

import FreeCAD as App
import Part

falhas = []


def check(cond, msg):
    print(("  OK  " if cond else "FALHA ") + msg)
    if not cond:
        falhas.append(msg)


def main():
    doc = App.openDocument(_env.FCSTD)
    g = _env.params_dict()
    peca1 = doc.getObject("peca1")
    shp = peca1.Shape
    bb = shp.BoundBox
    tol = 0.1

    check(shp.isValid(), "Shape.isValid()")
    check(len(shp.Solids) == 1, "solido unico (achou %d)" % len(shp.Solids))
    check(abs(bb.XLength - g["pegada_x"]) <= tol,
          "bbox X = %.2f ~ pegada_x %.1f" % (bb.XLength, g["pegada_x"]))
    check(abs(bb.YLength - g["pegada_y"]) <= tol,
          "bbox Y = %.2f ~ pegada_y %.1f" % (bb.YLength, g["pegada_y"]))
    check(abs(bb.ZLength - g["altura_externa"]) <= tol,
          "bbox Z = %.2f ~ altura_externa %.1f" % (bb.ZLength, g["altura_externa"]))

    # parede minima para FDM estrutural (>= 2.0 mm; recomendado 3 perimetros
    # de bico 0.4 mm)
    check(g["parede"] >= 2.0, "parede %.1f mm >= 2.0 mm (minimo FDM estrutural)" % g["parede"])

    # massa plausivel em solido cheio (limite superior; infill parcial reduz
    # a massa real - fatiador quem decide o infill)
    massa_g = shp.Volume * _env.DENSIDADE_PETG * 1e6
    check(0.1 < massa_g / 1000 < 1.5,
          "massa solido cheio %.0f g em faixa esperada (infill real reduz)" % massa_g)

    # --- flecha de LONGO PRAZO do tampo sob a carga de projeto (3.0 kgf) ---
    # Limite: L/300 do menor vao = criterio usual de rigidez de painel; nao ha
    # norma de pedestal de eletrodomestico, entao e' referencia conservadora.
    # A flecha checada e' a de LONGO PRAZO (elastica x fator_fluencia): e' a
    # fluencia do PETG sob carga continua + calor que faz a diferenca, nao a
    # flecha instantanea. Sem reforco, o numero ESTOURA o limite - por isso as
    # nervuras + colunas existem (ver deflexao_tampo_mm em _env3d.py).
    limite = min(g["pegada_x"], g["pegada_y"]) / 300.0
    flecha_lp = _env.deflexao_tampo_mm(g, com_reforco=True)
    flecha_sem = _env.deflexao_tampo_mm(g, com_reforco=False)
    print("       vao efetivo do tampo: %.0f mm (sem reforco %.0f mm)"
          % (_env.vao_menor_tampo_mm(g, True), _env.vao_menor_tampo_mm(g, False)))
    print("       flecha longo prazo SEM reforco ~ %.2f mm (referencia do audit; > limite)"
          % flecha_sem)
    check(flecha_lp < limite,
          "flecha longo prazo COM reforco %.3f mm < L/300 = %.3f mm"
          % (flecha_lp, limite))

    # --- margens de projeto das aberturas (a partir dos alias) ---
    mp, me = g["margem_parede"], g["margem_extremidade"]
    parede_top = g["altura_externa"] - g["parede"]
    centro = g["abertura_centro_z"]

    for nome, alt in [("janela_rf", g["janela_rf_h"]), ("tomada_j1", g["tomada_j1_h"])]:
        folga_topo = centro - alt / 2
        folga_base = (parede_top - centro) - alt / 2
        check(folga_topo >= mp, "%s: folga p/ topo %.1f >= %.0f" % (nome, folga_topo, mp))
        check(folga_base >= mp, "%s: folga p/ base %.1f >= %.0f" % (nome, folga_base, mp))

    for nome, x, w in [("janela_rf", g["janela_rf_x"], g["janela_rf_w"]),
                       ("furo_led", g["furo_led_x"], g["furo_led"]),
                       ("furo_botao", g["furo_botao_x"], g["furo_botao"]),
                       ("prensa_cabo", g["prensa_cabo_x"], g["prensa_cabo_d"]),
                       ("tomada_j1", g["tomada_j1_x"], g["tomada_j1_w"])]:
        check(x - w / 2 >= me, "%s: extremidade esq %.1f >= %.0f" % (nome, x - w / 2, me))
        check(g["pegada_x"] - (x + w / 2) >= me,
              "%s: extremidade dir %.1f >= %.0f" % (nome, g["pegada_x"] - (x + w / 2), me))

    check(g["rasgo_w"] <= 3.0, "rasgo_w %.1f <= 3 (bloqueia dedo)" % g["rasgo_w"])
    check(g["rasgo_altura"] - g["rasgo_w"] / 2 >= 0,
          "rasgo nao ultrapassa a borda inferior")

    rasgos = [o for o in doc.Objects if o.Name.startswith(("rasgo_e", "rasgo_d"))]
    for o in rasgos:
        b = o.Shape.BoundBox
        check(b.YMin >= me - 0.01 and b.YMax <= g["pegada_y"] - me + 0.01,
              "%s: Y [%.1f, %.1f] dentro de [%.0f, %.0f]"
              % (o.Name, b.YMin, b.YMax, me, g["pegada_y"] - me))

    # rasgos nao podem colidir com nenhum boss/insert de fixacao
    fixacao = [o for o in doc.Objects if o.Name.startswith(("boss_", "insert_"))]
    pior = 0.0
    for a in rasgos:
        for f in fixacao:
            pior = max(pior, a.Shape.common(f.Shape).Volume)
    check(pior < 1e-6, "rasgos x bosses/inserts sem sobreposicao (max %.3f mm3)" % pior)

    # --- toda abertura de face atravessa a parede (nao ficou pele) ---
    aberturas = ["janela_rf", "furo_led", "furo_botao", "prensa_cabo",
                 "tomada_j1", "recorte_usb"]
    aberturas += sorted(o.Name for o in doc.Objects
                        if o.Name.startswith(("rasgo_e", "rasgo_d")))
    for nome in aberturas:
        o = doc.getObject(nome)
        c = o.Shape.BoundBox.Center
        if o.Shape.BoundBox.YLength < 5:            # face frontal/traseira: normal Y
            probe = Part.makeBox(2, 4 * g["parede"], 2,
                                 App.Vector(c.x - 1, c.y - 2 * g["parede"], c.z - 1))
        else:                                       # lateral: normal X
            probe = Part.makeBox(4 * g["parede"], 2, 2,
                                 App.Vector(c.x - 2 * g["parede"], c.y - 1, c.z - 1))
        resto = shp.common(probe).Volume
        check(resto < 1e-6, "%s atravessa a parede (resto %.2f mm3)" % (nome, resto))

    # --- 8 bosses/inserts presentes e sem furo cego passante alem do previsto ---
    bosses = [o for o in doc.Objects if o.Name.startswith("boss_")]
    inserts = [o for o in doc.Objects if o.Name.startswith("insert_")]
    check(len(bosses) == 8, "8 bosses de insert (achou %d)" % len(bosses))
    check(len(inserts) == 8, "8 furos de insert (achou %d)" % len(inserts))
    check(g["insert_furo"] < g["boss_d"] - 1.0,
          "insert_furo %.1f < boss_d %.1f - 1 mm (parede minima do boss)"
          % (g["insert_furo"], g["boss_d"]))

    # --- reforco estrutural: geometria e folga com o arranjo interno ---
    reforco = [o for o in doc.Objects
               if o.Name.startswith(("nervura_perimetral_", "coluna_"))
               or o.Name == "nervura_transversal"]
    colunas = [o for o in doc.Objects if o.Name.startswith("coluna_")]
    check(len(colunas) == int(g["coluna_qtd"]),
          "colunas de canto: %d (coluna_qtd = %d)" % (len(colunas), int(g["coluna_qtd"])))
    for o in colunas:
        b = o.Shape.BoundBox
        check(abs(b.ZMax - (-g["parede"])) < 0.1 and abs(b.ZMin - (-g["altura_externa"])) < 0.1,
              "%s vai do tampo (z=%.1f) ao plano do fundo (z=%.1f)" % (o.Name, b.ZMax, b.ZMin))
    if g.get("nervura_transversal"):
        check(any(o.Name == "nervura_transversal" for o in doc.Objects),
              "nervura transversal presente (nervura_transversal = 1)")

    # nervuras/colunas nao podem colidir com nenhum volume de componentes_3d.csv,
    # e onde ficam por cima de um componente precisam de folga_comp_reforco.
    import Part as _P
    fc = g["folga_comp_reforco"]
    pior_inter = 0.0
    pior_folga = 1e9
    for c in _env.load_componentes():
        cx0, cy0, cz0 = c["x"], c["y"], c["z"]
        caixa = _P.makeBox(c["dx"], c["dy"], c["dz"], App.Vector(cx0, cy0, cz0))
        for o in reforco:
            inter = o.Shape.common(caixa).Volume
            pior_inter = max(pior_inter, inter)
            rb = o.Shape.BoundBox
            plan_overlap = (rb.XMin < cx0 + c["dx"] and rb.XMax > cx0
                            and rb.YMin < cy0 + c["dy"] and rb.YMax > cy0)
            if plan_overlap:
                folga = rb.ZMin - (cz0 + c["dz"])
                pior_folga = min(pior_folga, folga)
    check(pior_inter < 1.0,
          "reforco x componentes: sem interferencia (max %.2f mm3)" % pior_inter)
    check(pior_folga >= fc - 0.05,
          "folga minima reforco -> componente %.2f mm >= %.1f mm" % (pior_folga, fc))

    print()
    if falhas:
        raise SystemExit("%d criterio(s) falharam" % len(falhas))
    print("todos os criterios OK")


main()

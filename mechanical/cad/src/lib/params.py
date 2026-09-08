"""Parametros do pedestal impresso (FDM) — variante cadgen/build123d.

Transcricao 1:1 de mechanical/params/parametros_3d.csv e componentes_3d.csv do
projeto FreeCAD (o gate de freshness do cadgen NAO rastreia leitura de arquivo,
entao os valores viram constantes nomeadas aqui). Manter em sincronia com os
CSVs a mao — a comparacao com o modelo FreeCAD depende disso.

Sistema de coordenadas (identico ao do FreeCAD):
  - face superior do tampo em Z = 0; a peca desce para Z < 0
  - X ao longo de pegada_x (0..260), Y ao longo de pegada_y (0..210)
  - Y = 0  -> parede frontal (baixa tensao)
  - Y = pegada_y -> parede traseira (rede)
  - X = 0  -> parede esquerda
  - origem no canto (nao no centro) — divergencia deliberada do default cadgen,
    para casar com o FreeCAD e permitir comparar coordenadas diretamente.
"""

# --- processo ---
PAREDE = 2.4
PAREDE_FUNDO = 3.0

# --- pegada e altura ---
PEGADA_X = 260.0
PEGADA_Y = 210.0
ALTURA_EXTERNA = 53.0
ABA_FUNDO = 14.0            # flange interna continua na base

# --- reforco estrutural (audit da carga de 3 kg) ---
NERVURA_H = 6.0
NERVURA_W = 3.0
NERVURA_TRANSVERSAL = 1
COLUNA_QTD = 4
COLUNA_LADO = 12.0
TRAVESSIA_FOLGA = 6.0

# --- pes ---
PE_ALTURA = 10.0
PE_D = 15.0
PE_INSET = 20.0

# --- faixas internas ---
FAIXA_REDE = 120.0
DIVISORIA = 3.0
FAIXA_BAIXA = 84.0

# --- rasgos de ventilacao ---
RASGO_L = 25.0
RASGO_W = 3.0
RASGO_ALTURA = 15.0

# --- aberturas de face ---
ABERTURA_CENTRO_Z = 22.0
JANELA_RF_W = 40.0
JANELA_RF_H = 28.0
JANELA_RF_X = 75.0
FURO_LED = 5.0
FURO_LED_X = 150.0
FURO_BOTAO = 12.0
FURO_BOTAO_X = 195.0
PRENSA_CABO_D = 15.0
PRENSA_CABO_X = 70.0
TOMADA_J1_W = 45.5
TOMADA_J1_H = 23.0
TOMADA_J1_X = 185.0
USB_W = 16.0
USB_H = 9.0
USB_Y = 165.0               # da traseira: Y_real = PEGADA_Y - USB_Y

# --- fixacao Peca1/Peca2: insert termico M3 ---
INSERT_FURO = 4.0
INSERT_PROF = 6.0
BOSS_D = 9.0
FURO_PASSAGEM = 3.4

# --- margens ---
MARGEM_EXTREMIDADE = 25.0

# --- derivados ---
ALTURA_PAREDE = ALTURA_EXTERNA - PAREDE          # 50.6
Z_BASE = -ALTURA_EXTERNA                          # -53.0  (base das paredes/flange)
Z_TETO = -PAREDE                                  # -2.4   (face inferior do tampo)
Z_NERVURA_BOT = -PAREDE - NERVURA_H               # -8.4
Y_DIVISORIA = PAREDE + FAIXA_BAIXA                # 86.4
ABERTURA_Z = -ABERTURA_CENTRO_Z                   # -22.0

# --- volumes de referencia dos componentes (componentes_3d.csv) ---
# (nome, faixa, dx, dy, dz, x, y, z)  — (x,y,z) = canto minimo
COMPONENTES = [
    ("esp32",             "baixa", 29, 52, 13, 60,   3,    -47),
    ("placa_aux",         "baixa", 45, 32, 26, 105,  40,   -47),
    ("led_rgb",           "baixa", 6,  18, 6,  147,  1.2,  -25),
    ("botao",             "baixa", 16, 20, 16, 187,  1.2,  -30),
    ("usb_painel",        "baixa", 16, 18, 15, 1.2,  29,   -30),
    ("borne_rede",        "rede",  55, 25, 34, 10,   100,  -47),
    ("terra_j3",          "rede",  20, 18, 25, 72,   100,  -47),
    ("porta_fusivel",     "rede",  14, 66, 14, 114,  128,  -46),
    ("hlk_pm01",          "rede",  34, 20, 15, 100,  98.2, -47),
    ("modulo_rele",       "rede",  50, 27, 19, 150,  98.2, -47),
    ("prensa_cabo_corpo", "rede",  20, 22, 20, 60,   187,  -32),
    ("tomada_j1_corpo",   "rede",  45, 41, 23, 162,  168,  -33.5),
    ("travessia_fios",    "fios",  14, 32, 9,  148,  68,   -41),
]

# divisoria (componentes_3d.csv): placa + rasgo de passa-fio para a travessia
DIVISORIA_DX = 255.2
DIVISORIA_DY = 3.0
DIVISORIA_DZ = 40.0
DIVISORIA_X = 2.4
DIVISORIA_Y = 85.2
DIVISORIA_Z = -50.6
# rasgo da travessia (build_arranjo_3d.py): centrado na travessia_fios
TRAVESSIA_X = 148.0
TRAVESSIA_DX = 14.0
TRAVESSIA_Z = -41.0
TRAVESSIA_DZ = 9.0

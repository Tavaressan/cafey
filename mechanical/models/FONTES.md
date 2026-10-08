# Modelos visuais — origem, licença e divergências

Modelos usados por `scripts/build_arranjo_3d.py` (tabela `MODELS`) no lugar das
caixas de `params/componentes_3d.csv`. São **representação visual**: a validação
(`check_arranjo_3d.py`) continua sobre as caixas, que ficam ocultas no FCStd.

| Arquivo | Componente | Origem | Licença |
|---|---|---|---|
| `esp32-devkit-v1.step` | `esp32` | Gerado por `scripts/make_esp32_devkit_v1.py` com peças da biblioteca 3D do KiCad 10.0 (WROOM-32, 2× barra 1×15, micro-USB Molex 47346, 2× PTS810, SOT-223, QFN-28); placa, furos e fileiras pelo footprint `ESP32_30pin` de [syauqibilfaqih/ESP32-DevKit-V1-DOIT](https://github.com/syauqibilfaqih/ESP32-DevKit-V1-DOIT) @ `80365a4` (MIT) | Idem KiCad |
| `modulo-rele-v3.step` | `modulo_rele` | GrabCAD, `5v-relay-module-v3-0-1.snapshot.6.zip` | Termos do GrabCAD |
| `barramento-terra-lukma-6p.iges` | `terra_j3` | GrabCAD, `barra-de-bornes-terra-1.snapshot.6.zip` (`Barramento Terra LUKMA.iges`) | Termos do GrabCAD |
| `porta-fusivel-inline.step` | `porta_fusivel` | GrabCAD, `inline-fuse-holder-1.snapshot.2.zip` (autor no cabeçalho: jvalderrama) | Termos do GrabCAD |
| `tomada-tpa2-3-e3f.igs` | `tomada_j1_corpo` | GrabCAD, `tomada-2p-t-10-a-nbr-14136-referencia-tpa2-3-e3f-…-black-1.snapshot.2.zip` | Termos do GrabCAD |
| `placa-ilhada-60x40.step` | `placa_aux` | GrabCAD, `prototype-pcb-1.snapshot.1.zip` (`STEP/60mm x 40mm.step`) | Termos do GrabCAD |
| `PG9 Gland.step` | `prensa_cabo_corpo` | Fornecido pelo usuário; **origem não registrada** (cabeçalho: exportado do FreeCAD, 2023-01-13) | Desconhecida |
| `hlk-pmxx.step` | `hlk_pm01` | Biblioteca 3D do KiCad 10.0, `Converter_ACDC.3dshapes/Converter_ACDC_Hi-Link_HLK-PMxx.step` | [CC-BY-SA 4.0 com exceção para designs e arquivos gerados](https://gitlab.com/kicad/libraries/kicad-packages3D/-/blob/master/LICENSE.md) |
| `led-5mm-rgb.step` | `led_rgb` | Biblioteca 3D do KiCad 10.0, `LED_THT.3dshapes/LED_D5.0mm-4_RGB.step` | Idem KiCad |
| `adafruit-916-botao-metal-16mm.step` | `botao` | [adafruit/Adafruit_CAD_Parts](https://github.com/adafruit/Adafruit_CAD_Parts) @ `c128bce`, `916 metal button/916 Metal Button.step` | MIT |
| `adafruit-3258-microusb-painel.step` | `usb_painel` | Idem, `3258 USB Panel Mout Cable/3258 microUSB panel mount.step` | MIT |

Os nomes de `.zip` acima identificam o download original no GrabCAD; os zips
foram apagados em 08/10/2026 e só os arquivos usados ficaram aqui. Também
baixados e não usados: `tomada-eletrica-de-3-pinos…` (é um **plugue** macho,
não tomada), `Barramento de terra 12 Posições.STEP` (124 mm, não cabe na faixa
de rede), `bloque-de-terminales-2-rojo-1…` (borne de PCB de 2 vias, sem
componente definido) e `esp32-c3-mini-devkit-1…` (ESP32-C3, substituído pelo
DevKit V1 montado).

**Licença — antes de versionar.** O repositório é público. A
[central de ajuda do GrabCAD](https://help.grabcad.com/article/246-how-can-models-be-used-and-shared)
admite uso público não comercial com crédito e link ao autor, mas os
[Termos de Uso](https://grabcad.com/terms) concedem licença intransferível e não
sublicenciável, para uso próprio, interno e não comercial. Redistribuir os
arquivos (e o `arranjo_3d.step` gerado, que embute a geometria) num repositório
público não está claramente autorizado: pedir permissão aos autores ou manter
esses arquivos fora do git.

## Divergências modelo × peça do projeto

Os modelos são os mais próximos encontrados, não a peça comprada. Conferir
antes de usar o arranjo como referência dimensional:

- **`esp32`**: DevKit V1 montado — placa, furos, fileiras de pinos, WROOM-32 e
  micro-USB nas posições do footprint; botões EN/BOOT, AMS1117 e CP2102 em
  posição representativa; placa preta por escolha visual. Ocupa
  28,3 × 52,6 × 13,2 mm contra o volume de 29 × 52 × 13 (o micro-USB passa
  1,1 mm da borda). Para regerar: `QT_QPA_PLATFORM=offscreen FreeCAD
  mechanical/scripts/make_esp32_devkit_v1.py` (precisa do KiCad instalado).
- **`modulo_rele`**: o modelo tem **1 canal** (47 × 27 mm); o projeto usa o de
  2 canais LAFVIN (~50 × 39). Girado com a barra IN/5V/GND voltada para a
  divisória, passa 8 mm do volume em Y.
- **`terra_j3`**: barramento de terra de 6 posições (84,9 mm) contra um volume
  de 18 mm; a lista de compras pede borne DIN verde-amarelo em trilho (E14/E15).
  O IGES traz a barra como faces soltas e sem cor: o build lê com `Part.read` e
  pinta apoios de verde e barra/parafusos de latão.
- **`porta_fusivel`**: porta-fusível de **lâmina** (automotivo); a lista pede
  5×20 mm de vidro in-line (E12). Os rabichos de 138 mm são descartados no build.
- **`tomada_j1_corpo`**: moldura 45 × 25 mm, corpo ~44,2 × 21,2; o recorte
  `tomada_j1` é 45,5 × 23 — em X a moldura é **menor** que o recorte. IGES só de
  faces (sem sólido): no STEP reimportado vira um objeto por face.
- **`botao`**: Adafruit 916 é a versão **liga/desliga com anel de LED**; a lista
  (M1) pede momentâneo NA sem anel. Mesmo furo de 16 mm.
- **`usb_painel`**: corpo 12 × 9,8 mm contra o recorte `usb_w` × `usb_h` de
  16 × 9 (placeholder) — 0,8 mm mais alto que o recorte; flange com orelhas de
  parafuso de 25 mm que o recorte atual não prevê.
- **`prensa_cabo_corpo`**: o modelo prevê parede de 2,0 mm; com 2,4 mm a
  contraporca invade 0,4 mm a parede (só visual).
- **`hlk_pm01`**: pinos 4,9 mm abaixo do corpo; o volume de 15 mm não os incluía
  (passa 5 mm em Z). Eixo longo em X, com a saída DC voltada para a travessia.
- **`placa_aux`**: só a placa ilhada, sem C1/C2/C3/R1–R5 (o leiaute não
  existe ainda). O modelo de 60 × 40 é recortado no build para os 45 × 32 do
  volume; a altura de 26 mm do volume (definida pelo C1) não aparece. O STEP
  não traz cor: o build pinta fenolite e as ilhas de cobre.

Sem modelo (continuam caixa): `borne_rede` (a lista de compras troca os bornes
por conectores WAGO 221) e `travessia_fios`.

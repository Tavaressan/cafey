# Firmware — ESP32 (ESP-IDF)

Fatia atual: **I/O local, sem rede**. Botão alterna o relé; LED RGB indica o estado.

| Sinal | GPIO | Observação |
|---|---|---|
| Relé (IN) | 26 | gatilho de nível ALTO; R1 10 kΩ pull-down |
| Botão | 27 | R2 10 kΩ pull-up + C3 100 nF; solto = 1, pressionado = 0 |
| LED vermelho | 21 | R3 220 Ω — aceso = cafeteira desligada |
| LED verde | 22 | R4 100 Ω — aceso = cafeteira ligada |
| LED azul | 23 | R5 100 Ω — aceso durante o boot |

LED RGB é de cátodo comum: nível ALTO no GPIO acende.

## Simular no Wokwi (wokwi.com)

1. Abra <https://wokwi.com/projects/new/esp-idf-esp32> (template ESP-IDF + ESP32, compila no navegador).
2. Substitua o conteúdo de `main/main.c` pelo deste repositório.
3. Abra a aba `diagram.json` e cole o `diagram.json` daqui.
4. Confirme que o `wokwi.toml` do template aponta para o build do ESP-IDF
   (`firmware = 'build/flasher_args.json'`); se não, use o `wokwi.toml` daqui.
5. Clique em **Run**. O log do `ESP_LOGI` aparece no monitor serial; clicar em
   `SW1` alterna o relé e a cor do LED.

Se algum pino de GND do DevKit não existir com o nome `GND.1/2/3` no editor
visual, refaça essas ligações arrastando — a lógica não muda.

## Compilar localmente (ESP-IDF ≥ 5.x)

```sh
. $IDF_PATH/export.sh
idf.py set-target esp32
idf.py build
idf.py -p <porta> flash monitor
```

Ainda não compilado nesta máquina (sem toolchain ESP-IDF instalada).

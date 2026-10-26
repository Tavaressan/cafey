# Procedimento de Segurança em Hardware: Flash Encryption, NVS Encryption e Secure Boot v2

> **Origem:** Issue #277 (FW-25), spike `docs/spikes/cadastro-dispositivo-aws-iot.md` (Fase 2).  
> **Hardware Alvo:** ESP32-D0WD-V3 (ESP32 DevKit V1 30 pinos, chip revision 3.1).  
> **Status:** Documentado para validação em placa reserva de testes.  

---

> [!CAUTION]
> **AVISO DE RISCO CRÍTICO E IRREVERSIBILIDADE (QUEIMA DE eFUSES)**  
> O ESP32 clássico utiliza fusíveis eletrônicos programáveis (*eFuses*) do tipo OTP (*One-Time Programmable*).  
> Uma vez ativados o **Secure Boot v2** e o **Flash Encryption em modo Release**:
> 1. Os eFuses correspondentes são permanentemente queimados;
> 2. O chip aceitará **exclusivamente** binários assinados pela chave privada correspondente;
> 3. A perda da chave privada de assinatura (`secure_boot_signing_key.pem`) inutiliza o microcontrolador permanentemente para qualquer nova gravação ou atualização OTA;
> 4. **NUNCA execute este procedimento na placa protótipo única da bancada.** Utilize estritamente uma placa de desenvolvimento reserva dedicada a testes destrutivos.

---

## 1. Motivação e Arquitetura de Proteção

No firmware do Cafey, o certificado de cliente e a chave privada para conexão TLS mútua com o AWS IoT Core são armazenados na memória flash na partição NVS (`nvs`). Em modo padrão de fábrica, a flash SPI é lida em claro, permitindo a extração física das credenciais via leitura direta dos pinos da flash.

O ESP32 clássico não possui periférico dedicado de Digital Signature (presente apenas em chips mais recentes, como ESP32-S3 e ESP32-C3). Para alcançar proteção robusta em nível de hardware, a arquitetura adota a tríade recomendada pela Espressif:

1. **Flash Encryption (Modo Release):** Todos os dados gravados nas partições de código (`app`), tabelas e partição `nvs_keys` são cifrados com AES-256 usando uma chave de criptografia de flash gerada internamente pelo hardware no primeiro boot e protegida por eFuses de leitura desabilitada.
2. **NVS Encryption:** A partição NVS armazena dados em blocos criptografados usando chaves geradas e salvas na partição `nvs_keys` (4 KB, `subtype=nvs_keys`), que por sua vez reside sob a proteção do Flash Encryption.
3. **Secure Boot v2 (RSA-3072):** Garante a integridade da cadeia de boot (`ROM Bootloader` $\to$ `2nd Stage Bootloader` $\to$ `Application Image`), verificando a assinatura digital antes de permitir a execução do código.

---

## 2. Particionamento da Memória Flash (`firmware/partitions.csv`)

A tabela de partições do Cafey foi atualizada para comportar a partição de chaves de criptografia NVS sem alterar os offsets fixos das partições originais:

```csv
# ESP-IDF Partition Table for Cafey Firmware
# Name,     Type,  SubType,  Offset,  Size,   Flags
nvs,        data,  nvs,      0x9000,  0x6000,
phy_init,   data,  phy,      0xf000,  0x1000,
factory,    app,   factory,  0x10000, 2M,
storage,    data,  nvs,      ,        1M,
nvs_keys,   data,  nvs_keys, ,        0x1000, encrypted
```

- **`nvs` (0x9000, 24 KB):** Mantida no offset padrão utilizado pelo provisionamento inicial.
- **`nvs_keys` (Offset automático, 4 KB, flag `encrypted`):** Reserva os blocos necessários para as chaves XTS-AES-128 do NVS Encryption. A flag `encrypted` instrui o bootloader a exigir que este setor seja criptografado por Flash Encryption.

---

## 3. Preparação das Chaves Criptográficas

Execute os passos a seguir em uma máquina de desenvolvimento segura:

### 3.1 Geração da Chave de Assinatura do Secure Boot (RSA-3072)
```bash
# 1. Gerar chave privada RSA 3072 bits
espsecure.py generate_signing_key --version 2 secure_boot_signing_key.pem

# 2. Extrair a chave pública para validação de digest
espsecure.py extract_public_key --version 2 --keyfile secure_boot_signing_key.pem signature_verification_key.bin

# 3. Gerar o resumo da chave (digest) a ser gravado no eFuse BLOCK2
espsecure.py generate_digest --keyfile secure_boot_signing_key.pem digest.bin
```

> [!IMPORTANT]
> A chave `secure_boot_signing_key.pem` deve ser armazenada em cofre de chaves protegido (ex.: AWS Secrets Manager ou HSM offline) com backup redundante. **Se ela for perdida, novas versões de firmware não poderão ser gravadas nem instaladas por OTA.**

### 3.2 Geração da Chave de Criptografia da NVS
```bash
# Gera o binário de 4KB com as chaves NVS criptografadas
python $IDF_PATH/components/nvs_flash/nvs_partition_generator/nvs_partition_gen.py generate_key --keyfile nvs_keys.bin
```

---

## 4. Procedimento de Ativação Passo a Passo (Placa Reserva)

### Passo 1: Compilação com `sdkconfig.security`
Compile o bootloader e o firmware utilizando o arquivo de perfil seguro:
```bash
cd firmware
idf.py -D SDKCONFIG_DEFAULTS="sdkconfig.defaults;sdkconfig.security" build
```

O processo de build gerará automaticamente os binários assinados:
- `bootloader.bin` assinado com Secure Boot v2.
- `cafey_firmware.bin` assinado com Secure Boot v2.

### Passo 2: Gravação Inicial na Placa Reserva
Grave o bootloader, tabela de partições, partição de chaves NVS e o binário da aplicação:
```bash
# Gravar partição de chaves NVS (nvs_keys) no offset correspondente
esptool.py --chip esp32 --port /dev/ttyUSB0 write_flash \
    0x1000 build/bootloader/bootloader.bin \
    0x8000 build/partition_table/partition-table.bin \
    0x310000 nvs_keys.bin \
    0x10000 build/cafey_firmware.bin
```

### Passo 3: Primeiro Boot e Queima de eFuses
Ao reiniciar a placa, o bootloader de primeiro estágio (ROM) e o segundo estágio executam o processo de ativação:
1. Validação da assinatura do bootloader contra o digest RSA-3072.
2. Queima do eFuse `ABS_DONE_1` (Secure Boot v2 ativo).
3. Geração de chave aleatória pelo gerador de ruído térmico (TRNG) e gravação no eFuse `BLOCK1` (Flash Encryption Key).
4. Criptografia in-place dos blocos de flash marcados como criptografados (bootloader, partitions, factory app e `nvs_keys`).
5. Queima do eFuse `FLASH_CRYPT_CNT` e `FLASH_CRYPT_CONFIG`.
6. Reinicialização do dispositivo em modo seguro.

---

## 5. Gravação da Partição de Provisionamento NVS em Dispositivo Criptografado

Com Flash Encryption e NVS Encryption ativados, a imagem NVS gerada por `nvs_partition_gen.py` contendo certificados AWS IoT (`nvs_data.bin`) **não pode ser gravada diretamente em claro**.

Para gravar o provisionamento pós-criptografia via serial:

```bash
# 1. Gerar a imagem NVS com suporte a criptografia utilizando a chave nvs_keys.bin
python $IDF_PATH/components/nvs_flash/nvs_partition_generator/nvs_partition_gen.py encrypt \
    firmware/certs/provisioning.csv \
    firmware/certs/provisioning_encrypted.bin \
    0x6000 \
    --inputkey nvs_keys.bin

# 2. Gravar na partição nvs (offset 0x9000)
# A partição NVS gerencia a própria decriptografia via nvs_keys;
esptool.py --chip esp32 --port /dev/ttyUSB0 write_flash 0x9000 firmware/certs/provisioning_encrypted.bin
```

---

## 6. Atualizações OTA com Secure Boot v2 Ativo

Em dispositivos com Secure Boot ativado, o processo OTA do ESP-IDF (`esp_ota_ops`) valida rigorosamente a assinatura de cada novo binário antes de alternar a partição de boot:

1. O novo binário gerado pelo pipeline de CI/CD deve ser assinado:
   ```bash
   espsecure.py sign_data --version 2 --keyfile secure_boot_signing_key.pem --output cafey_firmware_signed.bin build/cafey_firmware.bin
   ```
2. O firmware baixa o binário assinado via HTTPS.
3. A biblioteca de OTA do ESP-IDF extrai a assinatura RSA-3072 no rodapé do binário, confere contra o digest armazenado no eFuse do ESP32 e, somente em caso de validação criptográfica perfeita, grava e ativa a nova partição.
4. Caso a assinatura seja inválida ou pertença a outra chave, o download é rejeitado com erro `ESP_ERR_OTA_VALIDATE_FAILED` e o firmware atual permanece intacto.

---

## 7. Matriz de Riscos Operacionais

| Risco | Impacto | Mitigação |
|---|---|---|
| **Perda da chave privada de assinatura** | **Catastrófico (Placa 'brickada')** | Backup triplo offline em mídias encriptadas e cofre AWS Secrets Manager com controle de acesso IAM estrito. |
| **Ativação acidental no protótipo de desenvolvimento** | **Grave (Perda de capacidade de depuração)** | `sdkconfig.security` mantido fora do pipeline padrão de CI e isolado de `sdkconfig.defaults`. |
| **Falha de energia no primeiro boot de encriptação** | **Médio (Corrupção de flash)** | Conectar a placa em fonte de alimentação DC regulada com no-break durante o procedimento de primeiro boot. |
| **Tentativa de gravação NVS em claro** | **Baixo (Dados corrompidos na NVS)** | Uso obrigatório do script `nvs_partition_gen.py encrypt` passando o arquivo de chaves `nvs_keys.bin`. |

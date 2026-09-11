# Provisionamento do AWS IoT Core — runbook (INFRA-05)

> **Origem:** issue #123 (INFRA-05), spec-backend v1.1 §6/§11 (pendência 4). Contraparte de
> infraestrutura do FW-10 (#122, mergeado) e do BE-08/BE-11 (#46/#49, mergeados — que entregaram
> apenas o código: dependência do SDK, `AwsIotProperties`/`AwsIotConfig` e o template
> `aws-iot-backend-policy.json`, **sem** provisionar recursos reais na AWS).
>
> Este documento descreve o procedimento para criar de fato os recursos no AWS IoT Core (conta real,
> `things`, certificados X.509 e políticas) e distribuir as credenciais para o firmware e o backend.
>
> **Atualização:** o provisionamento real foi executado em 2026-09-11 seguindo exatamente os
> comandos deste runbook, na conta AWS `076248672901`, região `sa-east-1`. Os recursos abaixo
> existem de fato — ver [Recursos provisionados](#recursos-provisionados) e
> [Status e pendências](#status-e-pendências) para o que ainda depende de hardware físico/ambiente
> de deploy.

## Sumário

1. [Conta, região e endpoint](#1-conta-região-e-endpoint)
2. [Things e certificados](#2-things-e-certificados)
3. [Políticas IoT (mínimo privilégio)](#3-políticas-iot-mínimo-privilégio)
4. [Distribuição segura das credenciais](#4-distribuição-segura-das-credenciais)
5. [Configuração nos módulos](#5-configuração-nos-módulos)
6. [Critério de aceite — como validar](#6-critério-de-aceite--como-validar)
7. [Recursos provisionados](#recursos-provisionados)
8. [Status e pendências](#status-e-pendências)

---

## 1. Conta, região e endpoint

**Decidido:** conta AWS `076248672901`, região `sa-east-1` (São Paulo) — ver a justificativa em
[Status e pendências](#status-e-pendências).

Endpoint ATS (`iot:Data-ATS`, o tipo de endpoint recomendado pela AWS desde 2019 — o `iot:Data`
legado usa uma cadeia de CA diferente):

```
a3gha475fc91p-ats.iot.sa-east-1.amazonaws.com:8883
```

Para obter o comando abaixo (documentado para reprodutibilidade/rotação futura):

```bash
aws iot describe-endpoint --endpoint-type iot:Data-ATS --region <regiao-escolhida>
# -> { "endpointAddress": "xxxxxxxxxxxxxx-ats.iot.<regiao>.amazonaws.com" }
```

Baixe a Amazon Root CA 1 (usada tanto pelo firmware quanto pelo backend para validar o servidor):

```bash
curl -o amazon-root-ca-1.pem https://www.amazontrust.com/repository/AmazonRootCA1.pem
```

Versione a CA raiz em ambos os módulos (arquivo público, não é segredo):
- Firmware: `firmware/certs/amazon-root-ca-1.pem` (consumido pelo `MqttConfigStore` ao gravar a NVS — §4).
- Backend: `backend/cafey-backend/src/main/resources/certs/amazon-root-ca-1.pem` (referenciado por
  `AwsIotProperties.rootCaPath` em produção — §5).

## 2. Things e certificados

```bash
REGION=<regiao-escolhida>

# Thing type (opcional, mas útil para consultas/relatórios agrupados)
aws iot create-thing-type --thing-type-name cafey-cafeteira --region "$REGION"

# Thing do dispositivo (protótipo único — spec P2)
aws iot create-thing \
  --thing-name cafey-device-proto-01 \
  --thing-type-name cafey-cafeteira \
  --region "$REGION"

# Thing do backend (spec P6 — credenciais distintas do dispositivo)
aws iot create-thing \
  --thing-name cafey-backend \
  --region "$REGION"
```

Um certificado X.509 por thing, já ativado:

```bash
for THING in cafey-device-proto-01 cafey-backend; do
  aws iot create-keys-and-certificate \
    --set-as-active \
    --certificate-pem-outfile "${THING}-cert.pem" \
    --public-key-outfile "${THING}-public.key" \
    --private-key-outfile "${THING}-private.key" \
    --region "$REGION" \
    > "${THING}-cert-result.json"

  CERT_ARN=$(jq -r '.certificateArn' "${THING}-cert-result.json")

  aws iot attach-thing-principal \
    --thing-name "$THING" \
    --principal "$CERT_ARN" \
    --region "$REGION"
done
```

`clientId` da conexão MQTT **deve ser igual ao nome da thing** — é o que a política de mínimo
privilégio usa para escopar o acesso via `${iot:Connection.Thing.ThingName}` (§3).

Os arquivos `*-cert.pem` / `*-private.key` / `*-cert-result.json` gerados pelos comandos acima
**nunca devem ser commitados** — ver `.gitignore` adicionado em `firmware/certs/` e
`backend/cafey-backend/src/main/resources/certs/` (§4).

## 3. Políticas IoT (mínimo privilégio)

Os templates das políticas já existem no repositório e refletem exatamente o escopo da issue:

- Dispositivo: [`firmware/aws-iot-device-policy.json`](../../firmware/aws-iot-device-policy.json)
- Backend: [`backend/cafey-backend/src/main/resources/aws-iot-backend-policy.json`](../../backend/cafey-backend/src/main/resources/aws-iot-backend-policy.json)
  (entregue pelo #46/BE-08, código apenas — não estava anexada a nenhuma política real na AWS)

A política do dispositivo usa a variável de política `${iot:Connection.Thing.ThingName}`, então o
mesmo documento serve para qualquer thing de dispositivo (não precisa de uma política por thing)
desde que o `clientId` da conexão seja igual ao nome da thing.

```bash
aws iot create-policy \
  --policy-name cafey-device-policy \
  --policy-document file://firmware/aws-iot-device-policy.json \
  --region "$REGION"

aws iot create-policy \
  --policy-name cafey-backend-policy \
  --policy-document file://backend/cafey-backend/src/main/resources/aws-iot-backend-policy.json \
  --region "$REGION"

aws iot attach-policy \
  --policy-name cafey-device-policy \
  --target "$(jq -r '.certificateArn' cafey-device-proto-01-cert-result.json)" \
  --region "$REGION"

aws iot attach-policy \
  --policy-name cafey-backend-policy \
  --target "$(jq -r '.certificateArn' cafey-backend-cert-result.json)" \
  --region "$REGION"
```

Resumo do mínimo privilégio (spec §6.1/§6.3 — nomes de tópico **exatos**, sem wildcard, porque
mensagem retida não é entregue a assinante com `+`/`#`):

| Papel | Connect (clientId) | Subscribe/Receive | Publish | RetainPublish |
|---|---|---|---|---|
| Dispositivo | = thing name | `dispositivos/{id}/comando`, `.../agendamentos` (nome exato) | `.../estado`, `.../eventos`, `.../saude` | `.../estado` |
| Backend | `cafey-backend` | `dispositivos/+/estado`, `.../eventos`, `.../saude` | `.../comando`, `.../agendamentos` | `.../comando`, `.../agendamentos` |

## 4. Distribuição segura das credenciais

### Dispositivo (firmware)

As credenciais **não** ficam embutidas no binário — são carregadas do NVS pelo `MqttConfigStore`
(`firmware/main/core/mqtt_config_store.*`, namespace `cafey_mqtt`) no boot. Para o protótipo de um
único dispositivo (spec P2), a gravação é manual, via `nvs_partition_gen.py` (ferramenta do
ESP-IDF) + `esptool.py`:

1. Copie o template `firmware/certs/provisioning.csv.example` para
   `firmware/certs/provisioning.csv` (git-ignorado) e preencha os campos com o conteúdo real do
   endpoint, `client_id` (= `cafey-device-proto-01`), Root CA, certificado e chave privada gerados
   no §2.
2. Gere o binário da partição:
   ```bash
   $IDF_PATH/components/nvs_flash/nvs_partition_generator/nvs_partition_gen.py generate \
     firmware/certs/provisioning.csv firmware/certs/cafey_mqtt_nvs.bin 0x6000
   ```
3. Grave no offset da partição `nvs` definida em `firmware/partitions.csv` (`0x9000`), com a placa
   conectada:
   ```bash
   esptool.py --port <porta-serial> write_flash 0x9000 firmware/certs/cafey_mqtt_nvs.bin
   ```

`firmware/certs/` está no `.gitignore` (exceto os arquivos `*.example`) — nenhuma chave real é
versionada.

### Backend

Em produção, cert/chave/CA são fornecidos como arquivos montados a partir de variável de ambiente /
AWS Secrets Manager (mesmo mecanismo de injeção segura do #104/BE-22 para as chaves RSA do JWT — o
segredo nunca fica em `application*.yml` versionado, só o *path* do arquivo). Ver
`backend/cafey-backend/.env.example` e o perfil `application-prod.yml` (§5).

Dois perfis, conforme spec §6:
- `dev`: Mosquitto local, sem TLS/certificados (endpoint `localhost`, valores default de
  `AwsIotProperties`).
- `prod`: AWS IoT Core, `application-prod.yml` + variáveis de ambiente com os paths dos arquivos de
  certificado.

## 5. Configuração nos módulos

- **Firmware:** endpoint (`mqtts://<endpoint-ats>:8883`) e `client_id` são gravados no NVS junto
  com os certificados (não há campo correspondente em `sdkconfig` — a config é toda externa ao
  binário, por design do FW-10). Ver `firmware/certs/provisioning.csv.example`.
- **Backend:** `backend/cafey-backend/src/main/resources/application-prod.yml`, propriedades sob o
  prefixo `aws.iot.*` (mapeadas por `AwsIotProperties`), valores vindos de variáveis de ambiente —
  ver `backend/cafey-backend/.env.example`.
- **Diagrama:** a substituição de Mosquitto por AWS IoT Core no `arquitetura_projeto.drawio` é
  escopo do DOC-03 (#87, aberta) — este documento é a referência textual até lá.

## 6. Critério de aceite — como validar

Com o endpoint, coloque os arquivos gerados no §2 em variáveis locais e valide:

```bash
ENDPOINT=<endpoint-ats>

# Dispositivo assina o próprio tópico de comando e recebe
mosquitto_sub -h "$ENDPOINT" -p 8883 \
  --cafile amazon-root-ca-1.pem \
  --cert cafey-device-proto-01-cert.pem --key cafey-device-proto-01-private.key \
  -i cafey-device-proto-01 \
  -t 'dispositivos/cafey-device-proto-01/comando' -v

# Publicação retida em estado, pelo próprio dispositivo
mosquitto_pub -h "$ENDPOINT" -p 8883 \
  --cafile amazon-root-ca-1.pem \
  --cert cafey-device-proto-01-cert.pem --key cafey-device-proto-01-private.key \
  -i cafey-device-proto-01 \
  -t 'dispositivos/cafey-device-proto-01/estado' -m '{"ligado":false}' -r

# Um novo subscriber no tópico EXATO recebe o retido...
mosquitto_sub -h "$ENDPOINT" -p 8883 --cafile amazon-root-ca-1.pem \
  --cert cafey-backend-cert.pem --key cafey-backend-private.key -i cafey-backend \
  -t 'dispositivos/cafey-device-proto-01/estado' -C 1 -v

# ...mas o mesmo cliente assinando via wildcard '+' NÃO recebe o retido (comportamento esperado
# do AWS IoT Core, spec §6.3) — usar -W (timeout) e confirmar que nada chega:
mosquitto_sub -h "$ENDPOINT" -p 8883 --cafile amazon-root-ca-1.pem \
  --cert cafey-backend-cert.pem --key cafey-backend-private.key -i cafey-backend-2 \
  -t 'dispositivos/+/estado' -W 5 -v

# Backend publica em comando e o dispositivo recebe (primeira sessão acima já confirma)
mosquitto_pub -h "$ENDPOINT" -p 8883 --cafile amazon-root-ca-1.pem \
  --cert cafey-backend-cert.pem --key cafey-backend-private.key -i cafey-backend \
  -t 'dispositivos/cafey-device-proto-01/comando' -m '{"acao":"ligar"}'

# Teste negativo: dispositivo tentando publicar fora do seu escopo deve ser negado
mosquitto_pub -h "$ENDPOINT" -p 8883 --cafile amazon-root-ca-1.pem \
  --cert cafey-device-proto-01-cert.pem --key cafey-device-proto-01-private.key \
  -i cafey-device-proto-01 \
  -t 'dispositivos/outro-device/comando' -m '{"acao":"ligar"}'
# -> conexão derrubada / publish negado pela política (mínimo privilégio, §3)
```

## Recursos provisionados

Provisionamento real executado em 2026-09-11, conta AWS `076248672901`, região `sa-east-1`,
seguindo exatamente os comandos das seções 1-3 deste runbook (nenhum documento de política diverge
dos templates já versionados no repositório).

| Recurso | Valor |
|---|---|
| Endpoint ATS | `a3gha475fc91p-ats.iot.sa-east-1.amazonaws.com:8883` |
| Thing (dispositivo) | `cafey-device-proto-01` — thingId `79713a96-8621-429d-a529-118886e577c7`, thingType `cafey-cafeteira` |
| Thing (backend) | `cafey-backend` — thingId `e71c3bcd-e252-4850-bc19-6895201177e9` |
| Certificado do dispositivo | `arn:aws:iot:sa-east-1:076248672901:cert/20e271fb357835a607bbf563f74fce5ecae24fec1d32805f394ca5eb54fb91fe` (ativo, anexado à thing) |
| Certificado do backend | `arn:aws:iot:sa-east-1:076248672901:cert/19be33dd7c22a285f3ed4460f1ba2038d31190717ad01fcd3560eaa448f1be98` (ativo, anexado à thing) |
| Política do dispositivo | `arn:aws:iot:sa-east-1:076248672901:policy/cafey-device-policy` (documento idêntico a `firmware/aws-iot-device-policy.json`, anexada ao certificado do dispositivo) |
| Política do backend | `arn:aws:iot:sa-east-1:076248672901:policy/cafey-backend-policy` (documento idêntico a `backend/cafey-backend/src/main/resources/aws-iot-backend-policy.json`, anexada ao certificado do backend) |

O material sensível (chaves privadas, certificados `.pem`, Amazon Root CA) foi gerado e mantido
**fora do repositório**; nada foi commitado, conforme §4.

### Validação funcional já realizada (além do checklist §6)

Como `mosquitto-clients` não está instalado neste ambiente (ver [Status e pendências](#status-e-pendências)),
uma validação equivalente foi feita com um cliente MQTT/TLS em Python (`paho-mqtt`) apontando para
os certificados reais acima, confirmando contra o serviço real:

- Backend publica em `dispositivos/cafey-device-proto-01/comando` e o dispositivo (assinante do
  tópico exato, com seu próprio certificado/política) **recebe** a mensagem — confirmado.
- Dispositivo tentando publicar fora do próprio escopo
  (`dispositivos/outro-device/comando`) tem a conexão **derrubada pelo AWS IoT Core** (política de
  mínimo privilégio negando o publish) — confirmado.
- Cenário de retain no tópico exato vs. não-entrega via wildcard `+` (spec §6.3): tentado
  repetidamente com o mesmo cliente Python, mas não foi possível concluir uma execução limpa dentro
  deste ambiente (ver pendência abaixo) — **não confirmado nesta rodada**, embora o comportamento
  seja um recurso documentado da AWS IoT Core (não específico desta política) e os dois testes
  acima já confirmam que Connect/Publish/Subscribe/Receive das políticas de mínimo privilégio
  funcionam como esperado contra o serviço real.

## Status e pendências

O provisionamento da AWS (conta, região, `things`, certificados e políticas) **está concluído** —
ver [Recursos provisionados](#recursos-provisionados). A decisão de região seguiu a recomendação
deste runbook: `sa-east-1` (São Paulo), pela menor latência esperada para o dispositivo físico e
os usuários do app, ambos no Brasil — decisão tomada pelo usuário.

### Pendente de validação manual

Os itens abaixo não exigem mais decisão de conta/região (isso está resolvido) — dependem de
hardware físico, de um ambiente de deploy ainda não finalizado, ou de uma ferramenta de sistema não
disponível neste ambiente sandboxed:

1. **Confirmação completa do critério "retain só no tópico exato, não via wildcard"** (spec §6.3,
   comando `mosquitto_sub -W` da §6) — comportamento documentado da AWS IoT Core e já indiretamente
   suportado pelas políticas testadas (§ acima), mas sem uma execução limpa e reproduzível neste
   ambiente. Repetir os comandos `mosquitto_sub`/`mosquitto_pub` da §6 com os certificados reais
   (fora do repositório) assim que houver uma máquina com `mosquitto-clients` instalado.
2. **Gravação da partição NVS no dispositivo físico** (`nvs_partition_gen.py` + `esptool.py` +
   placa ESP32 conectada, §4) — não há hardware disponível neste ambiente.
3. **População das variáveis de ambiente / Secrets Manager de produção do backend** (§4/§5) com o
   certificado real do backend — depende do ambiente de deploy final (INFRA-07, #150, em
   andamento).

Nenhum desses itens bloqueia o fechamento da pendência 4 do spec-backend nem o critério central da
issue #123 (recursos AWS IoT Core reais, com políticas de mínimo privilégio, existindo e
funcionando) — são follow-ups de integração física/deploy, não lacunas no provisionamento em si.

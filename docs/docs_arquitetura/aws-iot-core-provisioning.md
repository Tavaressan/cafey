# Provisionamento do AWS IoT Core — runbook (INFRA-05)

> **Origem:** issue #123 (INFRA-05), spec-backend v1.1 §6/§11 (pendência 4). Contraparte de
> infraestrutura do FW-10 (#122, mergeado) e do BE-08/BE-11 (#46/#49, mergeados — que entregaram
> apenas o código: dependência do SDK, `AwsIotProperties`/`AwsIotConfig` e o template
> `aws-iot-backend-policy.json`, **sem** provisionar recursos reais na AWS).
>
> Este documento descreve o procedimento para criar de fato os recursos no AWS IoT Core (conta real,
> `things`, certificados X.509 e políticas) e distribuir as credenciais para o firmware e o backend.
> Os passos que exigem uma conta AWS ativa **não foram executados** por este agente — ver
> [Status e bloqueio](#status-e-bloqueio) no final.

## Sumário

1. [Conta, região e endpoint](#1-conta-região-e-endpoint)
2. [Things e certificados](#2-things-e-certificados)
3. [Políticas IoT (mínimo privilégio)](#3-políticas-iot-mínimo-privilégio)
4. [Distribuição segura das credenciais](#4-distribuição-segura-das-credenciais)
5. [Configuração nos módulos](#5-configuração-nos-módulos)
6. [Critério de aceite — como validar](#6-critério-de-aceite--como-validar)
7. [Status e bloqueio](#status-e-bloqueio)

---

## 1. Conta, região e endpoint

**Decisão pendente (humana):** qual conta AWS e qual região usar. Ver
[Status e bloqueio](#status-e-bloqueio) para as opções e a recomendação.

Depois de decidida a região, obtenha o endpoint ATS (`iot:Data-ATS`, o tipo de endpoint recomendado
pela AWS desde 2019 — o `iot:Data` legado usa uma cadeia de CA diferente):

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

## Status e bloqueio

Os itens abaixo **exigem** uma conta AWS real e uma decisão de custo/latência que este agente não
pode tomar de forma autônoma. Tudo que era determinístico e local (templates de política, runbook
de comandos, estrutura de configuração dos módulos, `.gitignore`) foi entregue nesta branch — ver
commits. O que falta:

- Escolher a conta AWS e a região (`us-east-1` vs. `sa-east-1`, avaliando latência até o dispositivo
  no Brasil vs. custo/free tier da região).
- Executar de fato os comandos das seções 2 e 3 nessa conta (criar as `things`, gerar os
  certificados reais e anexar as políticas).
- Gerar o binário de NVS do dispositivo real (§4) e gravá-lo na placa física.
- Provisionar as variáveis de ambiente / Secrets Manager do backend em produção com o certificado
  real (§4/§5).

**Recomendação técnica:** `sa-east-1` (São Paulo) — o dispositivo físico e os desenvolvedores estão
no Brasil, então a latência de conexão MQTT/TLS e o RTT dos comandos ligar/desligar (crítico para a
UX do app, marco 25/09) tendem a ser menores do que em `us-east-1`. Verificar antes de decidir se
`sa-east-1` tem paridade de preço e de todos os recursos do AWS IoT Core usados aqui (não há
diferença de feature set relevante para o escopo desta issue, mas a confirmação de preço/free-tier
deve ser feita na calculadora oficial da AWS no momento da decisão, já que preços mudam com o
tempo).

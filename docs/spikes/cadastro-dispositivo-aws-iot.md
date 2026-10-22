# SPIKE — Cadastro da cafeteira, do app até o AWS IoT Core, pensando em produção

**Origem:** teste de bancada em 22/10/2026. O provisionamento de Wi-Fi por BLE (UC-04, #249)
chegou até o fim. O botão "Gravar" criou o dispositivo no backend, mas a cafeteira **não liga
pelo app**.
**Tipo:** avaliação, sem código de produto. Saem daqui uma recomendação em duas fases e as issues
sugeridas.
**Fontes externas:** consultadas em 22/10/2026 (ver [Fontes](#fontes)). Neste documento,
**[fato]** quer dizer verificado no código ou em fonte oficial, **[inferência]** é dedução a
partir de fatos e **[opinião]** é recomendação do autor.

## Resumo

- **[fato]** A falha não está na AWS. O "Gravar" cria um dispositivo com um **UUID novo**, e o
  backend publica o comando em `dispositivos/<UUID novo>/comando`. A placa só assina os tópicos
  da própria thing, `c40a1340-…`. Os dois nunca se encontram.
- O cadastro em produção envolve **dois problemas diferentes**:
  1. **Identidade na AWS:** como cada placa ganha thing, certificado e política.
  2. **Vínculo de posse:** como o backend associa a placa X ao usuário Y com segurança.
     Foi este que falhou.
- **[opinião]** Recomendação:
  - **Fase 1 (já):** o app envia o serial lido por BLE, e o backend vincula esse ID.
  - **Fase 2 (produção):**
    - script de fábrica com CSR (pedido de assinatura de certificado) gerado no próprio ESP32;
    - inventário de placas no backend;
    - vínculo por janela aberta com o botão físico e código de uso único;
    - canal BLE criptografado;
    - flash encryption e Secure Boot.

## O que falhou em 22/10/2026 (fatos verificados no código)

| Elo | Onde | O que faz |
|---|---|---|
| Placa | `firmware/main/main.cpp` (`ble_serial = mqtt_config.client_id`) | Expõe por BLE o serial = `client_id` MQTT = nome da thing (`c40a1340-e5cf-4290-8fd4-f6e838d80fb5`) |
| App | `DeviceFormViewModel.save()` → `CriarDispositivoRequest(nome, timezone, duracaoPreparoS, limiarDescalcificacao)` | Lê o serial (`deviceInfo.serial`), mas **não o envia** |
| Backend | `Dispositivo.id` com `@GeneratedValue(strategy = GenerationType.UUID)`; `V3__dispositivos.sql` com `DEFAULT gen_random_uuid()` | Gera um ID novo a cada cadastro |
| Backend | `DispositivoService.comandar` → `AwsIotProperties.topicComando(dispositivoId)` | Publica no tópico do ID do banco |
| AWS | `firmware/aws-iot-device-policy.json` (`${iot:Connection.Thing.ThingName}`) | A placa só assina/recebe `dispositivos/<sua thing>/comando` |

**[fato]** A spec `docs/specs/provisionamento-wifi-ble-esp32.md` já previa o elo que falta:
Goal 4 e RF-05 ("avança preenchendo o serial obtido na chamada de cadastro POST /dispositivos"),
mais o edge case 4 (409 se o serial já tiver dono). Isso não foi implementado.

## Restrições verificadas no código (valem para todas as opções)

1. **O nome da thing tem que ser UUID.** `MqttClientService` faz `UUID.fromString` no segmento do
   tópico (`estado`, `saude`, `eventos`). Uma thing nomeada por MAC ou por serial alfanumérico
   quebra a ingestão. Sem UUID, é preciso uma coluna `thing_name` e mudar o parser.
2. **Tópico novo exige versão nova de `cafey-device-policy`.** A política estava em 5/5 versões em
   14/10/2026. O perfil CLI `cafey-iot` não lista nem apaga versões (AccessDenied em
   `iot:ListPolicyVersions`), então a limpeza é manual no console da AWS.
3. **O firmware usa o `esp-mqtt` do ESP-IDF** (`REQUIRES mqtt`, `core/esp_mqtt_client.cpp`), não o
   coreMQTT.
   - O exemplo oficial `fleet_provisioning_with_csr` do esp-aws-iot depende de coreMQTT e
     corePKCS11, e "is incompatible with `release/v6.0`" do ESP-IDF.
   - A API MQTT de fleet provisioning, porém, é JSON sobre tópicos `$aws/...`, então dá para
     implementá-la no cliente atual.
4. **O backend não tem credenciais IAM** (identidade da AWS para chamar APIs). Ele só tem o
   `aws-iot-device-sdk` e o certificado MQTT (`compose.prod.yaml`, `/opt/cafey/secrets`).
   - Qualquer chamada de *control plane* exige credenciais novas na instância Lightsail. Exemplos:
     `CreateProvisioningClaim`, `DescribeCertificate`, `CreateThing`.
   - **Não verificado neste spike:** como entregar essas credenciais.
5. **O canal BLE não tem criptografia.** As características GATT não usam flags `_ENC`
   (`esp_nimble_server.cpp`), o anúncio é contínuo e a **senha do Wi-Fi trafega em claro**.
   Qualquer prova de posse que passe só por esse canal pode ser lida por quem estiver ao alcance
   do rádio.
6. **Hardware.**
   - O chip é ESP32-D0WD-V3, rev. v3.1 (log do `esptool`, 22/10/2026). O Secure Boot v2 está
     "available for ESP32 from chip revision v3.0 onwards".
   - **[inferência]** O ESP32 clássico não tem o periférico Digital Signature (DS): a página da
     API de DS existe para `esp32s3` e retorna 404 para `esp32` na documentação v5.5.2. A chave
     privada fica na NVS, protegida no máximo por flash encryption + NVS encryption.
7. **A identidade hoje é manual e para uma placa só:** `create-keys-and-certificate`, `.csv`,
   `nvs_partition_gen.py` e `esptool` (runbook `docs/docs_arquitetura/aws-iot-core-provisioning.md`
   §4). Nesse fluxo, a chave privada é gerada pela AWS e passa pelo PC.

## Problema 1 — Identidade na AWS: opções

A AWS organiza a escolha assim:
- se dá para instalar certificados únicos antes da entrega, use provisionamento de "devices that
  have device certificates": single thing, JITP/JITR (*just-in-time provisioning/registration*,
  registro automático na primeira conexão) ou bulk;
- se um app do usuário instala o certificado, use "trusted user";
- se nenhum dos dois, use "provisioning by claim".

| Opção | Como funciona | Chave privada sai do chip? | Como o inventário do backend é populado | Mudanças | Riscos e custos |
|---|---|---|---|---|---|
| **A. Script de fábrica, chave gerada pela AWS** (status quo automatizado) | `create-keys-and-certificate` + `create-thing` (UUID) + `attach-*` + imagem NVS por placa | **Sim:** gerada na AWS, passa pelo PC da fábrica | O próprio script insere o UUID como "fabricado, não vinculado" | Só script (CLI) + endpoint/admin de inventário | Chave exposta no PC de fábrica; exige script por unidade |
| **A′. Script de fábrica, CSR gerado no ESP32** | O firmware gera o par de chaves ECC P-256 (curva elíptica) e devolve o CSR pela serial. O script chama `CreateCertificateFromCsr`, cria a thing (UUID), anexa política e certificado e grava só o certificado + `client_id` na NVS | **Não** | O próprio script | Comando de console no firmware (mbedTLS) + script + inventário | Ainda exige uma etapa de fábrica por unidade, só que sem segredo fora do chip |
| **B. Fleet provisioning by claim (CSR)** | Certificado de *claim* compartilhado gravado na placa. No primeiro boot com Wi-Fi: `$aws/certificates/create-from-csr/json` → `$aws/provisioning-templates/<t>/provision/json` → reconecta com o certificado definitivo | **Não** (CSR) | **[inferência]** Um pre-provisioning hook (Lambda) valida o serial contra o inventário e pode devolver o UUID em `parameterOverrides` | Template, provisioning role, Lambda, implementação da API no `esp-mqtt` | Se a chave de claim vazar, quem a tiver provisiona placas falsas. A AWS manda monitorar e desativar o claim. Não há preço de provisioning na página de preços da AWS ("não informado"); a Lambda tem cobrança própria |
| **C. Fleet provisioning by trusted user** | O backend (usuário logado) chama `CreateProvisioningClaim` → certificado + chave temporários (**5 min**). O app manda isso por BLE junto com o Wi-Fi; a placa troca pelo certificado definitivo | O claim temporário **trafega por BLE** | Hook ou backend no momento do claim | IAM no backend, template, BLE criptografado obrigatório, implementação da API na placa | **Ganho:** elimina a etapa de fábrica, e é o fluxo "do app para o AWS IoT" que a AWS documenta. **Custos:** viola a ADR-P2 da spec (chave por BLE), o BLE hoje é em claro, 5 min para Wi-Fi + CSR + RegisterThing e credencial IAM nova em produção |
| **D. JITP/JITR com CA própria** | Certificados assinados por uma CA nossa registrada na AWS; a thing é criada na primeira conexão | Depende da fábrica | Hook/regra | Operar uma CA (guardar a chave da CA) | Custo operacional alto para o porte do projeto |

## Problema 2 — Vínculo de posse: opções

| Opção | Prova de quê | Ataque que sobra | Mudanças |
|---|---|---|---|
| **V1. Só o serial** (o que a spec pede) | Nenhuma: só que o app conhece o UUID | Quem souber o UUID (etiqueta, log, BLE em claro) vincula remotamente antes do dono | App envia `serial`; backend vincula/cria, com 409 se já tiver dono |
| **V2. Serial + código de etiqueta** | Posse da etiqueta | Etiqueta fotografada; se o código também estiver no BLE, a leitura é passiva | Coluna com hash do código no inventário; campo no app |
| **V3. Janela aberta pelo botão físico + código de uso único** | Posse física (botão GPIO 27) + proximidade BLE + placa genuína (MQTT autenticado pelo certificado dela) | Atacante ao alcance do BLE exatamente na janela, disputando com o dono. Mitigado por janela curta e código de uso único | Firmware: toque longo abre a janela (hoje o botão alterna o relé), gera o código, publica em `dispositivos/<thing>/vinculo` e expõe por BLE. Backend assina `dispositivos/+/vinculo`. **Política nova** (restrição 2) |
| **V4. Desafio assinado pela chave do dispositivo** | Posse da chave privada | Nenhum relevante | Assinatura mbedTLS no firmware; o backend precisa do certificado (inventário ou `DescribeCertificate`, que exige IAM) |

## Recomendação **[opinião]**

**Fase 1 — fazer a cafeteira ligar pelo app agora (uma placa só, sem mudar nada na AWS)**

1. O app envia `deviceInfo.serial` no `POST /dispositivos`; o campo é opcional, para não quebrar o
   cadastro manual.
2. O backend valida que o serial é UUID (400 se não for) e **só vincula, nunca cria**:
   - se o dispositivo não existe, devolve **404** ("Cafeteira não reconhecida pelo servidor").
     Desde o #169 o UUID da thing é gerado pelo próprio backend antes de a placa ser gravada,
     então uma placa legítima já tem registro. Criar com o ID enviado pelo app deixaria qualquer
     conta inventar IDs;
   - se existe e não tem proprietário, vincula como proprietário;
   - se o dono é **o próprio usuário**, a operação é idempotente: devolve o registro como está.
     Timezone e duração só mudam pelo `PUT`, que sobe a versão de agendamentos. Provavelmente é
     o caso do `c40a1340-…`, validado ponta a ponta em 21/10 (runbook §Status);
   - se o dono é **outro usuário**, devolve **409**, e o app mostra "pertence a outra conta"
     (edge case 4 da spec).
3. Limpar os registros duplicados criados pelo "Gravar" de 22/10, **sem apagar o
   `c40a1340-…`**.

**Armadilha da versão de agendamentos.**
- **[fato]** O padrão de `Dispositivo.versaoAgendamentos` é 1. O firmware descarta versões menores
  ou iguais à gravada, e o log de 22/10 mostra `agendamentos versao 3 ignorados (gravada: 3)`.
- **[inferência]** Se o registro da placa for apagado e recriado com o mesmo ID, o backend volta
  a 1. A placa passa a ignorar agendamentos, fuso e duração sem erro visível.
- A solução fica para a issue 1, mas o critério de aceite precisa cobrir esse caso.

Risco aceito conscientemente: V1 não prova posse. Só é aceitável enquanto existir um protótipo.

**Fase 2 — produção**

1. **Identidade: A′ (script de fábrica com CSR no ESP32).**
   - A chave nunca sai do chip, sem certificado de claim compartilhado, sem Lambda.
   - O script popula o inventário com o UUID.
   - Migrar para **B** só se a fabricação sair de bancada (fábrica terceirizada, lotes grandes). A
     API MQTT de B pode ser implementada sobre o `esp-mqtt` atual.
2. **Vínculo: V3**, com inventário (`fabricado → vinculado`). A exclusão do dispositivo pelo
   proprietário devolve a placa ao estado "não vinculado".
3. **Canal BLE com criptografia** antes de abrir para usuários reais. Duas saídas:
   - migrar o provisionamento para o componente `wifi_provisioning` do ESP-IDF com
     **Security 2**:
     - SRP6a (autenticação por senha sem enviá-la) + AES-GCM (criptografia autenticada);
     - apps/libs Android e iOS da Espressif;
     - endpoints customizados para serial e código de vínculo;
   - ou exigir pareamento LESC (*LE Secure Connections*, pareamento BLE com troca de chaves por
     curva elíptica) nas características atuais.
4. **Proteção da chave na placa:**
   - flash encryption em modo *release*;
   - NVS encryption, que exige uma partição `nvs_keys` nova em `partitions.csv`;
   - Secure Boot v2 (RSA-3072 no ESP32).
   - **Queimam eFuses (bits de configuração gravados uma única vez no chip) e são
     irreversíveis:** validar numa placa reserva, nunca no protótipo único.
5. **Não recomendado para este projeto:**
   - **C**, porque viola a ADR-P2, exige BLE seguro e IAM em produção e tem janela de 5 min;
   - **D**, pelo custo de operar uma CA.
   - **C** volta a ser candidato se a etapa de fábrica se tornar inviável.

Certificados emitidos pela AWS expiram em 31/12/2049. Mesmo assim, a AWS recomenda prever
rotação (fora do escopo deste spike).

## Issues de implementação sugeridas

(Não criadas no GitHub. Listadas para o usuário ou o coordinator abrirem.)

1. **BE+APP — Cadastro BLE vincula o serial da placa (Fase 1). Implementada em 22/10/2026,
   sem commit.**
   - `CriarDispositivoRequest.serial` opcional no app e no backend.
   - Regras: 404 para serial desconhecido, vínculo se não houver dono, idempotente para o mesmo
     dono, 409 para outro dono e 400 se não for UUID.
   - Testes de serviço, HTTP (MockMvc) e ViewModel.
   - O `@GeneratedValue` de `Dispositivo.id` foi mantido, porque o vínculo nunca cria registro.
   - Aceite:
     - depois do cadastro BLE, `POST /dispositivos/{serial}/comando` liga o relé;
     - recadastrar a mesma placa pela mesma conta não gera 409;
     - depois de excluir e recadastrar, a mudança de duração chega à placa (armadilha da versão
       de agendamentos).
2. **OPS — Remover duplicatas criadas em 22/10/2026 e confirmar o vínculo de `c40a1340-…` à
   conta de teste (Fase 1, manual). Feita em 22/10/2026.**
   - Causa real do "não liga": o app estava logado como `admin@cafey.com`, mas o `c40a1340-…`
     ("Cafey bancada", versão 3) pertencia a `vitortctavares@gmail.com`.
   - A tela inicial mostra só o primeiro dispositivo da lista (`HomeViewModel`:
     `listar().firstOrNull()`), e esse primeiro era uma duplicata.
   - Correção aprovada pelo usuário: o vínculo `PROPRIETARIO` do `c40a1340-…` foi transferido
     para `admin@cafey.com` e os outros 7 dispositivos dessa conta foram apagados. Tudo numa
     transação, no banco de produção.
   - Validado: "Ligar" no app aciona o relé (log serial `Relay on GPIO 26 -> ON`).
   - O `c40a1340-…` não foi removido nem recriado, então `versao_agendamentos` continua em 3.
3. **FW+INFRA — Script de fábrica com CSR gerado no ESP32 (Fase 2, opção A′).**
   - Comando de console que gera ECC P-256 e imprime o CSR.
   - Script CLI: `create-thing` (UUID) + `create-certificate-from-csr` + `attach-*` + imagem NVS
     sem chave + registro no inventário.
4. **BE+FW+INFRA — Janela de vínculo por botão e código de uso único (Fase 2, V3).**
   - Tópico `dispositivos/<thing>/vinculo` e versão nova das duas políticas: liberar versões
     antigas pelo console antes.
   - Inventário com estado `fabricado/vinculado` e endpoint de vínculo com TTL (prazo de
     validade do código).
5. **FW+APP — Canal BLE criptografado (Fase 2).**
   - `wifi_provisioning` com Security 2 ou pareamento LESC.
   - A senha do Wi-Fi deixa de trafegar em claro.
6. **FW — Flash encryption, NVS encryption e Secure Boot v2 em placa reserva (Fase 2).**
   - Inclui a partição `nvs_keys` e o procedimento de gravação em modo *release*.
7. **(Opcional) FW+INFRA — Fleet provisioning by claim com CSR sobre `esp-mqtt` + pre-provisioning
   hook validando o inventário**, se a fabricação escalar.

## Fontes

Todas consultadas em 22/10/2026.

- AWS IoT Core — Device provisioning (escolha entre JITP/JITR, trusted user e claim):
  <https://docs.aws.amazon.com/iot/latest/developerguide/iot-provision.html>
- AWS IoT Core — Fleet provisioning (by claim, by trusted user, claim de 5 min, token de 1 h,
  pre-provisioning hooks): <https://docs.aws.amazon.com/iot/latest/developerguide/provision-wo-cert.html>
- AWS IoT Core — Device provisioning MQTT API (tópicos `$aws/certificates/create-from-csr/...` e
  `$aws/provisioning-templates/.../provision/...`):
  <https://docs.aws.amazon.com/iot/latest/developerguide/fleet-provision-api.html>
- AWS IoT API Reference — CreateProvisioningClaim:
  <https://docs.aws.amazon.com/iot/latest/apireference/API_CreateProvisioningClaim.html>
- AWS IoT Core — X.509 client certificates (CSR com RSA ≥ 2048 ou ECC P-256/384/521; expiração em
  2049-12-31): <https://docs.aws.amazon.com/iot/latest/developerguide/x509-client-certs.html>
- AWS IoT Core — Pricing (não menciona cobrança por provisioning; registry medido por operação):
  <https://aws.amazon.com/iot-core/pricing/>
- Espressif — esp-aws-iot (versões do IDF suportadas; `fleet_provisioning_with_csr` incompatível
  com `release/v6.0`): <https://github.com/espressif/esp-aws-iot>
- ESP-IDF v5.5.2 — Wi-Fi Provisioning (Security 0/1/2, apps Android/iOS, endpoints customizados):
  <https://docs.espressif.com/projects/esp-idf/en/v5.5.2/esp32/api-reference/provisioning/wifi_provisioning.html>
- ESP-IDF v5.5.2 — Secure Boot v2 no ESP32 (rev. ≥ v3.0, RSA-3072):
  <https://docs.espressif.com/projects/esp-idf/en/v5.5.2/esp32/security/secure-boot-v2.html>
- ESP-IDF v5.5.2 — NVS Encryption no ESP32 (depende de flash encryption e partição `nvs_keys`):
  <https://docs.espressif.com/projects/esp-idf/en/v5.5.2/esp32/api-reference/storage/nvs_encryption.html>
- ESP-IDF v5.5.2 — Security overview (flash encryption em modo *release* para produção):
  <https://docs.espressif.com/projects/esp-idf/en/v5.5.2/esp32/security/security.html>
- ESP-IDF v5.5.2 — página da API de Digital Signature: existe para
  [esp32s3](https://docs.espressif.com/projects/esp-idf/en/v5.5.2/esp32s3/api-reference/peripherals/ds.html),
  retorna 404 para `esp32` (base da inferência da restrição 6).

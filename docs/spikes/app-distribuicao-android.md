# SPIKE — Distribuição do app Android para uso real no celular (#151)

**Depende de:** APP-12 (#147, implementada nesta mesma branch antes deste documento).
**Tipo:** avaliação — sem implementação de código. Resultado: recomendação com custo e esforço.

## Contexto e restrições verificadas no código

Verificado em `apps/androidApp/build.gradle.kts` (estado antes da issue #147) e no restante do
módulo `apps/androidApp`:

- Não havia bloco `signingConfigs` nem `buildTypes`. O build de `debug` usa a keystore de debug
  automática do Android Studio/AGP; um build de `release` sairia sem assinatura e o Android recusa
  instalar um APK/AAB de release sem assinatura.
- `versionCode = 1` e `versionName = "0.1.0"` são valores fixos em `defaultConfig`. Qualquer canal
  de distribuição com atualização (o usuário reinstala uma versão nova por cima da antiga) exige
  `versionCode` estritamente crescente a cada build publicado — hoje nada gera esse incremento.
- `applicationId = "br.com.tavaressan.cafey.android"`.
- A issue #147 (App-12, resolvida nesta mesma branch) já remove a dependência de estar preso ao
  alias de emulador `10.0.2.2`: a URL do backend agora vem de `BuildConfig.BACKEND_BASE_URL`,
  configurável por `-PbackendBaseUrl`, variável de ambiente ou `local.properties`, sem precisar
  editar código-fonte.
- INFRA-07 (backend remoto com HTTPS) é issue separada, ainda não implementada. Sem ela, o app
  instalado num celular só alcança o backend se: (a) o celular estiver na mesma rede Wi-Fi do
  computador rodando o backend, apontando `backendBaseUrl` para o IP local da máquina (ex.:
  `192.168.x.x`), ou (b) via `adb reverse tcp:8080 tcp:8080` com o aparelho conectado por cabo USB
  e depuração habilitada.

## Comparação das opções

| Opção | Custo | Esforço de configuração | Atualização | Observações |
|---|---|---|---|---|
| **APK de debug via `adb install`/transferência** | Zero | Nenhum além do já existente (`./gradlew :androidApp:installDebug` ou copiar o `.apk` gerado em `apps/androidApp/build/outputs/apk/debug/`) | Manual: reinstalar a cada mudança | Usa a keystore de debug automática (não é uma keystore "real", nunca deve assinar builds distribuídos a terceiros). Cleartext liberado só para os hosts de desenvolvimento (10.0.2.2, localhost, 127.0.0.1 e o host configurado — issue #147). Adequado só para teste rápido do próprio desenvolvedor. |
| **APK de release assinado, distribuído por arquivo** (ex.: link, e-mail, USB) | Zero (fora o tempo) | Requer criar `signingConfigs`/`buildTypes` em `androidApp/build.gradle.kts`, gerar uma keystore de release (`keytool -genkeypair`) e guardá-la com segurança — nenhuma dessas peças existe hoje | Manual: cada nova versão precisa ser gerada, assinada e reenviada a quem for instalar | Sem canal de notificação de atualização; quem instala precisa saber que existe uma versão nova. Risco de perda da keystore (impede atualizar o mesmo `applicationId` no futuro sem desinstalar). |
| **Firebase App Distribution** | Serviço gratuito (parte do plano gratuito "Spark" do Firebase — **confirmar o status vigente na [documentação oficial](https://firebase.google.com/docs/app-distribution) antes de decidir**, preços/planos do Google mudam) | Cadastro de projeto Firebase, plugin Gradle (`firebase-appdistribution-gradle`) ou CLI, lista de testadores por e-mail | Testador recebe notificação por app dedicado (Firebase App Tester) ou e-mail com link; instala a nova versão sem passar por loja | Ainda exige um build assinado (debug ou release) e, em builds de release, keystore própria. Não passa por revisão de conteúdo da Play Store. Bom meio-termo entre "arquivo manual" e "loja". |
| **Play Console — teste interno** | Taxa única de cadastro de desenvolvedor (**valor histórico de referência: USD 25, cobrado uma única vez por conta — confirmar o valor vigente na [página oficial de cadastro](https://support.google.com/googleplay/android-developer/answer/6112435) antes de pagar**, pois políticas e valores da Play Store são alterados pela Google sem aviso prévio ao autor deste documento) | Cadastro de conta de desenvolvedor (inclui verificação de identidade, que pode levar dias), criação do app no Console, upload do primeiro AAB assinado, configuração da faixa de teste interno (até 100 testadores por e-mail, sem revisão de conteúdo demorada) | Automática: testador com o app instalado da faixa interna recebe a atualização pela própria Play Store, como qualquer app publicado | Único caminho com atualização "invisível" (sem o testador precisar reinstalar manualmente). Exige `versionCode` crescente a cada envio (a Play Store rejeita reenvio com o mesmo código). Prazo de revisão da faixa de teste interno costuma ser rápido (minutos a poucas horas), mas a verificação de identidade da conta nova pode ser o item de maior prazo. |

## Recomendação

**Play Console — teste interno**, com **Firebase App Distribution como alternativa imediata**
enquanto a conta de desenvolvedor Google está em verificação (ou como canal complementar de longo
prazo, caso o custo/processo do Play Console não se justifique para o escopo do projeto).

Razão da escolha:

1. É a única opção das quatro que resolve o problema real enunciado pela issue ("manter atualizada
   sem depender de reconstruir e reinstalar a mão a cada mudança") de forma automática — as demais
   ainda dependem de uma ação manual do usuário a cada versão nova.
2. O custo (taxa única, não recorrente) é baixo frente ao benefício, e a taxa já cobre publicação
   de qualquer número de apps futuros na mesma conta — não é um custo por-app.
3. O prazo de verificação de identidade da conta é a única variável fora do controle do projeto;
   por isso a recomendação prática é abrir a conta o quanto antes (mesmo antes de terminar
   APP-13/INFRA-07) e usar Firebase App Distribution nesse intervalo, para não bloquear os testes
   em aparelho físico.
4. Teste interno não exige revisão de conteúdo como a faixa de produção, então não introduz atraso
   de política de loja incompatível com o cronograma do projeto (marco da banca em 16/10, conforme
   já registrado no backlog junto a INFRA-07).

Ambas as opções (Play Console e Firebase App Distribution) exigem um build de **release assinado**
— a keystore de debug automática do Android Studio não serve para nenhuma delas.

## Onde ficaria a keystore de release e como seria protegida

Hoje não existe keystore de release nem `signingConfigs` no projeto — isso é trabalho novo, listado
como issue de implementação sugerida abaixo. Proposta:

- Gerar a keystore com `keytool -genkeypair -v -keystore cafey-release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias cafey`.
- **Nunca** commitar a keystore nem as senhas no repositório (nem em `apps/`, nem em nenhum outro
  diretório do monorepo). `apps/.gitignore` já ignora `local.properties`; a mesma lógica se aplica
  aqui — arquivo local, fora do controle de versão.
- Guardar o arquivo `.keystore`/`.jks` e as duas senhas (da keystore e da chave) num cofre de
  segredos fora do Git — ex.: GitHub Actions Secrets (se a assinatura for automatizada em CI) e/ou
  um gerenciador de senhas (1Password, Bitwarden) para o backup pessoal do responsável pela conta
  de desenvolvedor. Perder a keystore de release impede publicar atualizações do mesmo
  `applicationId` para sempre (a Play Store recusa um pacote assinado com chave diferente da
  original) — o backup é o item crítico aqui, não só o sigilo.
- `androidApp/build.gradle.kts` deve ler caminho/senhas de variáveis de ambiente ou de
  `local.properties` (mesmo padrão já usado para `sdk.dir` e, após #147, para `backendBaseUrl`),
  nunca como literal no `build.gradle.kts`.

## Como o `versionCode` passaria a ser incrementado

`versionCode` e `versionName` hoje são literais fixos em `defaultConfig`
(`apps/androidApp/build.gradle.kts`). Proposta mínima (a decidir/implementar na issue de
implementação sugerida):

- `versionCode` passa a ser derivado do número de commits ou de uma contagem monotônica simples
  (ex.: `git rev-list --count HEAD` no momento do build de release), garantindo que cresça a cada
  build publicado sem exigir edição manual do arquivo a cada envio.
- `versionName` continua semântico e editado manualmente (`MAJOR.MINOR.PATCH`), pois comunica
  significado ao usuário — não precisa ser automático.
- Builds de debug/desenvolvimento local continuam com o `versionCode` fixo atual (ou qualquer
  valor: a Play Store e o Firebase App Distribution só validam o `versionCode` dos artefatos que
  de fato são enviados a eles, não dos builds de debug locais).

## Issues de implementação sugeridas

(Não criadas no GitHub — fora do escopo deste agente. Listadas aqui para o coordinator ou o
usuário abrirem manualmente.)

1. **APP-14 — Assinatura de release: `signingConfigs` e `versionCode` incremental**
   Adicionar `signingConfigs`/`buildTypes` (`release`) em `apps/androidApp/build.gradle.kts`,
   lendo caminho da keystore e senhas de variáveis de ambiente/`local.properties` (nunca literal).
   Trocar o `versionCode` fixo por um valor derivado (ex.: contagem de commits) calculado no script
   Gradle. Critério de aceite: `./gradlew :androidApp:assembleRelease` produz um AAB/APK assinado,
   sem segredo nenhum commitado no repositório.

2. **INFRA-08 — Conta de desenvolvedor Google Play e faixa de teste interno**
   Cadastrar a conta de desenvolvedor (confirmar taxa vigente na fonte oficial no momento do
   cadastro), criar o app no Play Console, subir o primeiro AAB assinado (depende de APP-14) e
   configurar a faixa de teste interno com os testadores do projeto. Critério de aceite: um
   testador cadastrado recebe e instala a atualização pela própria Play Store, sem reinstalação
   manual.

3. **APP-15 — (opcional/complementar) Firebase App Distribution como canal imediato**
   Configurar o plugin `firebase-appdistribution-gradle` (ou uso via CLI) em `androidApp`, para
   distribuir builds de release aos testadores enquanto a conta do Play Console está em
   verificação, ou como canal permanente caso o time decida não seguir com o Play Console.
   Critério de aceite: um testador recebe o convite por e-mail e instala a versão distribuída sem
   passar pela Play Store.

Nenhuma das três depende de código-produto do app além do já entregue em APP-12 (#147); todas são
configuração de build e de conta externa.

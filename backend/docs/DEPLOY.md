# Deploy remoto do backend na AWS — runbook (INFRA-07 / #150)

> **Decisão do usuário:** usar AWS, na mesma conta já usada por INFRA-05/#123 (conta `076248672901`),
> em vez de um provedor com free tier (Render foi descartado: o Postgres gratuito expira 30 dias
> após criação — nasceria morto antes da banca de 16/10 se criado agora — e o Web Service dorme
> após 15 min de inatividade sem tráfego, confirmado na fonte oficial
> [render.com/docs/free](https://render.com/docs/free)).
>
> **Revisão desta rodada:** o plano anterior (App Runner + RDS) foi descartado — confirmado em
> [docs.aws.amazon.com/apprunner/.../apprunner-availability-change.html](https://docs.aws.amazon.com/apprunner/latest/dg/apprunner-availability-change.html)
> que **"AWS App Runner is no longer open to new customers"**; como esta conta nunca usou o
> serviço, o plano anterior provavelmente não executaria. A própria AWS recomenda o Amazon ECS
> Express Mode como sucessor, mas ele usa Fargate+ALB por baixo — mesmo perfil de custo da opção
> "ECS Fargate + ALB" já descartada por preço na rodada anterior (~US$90/mês). **Decisão do
> usuário: usar AWS Lightsail**, priorizando custo mínimo e aproveitando os créditos iniciais já
> confirmados na conta (billing habilitado, sem risco de fechamento automático).
>
> Este documento é o plano/runbook para revisão humana. **Nenhum recurso AWS real foi criado** —
> ver [Status e bloqueio](#status-e-bloqueio) no final. Segue o mesmo formato do runbook de
> INFRA-05 (`docs/docs_arquitetura/aws-iot-core-provisioning.md`, branch `feat/123-provisionar-aws-iot`).

## Sumário

1. [Decisão de arquitetura](#1-decisão-de-arquitetura)
2. [Estimativa de custo mensal](#2-estimativa-de-custo-mensal)
3. [Domínio e HTTPS](#3-domínio-e-https)
4. [Segredos](#4-segredos)
5. [Runbook — recursos a criar](#5-runbook--recursos-a-criar)
6. [Migrations e CORS](#6-migrations-e-cors)
7. [Publicação (manual vs. GitHub Actions)](#7-publicação-manual-vs-github-actions)
8. [Status e bloqueio](#status-e-bloqueio)

---

## 1. Decisão de arquitetura

### O ponto crítico: persistência de dados do Postgres

Assim como o App Runner, o **Lightsail Container Service** não documenta em nenhuma página do seu
guia oficial (Container services, Deployments, Deployment versions, Pushing images, Metrics) uma
opção de disco/volume persistente anexável aos containers — ao contrário da página de
[Lightsail Instances](https://aws.amazon.com/lightsail/pricing/), que lista "Highly available SSD
storage" como característica central do produto. Não encontrei uma afirmação textual explícita do
tipo "os containers são efêmeros", mas a ausência completa de qualquer menção a volume/disco/estado
persistente nas páginas de Container Service, tratada em conjunto com o padrão dos concorrentes
gerenciados equivalentes (App Runner, ECS Fargate sem EFS), é evidência forte o suficiente para
**não arriscar rodar o Postgres dentro do Container Service**. Por isso as duas variantes abaixo
mantêm o Postgres fora do Container Service (ou fora de qualquer container efêmero):

| Variante | O que roda onde | HTTPS | Persistência dos dados |
|---|---|---|---|
| **A — Container Service + Managed Database** | App no Lightsail Container Service (Nano); Postgres no Lightsail Managed Database (plano Standard) | Automático no domínio default do Container Service, sem custo/config extra (confirmado em [docs.aws.amazon.com/.../amazon-lightsail-container-services.html](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-container-services.html): *"The public endpoint of Lightsail container services supports HTTPS only"*, domínio `https://<ServiceName>.<RandomGUID>.<AWSRegion>.cs.amazonlightsail.com`) | Alta — banco gerenciado, com snapshot/backup e *point-in-time restore* documentados ([amazon-lightsail-databases.html](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-databases.html)), independente do ciclo de vida/redeploy do container da API |
| **B — Instance (VPS) + Docker Compose** | App e Postgres no mesmo Lightsail Instance (bundle Linux/Unix), reaproveitando `backend/compose.yaml` da #149 quase sem alteração | **Não automático** — precisa de reverse proxy com Let's Encrypt (ex.: Caddy) na frente do Compose, e de um nome DNS público para o Let's Encrypt validar o domínio (Lightsail Instance não tem um domínio HTTPS gerenciado por padrão como o Container Service) | Alta na prática (volume nomeado do Compose sobre o SSD persistente da instância, que não é recriado em redeploys de container — só some se a instância for deletada), mas depende de disciplina operacional (não rodar `docker compose down -v` por engano; sem backup automático como o Managed Database) |

### Decisão: Variante A — Container Service (Nano) + Managed Database (Standard)

Critério decisivo, na mesma linha da rodada anterior (App Runner): **HTTPS válido sem precisar de
domínio próprio** é um requisito de aceite da issue, e só a Variante A entrega isso de graça. A
Variante B — mais barata (§2) — reintroduziria o mesmo problema que descartou a opção "ECS
Fargate + ALB": precisaria de um domínio público real para o Let's Encrypt emitir um certificado
válido (um IP puro ou o hostname público padrão de uma Lightsail Instance não server para isso sem
um nome DNS estável apontando para ele; a alternativa seria um serviço de DNS curinga gratuito de
terceiros como `sslip.io`, que funciona tecnicamente mas não é um domínio "próprio" nem um recurso
gerenciado pela AWS — troca robustez por economia).

Também pesa a favor da Variante A: banco com snapshot/backup gerenciado pela AWS elimina o risco de
perder os dados da demo por um erro operacional no host (ex.: `docker compose down -v`), que é
justamente o tipo de risco que motivou descartar o Postgres free do Render (dados podem sumir antes
de 16/10). Dado que o marco de 16/10 é crítico e não há margem para redo, a Variante A troca
~US$15/mês a mais (§2) por menos risco operacional numa janela de tempo curta — troca que considero
adequada para este caso.

**Registrado para o usuário decidir se discordar:** se o custo mínimo (Variante B, ~US$5–7/mês)
for mais importante do que o risco operacional acima, ou se preferir usar `sslip.io`/um domínio já
possuído para o Let's Encrypt, a Variante B está descrita nesta seção e pode ser escolhida no lugar.

## 2. Estimativa de custo mensal

Fonte: [aws.amazon.com/lightsail/pricing](https://aws.amazon.com/lightsail/pricing/) (página oficial
de preços do Lightsail, consultada nesta análise). Lightsail cobra por bundle fixo mensal (não por
hora como App Runner/ECS/RDS), então os valores abaixo já são o teto — não há economia por pausar o
recurso fora dos dias de demo (diferente da alternativa AWS "clássica" avaliada na rodada anterior).

### Variante A (recomendada) — Container Service + Managed Database

| Item | Plano | Preço (fonte oficial) |
|---|---|---|
| Lightsail Container Service | Nano — 0,25 vCPU (compartilhado), 512 MB RAM, 500 GB transferência/mês | **$7 USD/mês** |
| Lightsail Managed Database | Standard — 1 GB memória, 1 core, 40 GB SSD, 100 GB transferência/mês, sem criptografia de dados | **$15 USD/mês** |
| **Total** | | **$22 USD/mês** |

Observação: o plano Standard de banco listado acima é **sem criptografia de dados** — o próximo
degrau ($30/mês, 2 GB memória) já inclui "Data encrypted". Para uma demo acadêmica, considero o
plano sem criptografia em repouso aceitável (segredos de aplicação continuam fora do banco, via
variável de ambiente — §4), mas registro a opção para o usuário decidir se prefere pagar o degrau
seguinte por criptografia em repouso.

### Variante B (alternativa mais barata, com as ressalvas do §1) — Instance + Compose

| Item | Plano | Preço (fonte oficial) |
|---|---|---|
| Lightsail Instance (Linux/Unix) | Menor bundle — 0,5 GB memória, 2 vCPUs compartilhadas, 20 GB SSD, 1 TB transferência/mês | **$5 USD/mês** |
| Lightsail Instance (Linux/Unix), alternativa mais folgada | 1 GB memória, 2 vCPUs compartilhadas, 40 GB SSD, 2 TB transferência/mês | **$7 USD/mês** |
| **Total** | | **$5–7 USD/mês** |

0,5 GB de memória é pouco para JVM (Spring Boot) + Postgres no mesmo host rodando ao mesmo tempo —
recomendo o bundle de $7/mês (1 GB) se a Variante B for a escolhida, para não arriscar OOM na
demo.

**Custo mensal declarado (critério de aceite):** **US$ 22/mês** (Variante A, recomendada) ou
**US$ 7/mês** (Variante B, alternativa mais barata com HTTPS manual). Ambos os valores devem ser
confirmados na [página oficial de preços](https://aws.amazon.com/lightsail/pricing/) no momento da
criação dos recursos, já que preços podem mudar.

## 3. Domínio e HTTPS

**Variante A (recomendada):** usar o domínio default do Container Service
(`https://<ServiceName>.<RandomGUID>.<AWSRegion>.cs.amazonlightsail.com`), com certificado emitido
automaticamente pela AWS — confirmado na documentação oficial (§1). Nenhuma ação extra, nenhum
custo de domínio. Consistente com a autorização padrão do usuário ("aceitável para a banca, a menos
que eu diga o contrário").

**Variante B (se escolhida):** precisa de um nome DNS público estável apontando para o IP estático
da instância — via domínio próprio (registro + apontamento de DNS) ou via um serviço de DNS
curinga gratuito de terceiro (`sslip.io`/`nip.io`), e um reverse proxy com renovação automática de
certificado (Caddy é a opção mais simples — renova Let's Encrypt sozinho). Este documento não
detalha o passo a passo dessa variante porque não é a recomendação — se o usuário optar por ela,
detalho na próxima rodada.

## 4. Segredos

Nenhum segredo entra no repositório nem na imagem Docker. O Lightsail Container Service aceita
variáveis de ambiente por container na definição do deployment (`containers.<nome>.environment`),
mas **não tem um mecanismo nativo equivalente ao `RuntimeEnvironmentSecrets` do App Runner** para
buscar segredos do Secrets Manager/SSM em runtime — as variáveis de ambiente do deployment ficam
armazenadas como texto na definição do serviço (visível a quem tiver acesso de leitura ao recurso
Lightsail, não ao público). Para manter o mesmo nível de higiene dos demais módulos (chaves nunca
em texto no repositório/imagem), a prática recomendada é:

- Gerar o par de chaves RSA de produção (`CAFEY_JWT_PRIVATE_KEY`/`PUBLIC_KEY`, distinto do par
  efêmero de dev) e a senha do banco **fora do repositório**, e colá-los diretamente no console/CLI
  do Lightsail ao criar o deployment — nunca commitados, nunca na imagem Docker.
- Restringir o acesso IAM à conta `076248672901` (ou ao usuário/role que gerencia o Lightsail) a
  quem precisa ver a definição do deployment.
- Documentar aqui (sem os valores) que as variáveis abaixo são preenchidas manualmente no console
  no momento do deploy: `CAFEY_JWT_PRIVATE_KEY`, `CAFEY_JWT_PUBLIC_KEY`,
  `SPRING_DATASOURCE_PASSWORD`.

Se essa limitação for um problema (ex.: mais pessoas precisarem gerenciar o deployment sem ver os
segredos), a alternativa é usar AWS Secrets Manager/SSM Parameter Store e buscar o valor no boot da
aplicação (via um pequeno *entrypoint* que popula a variável de ambiente antes de iniciar o jar) —
mais trabalho de implementação, fica registrado como opção futura, não necessária para a banca.

## 5. Runbook — recursos a criar

**Nada abaixo foi executado.** Comandos de referência para quando o usuário autorizar (Variante A).

```bash
REGION=us-east-1   # ou a região Lightsail preferida — confirmar disponibilidade de Container
                    # Service e Managed Database na região escolhida antes de criar

# 1) Banco gerenciado (Postgres, plano Standard 1GB/1 core/40GB)
aws lightsail create-relational-database \
  --relational-database-name cafey-backend-db \
  --relational-database-blueprint-id postgres_16 \
  --relational-database-bundle-id micro_2_0 \
  --master-database-name cafey_db \
  --master-username cafey_user \
  --master-user-password "<gerar e guardar fora do repositório>" \
  --region "$REGION"

# 2) Container service (Nano)
aws lightsail create-container-service \
  --service-name cafey-backend \
  --power nano \
  --scale 1 \
  --region "$REGION"

# 3) Build + push da imagem para o registro do próprio Container Service
#    (reaproveita o Dockerfile da #149; não precisa de ECR separado)
docker build -t cafey-backend backend/cafey-backend
aws lightsail push-container-image \
  --service-name cafey-backend \
  --label app \
  --image cafey-backend:latest \
  --region "$REGION"

# 4) Endpoint do banco, para a variável SPRING_DATASOURCE_URL
aws lightsail get-relational-database \
  --relational-database-name cafey-backend-db \
  --region "$REGION" \
  --query 'relationalDatabase.masterEndpoint'

# 5) Deployment do container, com as variáveis de ambiente (segredos preenchidos manualmente,
#    nunca neste arquivo/commit — ver §4)
aws lightsail create-container-service-deployment \
  --service-name cafey-backend \
  --containers '{
    "app": {
      "image": ":cafey-backend.app.latest",
      "ports": {"8080": "HTTP"},
      "environment": {
        "SPRING_PROFILES_ACTIVE": "prod",
        "SPRING_DATASOURCE_URL": "jdbc:postgresql://<endpoint-do-banco>:5432/cafey_db",
        "SPRING_DATASOURCE_USERNAME": "cafey_user",
        "SPRING_DATASOURCE_PASSWORD": "<preencher na hora, não versionar>",
        "CAFEY_JWT_PRIVATE_KEY": "<preencher na hora, não versionar>",
        "CAFEY_JWT_PUBLIC_KEY": "<preencher na hora, não versionar>",
        "CAFEY_CORS_ALLOWED_ORIGINS": "<origem real do app Web publicado>"
      }
    }
  }' \
  --public-endpoint '{"containerName": "app", "containerPort": 8080, "healthCheck": {"path": "/actuator/health", "healthyThreshold": 2}}' \
  --region "$REGION"

# 6) Obter a URL pública HTTPS do serviço
aws lightsail get-container-services --service-name cafey-backend --region "$REGION" \
  --query 'containerServices[0].url'
```

**Pendências a confirmar durante a execução (não bloqueiam o plano, mas precisam de atenção na
hora):**
- Expor um endpoint de health check em `/actuator/health` (verificar se o `spring-boot-starter-
  actuator` está entre as dependências do backend; se não estiver, ajustar o `healthCheck.path` do
  passo 5 para um endpoint existente, ex. `/` ou um endpoint público do `AuthController`).
- Confirmar a forma exata pela qual o Container Service alcança o Managed Database (mesma conta,
  possivelmente rede privada do Lightsail vs. endpoint público do banco protegido por firewall) —
  a documentação consultada nesta análise não detalhou esse ponto explicitamente; validar ao
  provisionar e, se necessário, habilitar o modo público do banco com a lista de IPs permitidos
  restrita.

## 6. Migrations e CORS

- **Flyway** roda no boot da aplicação (`ddl-auto: validate`), mesmo mecanismo já validado
  localmente no Compose da #149 — não é um passo separado, só depende de
  `SPRING_DATASOURCE_URL` apontar para o Managed Database.
- **`CAFEY_CORS_ALLOWED_ORIGINS`**: ainda não há URL pública do app Web no repositório — atualizar
  este valor assim que o deploy do app Web (fora do escopo desta issue) existir.

## 7. Publicação (manual vs. GitHub Actions)

**Recomendação:** GitHub Actions a partir de `main`, mas a criação do workflow fica para depois da
primeira publicação manual validada (§5) — o workflow reaproveita os mesmos comandos
`aws lightsail push-container-image` / `create-container-service-deployment`, com credenciais AWS
via OIDC (sem chave de longo prazo no repositório).

## Status e bloqueio

Os itens abaixo **exigem** aprovação humana explícita antes de qualquer execução, por envolverem
custo real e recursos fora do repositório na conta AWS `076248672901`:

- Autorização para criar os recursos do §5 (Managed Database, Container Service, deployment) —
  aguardando.
- Confirmação da Variante A (recomendada, ~US$22/mês) vs. Variante B (~US$7/mês, HTTPS manual) —
  ver trade-off no §1.
- Senha do usuário do banco de produção e o par de chaves RSA de produção
  (`CAFEY_JWT_PRIVATE_KEY`/`PUBLIC_KEY`) — a gerar fora deste repositório.
- Região Lightsail a usar (este runbook assume `us-east-1` como placeholder — confirmar
  disponibilidade de Container Service e Managed Database na região preferida antes de criar).

**Recomendação técnica (resumo):** Lightsail Container Service (Nano, $7/mês) + Lightsail Managed
Database (Standard, $15/mês) = **≈US$22/mês**, domínio HTTPS default do Container Service (sem
domínio próprio, sem custo/configuração extra), dados do Postgres protegidos por backup/snapshot
gerenciado (não sujeitos ao risco operacional de perder o volume de um host único). Alternativa
mais barata (Instance + Compose, ~US$7/mês) documentada no §1/§2 caso o usuário prefira priorizar
custo sobre o risco operacional. Nenhum recurso foi criado — aguardando autorização para executar
o §5.

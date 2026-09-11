# Deploy remoto do backend na AWS — runbook (INFRA-07 / #150)

> **Decisão do usuário:** usar AWS, na mesma conta já usada por INFRA-05/#123 (conta `076248672901`),
> em vez de um provedor com free tier (Render foi descartado: o Postgres gratuito expira 30 dias
> após criação — nasceria morto antes da banca de 16/10 se criado agora — e o Web Service dorme
> após 15 min de inatividade sem tráfego, confirmado na fonte oficial
> [render.com/docs/free](https://render.com/docs/free), consultada nesta análise).
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

### Achado que muda a decisão: App Runner não existe em `sa-east-1`

INFRA-05/#123 escolheu `sa-east-1` (São Paulo) para o AWS IoT Core por latência entre o dispositivo
físico/app e o broker MQTT — critério que não se aplica aqui (o backend já fala com o IoT Core pela
internet pública via TLS, não numa rede privada; a região do backend não afeta essa latência de
forma relevante para uma demo). Ao avaliar as opções, confirmei na
[lista de regiões do AWS App Runner](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AWSAppRunner/current/region_index.json)
(API pública de pricing da AWS, consultada nesta análise, publicação de 2026-08-31) que o App
Runner **não está disponível em `sa-east-1`** — apenas em `us-east-1`, `us-east-2`, `us-west-2`,
`eu-central-1`, `eu-west-1/2/3`, `ap-south-1`, `ap-southeast-1/2`, `ap-northeast-1` (e mais algumas
não citadas). ECS, RDS e ELB/ALB, por outro lado, **estão** disponíveis em `sa-east-1` (confirmado
na mesma API de pricing).

Isso muda a decisão: para usar App Runner (a opção mais simples) é preciso sair de `sa-east-1`.
Como a região do backend/banco não tem o mesmo peso de latência que teve a decisão do IoT Core,
avaliei o custo/simplicidade de cada opção nas regiões onde cada serviço existe.

### Opções comparadas

| Opção | Região viável | HTTPS | A favor | Contra |
|---|---|---|---|---|
| **App Runner + RDS** | `us-east-1` (App Runner não existe em `sa-east-1`) | Automático no domínio default `*.awsapprunner.com`, **sem precisar de domínio próprio nem ACM** | Mais simples: sobe direto de uma imagem no ECR, sem ALB/VPC para configurar à mão; mais barato (sem ALB) | Backend fica em região diferente da instância de IoT Core (não crítico — ver acima); precisa de VPC Connector para o App Runner alcançar o RDS numa subnet privada |
| **ECS Fargate + ALB + RDS** | `sa-east-1` (mesma região do IoT Core) | ALB + certificado ACM — **exige domínio próprio** validado no Route 53/ACM para emitir certificado; sem domínio, não há HTTPS válido | Mesma região do IoT Core; mais controle de infra | Mais caro (ALB cobra por hora + por LCU, ver §2); mais peças para montar (VPC, subnets, security groups, target group, listener HTTPS) |
| **EC2 + Docker Compose** | `sa-east-1` | Precisa de reverse proxy com Let's Encrypt (ex.: Caddy) apontado para um nome DNS público estável — o hostname público default do EC2 muda se a instância for recriada, então precisa de Elastic IP para manter o nome estável | Reaproveita quase o `compose.yaml` da #149 sem alterar; menor custo bruto de compute | Mais operação manual (patch do SO, renovação de certificado, restart do serviço), menos gerenciado; sem auto-healing como App Runner/ECS |

**Decisão: App Runner + RDS, em `us-east-1`.** Critério decisivo: satisfaz "HTTPS com certificado
válido em domínio público" (critério de aceite da issue) **sem precisar comprar/gerenciar um
domínio** — o domínio default do serviço já vem com certificado válido emitido pela AWS. As outras
duas opções exigem providenciar um domínio próprio para ter HTTPS válido (ALB+ACM) ou configurar
renovação de certificado manualmente (EC2+Let's Encrypt). Combinado com o menor custo mensal (§2),
App Runner é a opção mais adequada para uma demo de banca acadêmica, não operação de longo prazo.

Banco: **RDS gerenciado** (`db.t4g.micro`, Single-AZ, Postgres), não container ao lado da app —
para não perder os dados da demo a cada redeploy/reinício do App Runner (App Runner não tem
armazenamento persistente entre implantações) e para poder simplesmente **parar** a instância RDS
(`aws rds stop-db-instance`, até 7 dias por vez, reinicia automaticamente depois) entre sessões de
uso e economizar durante os dias sem demo, sem perder os dados.

## 2. Estimativa de custo mensal

Fonte: [AWS Price List API](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/index.json)
(catálogo oficial de preços, mesma fonte usada pela AWS Pricing Calculator), consultada nesta
análise (publicação `AWSAppRunner`/`AmazonECS`/`AWSELB`: 2026-08-31; `AmazonRDS`: 2026-09-09).
**Confirme os valores na [AWS Pricing Calculator](https://calculator.aws) antes de provisionar** —
preços da AWS mudam com o tempo e variam por SKU/promoção.

Todos os valores assumem operação **24/7** (720h/mês) como teto conservador — na prática, para uma
demo pontual, o custo real fica bem abaixo disso se os recursos forem parados fora dos dias de uso
(App Runner pausa cobrança de vCPU quando ocioso; RDS pode ser parado).

### Opção escolhida — App Runner + RDS (`us-east-1`)

| Item | Preço unitário (fonte AWS) | Estimativa 24/7 |
|---|---|---|
| App Runner — vCPU (0,25 vCPU) | $0,064/vCPU-hora | 0,25 × 0,064 × 720 ≈ **$11,52** |
| App Runner — memória (0,5 GB) | $0,007/GB-hora | 0,5 × 0,007 × 720 ≈ **$2,52** |
| RDS `db.t4g.micro` Single-AZ Postgres | $0,016/instância-hora | 0,016 × 720 ≈ **$11,52** |
| RDS storage 20 GB gp3 | $0,115/GB-mês | 20 × 0,115 ≈ **$2,30** |
| **Total (24/7)** | | **≈ $27,86/mês** |

Sem ALB (App Runner já entrega HTTPS). Data transfer/build minutes não incluídos (marginais para o
volume de uma demo).

### Alternativa descartada — ECS Fargate + ALB + RDS (`sa-east-1`)

| Item | Preço unitário (fonte AWS, `sa-east-1`) | Estimativa 24/7 |
|---|---|---|
| Fargate — 0,5 vCPU | $0,0696/vCPU-hora | 0,5 × 0,0696 × 720 ≈ **$25,06** |
| Fargate — 1 GB memória | $0,0076/GB-hora | 1 × 0,0076 × 720 ≈ **$5,47** |
| ALB — horas do balanceador | $0,034/hora | 0,034 × 720 ≈ **$24,48** |
| ALB — 1 LCU (mínimo) | $0,011/LCU-hora | 0,011 × 720 ≈ **$7,92** |
| RDS `db.t4g.micro` Single-AZ Postgres (`sa-east-1`) | $0,034/instância-hora | 0,034 × 720 ≈ **$24,48** |
| RDS storage 20 GB gp3 (`sa-east-1`) | $0,219/GB-mês | 20 × 0,219 ≈ **$4,38** |
| **Total (24/7)** | | **≈ $91,79/mês** |

Cerca de **3,3× mais caro** que a opção escolhida, majoritariamente pelo ALB — e ainda exigiria
domínio próprio para o certificado ser válido. Descartada por esses dois motivos.

**Custo mensal declarado (critério de aceite):** ≈ **US$ 28/mês** rodando 24/7 (App Runner + RDS,
`us-east-1`); menor na prática se os recursos forem pausados entre sessões de demonstração.

## 3. Domínio e HTTPS

**Decisão padrão: usar o endpoint HTTPS default do App Runner**
(`https://<id-gerado>.us-east-1.awsapprunner.com`), como autorizado pelo usuário caso o contrário
não seja dito. Certificado é emitido e renovado automaticamente pela AWS, sem custo adicional e sem
comprar/gerenciar domínio. Satisfaz o critério "HTTPS com certificado válido em domínio público" sem
trabalho extra.

Se no futuro for necessário um domínio próprio (ex.: para o app mobile/web citar uma URL mais
memorável), App Runner suporta domínio customizado com certificado ACM validado por DNS — fica como
extensão possível, não necessária para a banca.

## 4. Segredos

Nenhum segredo entra no repositório nem na imagem Docker. Usar **AWS Systems Manager Parameter
Store** (`SecureString`, sem custo adicional para parâmetros padrão) e referenciá-los no App Runner
via `RuntimeEnvironmentSecrets` (App Runner busca o valor em runtime, não fica em texto plano na
definição do serviço):

- `/cafey/prod/jwt/private-key`, `/cafey/prod/jwt/public-key` → `CAFEY_JWT_PRIVATE_KEY` /
  `CAFEY_JWT_PUBLIC_KEY` (obrigatórias com `SPRING_PROFILES_ACTIVE=prod`, ver
  `JwtTokenService.resolveKeyPair` — o boot falha sem elas em `prod`, por design da #104).
- `/cafey/prod/db/password` → `SPRING_DATASOURCE_PASSWORD`.
- Certificados X.509 do AWS IoT (thing `cafey-backend`, criada em INFRA-05/#123) como
  `SecureString` separados, se a integração MQTT for ativada no ambiente remoto.

Variáveis não sensíveis (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`CAFEY_CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE=prod`) vão como `RuntimeEnvironmentVariables`
normais do App Runner.

## 5. Runbook — recursos a criar

**Nada abaixo foi executado.** Comandos de referência para quando o usuário autorizar.

```bash
REGION=us-east-1
ACCOUNT_ID=076248672901

# 1) Repositório de imagem
aws ecr create-repository --repository-name cafey-backend --region "$REGION"

# 2) Build + push da imagem (reaproveita o Dockerfile da #149)
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
docker build -t cafey-backend backend/cafey-backend
docker tag cafey-backend:latest "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cafey-backend:latest"
docker push "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/cafey-backend:latest"

# 3) VPC + subnets privadas para o RDS (pode reaproveitar a VPC default da conta,
#    criando só as subnets/security group necessários — detalhar ao executar)
SG_ID=$(aws ec2 create-security-group \
  --group-name cafey-rds-sg --description "RDS Postgres cafey-backend" \
  --vpc-id <vpc-id> --region "$REGION" --query GroupId --output text)

# 4) Instância RDS (Postgres, db.t4g.micro, Single-AZ, não publicamente acessível)
aws rds create-db-instance \
  --db-instance-identifier cafey-backend-prod \
  --engine postgres \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --storage-type gp3 \
  --master-username cafey_user \
  --master-user-password "<gerar e guardar no Parameter Store>" \
  --db-name cafey_db \
  --vpc-security-group-ids "$SG_ID" \
  --no-publicly-accessible \
  --backup-retention-period 1 \
  --region "$REGION"

# 5) VPC Connector do App Runner, para alcançar o RDS na VPC privada
aws apprunner create-vpc-connector \
  --vpc-connector-name cafey-backend-vpc-connector \
  --subnets <subnet-id-1> <subnet-id-2> \
  --security-groups "$SG_ID" \
  --region "$REGION"

# 6) Parâmetros de segredo (SecureString) — valores reais preenchidos na hora, nunca no repo
aws ssm put-parameter --name /cafey/prod/jwt/private-key --type SecureString --value "<...>" --region "$REGION"
aws ssm put-parameter --name /cafey/prod/jwt/public-key  --type SecureString --value "<...>" --region "$REGION"
aws ssm put-parameter --name /cafey/prod/db/password      --type SecureString --value "<...>" --region "$REGION"

# 7) IAM: role de acesso à ECR (build) + role de instância com permissão ssm:GetParameters
#    nos parâmetros acima (detalhar policy mínima ao executar)

# 8) Serviço App Runner
aws apprunner create-service \
  --service-name cafey-backend \
  --source-configuration '{
    "ImageRepository": {
      "ImageIdentifier": "'"$ACCOUNT_ID"'.dkr.ecr.'"$REGION"'.amazonaws.com/cafey-backend:latest",
      "ImageRepositoryType": "ECR",
      "ImageConfiguration": {
        "Port": "8080",
        "RuntimeEnvironmentVariables": {
          "SPRING_PROFILES_ACTIVE": "prod",
          "SPRING_DATASOURCE_URL": "jdbc:postgresql://<endpoint-rds>:5432/cafey_db",
          "SPRING_DATASOURCE_USERNAME": "cafey_user",
          "CAFEY_CORS_ALLOWED_ORIGINS": "<origem real do app Web publicado>"
        },
        "RuntimeEnvironmentSecrets": {
          "CAFEY_JWT_PRIVATE_KEY": "arn:aws:ssm:'"$REGION"':'"$ACCOUNT_ID"':parameter/cafey/prod/jwt/private-key",
          "CAFEY_JWT_PUBLIC_KEY": "arn:aws:ssm:'"$REGION"':'"$ACCOUNT_ID"':parameter/cafey/prod/jwt/public-key",
          "SPRING_DATASOURCE_PASSWORD": "arn:aws:ssm:'"$REGION"':'"$ACCOUNT_ID"':parameter/cafey/prod/db/password"
        }
      }
    },
    "AuthenticationConfiguration": { "AccessRoleArn": "<arn-role-acesso-ecr>" }
  }' \
  --instance-configuration '{"Cpu": "0.25 vCPU", "Memory": "0.5 GB"}' \
  --network-configuration '{"EgressConfiguration": {"EgressType": "VPC", "VpcConnectorArn": "<arn-vpc-connector>"}}' \
  --region "$REGION"

# 9) Obter a URL pública do serviço
aws apprunner describe-service --service-arn <arn-do-servico> --region "$REGION" \
  --query 'Service.ServiceUrl'
```

## 6. Migrations e CORS

- **Flyway** já roda no boot da aplicação (`ddl-auto: validate`, mesmo mecanismo usado localmente
  no Compose da #149) — não é um passo separado no deploy, basta a `SPRING_DATASOURCE_URL` apontar
  para o RDS e o boot aplica as migrations pendentes automaticamente.
- **`CAFEY_CORS_ALLOWED_ORIGINS`**: definir com a origem real do app Web publicado assim que ela
  existir (não `http://localhost:8081`) — hoje ainda não há URL pública do app Web no repositório;
  atualizar este valor quando o deploy do app Web (fora do escopo desta issue) estiver definido.

## 7. Publicação (manual vs. GitHub Actions)

**Recomendação:** GitHub Actions a partir de `main`, para ser reproduzível e não depender de rodar
os comandos do §5 manualmente a cada mudança — mas a criação do workflow (`.github/workflows/`) com
credenciais AWS (`AWS_ROLE_ARN` via OIDC, sem chave de longo prazo no repo) fica para depois da
aprovação da arquitetura e da criação do App Runner/ECR/RDS, já que o workflow depende dos ARNs
gerados no §5. Registrar aqui o passo a passo assim que a primeira publicação manual for validada.

## Status e bloqueio

Os itens abaixo **exigem** aprovação humana explícita antes de qualquer execução, por envolverem
custo real e recursos fora do repositório na conta AWS `076248672901`:

- Autorização para criar os recursos do §5 (ECR, VPC Connector, RDS, SSM Parameters, App Runner) —
  aguardando.
- Senha do usuário do banco de produção e geração/obtenção das chaves RSA de produção
  (`CAFEY_JWT_PRIVATE_KEY`/`PUBLIC_KEY` — podem ser um par novo, gerado especificamente para
  produção, distinto do par efêmero de dev).
- Confirmação de que `us-east-1` (região diferente da `sa-east-1` do IoT Core) é aceitável — ver
  justificativa em [§1](#1-decisão-de-arquitetura).

**Recomendação técnica (resumo):** App Runner + RDS `db.t4g.micro` em `us-east-1`, domínio HTTPS
default do App Runner (sem domínio próprio), segredos via SSM Parameter Store `SecureString`,
custo estimado ≈ US$ 28/mês rodando 24/7 (menor se pausado entre demos). Publicação inicial manual
(§5), GitHub Actions como evolução após a primeira publicação validada. Nenhum recurso foi criado —
aguardando autorização para executar o §5.

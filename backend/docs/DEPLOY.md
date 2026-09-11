# Deploy remoto do backend na AWS — runbook (INFRA-07 / #150)

> **Decisão do usuário:** usar AWS, na mesma conta já usada por INFRA-05/#123 (conta `076248672901`),
> em vez de um provedor com free tier (Render foi descartado: o Postgres gratuito expira 30 dias
> após criação — nasceria morto antes da banca de 16/10 se criado agora — e o Web Service dorme
> após 15 min de inatividade sem tráfego, confirmado na fonte oficial
> [render.com/docs/free](https://render.com/docs/free)).
>
> **1ª revisão:** o plano App Runner + RDS foi descartado — confirmado em
> [docs.aws.amazon.com/apprunner/.../apprunner-availability-change.html](https://docs.aws.amazon.com/apprunner/latest/dg/apprunner-availability-change.html)
> que **"AWS App Runner is no longer open to new customers"**. Decisão: usar **AWS Lightsail**.
>
> **2ª revisão (decisão final do usuário):** **Variante B — Lightsail Instance + Docker Compose**,
> com **DuckDNS** (DNS dinâmico gratuito) resolvendo o domínio, eliminando a objeção "precisa de
> domínio próprio" que pesava contra essa variante na rodada anterior. Custo final: **≈US$7/mês**
> (só a instância — sem custo de banco gerenciado, sem custo de domínio).
>
> Este documento é o plano/runbook para revisão humana. **Nenhum recurso AWS/DuckDNS real foi
> criado** — ver [Status e bloqueio](#status-e-bloqueio) no final. Segue o mesmo formato do runbook
> de INFRA-05 (`docs/docs_arquitetura/aws-iot-core-provisioning.md`, branch
> `feat/123-provisionar-aws-iot`).

## Sumário

1. [Decisão de arquitetura](#1-decisão-de-arquitetura)
2. [Estimativa de custo mensal](#2-estimativa-de-custo-mensal)
3. [Domínio e HTTPS (DuckDNS + Caddy)](#3-domínio-e-https-duckdns--caddy)
4. [Segredos](#4-segredos)
5. [Backup do Postgres](#5-backup-do-postgres)
6. [Runbook — recursos a criar](#6-runbook--recursos-a-criar)
7. [Migrations e CORS](#7-migrations-e-cors)
8. [Publicação (manual vs. GitHub Actions)](#8-publicação-manual-vs-github-actions)
9. [Status e bloqueio](#status-e-bloqueio)

---

## 1. Decisão de arquitetura

**Variante escolhida: Lightsail Instance (VPS) rodando `backend/compose.yaml` da #149** (app +
Postgres no mesmo host), na frente um Caddy fazendo HTTPS automático para um subdomínio DuckDNS.

Histórico da decisão (rodada anterior comparou duas variantes — ver commits anteriores deste
arquivo): a Variante A (Container Service + Managed Database gerenciado, ~US$22/mês) foi
inicialmente recomendada por eliminar dois riscos — precisar de domínio próprio para HTTPS válido,
e backup dos dados. O usuário decidiu pela **Variante B** (mais barata, ~US$7/mês) resolvendo os
dois pontos diretamente:

1. **Domínio sem custo:** DuckDNS (DNS dinâmico gratuito) resolve a necessidade de um nome DNS
   público estável apontando para o IP da instância, sem comprar domínio — ver §3.
2. **Backup dos dados:** um mecanismo simples de `pg_dump` periódico, coberto no §5, já que a
   instância não tem snapshot automático do banco como o Managed Database teria.

Com os dois riscos endereçados, a Variante B reaproveita o `Dockerfile`/`compose.yaml` já entregues
na #149 quase sem alteração (só adicionando o serviço do Caddy), o que reduz o trabalho de
implementação antes de 16/10 — critério que também pesa a favor desta escolha.

## 2. Estimativa de custo mensal

Fonte: [aws.amazon.com/lightsail/pricing](https://aws.amazon.com/lightsail/pricing/) (consultada
nesta análise). DuckDNS é gratuito (sem tier pago).

| Item | Plano | Preço (fonte oficial) |
|---|---|---|
| Lightsail Instance (Linux/Unix) | 1 GB memória, 2 vCPUs compartilhadas, 40 GB SSD, 2 TB transferência/mês | **$7 USD/mês** |
| Static IP (Lightsail) | Grátis enquanto anexado a uma instância em execução | **$0** |
| DuckDNS | Subdomínio `*.duckdns.org`, plano gratuito | **$0** |
| Certificado TLS (Let's Encrypt via Caddy) | — | **$0** |
| **Total** | | **≈$7 USD/mês** |

Escolhido o bundle de **1 GB de memória** (não o de $5/mês com 0,5 GB) porque JVM (Spring Boot) e
Postgres rodando juntos no mesmo host precisam de mais do que 512 MB para não arriscar OOM durante
a demo.

**Backup opcional fora da instância** (§5): se o usuário quiser uma cópia do dump fora do disco da
própria instância (proteção contra perda/corrupção do disco, não só contra erro de operação), um
bucket Lightsail Object Storage custa **$1 USD/mês** (bundle de 5 GB storage / 25 GB transferência —
mesma fonte de preços), o que levaria o total para **≈$8 USD/mês**. Ver §5 para a recomendação.

**Custo mensal declarado (critério de aceite): ≈US$7/mês** (ou ≈US$8/mês com backup externo
opcional).

## 3. Domínio e HTTPS (DuckDNS + Caddy)

### DuckDNS

[DuckDNS](https://www.duckdns.org) é um serviço de DNS dinâmico gratuito. Fluxo (confirmado na
[especificação oficial da API](https://www.duckdns.org/spec.jsp)):

1. Criar conta gratuita em duckdns.org (login via GitHub/Google/Reddit/Twitter — sem cartão).
2. Registrar um subdomínio, ex. `cafey-backend.duckdns.org`, apontando inicialmente para o IP
   estático da instância Lightsail.
3. Manter o registro atualizado com uma chamada HTTPS simples:
   `https://www.duckdns.org/update?domains=cafey-backend&token=<token>&ip=<ip-estatico>` — como o
   IP é **estático** (Lightsail Static IP, gratuito enquanto anexado à instância), essa chamada só
   precisa ser feita uma vez (ou, por segurança/simplicidade, num cron a cada poucas horas, caso o
   IP eventualmente mude por alguma reassociação manual).

### HTTP-01 (escolhido) em vez de DNS-01

O pedido pediu para avaliar o desafio DNS-01 (via plugin DuckDNS do Caddy, como no tutorial do Home
Assistant) contra o HTTP-01 simples. **Escolha: HTTP-01**, o modo *default* do Caddy
— confirmado na [documentação oficial do Caddy sobre HTTPS automático](https://caddyserver.com/docs/automatic-https):
*"Caddy keeps all managed certificates renewed [...] Certificates are obtained and renewed for all
qualifying domain names"* usando a porta 80 para o desafio HTTP, sem qualquer plugin/config
adicional, desde que o domínio resolva publicamente para o host e a porta 80 esteja acessível.

Como a instância já precisa expor a porta 80 (para o próprio redirecionamento HTTP→HTTPS do Caddy)
e a porta 443, **não há motivo para a complexidade extra do desafio DNS-01** (que exigiria compilar
um binário Caddy customizado com `xcaddy` incluindo o módulo `github.com/caddy-dns/duckdns`, e
gerenciar o token da API DuckDNS como segredo dentro da config do Caddy). DNS-01 só compensaria se a
porta 80 não pudesse ficar aberta (não é o caso aqui) ou se fosse necessário um certificado
wildcard (não é necessário — um único subdomínio basta).

### Configuração do Caddy no Compose

Adicionar um serviço `caddy` ao `backend/compose.yaml`, na frente do serviço `app`, com um
`Caddyfile` mínimo:

```
cafey-backend.duckdns.org {
    reverse_proxy app:8080
}
```

O Caddy oficial (imagem `caddy:2-alpine`) já inclui suporte a HTTPS automático via Let's Encrypt
(HTTP-01) sem plugins. Portas `80`/`443` do host mapeadas para o container do Caddy; o serviço
`app` deixa de publicar a porta `8080` no host (só acessível internamente, via a rede do Compose).

## 4. Segredos

Sem Secrets Manager nativo nesse caminho (é uma instância genérica, não um recurso gerenciado com
integração de segredos como o Container Service teria). Mesma abordagem usada localmente pela #149,
adaptada para produção:

- As variáveis sensíveis (`CAFEY_JWT_PRIVATE_KEY`, `CAFEY_JWT_PUBLIC_KEY`,
  `SPRING_DATASOURCE_PASSWORD`) entram via um arquivo `.env` **na instância**, fora do
  repositório (git-ignorado, nunca commitado, copiado manualmente ou colado via SSH ao provisionar)
  — o Compose já carrega `.env` automaticamente (mesmo mecanismo documentado em
  `backend/README.md` para uso local).
- Permissões do arquivo restritas ao usuário SSH da instância (`chmod 600 .env`).
- Chaves RSA de produção geradas especificamente para este ambiente, distintas do par efêmero de
  dev — nunca reaproveitar uma chave de teste.
- `SPRING_PROFILES_ACTIVE=prod` ativo, para que o boot falhe caso as chaves não estejam presentes
  (`JwtTokenService.resolveKeyPair`), evitando subir com chave efêmera em produção por engano.

## 5. Backup do Postgres

A instância não tem snapshot automático do banco (diferente do Managed Database da Variante A).
Mecanismo simples, suficiente para proteger os dados da demo até 16/10 (não é uma solução de
produção):

### Cron no host (recomendado, custo zero)

Um cron job no host da instância, fora do Compose (mais simples que adicionar um serviço extra ao
`compose.yaml` só para isso), rodando `pg_dump` dentro do container `db` e salvando o dump
comprimido num diretório do host:

```bash
# /etc/cron.d/cafey-backup (na instância, não no repositório)
0 */6 * * * root docker compose -f /opt/cafey/backend/compose.yaml exec -T db \
  pg_dump -U cafey_user cafey_db | gzip > /opt/cafey/backups/cafey_db_$(date +\%Y\%m\%d_\%H\%M).sql.gz
  find /opt/cafey/backups -name '*.sql.gz' -mtime +7 -delete
```

A cada 6 horas, mantendo os últimos 7 dias — ajustável. Dump fica no SSD da própria instância (não
protege contra perda do disco/instância inteira, só contra erro operacional pontual, ex.: uma
migration ruim ou um `DELETE` sem `WHERE`).

### Cópia externa opcional (Lightsail Object Storage, ~$1/mês — ver §2)

Para proteção também contra perda da instância/disco, adicionar ao mesmo cron uma linha de
`aws s3 cp` para um bucket Lightsail Object Storage (compatível com a API S3):

```bash
aws s3 cp /opt/cafey/backups/cafey_db_$(date +\%Y\%m\%d_\%H\%M).sql.gz \
  s3://cafey-backend-backups/ --endpoint-url https://s3.<regiao>.amazonaws.com
```

**Recomendação:** habilitar a cópia externa (+$1/mês, marginal) dado que a issue é classificada
como `crítico` e o marco de 16/10 não tem margem para "redo" — mas fica registrado como opcional
para o usuário decidir junto da autorização de execução.

## 6. Runbook — recursos a criar

**Nada abaixo foi executado.**

```bash
REGION=us-east-1   # ou a região Lightsail preferida

# 1) Instância (bundle 1 GB / 2 vCPUs compartilhadas / 40 GB SSD)
aws lightsail create-instances \
  --instance-names cafey-backend-vps \
  --availability-zone "${REGION}a" \
  --blueprint-id ubuntu_24_04 \
  --bundle-id small_3_0 \
  --region "$REGION"

# 2) IP estático, anexado à instância (gratuito enquanto anexado)
aws lightsail allocate-static-ip --static-ip-name cafey-backend-ip --region "$REGION"
aws lightsail attach-static-ip \
  --static-ip-name cafey-backend-ip \
  --instance-name cafey-backend-vps \
  --region "$REGION"

# 3) Abrir as portas necessárias (22 já vem aberta por padrão; 80/443 para o Caddy)
aws lightsail put-instance-public-ports \
  --instance-name cafey-backend-vps \
  --port-infos fromPort=22,toPort=22,protocol=TCP fromPort=80,toPort=80,protocol=TCP fromPort=443,toPort=443,protocol=TCP \
  --region "$REGION"

# 4) DuckDNS — criar conta e subdomínio manualmente em duckdns.org (sem CLI oficial),
#    depois apontar para o IP estático obtido no passo 2:
curl "https://www.duckdns.org/update?domains=cafey-backend&token=<token-duckdns>&ip=<ip-estatico>"

# 5) Provisionar a instância via SSH: instalar Docker + Docker Compose plugin, clonar o
#    repositório (ou copiar só backend/), criar o .env (§4) e o Caddyfile (§3), então:
#    docker compose -f backend/compose.yaml up -d --build
#    (reaproveita o Dockerfile/compose.yaml da #149, adicionando o serviço `caddy`)

# 6) Cron de backup (§5) — copiar o arquivo /etc/cron.d/cafey-backup para a instância

# 7) Se optar pela cópia externa (§5): criar o bucket
aws lightsail create-bucket \
  --bucket-name cafey-backend-backups \
  --bundle-id small_1_0 \
  --region "$REGION"
```

**Pendências a confirmar durante a execução (não bloqueiam o plano):**
- Confirmar disponibilidade do bundle/blueprint (`small_3_0`/`ubuntu_24_04`) na região escolhida —
  IDs de bundle podem variar; validar com `aws lightsail get-bundles` e
  `aws lightsail get-blueprints` no momento da criação.
- Testar a emissão do certificado Let's Encrypt via Caddy assim que o DNS do DuckDNS propagar
  (pode levar alguns minutos) — se falhar, checar se a porta 80 está mesmo acessível
  externamente (grupo de portas do passo 3).

## 7. Migrations e CORS

- **Flyway** roda no boot da aplicação (`ddl-auto: validate`), mesmo mecanismo já validado
  localmente no Compose da #149 — não é um passo separado, só depende de `SPRING_DATASOURCE_URL`
  apontar para `db` (mesma rede do Compose, sem mudança em relação à #149 — Postgres continua no
  mesmo host/Compose, diferente da Variante A que usaria um Managed Database externo).
- **`CAFEY_CORS_ALLOWED_ORIGINS`**: ainda não há URL pública do app Web no repositório. O usuário
  mencionou estar avaliando Vercel para o frontend Web, separadamente desta issue — atualizar esta
  variável assim que essa URL existir; não é responsabilidade de #150 decidir a hospedagem do
  frontend.

## 8. Publicação (manual vs. GitHub Actions)

**Recomendação:** primeira publicação manual (via SSH, passo 5 do §6) para validar o ambiente antes
de 16/10; GitHub Actions como evolução depois disso — um workflow simples que conecta via SSH
(chave privada como GitHub Secret) e roda `git pull && docker compose up -d --build` na instância.
Fica para a próxima rodada, depois da primeira publicação manual validada.

## Status e bloqueio

Os itens abaixo **exigem** aprovação humana explícita antes de qualquer execução, por envolverem
custo real e recursos fora do repositório na conta AWS `076248672901` e numa conta DuckDNS pessoal:

- Autorização para criar os recursos do §6 (instância, IP estático, portas, bucket opcional).
- Criar a conta DuckDNS e registrar o subdomínio (ação fora da AWS, precisa de decisão de qual
  conta/login usar).
- Decidir se a cópia externa de backup (§5, +$1/mês) entra ou não.
- Senha do usuário do banco de produção e o par de chaves RSA de produção
  (`CAFEY_JWT_PRIVATE_KEY`/`PUBLIC_KEY`) — a gerar fora deste repositório, para o `.env` da
  instância (§4).
- Região Lightsail a usar (runbook assume `us-east-1` como placeholder).

**Recomendação técnica (resumo):** Lightsail Instance (1 GB, $7/mês) + Docker Compose (reaproveita
a #149) + Caddy com HTTPS automático via HTTP-01 (mais simples que DNS-01, sem plugin/token no
Caddy) + DuckDNS gratuito para o domínio + cron de `pg_dump` a cada 6h (7 dias de retenção),
com cópia externa opcional para Lightsail Object Storage (+$1/mês) dado o caráter crítico da
issue. Total: **≈US$7–8/mês**. Nenhum recurso foi criado — aguardando autorização para executar
o §6.

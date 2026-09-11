# Deploy remoto do backend (INFRA-07 / #150)

Este documento registra a análise preparatória para o deploy remoto. **A escolha final de
hospedagem, a criação de contas/domínio e a execução do deploy exigem decisão e ação humana** —
envolvem custo real e efeitos fora do repositório (conta em provedor externo, domínio, certificado,
segredos de produção), por isso não foram executados por este agente. Ver bloqueio registrado no
status file da branch.

## Opções avaliadas para hospedagem

| Opção | A favor | Contra | Observação |
|---|---|---|---|
| **AWS (ECS/App Runner/EC2)** | Já é a plataforma do projeto (INFRA-05/#123 usa AWS IoT Core); um único provedor simplifica IAM/rede/observabilidade | Sem free tier permanente comparável para compute+HTTPS gerenciado; mais peças para configurar (load balancer, certificado ACM, domínio no Route 53) | Faz sentido se o projeto for operar além da banca; custo mensal precisa ser orçado caso a caso |
| **Render** | Free tier para Web Service (com sleep após inatividade) e Postgres gerenciado (free tier com expiração, historicamente ~90 dias); HTTPS automático em domínio `*.onrender.com` sem configuração manual; deploy direto de imagem Docker | Free tier com cold start perceptível e Postgres free com prazo de expiração — precisa confirmar prazo vigente antes de comprometer a banca | Bom encaixe para uma demonstração pontual com pouca configuração |
| **Fly.io** | Free tier de compute; roda a imagem Docker diretamente; HTTPS automático | Modelo de free tier mudou nos últimos anos (passou a exigir cartão e cobrar por uso acima de um crédito) — **confirmar condição vigente antes de decidir** | Requer mais familiaridade com `fly.toml`/CLI |
| **Railway** | Deploy simples a partir de Dockerfile/imagem, HTTPS automático | Não tem mais free tier permanente sem cartão (passou a ser trial por tempo/crédito) — **confirmar condição vigente antes de decidir** | |

**Recomendação preliminar (a confirmar com o usuário):** para o marco de 16/10 — uma demonstração
pontual, não operação contínua — um provedor com free tier simples (Render é o mais direto: deploy
por imagem Docker + Postgres gerenciado + HTTPS automático sem configurar certificado/domínio à
mão) tende a exigir menos trabalho de infraestrutura do que AWS, que faz mais sentido se o backend
for operar além da banca. Os preços e condições de free tier mudam com frequência nesses provedores
— **confirme o plano vigente na página oficial de cada um antes de decidir**, não assuma os valores
acima como atuais.

## Pontos que exigem decisão/ação humana (fora do escopo deste agente)

1. Escolher o provedor (com custo mensal declarado, ainda que zero) e criar a conta.
2. Registrar/apontar um domínio público (ou usar o subdomínio gratuito do provedor, ex.:
   `*.onrender.com`), e confirmar que o certificado HTTPS é emitido automaticamente ou precisa de
   configuração adicional.
3. Provisionar o banco (gerenciado pelo provedor ou container ao lado da API) e decidir a
   persistência dos dados da demonstração entre reinícios.
4. Cadastrar os segredos como variáveis de ambiente no painel do provedor (nunca no repositório):
   `CAFEY_JWT_PRIVATE_KEY`, `CAFEY_JWT_PUBLIC_KEY`, `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD`,
   `CAFEY_CORS_ALLOWED_ORIGINS` (apontando para a origem real do app Web publicado), e os
   certificados X.509 do AWS IoT se a integração MQTT estiver ativa no ambiente remoto.
5. Ativar o perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`) no serviço remoto — é o que faz o boot
   falhar caso as chaves JWT externas não estejam configuradas (ver
   `JwtTokenService.resolveKeyPair`), evitando subir em produção com chave efêmera.
6. Confirmar que o Flyway aplica as migrations no boot contra o banco remoto (mesmo mecanismo do
   Compose local, `ddl-auto: validate`).
7. Decidir o procedimento de publicação: manual (`docker build` + push para o registro do provedor)
   ou GitHub Actions disparado a partir de `main`. Uma vez decidido, documentar o passo a passo aqui.

## O que este repositório já oferece como base

- `backend/cafey-backend/Dockerfile`: build multi-stage já pronto para qualquer provedor que aceite
  imagem Docker (Render, Fly.io, Railway, ECS/App Runner).
- `backend/compose.yaml`: referência de quais variáveis de ambiente o serviço `app` espera —
  o mesmo conjunto (com valores de produção, não os defaults de dev) deve ser replicado no painel
  do provedor escolhido.
- `backend/README.md`: documenta os dois modos de execução local.

Quando a decisão de hospedagem for tomada, atualize este arquivo com o procedimento de publicação
reproduzível, a URL pública, o custo mensal confirmado e a data em que o ambiente foi validado
antes de 16/10.

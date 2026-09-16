<!-- RASCUNHO DE CONTEÚDO — revisão antes da formatação em .docx. Numeração de seção/citação segue estrutura_prevista_artigo.md. -->

# Título

**PT:** Integração de eletrodomésticos legados de chave mecânica travada em sistemas de automação residencial via retrofit IoT

**EN:** Integrating Legacy Mechanical-Switch Appliances into Home Automation Systems via IoT Retrofit

**ES:** Integración de electrodomésticos legados de interruptor mecánico trabado en sistemas de automatización del hogar mediante retrofit IoT

# Autores

Vitor Tavares Chaves - vitor.chaves@aluno.cps.sp.gov.br

Jeferson de Souza Dias — jeferson.dias01@cps.sp.gov.br

*(nota: por orientação da disciplina, o autor principal deve vir primeiro e o professor-orientador por último)*

# Palavras-chave (quadro lateral)

**PT:** Internet das Coisas; Retrofit; Automação Residencial; Sistemas Embarcados

**EN:** Internet of Things; Retrofit; Home Automation; Embedded Systems

**ES:** Internet de las Cosas; Retrofit; Automatización del Hogar; Sistemas Embebidos

# Resumo

Parte significativa das cafeteiras elétricas domésticas ainda opera com chave mecânica sem programação, e a resposta usual para automação — substituir o aparelho por um modelo inteligente — descarta um equipamento funcional, com custo econômico e ambiental questionável [3]. Tomadas inteligentes genéricas atenuam o problema, mas não atendem a um aparelho cuja chave mecânica trava na posição ligada, dependendo apenas do corte e retomada de energia [1]. Este trabalho apresenta o Cafey, um módulo retrofit baseado em ESP32 que se interpõe entre a rede elétrica e a cafeteira, acionando-a por relé sem modificação interna. O firmware segue o padrão Active Object, com agendamento persistido em memória não volátil e sincronizado por NTP, garantindo funcionamento autônomo sem conectividade. A comunicação com a nuvem usa MQTT sobre TLS, com contrato de tópicos idempotente para os agendamentos. Resultados parciais de bancada confirmam a verificação elétrica do esquemático e o acionamento do relé por comando do ESP32; a validação com carga real é o próximo passo. Discute-se a diferença frente a tomadas inteligentes comerciais.

# Abstract

A significant share of household electric coffee makers still operate through an unprogrammable mechanical switch, and the usual response for adding automation — replacing the appliance with a "smart" model — discards a functional device, at a questionable economic and environmental cost [3]. Generic smart plugs partially mitigate the problem but do not address the specifics of an appliance whose mechanical switch latches in the on position, relying solely on cutting and restoring power to operate [1]. This work presents Cafey, an ESP32-based retrofit module placed between the mains and the coffee maker, switching it through a relay without any internal modification to the appliance. The firmware follows the Active Object pattern, with schedules persisted in non-volatile memory and synchronized via NTP, ensuring autonomous operation even without connectivity. Cloud communication uses MQTT over TLS, with an idempotent topic contract for the schedule list. Partial bench results confirm the schematic's electrical verification and relay actuation by ESP32 command; validation under real load remains a next step. The proposal is discussed in contrast to commercial smart plugs.

# Resumen

Parte del parque doméstico de cafeteras eléctricas todavía funciona con un interruptor mecánico sin programación, y la respuesta habitual para añadir automatización —sustituir el aparato por un modelo inteligente— descarta un equipo funcional, con un costo económico y ambiental cuestionable [3]. Los enchufes inteligentes genéricos mitigan el problema, pero no atienden a un aparato cuyo interruptor mecánico queda trabado en la posición encendida, dependiendo solo del corte y la reposición de energía [1]. Este trabajo presenta Cafey, un módulo retrofit basado en ESP32 que se interpone entre la red eléctrica y la cafetera, accionándola mediante un relé sin modificación interna. El firmware sigue el patrón Active Object, con agendamientos persistidos en memoria no volátil y sincronizados por NTP, garantizando funcionamiento autónomo sin conectividad. La comunicación con la nube usa MQTT sobre TLS, con un contrato de tópicos idempotente para los agendamientos. Resultados parciales de banco confirman la verificación eléctrica del esquemático y el accionamiento del relé por comando del ESP32; la validación con carga real es el próximo paso. Se discute la diferencia frente a los enchufes inteligentes comerciales.

---

# 1. Introdução

Grande parte das cafeteiras elétricas domésticas em uso no Brasil não possui qualquer forma de programação: o usuário liga o aparelho manualmente por meio de uma chave mecânica, sem agendamento, controle remoto ou registro de uso. Quando se deseja adicionar automação a esse parque instalado, a resposta mais comum é substituir o aparelho por um modelo "inteligente" com Wi-Fi embutido. Essa alternativa, no entanto, é insatisfatória sob a ótica do ciclo de vida do produto: descartar um equipamento funcional por razões de conveniência tem impacto econômico e ambiental relevante, e a literatura sobre reparo e prolongamento de vida útil de pequenos eletrodomésticos mostra que, na maioria dos cenários analisados, estender o uso do aparelho existente é preferível à sua substituição precoce [3].

Uma alternativa intermediária, mais barata, é o uso de tomadas inteligentes genéricas, que cortam e restauram a energia do aparelho a partir de comandos remotos. Essa solução funciona razoavelmente bem para cargas simples, mas não fecha a lacuna técnica de aparelhos legados cujo acionamento depende de uma chave mecânica com trava: se a chave estiver na posição desligada, cortar e restaurar a energia não liga o aparelho; e se o usuário desligar a cafeteira manualmente sem antes desligá-la pela tomada inteligente, o estado do sistema diverge do estado real do aparelho. A integração de aparelhos legados a sistemas de gestão de energia residencial exige, portanto, um projeto que leve em conta essa restrição física do chaveamento, e não apenas a automação do fornecimento de energia [1].

Este trabalho apresenta o Cafey, um módulo eletrônico retrofit que se instala entre a tomada de parede e uma cafeteira de chave mecânica travada, sem qualquer modificação interna do aparelho. O módulo usa um microcontrolador ESP32 para acionar um relé que corta e restabelece a fase de alimentação da cafeteira, com a chave mecânica do aparelho permanecendo sempre na posição ligada. O controle é feito por um aplicativo multiplataforma, com agendamentos que são executados localmente pelo próprio módulo — sem depender de conectividade no momento do disparo — e sincronizados com a nuvem por MQTT quando a rede está disponível.

A contribuição deste artigo é dupla: (i) uma arquitetura de hardware e firmware para retrofit de aparelhos de chave mecânica travada, com dimensionamento elétrico documentado e verificação do esquemático por regras elétricas (ERC); e (ii) um contrato de comunicação MQTT idempotente para sincronização de agendamentos entre nuvem e dispositivo, tolerante a reconexões e a reentrega de mensagens retidas. O texto está organizado da seguinte forma: a Seção 2 revisa a fundamentação teórica sobre integração de aparelhos legados, prolongamento de vida útil, interoperabilidade em automação residencial, comunicação em dispositivos restritos e arquitetura de software orientada a eventos; a Seção 3 descreve os materiais e métodos empregados no projeto do módulo; a Seção 4 apresenta os resultados parciais obtidos até o momento e discute limitações; e a Seção 5 traz as considerações finais.

# 2. Fundamentação Teórica

## 2.1 Integração de aparelhos legados e limites do chaveamento de fase

Sistemas de gestão de energia residencial frequentemente precisam incorporar aparelhos que não foram projetados para conectividade. Egarter et al. [1] discutem estratégias de integração de aparelhos legados a sistemas de gestão de energia doméstica, destacando que o chaveamento simples da fase de alimentação — a técnica mais barata e mais amplamente disponível — é suficiente para aparelhos cujo acionamento independe de um estado interno preservado, mas insuficiente para aparelhos cujo funcionamento depende de uma interface física (como uma chave mecânica) que precisa estar em um estado específico para que o corte e a retomada de energia produzam o efeito desejado. Esse é exatamente o caso de cafeteiras de chave mecânica com trava: o chaveamento de fase só liga o aparelho se a chave já estiver fisicamente na posição ligada, o que desloca parte do problema de automação para o desenho mecânico do próprio retrofit.

Santos et al. [2] reforçam essa limitação a partir de um ângulo diferente: ao avaliar o uso de tomadas inteligentes comerciais para aquisição de dados em pesquisa, os autores mostram que esses dispositivos frequentemente assumem que o aparelho controlado permanece em um estado elétrico previsível entre os ciclos de chaveamento, premissa que não se sustenta para aparelhos com chave mecânica manual, sujeitos a intervenção humana direta e independente do sistema de automação.

## 2.2 Prolongamento de vida útil como alternativa à substituição

Do ponto de vista de ciclo de vida do produto, Bovea, Ibáñez-Forés e Pérez-Belis [3] comparam cenários de reparo e substituição para pequenos eletrodomésticos por meio de avaliação de ciclo de vida (ACV), concluindo que o reparo — ou, por extensão, o prolongamento funcional do aparelho existente — tende a ser ambientalmente preferível à substituição precoce, sobretudo quando o aparelho ainda cumpre adequadamente sua função primária. Slade [12], em uma análise histórica da obsolescência tecnológica nos Estados Unidos, documenta como estratégias de obsolescência planejada e percebida induzem à substituição de aparelhos ainda funcionais por motivos de conveniência ou moda, e não por falha real do equipamento. Nesse contexto, retrofit — adicionar capacidade de automação a um aparelho existente, sem substituí-lo — surge como estratégia coerente com o prolongamento de vida útil, atacando diretamente a causa apontada por Slade [12]: a decisão de descarte não motivada por defeito, mas pela ausência de uma funcionalidade que pode, em vez disso, ser adicionada externamente.

## 2.3 Fragmentação e interoperabilidade em automação residencial

O ecossistema de automação residencial é historicamente fragmentado, com protocolos, nuvens e ecossistemas de fabricantes incompatíveis entre si. Russo et al. [4] revisam duas décadas de tentativas de padronização de interoperabilidade em automação residencial, apontando que a maior parte das soluções falhou em resolver a fragmentação porque tratava a interoperabilidade como problema exclusivamente de protocolo, sem considerar a diversidade de aparelhos legados já instalados nas residências. Madadi-Barough et al. [5] descrevem o Matter, iniciativa mais recente de padronização de interoperabilidade para casas inteligentes, e observam que, mesmo com um padrão amplamente apoiado pela indústria, aparelhos sem qualquer capacidade de conectividade original — como a maioria das cafeteiras de chave mecânica em uso — permanecem fora do alcance do padrão, pois este assume a existência de um componente de comunicação já embarcado no aparelho. Esse tipo de lacuna é justamente o espaço de projeto que retrofits como o Cafey ocupam.

## 2.4 Comunicação em dispositivos restritos: MQTT, QoS e TLS

Dispositivos embarcados de baixo custo, como o ESP32 usado neste projeto, têm restrições de processamento, memória e energia que orientam a escolha de protocolos de comunicação. Bayılmış et al. [7] comparam protocolos de comunicação para Internet das Coisas (IoT, do inglês *Internet of Things*) quanto a desempenho, apontando o MQTT (*Message Queuing Telemetry Transport*) como protocolo adequado para cenários de conectividade intermitente por seu modelo publish/subscribe leve e por oferecer níveis de qualidade de serviço (QoS, do inglês *Quality of Service*) configuráveis. Akshatha, Dilip Kumar e Venugopal [8] detalham implementações e desafios abertos do MQTT, incluindo o papel do QoS 1 — que garante entrega ao menos uma vez, mas pode gerar reentrega de mensagens duplicadas — como comprometimento adequado entre confiabilidade e simplicidade para dispositivos restritos. Gentile et al. [10] avaliam o desempenho de protocolos de segurança para sistemas de medição distribuída baseados em IoT com hardware restrito, mostrando que o uso de TLS (*Transport Layer Security*) com autenticação por certificado é viável mesmo em microcontroladores de baixo custo, desde que o protocolo de aplicação acomode a latência adicional do handshake. Essas três referências fundamentam, em conjunto, a escolha de MQTT sobre TLS com QoS 1 adotada no Cafey, assim como a necessidade de tratar explicitamente a possibilidade de reentrega de mensagens no contrato de tópicos.

## 2.5 Autonomia local e dependência de nuvem

Sistemas de automação residencial que dependem inteiramente da nuvem para operar ficam inoperantes durante quedas de conectividade ou indisponibilidade do provedor, mesmo para funções básicas que não exigiriam, em princípio, processamento remoto algum. Khomenko e Babichev [6] propõem uma arquitetura modular de IoT para monitoramento e controle de ambientes de escritório baseada no Home Assistant, defendendo que decisões de controle com requisitos de tempo real ou de disponibilidade — como o disparo de um agendamento programado — devem ser executadas localmente, reservando a nuvem para funções que toleram atraso ou indisponibilidade, como sincronização de configuração, histórico e notificações. Essa separação de responsabilidades orienta diretamente o projeto do agendador do Cafey, descrito na Seção 3.4, que executa localmente e independe da nuvem no momento do disparo.

## 2.6 Arquitetura de software embarcado orientada a eventos

Samek [11] descreve o padrão Active Object como forma de estruturar sistemas embarcados orientados a eventos: cada componente do sistema é modelado como uma máquina de estados independente, com sua própria fila de mensagens e sua própria tarefa de execução, comunicando-se com os demais componentes exclusivamente por troca de mensagens assíncronas, sem memória compartilhada. Esse modelo evita boa parte das condições de corrida comuns em sistemas embarcados multitarefa e torna explícito o protocolo de interação entre componentes, o que facilita tanto o teste unitário de cada máquina de estados isoladamente quanto o raciocínio sobre concorrência do sistema como um todo. O firmware do Cafey adota esse padrão diretamente, conforme detalhado na Seção 3.4.

# 3. Materiais e Métodos

## 3.1 Critérios de seleção do aparelho-alvo

O aparelho-alvo escolhido para o desenvolvimento e a validação do protótipo foi uma cafeteira elétrica Britânia CP30, com potência nominal de 800 W em 127 V (corrente aproximada de 6,3 A) e chave mecânica de acionamento com trava (modelo de chave KCD1-106). O critério decisivo de seleção, coerente com a discussão da Seção 2.1, foi a natureza do acionamento: aparelhos com chave eletrônica não retomam o funcionamento automaticamente ao serem reenergizados, exigindo um comando ativo do usuário mesmo após a energia ser restabelecida, o que inviabiliza o controle por corte e retomada de fase. Já um aparelho de chave mecânica com trava permanece fisicamente na posição ligada até que o usuário a desligue manualmente; nesse caso, quem decide se a cafeteira liga ou desliga é o relé do módulo Cafey, e não a chave do aparelho.

A interface elétrica entre o módulo e a cafeteira segue o padrão brasileiro de plugues e tomadas NBR 14136: o módulo Cafey recebe a alimentação da rede por um cabo com plugue macho NBR 14136 e fornece energia à cafeteira por meio de uma tomada fêmea do mesmo padrão embutida em seu invólucro, preservando a compatibilidade do módulo com qualquer cafeteira de chave mecânica cuja corrente esteja dentro do limite suportado pelo relé.

## 3.2 Arquitetura do módulo: ESP32, relé, fonte AC-DC embarcada, LED, botão

O módulo é organizado em torno de um microcontrolador ESP32 (kit DevKit V1, 30 pinos), responsável por executar o firmware descrito na Seção 3.4 e por se comunicar com a rede Wi-Fi e com o aplicativo de controle. O acionamento da carga é feito por um módulo relé de dois canais (apenas um canal em uso), com acionamento em nível lógico baixo confirmado experimentalmente em bancada (Seção 3.6). A alimentação de todo o circuito de baixa tensão é obtida por uma fonte AC-DC embarcada de 5 V, dispensando fonte externa e mantendo o módulo autocontido dentro do próprio invólucro. Um LED RGB indica visualmente o estado do sistema (ocioso, ligado, erro de conectividade), e um botão físico momentâneo, de 16 mm, permite ligar ou desligar a cafeteira manualmente mesmo sem rede Wi-Fi, aplicativo ou nuvem disponíveis — uma via de controle local sempre disponível, coerente com a discussão de autonomia da Seção 2.5.

A Tabela 1 resume os principais componentes elétricos do módulo e suas especificações.

Tabela 1 – Componentes elétricos do módulo Cafey

| Componente | Modelo / especificação | Função |
| --- | --- | --- |
| Microcontrolador | ESP32 DevKit V1, 30 pinos | Controle geral, Wi-Fi, MQTT, agendamento local |
| Relé | Módulo relé 2 canais, acionamento em nível lógico baixo | Chaveamento da fase de alimentação da cafeteira |
| Fonte de alimentação | Conversor AC-DC embarcado, 5 V | Alimentação da eletrônica de baixa tensão |
| Indicador visual | LED RGB | Sinalização do estado do sistema |
| Controle manual | Botão momentâneo de 16 mm | Acionamento local independente de rede |
| Proteção | Fusível de 10 A, ação retardada | Proteção contra sobrecorrente no ramal de saída |

Fonte: autor (2026)

O invólucro do módulo é fisicamente segmentado em duas zonas — uma para os componentes de rede elétrica (127 V) e outra para a eletrônica de baixa tensão (5 V) — separadas por uma divisória interna, com uma única passagem de fios entre as duas regiões, conforme ilustrado na Figura 1.

<!-- arquivo: docs/docs_artigo/imagens_artigo/mechanical_open_top_all_parts.png -->

Figura 1 – Módulo Cafey com a tampa superior aberta, mostrando o arranjo interno de todos os componentes

Fonte: autor (2026)

## 3.3 Projeto elétrico e dimensionamento do relé

O dimensionamento do relé de acionamento segue a relação básica entre potência, tensão e corrente elétrica, I = P / V. Para a cafeteira-alvo (800 W, 127 V), a corrente nominal aproximada é de 6,3 A. Por se tratar de uma carga puramente resistiva (elemento de aquecimento), sem corrente de partida (*inrush*) relevante — ao contrário de cargas indutivas como motores —, adotou-se um relé com contatos especificados para 10 A, com margem de aproximadamente duas vezes sobre a corrente calculada. Em série com o relé, um fusível de 10 A de ação retardada protege o ramal de saída contra sobrecorrente, dimensionado entre a corrente nominal do aparelho e a capacidade dos contatos do relé.

Quanto ao isolamento elétrico entre o lado de rede (127 V) e o lado de baixa tensão (5 V) controlado pelo ESP32, o projeto original previa isolamento galvânico por meio do optoacoplador interno do módulo relé. A verificação da montagem física identificou, no entanto, que o jumper de fábrica do módulo relé (JD-VCC) compartilha a alimentação e o terra do lado de acionamento com o lado de controle quando ambos são alimentados pela mesma fonte — como ocorre neste projeto —, de modo que o optoacoplador deixa de prover isolamento galvânico efetivo nessa configuração. A separação elétrica entre os dois lados do circuito é garantida, então, apenas pela rigidez dielétrica dos contatos do relé, e não por isolamento galvânico. Essa correção é registrada aqui como parte dos resultados do processo de verificação da montagem (Seção 3.6), e reforça a recomendação de validação da montagem por profissional de eletricidade antes do primeiro uso com carga real, discutida como limitação na Seção 4.4.

## 3.4 Firmware: Active Objects, persistência em NVS, agendamento local

O firmware do ESP32 é estruturado segundo o padrão Active Object [11] (Seção 2.6), com três componentes independentes, cada um executando em sua própria tarefa e comunicando-se apenas por troca de mensagens: (i) *Cafeteira*, responsável pela máquina de estados do acionamento do relé, do LED e da leitura do botão físico; (ii) *Conectividade*, responsável pela conexão Wi-Fi, pelo cliente MQTT e pela fila de eventos pendentes de envio; e (iii) *Agendador*, responsável por manter a lista de agendamentos e disparar o acionamento da cafeteira no horário programado.

A persistência de dados usa o armazenamento não volátil (NVS, do inglês *Non-Volatile Storage*) do ESP32, protegido por mutex contra acesso concorrente. A lista de agendamentos é armazenada como um único bloco de dados com um prefixo de versão de esquema, permitindo até 16 agendamentos simultâneos e possibilitando a migração segura do formato armazenado em versões futuras do firmware. Um segundo armazenamento mantém uma fila circular de eventos de preparo (início e fim de cada ciclo da cafeteira), descartando o evento mais antigo em caso de sobrecarga do buffer — uma decisão de projeto deliberadamente simples, priorizando a robustez do dispositivo embarcado sobre a completude do histórico local, já que o histórico completo é mantido no backend.

O Agendador opera de forma local: uma vez sincronizado o relógio interno por NTP (*Network Time Protocol*), o disparo dos agendamentos programados independe de qualquer comunicação com a nuvem no momento da execução, conforme a discussão de autonomia local da Seção 2.5. Essa característica foi verificada por testes automatizados que simulam a passagem do tempo e o casamento de horário e dia da semana contra a lista de agendamentos persistida.

## 3.5 Contrato de tópicos MQTT e idempotência da lista de agendamentos

A comunicação entre o módulo Cafey e o backend usa o protocolo MQTT sobre uma conexão TLS autenticada por certificado X.509 por dispositivo, com qualidade de serviço (QoS) nível 1 — que garante a entrega de cada mensagem ao menos uma vez, mas admite a reentrega de mensagens já processadas — em todos os tópicos, conforme a fundamentação da Seção 2.4. O contrato de tópicos separa claramente as direções de comunicação: no sentido nuvem-para-dispositivo (*downlink*), o tópico de comando não é retido, enquanto o tópico de agendamentos é retido e sempre carrega a lista completa e mais recente de agendamentos, nunca operações incrementais; no sentido dispositivo-para-nuvem (*uplink*), tópicos distintos publicam o estado atual do dispositivo, os eventos de preparo e um sinal de saúde com mensagem de última vontade (*Last Will*), que sinaliza automaticamente a desconexão inesperada do dispositivo.

A escolha de sempre retransmitir a lista completa de agendamentos, em vez de operações incrementais de adicionar/remover, foi deliberada: como o protocolo MQTT com QoS 1 pode reentregar mensagens já processadas — por exemplo, ao reconectar após uma queda de rede, quando o broker reenvia a última mensagem retida do tópico de agendamentos —, o dispositivo precisa de um mecanismo para distinguir uma atualização legítima de uma reentrega de uma mensagem antiga. Esse mecanismo é um número de versão monotonicamente crescente associado à lista de agendamentos: o dispositivo só substitui a lista armazenada localmente se a versão recebida for estritamente maior que a versão já persistida, ignorando silenciosamente versões iguais ou menores. Esse comportamento foi verificado por testes automatizados que simulam explicitamente o cenário de reentrega de uma mensagem retida com versão desatualizada, confirmando que a lista local permanece inalterada nesses casos.

## 3.6 Procedimento de verificação em bancada

A verificação da montagem do módulo, até o presente estágio do projeto, combinou duas etapas complementares. A primeira foi a verificação do esquemático elétrico por meio de regras de projeto elétrico (ERC, do inglês *Electrical Rule Check*) e da conferência do netlist gerado a partir do esquemático, nó a nó, confirmando que as conexões elétricas correspondem ao projeto pretendido (detalhada na Seção 4.1). A segunda foi um teste de bancada com o ESP32, o módulo relé e uma carga simulada (LED em protoboard), seguindo a recomendação do próprio fabricante do módulo relé de usar uma carga de baixa potência durante o desenvolvimento por segurança; esse teste confirmou experimentalmente que o acionamento do relé ocorre em nível lógico baixo, e não em nível alto como constava inicialmente no esquemático — divergência corrigida a partir desse resultado, conforme descrito na Seção 3.3. A Figura 2 registra o arranjo desse teste de bancada.

<!-- arquivo: docs/docs_artigo/imagens_artigo/screenshot_teste_bancada_prototipo_embarcado_cafey.png -->

Figura 2 – Teste de bancada do módulo embarcado, com carga simulada em protoboard

Fonte: autor (2026). Vídeo do teste de bancada (10 s) disponível em: https://drive.google.com/file/d/1TAvuFmtWNe2M852NhJ34b9LJA3ZlqHY9/view?usp=drive_link. Acesso em: 16 set. 2026.

Como resultado parcial do processo de verificação, permanecem pendentes, neste estágio do projeto: a validação da montagem completa por profissional de eletricidade antes do primeiro uso com carga real; um teste térmico do invólucro impresso após um ciclo completo de preparo, para confirmar a adequação do material (PETG) à temperatura da base da cafeteira; e a verificação prática do acionamento do relé sob a carga real da cafeteira (800 W/127 V), ainda não realizada. Esses itens são retomados como limitações na Seção 4.4.

# 4. Resultados e Discussões

## 4.1 Esquemático consolidado e verificação por ERC/netlist

O esquemático elétrico consolidado do módulo foi desenvolvido em uma única folha no software KiCad, reunindo o microcontrolador ESP32, o módulo relé, a fonte AC-DC, o fusível de proteção, o LED indicador, o botão de acionamento manual e os conectores de entrada e saída de energia no padrão NBR 14136. A verificação por regras de projeto elétrico (ERC) não identificou nenhum erro; os avisos remanescentes gerados pela ferramenta foram integralmente revisados e correspondem a situações elétricas esperadas e documentadas — como pinos de uso geral do microcontrolador intencionalmente não conectados —, e não a falhas de projeto. A conferência complementar do netlist exportado, nó a nó, confirmou que as conexões elétricas do esquemático correspondem à intenção de projeto descrita na Seção 3.3. O leiaute de placa de circuito impresso e a atribuição de footprints aos componentes ainda não foram realizados neste estágio do projeto, que permanece na etapa de esquemático validado.

## 4.2 Acionamento da carga por comando do ESP32

O acionamento do relé por comando de software do ESP32 foi confirmado em bancada com uma carga simulada de baixa potência (Seção 3.6), demonstrando a correção lógica da cadeia de acionamento entre o firmware, o sinal de controle do relé e a comutação dos contatos. Até o presente estágio do projeto, contudo, não foi realizado o teste de acionamento sob a carga real da cafeteira (800 W/127 V): esse é um resultado parcial, e a validação com carga real — precedida de revisão por profissional de eletricidade, dado o dimensionamento com carga próxima ao limite adotado (Seção 3.3) — é o próximo passo planejado antes de qualquer uso do módulo fora do ambiente de bancada.

## 4.3 Comparação com tomadas inteligentes comerciais

Diferentemente de uma tomada inteligente comercial genérica, que assume implicitamente que o aparelho controlado permanece em um estado elétrico previsível entre os ciclos de chaveamento — premissa que Santos et al. [2] mostram ser frágil mesmo em contextos de pesquisa controlados —, o Cafey é projetado especificamente para o caso de um aparelho com chave mecânica travada, tratando explicitamente a interação entre o estado físico da chave e o estado do relé como parte do problema de projeto, e não como uma limitação a ser contornada pelo usuário. Muñoz et al. [9], ao descrever um medidor inteligente com controle de carga para gestão de energia residencial, também chaveiam a fase de alimentação da carga controlada, mas sem tratar a hipótese de uma interface mecânica interposta pelo próprio usuário — hipótese central no projeto do Cafey.

O Quadro 1 resume qualitativamente as diferenças entre o Cafey e uma tomada inteligente comercial genérica.

Quadro 1 – Comparação qualitativa entre o Cafey e uma tomada inteligente comercial genérica

| Aspecto | Tomada inteligente genérica | Cafey |
| --- | --- | --- |
| Compatibilidade com chave mecânica travada | Não trata o estado da chave do aparelho | Projetado especificamente para esse caso |
| Controle manual sem rede/aplicativo | Geralmente ausente ou limitado ao próprio corpo da tomada | Botão físico dedicado no módulo |
| Agendamento sem conectividade | Depende, em geral, da nuvem do fabricante | Executado localmente, sincronizado por NTP |
| Especificidade ao aparelho-alvo | Genérica, para qualquer carga | Dimensionada para a cafeteira-alvo (Seção 3.3) |
| Abertura do projeto | Fechado, proprietário | Hardware e firmware documentados neste trabalho |

Fonte: autor (2026)

## 4.4 Limitações e ameaças à validade

O módulo Cafey, tal como projetado, apenas chaveia a energia da cafeteira e não possui qualquer sensor de corrente, temperatura ou nível de água; o sistema reporta, portanto, o acionamento do relé, e não a conclusão efetiva do preparo do café. Em consequência, uma eventual queda de energia durante o preparo não gera um evento de falha distinguível de um preparo concluído normalmente — limitação declarada de projeto, e não uma lacuna de implementação a ser corrigida.

A alegação inicial de isolamento galvânico entre o lado de rede e o lado de controle do circuito, obtida por meio do optoacoplador do módulo relé, mostrou-se incorreta na configuração física adotada, conforme descrito na Seção 3.3; a separação elétrica atual depende exclusivamente da rigidez dielétrica dos contatos do relé, o que reforça a necessidade de validação da montagem por profissional de eletricidade antes de qualquer uso com carga real.

Duas hipóteses de engenharia do invólucro mecânico permanecem sem validação experimental neste estágio: a adequação do material termoplástico do invólucro (PETG) à temperatura da base da cafeteira após um ciclo completo de preparo, e a estimativa de deflexão estrutural sob carga, obtida analiticamente e não por ensaio físico ou simulação numérica. Por fim, como discutido nas Seções 3.6 e 4.2, o acionamento do relé foi verificado apenas com carga simulada de baixa potência, e não com a carga real da cafeteira-alvo; os resultados apresentados neste artigo devem ser lidos como parciais, descrevendo o estado do projeto até o momento da submissão, sem prejuízo da continuidade do trabalho.

# 5. Considerações Finais

Este artigo apresentou o Cafey, um módulo IoT retrofit que adiciona agendamento e controle remoto a cafeteiras de chave mecânica travada, sem exigir a substituição do aparelho nem qualquer modificação em seu interior. A proposta combina um projeto elétrico dimensionado e verificado por regras de projeto (ERC), um firmware embarcado estruturado segundo o padrão Active Object com agendamento local persistido e sincronizado por NTP, e um contrato de comunicação MQTT idempotente, tolerante a reconexões e a reentrega de mensagens retidas.

Os resultados apresentados são parciais: a verificação elétrica do esquemático e o acionamento do relé por comando do ESP32 foram confirmados em bancada com carga simulada, mas a validação com a carga real da cafeteira-alvo, precedida de revisão por profissional de eletricidade, ainda está pendente, assim como testes térmicos e estruturais do invólucro mecânico. Como trabalho futuro, pretende-se concluir essas validações pendentes, avançar do esquemático para o leiaute de placa de circuito impresso, e ampliar os testes de campo do dispositivo em uso doméstico real.

# Referências

1. EGARTER, D.; MONACCHI, A.; KHATIB, T.; ELMENREICH, W. Integration of Legacy Appliances into Home Energy Management Systems. arXiv:1406.3252, 2014.
2. SANTOS, A.; DUGGAN, G. P.; DAVIS, J.; ZIMMERLE, D. A cautionary note on using smart plugs for research data acquisition. e-Prime — Advances in Electrical Engineering, Electronics and Energy, v. 4, art. 100137, 2023. DOI 10.1016/j.prime.2023.100137
3. BOVEA, M. D.; IBÁÑEZ-FORÉS, V.; PÉREZ-BELIS, V. Repair vs. replacement: selection of the best end-of-life scenario for small household electric and electronic equipment based on life cycle assessment. Journal of Environmental Management, v. 254, art. 109679, 2020. DOI 10.1016/j.jenvman.2019.109679
4. RUSSO, D.; MIORI, V.; TOLOMEI, G.; BELLI, D. Home automation interoperability: two decades of lessons learned and future prospects into the development of IoT ecosystems. Internet of Things, v. 37, art. 101906, 2026. DOI 10.1016/j.iot.2026.101906
5. MADADI-BAROUGH, S.; RUIZ-BLANCO, P.; LIN, J.; VIDAL, R.; GOMEZ, C. Matter: IoT Interoperability for Smart Homes. arXiv:2405.01618, 2024. Universitat Politècnica de Catalunya.
6. KHOMENKO, Y.; BABICHEV, S. Modular IoT Architecture for Monitoring and Control of Office Environments Based on Home Assistant. IoT (MDPI), v. 6, n. 4, art. 69, 2025. DOI 10.3390/iot6040069
7. BAYILMIŞ, C.; EBLEME, M. A.; ÇAVUŞOĞLU, Ü.; KÜÇÜK, K.; SEVİN, A. A survey on communication protocols and performance evaluations for Internet of Things. Digital Communications and Networks, v. 8, n. 6, p. 1094–1104, 2022.
8. AKSHATHA, P. S.; DILIP KUMAR, S. M.; VENUGOPAL, K. R. MQTT Implementations, Open Issues, and Challenges: A Detailed Comparison and Survey. International Journal of Sensors, Wireless Communications and Control, v. 12, n. 8, p. 553–576, 2022. DOI 10.2174/2210327913666221216152446
9. MUÑOZ, O.; RUELAS, A.; ROSALES, P.; ACUÑA, A.; SUASTEGUI, A.; LARA, F. Design and Development of an IoT Smart Meter with Load Control for Home Energy Management Systems. Sensors, v. 22, n. 19, art. 7536, 2022. DOI 10.3390/s22197536
10. GENTILE, A. F.; MACRÌ, D.; CARNÌ, D. L.; GRECO, E.; LAMONACA, F. A Performance Analysis of Security Protocols for Distributed Measurement Systems Based on Internet of Things with Constrained Hardware and Open Source Infrastructures. Sensors, v. 24, n. 9, art. 2781, 2024. DOI 10.3390/s24092781
11. SAMEK, M. Practical UML Statecharts in C/C++: Event-Driven Programming for Embedded Systems. 2. ed. Oxford: Newnes/Elsevier, 2008. 728 p. ISBN 978-0-7506-8706-5.
12. SLADE, G. Made to Break: Technology and Obsolescence in America. Cambridge, MA: Harvard University Press, 2006. 330 p. ISBN 978-0-674-02203-4.

# Declarações finais

"Os conteúdos expressos no trabalho, assim como os direitos autorais de figuras e dados, bem como sua revisão ortográfica e das normas são de inteira responsabilidade do(s) autor(es)."

"O(s) autor(es) do trabalho declara(m) que durante a preparação do manuscrito foi(foram) utilizado(as) a(s) ferramenta(s)/serviço(s) Claude Code (Anthropic) de Inteligência Artificial (IA) para a redação e a formatação do manuscrito a partir da documentação técnica do projeto. Após utilizar esta ferramenta/serviço, os autores editaram e revisaram o conteúdo conforme necessário e assumem total responsabilidade pelo conteúdo da publicação."

---

## Observações para a revisão do autor (não fazem parte do artigo)

1. ~~Preencher o nome completo do autor principal~~ — feito (Vitor Tavares Chaves).
2. **Conferir a contagem de palavras** dos três resumos (meta: até 180 palavras cada) — já conferido: PT 171, EN 175, ES 179 palavras. O resumo em PT teve um pequeno ajuste de redação após a revisão do autor; reconferir a contagem antes da formatação final.
3. ~~Nível de transparência sobre resultados parciais/correção de projeto~~ — confirmado pelo autor, manter como está.
4. ~~Definir as imagens das Figuras 1 e 2~~ — definido: Figura 1 = `mechanical_open_top_all_parts.png` (3.2); Figura 2 = `screenshot_teste_bancada_prototipo_embarcado_cafey.png` (3.6), com nota de rodapé apontando o vídeo de 10s do mesmo teste no link do Drive. Os arquivos de origem estão indicados em comentários HTML acima de cada legenda, para uso na etapa de formatação em .docx.
5. ~~Escolher um título mais científico~~ — feito: "Integração de eletrodomésticos legados de chave mecânica travada em sistemas de automação residencial via retrofit IoT" (e traduções EN/ES). O nome do projeto ("Cafey") permanece usado no corpo do texto para nomear o protótipo, apenas fora do título.
6. Título, resumo e palavras-chave já estão nos três idiomas exigidos (PT/EN/ES); o corpo do texto permanece integralmente em português, conforme permitido pelo template.

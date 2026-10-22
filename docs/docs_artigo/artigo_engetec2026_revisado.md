# Retrofit IoT para automação de eletrodomésticos legados com chave mecânica de retenção: projeto e validação de um módulo para cafeteiras

**IoT Retrofit for Automating Legacy Appliances with Retained Mechanical Switches: Design and Validation of a Coffee-Maker Module**

**Retrofit IoT para la automatización de electrodomésticos legados con interruptor mecánico de retención: diseño y validación de un módulo para cafeteras**

Vitor Tavares Chaves — vitor.chaves@aluno.cps.sp.gov.br  
Jeferson de Souza Dias — jeferson.dias01@cps.sp.gov.br

**Palavras-chave:** Internet das Coisas; Retrofit; Automação Residencial; Eletrodomésticos Legados

**Keywords:** Internet of Things; Retrofit; Home Automation; Legacy Appliances

**Palabras clave:** Internet de las Cosas; Retrofit; Automatización del Hogar; Electrodomésticos Legados

## Resumo

Cafeteiras elétricas com chave mecânica de retenção não ligam automaticamente quando a alimentação retorna e a chave física está desligada. Este trabalho avalia um módulo de retrofit que automatiza esse tipo de aparelho sem modificar sua estrutura elétrica interna. O Cafey, baseado em ESP32, é instalado entre a rede elétrica e a cafeteira e controla a alimentação por relé, com a chave física mantida ligada. O firmware segue o padrão Active Object, persiste agendamentos em memória não volátil e os executa localmente após sincronizar o relógio por NTP. A comunicação com o backend usa MQTT sobre TLS, QoS 1 e uma lista versionada de agendamentos, tornando as atualizações idempotentes diante de reconexões e reentregas. A validação é parcial: o esquemático foi verificado, o relé foi acionado em bancada com carga simulada e testes automatizados cobriram agendamentos e versões repetidas ou desatualizadas. Permanecem necessários ensaios com carga real de 800 W/127 V, validação térmica e estrutural do invólucro e revisão profissional de segurança elétrica antes de uso residencial.

## Abstract

Electric coffee makers with retained mechanical switches do not turn on automatically when power returns if the physical switch is off. This study evaluates a retrofit module that automates this appliance class without modifying its internal electrical structure. Cafey, based on an ESP32, is installed between the mains and the coffee maker and switches its power through a relay while the physical switch remains on. The firmware follows the Active Object pattern, stores schedules in non-volatile memory, and runs them locally after NTP clock synchronization. Backend communication uses MQTT over TLS, QoS 1, and a versioned schedule list to keep updates idempotent across reconnections and message redelivery. Validation is partial: the schematic was checked, relay actuation was confirmed on a bench with a simulated load, and automated tests covered schedules and repeated or stale versions. Tests with the real 800 W/127 V load, thermal and structural enclosure validation, and a professional electrical safety review remain necessary before residential use.

## Resumen

Las cafeteras eléctricas con interruptor mecánico de retención no se encienden automáticamente cuando vuelve la alimentación si el interruptor está apagado. Este trabajo evalúa un módulo de retrofit que automatiza este tipo de aparato sin modificar su estructura eléctrica interna. Cafey, basado en ESP32, se instala entre la red eléctrica y la cafetera y conmuta su alimentación mediante un relé, con el interruptor físico encendido. El firmware sigue el patrón Active Object, guarda las programaciones en memoria no volátil y las ejecuta localmente después de sincronizar el reloj mediante NTP. La comunicación con el backend utiliza MQTT sobre TLS, QoS 1 y una lista versionada de programaciones, para que las actualizaciones sean idempotentes ante reconexiones y reentregas. La validación es parcial: se verificó el esquema, se accionó el relé en bancada con carga simulada y pruebas automatizadas cubrieron programaciones y versiones repetidas u obsoletas. Antes del uso residencial aún se requieren ensayos con carga real de 800 W/127 V, validación térmica y estructural de la carcasa y revisión profesional de seguridad eléctrica.

## 1. Introdução

Cafeteiras elétricas com chave mecânica de retenção constituem um caso particular de integração de aparelhos legados a sistemas de automação residencial. Em equipamentos desse tipo, a função de ligar e desligar é determinada por uma interface física que permanece em uma posição até nova intervenção do usuário. Consequentemente, adicionar conectividade ao aparelho não é apenas uma questão de controlar remotamente sua alimentação elétrica.

Quando se deseja adicionar automação a um equipamento funcional desse tipo, uma alternativa é substituí-lo por um modelo que já disponha de conectividade e programação. Sob a ótica do ciclo de vida do produto, porém, a substituição de um equipamento ainda funcional por outro que ofereça uma funcionalidade adicional não é necessariamente a única alternativa. Estudos de avaliação de ciclo de vida sobre pequenos equipamentos elétricos e eletrônicos indicam que o reparo e o prolongamento da vida útil podem ser preferíveis à substituição precoce em determinados cenários (Bovea; Ibáñez-Forés; Pérez-Belis, 2020). Neste trabalho, esse resultado é utilizado como motivação para investigar o retrofit como alternativa de integração, e não como demonstração de que o módulo proposto produz, por si só, um benefício ambiental quantificado.

Uma alternativa intermediária é o uso de tomadas inteligentes genéricas, que controlam remotamente o fornecimento de energia. Para aparelhos cujo funcionamento é determinado apenas pela presença de alimentação, essa estratégia pode ser suficiente. Em uma cafeteira com chave mecânica de retenção, contudo, a situação é diferente: se a chave estiver fisicamente na posição desligada, o restabelecimento da alimentação não liga o aparelho; além disso, uma intervenção manual do usuário pode fazer com que o estado lógico informado pelo sistema deixe de corresponder ao estado físico da chave. A literatura sobre integração de aparelhos legados e sobre as limitações de tomadas inteligentes em aquisição de dados sustenta a necessidade de considerar explicitamente essa interação entre o aparelho e o mecanismo de chaveamento (Egarter et al., 2014; Santos et al., 2023).

A partir desse problema, este trabalho investiga a seguinte pergunta de pesquisa:

> **É possível implementar um módulo de retrofit IoT externo capaz de automatizar uma cafeteira com chave mecânica de retenção, mantendo o acionamento local funcional e permitindo a execução autônoma de agendamentos durante indisponibilidade de conectividade?**

O objetivo geral é desenvolver e avaliar um módulo eletrônico de retrofit que adicione agendamento e controle remoto a uma cafeteira com chave mecânica de retenção sem modificar sua estrutura elétrica interna.

São objetivos específicos:

1. projetar a arquitetura elétrica e de controle do módulo;
2. dimensionar e documentar o circuito de chaveamento da carga;
3. implementar controle local por ESP32, incluindo acionamento manual e agendamento;
4. persistir os agendamentos em memória não volátil;
5. implementar sincronização de configuração e eventos por MQTT sobre TLS;
6. definir um mecanismo de versionamento que torne a atualização da lista de agendamentos idempotente diante de reentregas;
7. verificar o esquemático e o funcionamento lógico do acionamento em bancada;
8. explicitar as limitações elétricas, térmicas, estruturais e funcionais que ainda impedem a caracterização do protótipo como produto pronto para uso residencial.

As contribuições do trabalho são, portanto, apresentadas em duas camadas. A primeira é uma arquitetura de hardware e firmware para retrofit de aparelhos cuja interface de acionamento permanece fisicamente retida, demonstrada em uma cafeteira específica. A segunda é um contrato de comunicação MQTT para sincronização de agendamentos baseado em substituição da lista completa e versionamento monotônico, com comportamento idempotente diante de mensagens repetidas ou desatualizadas.

O texto está organizado da seguinte forma: a Seção 2 revisa a fundamentação teórica sobre integração de aparelhos legados, prolongamento de vida útil, interoperabilidade em automação residencial, comunicação em dispositivos restritos, autonomia local e arquitetura de software orientada a eventos; a Seção 3 descreve os materiais e métodos empregados no desenvolvimento e na validação do módulo; a Seção 4 apresenta os resultados obtidos, a comparação qualitativa com tomadas inteligentes e as limitações e ameaças à validade; e a Seção 5 apresenta as considerações finais.

## 2. Fundamentação Teórica

### 2.1 Integração de aparelhos legados e limites do chaveamento de fase

Sistemas de gestão de energia residencial frequentemente precisam incorporar aparelhos que não foram projetados para conectividade. Egarter et al. (2014) discutem estratégias de integração de aparelhos legados a sistemas de gestão de energia doméstica, destacando que o chaveamento simples da fase de alimentação pode ser suficiente quando o aparelho não depende da preservação de uma interface física específica, mas é insuficiente quando o funcionamento depende de uma interface cujo estado precisa ser mantido.

Esse é o caso estudado neste trabalho. Em uma cafeteira com chave mecânica de retenção, o chaveamento da fase só produz o acionamento quando a chave física já se encontra na posição ligada. O problema de automação, portanto, não se resume ao fornecimento de energia: é necessário estabelecer uma relação controlada entre a posição da chave física e o estado do elemento de chaveamento externo.

Santos et al. (2023) reforçam essa questão a partir de outro contexto. Ao avaliar o uso de tomadas inteligentes comerciais para aquisição de dados em pesquisa, os autores discutem limitações relacionadas às hipóteses feitas sobre o estado do equipamento conectado e às intervenções manuais que podem alterar esse estado. No contexto deste trabalho, essa observação é relevante porque uma chave mecânica permite uma intervenção física independente do software de automação.

### 2.2 Prolongamento de vida útil como alternativa à substituição

Do ponto de vista de ciclo de vida do produto, Bovea, Ibáñez-Forés e Pérez-Belis (2020) comparam cenários de reparo e substituição para pequenos equipamentos elétricos e eletrônicos por meio de avaliação de ciclo de vida, mostrando que o reparo pode apresentar vantagens ambientais em determinados cenários, especialmente quando o equipamento ainda cumpre adequadamente sua função primária.

Slade (2006), em uma análise histórica da obsolescência tecnológica nos Estados Unidos, discute mecanismos de obsolescência planejada e percebida e sua relação com a substituição de produtos ainda funcionais. Neste trabalho, essas referências são utilizadas para contextualizar o retrofit como estratégia potencial de prolongamento da vida útil, e não para atribuir ao Cafey um impacto ambiental específico que não foi medido.

Assim, a motivação ambiental do retrofit deve ser entendida como uma hipótese de contexto: adicionar uma funcionalidade a um equipamento existente pode evitar sua substituição, mas a vantagem ambiental efetiva da solução depende de fatores como materiais empregados, consumo energético, vida útil adicional e destino do equipamento, não sendo quantificada neste estudo.

### 2.3 Fragmentação e interoperabilidade em automação residencial

O ecossistema de automação residencial é historicamente fragmentado, com diferentes protocolos, plataformas e ecossistemas de fabricantes. Russo et al. (2026) revisam duas décadas de tentativas de padronização e discutem que a interoperabilidade envolve não apenas protocolos de comunicação, mas também a diversidade de dispositivos e arquiteturas já instalados.

Madadi-Barough et al. (2024) descrevem o Matter como uma iniciativa de padronização para interoperabilidade em casas inteligentes. Para aparelhos que originalmente não possuem conectividade, entretanto, a adoção de um padrão de comunicação não elimina a necessidade de um componente intermediário capaz de conferir ao equipamento uma interface de controle. Essa distinção define o espaço de projeto considerado pelo Cafey: o retrofit atua na camada física e de controle do aparelho, enquanto protocolos de interoperabilidade podem atuar na comunicação entre componentes que já possuem capacidade de conectividade.

### 2.4 Comunicação em dispositivos restritos: MQTT, QoS e TLS

Dispositivos embarcados de baixo custo, como o ESP32 utilizado neste projeto, possuem restrições de processamento, memória e recursos que orientam a escolha dos protocolos de comunicação. Bayılmış et al. (2022) comparam protocolos de comunicação para Internet das Coisas (IoT, do inglês *Internet of Things*) e apresentam o MQTT (*Message Queuing Telemetry Transport*) como uma alternativa adequada para diversos cenários de comunicação entre dispositivos devido ao seu modelo *publish/subscribe* e aos níveis configuráveis de qualidade de serviço.

Akshatha, Dilip Kumar e Venugopal (2022) discutem implementações e desafios do MQTT, incluindo o QoS 1, que fornece entrega de mensagens ao menos uma vez, mas admite reentrega de mensagens já processadas. Essa característica é relevante para o Cafey porque uma atualização de configuração não pode produzir efeitos diferentes quando recebida mais de uma vez.

Gentile et al. (2024) avaliam o uso de protocolos de segurança em sistemas IoT com hardware restrito e discutem a viabilidade do TLS com autenticação por certificado em microcontroladores de baixo custo. Essas referências fundamentam a escolha de MQTT sobre TLS com QoS 1 adotada no Cafey e, principalmente, a necessidade de tratar a semântica de duplicação no nível do protocolo de aplicação.

É importante distinguir as duas propriedades: **QoS 1 não torna uma operação idempotente**. Ele define uma semântica de entrega que admite duplicação. A idempotência apresentada neste trabalho é obtida pelo contrato de aplicação implementado sobre MQTT, por meio de versionamento monotônico da lista de agendamentos.

### 2.5 Autonomia local e dependência de nuvem

Sistemas de automação residencial que dependem integralmente de serviços remotos podem perder funções durante quedas de conectividade, mesmo quando determinadas tarefas não exigem processamento remoto. Khomenko e Babichev (2025) discutem uma arquitetura modular de IoT para monitoramento e controle de ambientes e defendem a execução local de funções que possuem requisitos de disponibilidade, reservando serviços remotos para tarefas como sincronização, histórico e notificações.

Essa separação orienta diretamente o projeto do Cafey. O agendador é executado localmente e não depende da nuvem no momento do disparo. A conectividade é utilizada para sincronizar configurações, publicar eventos e manter a comunicação com o sistema externo. Dessa forma, a perda temporária da rede não elimina a capacidade previamente configurada de executar um agendamento.

### 2.6 Arquitetura de software embarcado orientada a eventos

Samek (2008) descreve o padrão Active Object como uma forma de estruturar sistemas embarcados orientados a eventos, na qual componentes independentes possuem suas próprias filas e tarefas de execução e interagem por meio de mensagens assíncronas. O modelo reduz a necessidade de memória compartilhada entre componentes e torna explícitas as interfaces de comunicação.

O firmware do Cafey adota esse padrão diretamente. A separação em máquinas de estado para acionamento, conectividade e agendamento permite isolar responsabilidades e facilita a verificação das regras de cada componente. A persistência em NVS constitui uma exceção importante ao modelo puramente baseado em troca de mensagens: o acesso ao armazenamento é um recurso compartilhado da infraestrutura do dispositivo e, por isso, utiliza mecanismo de exclusão mútua próprio da camada de persistência. O mutex não é utilizado como mecanismo de comunicação entre os Active Objects, mas para proteger o recurso persistente contra acessos concorrentes.

## 3. Materiais e Métodos

### 3.1 Critérios de seleção do aparelho-alvo

O aparelho-alvo escolhido para o desenvolvimento e a validação do protótipo foi uma cafeteira elétrica Britânia CP30, com potência nominal de 800 W em 127 V, corrente nominal aproximada de 6,3 A e chave mecânica de acionamento com retenção, modelo KCD1-106.

O critério decisivo de seleção foi a natureza do acionamento. O projeto considera aparelhos nos quais o estado físico da chave permanece após a retirada da alimentação. Nesse cenário, o aparelho pode ser mantido com a chave na posição ligada, enquanto o módulo externo controla o fornecimento de energia por meio de um relé. A seleção da CP30 constitui, portanto, um caso de demonstração de uma classe de problema, e não uma evidência experimental de compatibilidade com qualquer eletrodoméstico com chave mecânica.

A interface elétrica entre o módulo e a cafeteira segue o padrão brasileiro de plugues e tomadas NBR 14136: o módulo recebe a alimentação da rede por um cabo com plugue macho e fornece energia à cafeteira por meio de uma tomada fêmea do mesmo padrão embutida em seu invólucro. A compatibilidade prática com outros aparelhos depende, entretanto, do dimensionamento elétrico, mecânico e térmico do módulo e não é estabelecida experimentalmente neste trabalho.

### 3.2 Arquitetura do módulo: ESP32, relé, fonte AC-DC embarcada, LED e botão

O módulo é organizado em torno de um microcontrolador ESP32, kit DevKit V1 de 30 pinos, responsável pelo firmware, comunicação Wi-Fi, execução do agendador e controle do relé. O acionamento da carga é realizado por um módulo relé de dois canais, com apenas um canal utilizado. O acionamento em nível lógico baixo foi confirmado experimentalmente em bancada.

A alimentação da eletrônica de baixa tensão é obtida por uma fonte AC-DC embarcada de 5 V. Um LED RGB fornece indicação visual do estado do sistema, e um botão físico momentâneo permite o acionamento manual da cafeteira independentemente da disponibilidade de rede, aplicativo ou nuvem.

A Tabela 1 resume os principais componentes.

**Tabela 1 – Componentes elétricos do módulo Cafey**

| Componente | Modelo / especificação | Função |
|---|---|---|
| Microcontrolador | ESP32 DevKit V1, 30 pinos | Controle geral, Wi-Fi, MQTT e agendamento local |
| Relé | Módulo relé de 2 canais, acionamento em nível lógico baixo | Chaveamento da fase de alimentação |
| Fonte de alimentação | Conversor AC-DC embarcado, 5 V | Alimentação da eletrônica de baixa tensão |
| Indicador visual | LED RGB | Sinalização do estado do sistema |
| Controle manual | Botão momentâneo de 16 mm | Acionamento local independente da rede |
| Proteção | Fusível de 10 A, ação retardada | Proteção contra sobrecorrente do ramal de saída |

*Fonte: autor (2026).*

O invólucro é fisicamente segmentado em duas zonas: uma destinada aos componentes conectados à rede elétrica de 127 V e outra destinada à eletrônica de baixa tensão. Uma divisória interna separa as duas regiões. Essa separação é uma característica construtiva do protótipo, mas não deve ser interpretada, isoladamente, como certificação de segurança elétrica.

**Figura 1 – Módulo Cafey com a tampa superior aberta, mostrando o arranjo interno dos componentes**

*Fonte: autor (2026).*

### 3.3 Projeto elétrico e dimensionamento do relé

O dimensionamento preliminar do circuito parte da relação entre potência, tensão e corrente:

\[
I = \frac{P}{V}
\]

Para a cafeteira-alvo:

\[
I = \frac{800}{127} \approx 6,3\ A
\]

Como o elemento de aquecimento constitui predominantemente uma carga resistiva, foi adotado, para o protótipo, um relé com contatos especificados para 10 A. Essa escolha constitui um dimensionamento preliminar baseado na corrente nominal calculada, mas não substitui a avaliação das demais características necessárias à segurança do conjunto, como categoria de utilização, capacidade de interrupção, aquecimento, qualidade dos contatos, seção dos condutores, proteção contra contato acidental e adequação dos componentes à aplicação em rede elétrica.

Foi empregado um fusível de 10 A de ação retardada no ramal de saída. O valor adotado deve ser entendido como parte do protótipo e não como comprovação de que essa proteção é a configuração definitiva para uso residencial. A seleção final de proteção deve considerar as características do circuito, dos condutores, do relé e da carga e deve ser validada antes de qualquer uso com a carga real.

Quanto à separação entre a rede de 127 V e a eletrônica de 5 V, o projeto original previa isolamento galvânico por meio do optoacoplador presente no módulo relé. A inspeção da montagem física identificou, contudo, que a configuração de alimentação adotada mantém a interligação prevista pelo módulo entre os lados de controle e acionamento, de modo que o optoacoplador não deve ser considerado, nessa configuração, como garantia de isolamento galvânico do conjunto. A separação entre os circuitos depende da isolação proporcionada pelos componentes e, principalmente, da rigidez dielétrica dos contatos do relé e da construção física do conjunto.

Essa constatação foi incorporada ao projeto como resultado do processo de verificação e como limitação de segurança. Antes de qualquer conexão da carga real, a montagem elétrica completa deve ser revisada por profissional habilitado, e o protótipo não deve ser tratado como equipamento certificado ou pronto para uso residencial.

### 3.4 Firmware: Active Objects, persistência em NVS e agendamento local

O firmware do ESP32 é estruturado segundo o padrão Active Object (Samek, 2008), com três componentes independentes, cada um executando em sua própria tarefa e comunicando-se por troca de mensagens:

1. **Cafeteira:** responsável pela máquina de estados do acionamento do relé, pela sinalização do LED e pela leitura do botão físico;
2. **Conectividade:** responsável pela conexão Wi-Fi, pelo cliente MQTT e pela fila de eventos pendentes de envio;
3. **Agendador:** responsável por manter a lista de agendamentos e disparar o acionamento da cafeteira nos horários programados.

A persistência utiliza o armazenamento não volátil (NVS, do inglês *Non-Volatile Storage*) do ESP32. O acesso ao recurso persistente é protegido por mutex na camada de armazenamento para evitar acessos concorrentes, sem alterar o modelo de comunicação baseado em mensagens utilizado entre os Active Objects.

A lista de agendamentos é armazenada como um único bloco de dados com um prefixo de versão de esquema. O protótipo suporta até 16 agendamentos simultâneos, limite adotado para manter previsível o tamanho do conjunto persistido e da mensagem de sincronização. A inclusão de uma versão de esquema permite que futuras versões do firmware identifiquem formatos incompatíveis e realizem migração controlada.

Um segundo armazenamento mantém uma fila circular de eventos de preparo, registrando início e fim de cada ciclo. Em caso de sobrecarga, o evento mais antigo é descartado. Essa decisão prioriza a previsibilidade do armazenamento embarcado e não representa uma tentativa de manter o histórico completo no dispositivo, uma vez que o backend é responsável pelo histórico de longo prazo.

O Agendador opera localmente. Uma vez sincronizado o relógio por NTP, o disparo dos agendamentos independe da comunicação com a nuvem no momento da execução. Essa característica foi verificada por testes automatizados que simulam a passagem do tempo e o casamento entre horário, dia da semana e lista persistida.

### 3.5 Contrato de tópicos MQTT e idempotência da lista de agendamentos

A comunicação entre o módulo Cafey e o backend utiliza MQTT sobre TLS com autenticação por certificado X.509 por dispositivo e QoS 1. O QoS 1 admite reentrega de mensagens já processadas; por isso, a semântica de atualização da configuração é definida no nível do protocolo de aplicação.

No sentido nuvem-dispositivo (*downlink*), o tópico de comando não é retido, enquanto o tópico de agendamentos é retido e contém sempre a lista completa e mais recente de agendamentos. No sentido dispositivo-nuvem (*uplink*), tópicos distintos publicam o estado atual do dispositivo, os eventos de preparo e um sinal de saúde associado à mensagem de última vontade (*Last Will*).

A escolha de transmitir a lista completa, em vez de operações incrementais de adicionar e remover, reduz o estado implícito necessário para reconstruir a configuração. Cada lista recebe um número de versão monotonicamente crescente, persistido juntamente com a configuração local.

A regra de atualização é:

| Relação entre versões | Ação do dispositivo |
|---|---|
| `v_recebida > v_local` | Substitui a lista local e persiste a nova versão |
| `v_recebida = v_local` | Ignora a atualização, pois representa estado já aplicado |
| `v_recebida < v_local` | Ignora a atualização, pois representa estado obsoleto |

Assim, a operação de aplicação é idempotente em relação à mesma versão: receber duas ou mais vezes a mesma lista não produz efeitos adicionais após a primeira aplicação. O mecanismo também impede que uma mensagem atrasada substitua uma configuração mais recente.

Esse comportamento foi verificado por testes automatizados que simulam explicitamente a reentrega de uma mensagem retida com versão igual ou inferior à versão local, verificando que a lista armazenada não é alterada nesses casos.

### 3.6 Procedimento de verificação e critérios de validação

A validação foi organizada em três níveis: verificação do projeto elétrico, verificação funcional do firmware e teste físico de bancada.

A primeira etapa utilizou regras de projeto elétrico (ERC, do inglês *Electrical Rule Check*) e conferência do netlist gerado a partir do esquemático, nó a nó. O objetivo foi verificar se as conexões representadas no projeto correspondiam à intenção de projeto.

A segunda etapa utilizou testes automatizados para verificar o comportamento do agendador e do mecanismo de versionamento da lista de agendamentos. Foram considerados, entre outros, os cenários de execução de um agendamento, persistência após reinicialização simulada e recebimento de versões repetidas ou desatualizadas.

A terceira etapa utilizou o ESP32, o módulo relé e uma carga simulada de baixa potência em protoboard. O teste confirmou experimentalmente que o sinal de controle esperado para o relé era ativo em nível lógico baixo. A divergência em relação ao comportamento inicialmente previsto no esquemático foi corrigida após essa verificação.

Os critérios de validação adotados são apresentados na Tabela 2.

**Tabela 2 – Requisitos e critérios de validação**

| Requisito | Método de verificação | Estado na submissão |
|---|---|---|
| Conexões do esquemático coerentes com o projeto | ERC + conferência do netlist | Verificado |
| Acionamento lógico do relé | Teste de bancada com carga simulada | Verificado |
| Execução local de agendamentos | Testes automatizados | Verificado |
| Persistência dos agendamentos | Testes automatizados com reinicialização | Verificado |
| Rejeição de versões iguais ou antigas | Testes automatizados de reentrega | Verificado |
| Comunicação MQTT sobre TLS | Implementação funcional do firmware | Implementado; validação de campo pendente |
| Acionamento sob carga real de 800 W/127 V | Ensaio com a cafeteira-alvo | Pendente |
| Aquecimento do conjunto durante ciclo completo | Ensaio térmico | Pendente |
| Adequação estrutural do invólucro | Ensaio físico ou simulação validada | Pendente |
| Segurança elétrica do conjunto | Revisão da montagem e ensaio apropriado | Pendente |

*Fonte: autor (2026).*

A Figura 2 registra o arranjo do teste de bancada com carga simulada.

**Figura 2 – Teste de bancada do módulo embarcado, com carga simulada em protoboard**

*Fonte: autor (2026). Vídeo do teste de bancada disponível como material suplementar.*

Os testes com a carga real não foram realizados nesta etapa. A ausência desse ensaio é tratada como limitação metodológica e de segurança, e não como evidência de funcionamento do módulo sob a potência nominal da cafeteira.

## 4. Resultados e Discussão

### 4.1 Esquemático consolidado e verificação por ERC/netlist

O esquemático elétrico consolidado do módulo foi desenvolvido em uma única folha no software KiCad, reunindo o ESP32, o módulo relé, a fonte AC-DC, o fusível, o LED indicador, o botão de acionamento manual e os conectores de entrada e saída de energia.

A verificação por regras de projeto elétrico (ERC) não identificou erros de projeto. Os avisos remanescentes foram revisados individualmente e correspondem a situações esperadas e documentadas, como pinos de uso geral do microcontrolador intencionalmente não conectados. A conferência complementar do netlist confirmou que as conexões elétricas representadas correspondem à intenção de projeto.

Esse resultado demonstra a consistência lógica do esquemático, mas não equivale à validação de segurança da montagem física. O leiaute de placa de circuito impresso e a atribuição definitiva de *footprints* ainda não foram realizados, de modo que o projeto permanece na etapa de esquemático validado.

### 4.2 Acionamento do relé e verificação funcional

O acionamento do relé por comando de software do ESP32 foi confirmado em bancada com uma carga simulada de baixa potência. O teste demonstrou a correção da cadeia lógica entre firmware, sinal de controle e comutação dos contatos do relé.

O ensaio também revelou uma divergência entre o nível lógico inicialmente previsto no esquemático e o comportamento observado no módulo comercial utilizado. O acionamento efetivo foi identificado como ativo em nível lógico baixo e o projeto foi corrigido de acordo com o comportamento observado.

Esse resultado valida a lógica de controle em condições de baixa potência, mas não permite concluir que o relé, o fusível, os condutores, os conectores, a fonte e o invólucro operarão adequadamente sob a carga nominal da cafeteira. A validação com a carga real permanece pendente.

### 4.3 Funcionamento local, persistência e idempotência

Os testes automatizados confirmaram o comportamento previsto para o agendador local: após a sincronização do relógio, o disparo é determinado pela configuração persistida no dispositivo e não requer uma comunicação com a nuvem no instante da execução.

A persistência em NVS permite que a configuração sobreviva à reinicialização do microcontrolador. O armazenamento da lista como uma unidade versionada também simplifica a reconstrução do estado após uma reconexão.

No protocolo MQTT, os testes de reentrega demonstraram o comportamento esperado do versionamento. Uma versão superior à local é aplicada; uma versão igual é tratada como estado já conhecido; e uma versão inferior é descartada. Esse resultado sustenta a propriedade de idempotência da operação de atualização no nível da aplicação.

A validação realizada, entretanto, não mede desempenho de rede, latência, disponibilidade do broker ou consumo de memória durante períodos prolongados. Esses aspectos podem ser avaliados em uma etapa posterior de testes de campo.

### 4.4 Comparação com tomadas inteligentes comerciais

A diferença central entre o Cafey e uma tomada inteligente genérica não está apenas na existência de controle remoto, mas na hipótese sobre o estado físico do aparelho conectado.

Uma tomada inteligente genérica pode ser suficiente para uma carga cujo funcionamento seja determinado diretamente pelo fornecimento de energia. No caso estudado, entretanto, o estado físico da chave mecânica faz parte da condição necessária para o funcionamento. O Cafey foi projetado especificamente para que a chave do aparelho permaneça na posição ligada e para que o relé externo assuma o controle do fornecimento de energia.

O Quadro 1 apresenta uma comparação qualitativa. As características atribuídas às tomadas genéricas devem ser interpretadas como propriedades típicas da categoria e não como afirmações universais sobre todos os produtos comerciais.

**Quadro 1 – Comparação qualitativa entre o Cafey e uma tomada inteligente genérica**

| Aspecto | Tomada inteligente genérica | Cafey |
|---|---|---|
| Chave mecânica de retenção | Não trata explicitamente a posição física da chave | Problema tratado como requisito central |
| Controle manual sem rede | Depende das características do modelo | Botão físico dedicado |
| Agendamento sem conectividade no momento do disparo | Depende da arquitetura do produto | Executado localmente |
| Sincronização de horário | Depende do produto e de sua infraestrutura | NTP no dispositivo |
| Especificidade ao aparelho | Projetada como dispositivo genérico de chaveamento | Demonstrada em uma cafeteira-alvo |
| Abertura do projeto | Normalmente fechada do ponto de vista de hardware/firmware | Arquitetura documentada neste trabalho |

*Fonte: autor (2026).*

A comparação não deve ser interpretada como uma avaliação geral de superioridade. O objetivo é delimitar a diferença de escopo entre uma tomada inteligente genérica e uma solução concebida para uma restrição física específica.

### 4.5 Limitações e ameaças à validade

O Cafey, no estágio apresentado, apenas controla o fornecimento de energia da cafeteira e não possui sensores de corrente, temperatura ou nível de água. Consequentemente, o sistema conhece o estado do relé, mas não consegue determinar de forma independente se um ciclo de preparo foi efetivamente concluído. Uma queda de energia durante o preparo, por exemplo, não é distinguida de um ciclo encerrado normalmente apenas a partir do estado do relé.

A alegação inicial de isolamento galvânico por meio do optoacoplador do módulo relé foi revista após a inspeção da configuração física. Na montagem adotada, o optoacoplador não deve ser considerado garantia de isolamento galvânico entre a rede e a eletrônica de controle. Essa questão constitui uma limitação de segurança e exige revisão da montagem antes de qualquer teste com a carga real.

A validação elétrica apresentada também possui escopo limitado. O teste físico utilizou carga simulada de baixa potência, e não a cafeteira de 800 W/127 V. Portanto, não foram demonstrados, neste estudo, o comportamento térmico do circuito sob carga nominal, a capacidade de interrupção do relé na aplicação específica, o comportamento do fusível em uma condição de falha ou a adequação definitiva dos condutores, conectores e invólucro.

Duas hipóteses de engenharia do invólucro permanecem sem validação experimental: a adequação do PETG à temperatura atingida pela base da cafeteira após um ciclo completo e a resistência estrutural do invólucro. A estimativa de deflexão foi obtida analiticamente, mas ainda não foi confrontada com ensaio físico ou simulação numérica.

Também existe uma limitação de generalização. A arquitetura foi concebida para a classe de aparelhos com chave mecânica de retenção, mas a validação experimental apresentada está restrita à Britânia CP30. Diferenças de potência, tensão, geometria, tipo de chave, temperatura e construção interna podem exigir alterações no projeto de outros aparelhos.

Por fim, o protótipo não foi submetido a certificação ou ensaio normativo que permita caracterizá-lo como equipamento elétrico destinado ao consumidor. Os resultados devem, portanto, ser interpretados como validação parcial de um protótipo de pesquisa.

## 5. Considerações Finais

Este trabalho investigou a viabilidade de utilizar um módulo IoT externo para automatizar uma cafeteira com chave mecânica de retenção sem modificar sua estrutura elétrica interna. O Cafey combina uma arquitetura de hardware baseada em ESP32 e relé com um firmware estruturado segundo o padrão Active Object, agendamento local persistido em NVS e sincronização de configuração por MQTT sobre TLS.

A principal contribuição de software é um contrato de aplicação para sincronização da lista de agendamentos que utiliza versionamento monotônico. Como o dispositivo somente aceita versões estritamente superiores à versão persistida, a reentrega de uma mensagem MQTT não produz uma nova alteração de estado. Essa propriedade foi verificada por testes automatizados com versões repetidas e desatualizadas.

Os resultados físicos obtidos até o estágio de submissão demonstram a consistência do esquemático por ERC e netlist e o acionamento do relé pelo ESP32 em bancada com carga simulada. Os testes automatizados também verificaram a execução local de agendamentos, a persistência da configuração e o tratamento de reentregas.

Esses resultados respondem parcialmente à pergunta de pesquisa. Eles demonstram que a arquitetura proposta pode implementar, em nível de protótipo, os mecanismos necessários para controle local e sincronização resiliente. Entretanto, não permitem ainda concluir que o conjunto é adequado para operação residencial com a carga real. A validação da cafeteira de 800 W/127 V, a revisão da montagem elétrica, os ensaios térmicos e estruturais e a avaliação apropriada da proteção contra sobrecorrente permanecem necessários.

Como continuidade do trabalho, pretende-se concluir essas validações, avançar do esquemático para o leiaute de placa de circuito impresso, avaliar alternativas de proteção e isolamento mais apropriadas, e realizar testes prolongados com o aparelho-alvo. Em uma etapa posterior, a arquitetura poderá ser avaliada em outros aparelhos com características semelhantes, permitindo distinguir quais elementos são específicos da CP30 e quais podem ser generalizados para a classe de eletrodomésticos com chave mecânica de retenção.

## Referências

AKSHATHA, P. S.; DILIP KUMAR, S. M.; VENUGOPAL, K. R. MQTT Implementations, Open Issues, and Challenges: A Detailed Comparison and Survey. *International Journal of Sensors, Wireless Communications and Control*, v. 12, n. 8, p. 553–576, 2022. DOI: 10.2174/2210327913666221216152446.

BAYILMIŞ, C.; EBLEME, M. A.; ÇAVUŞOĞLU, Ü.; KÜÇÜK, K.; SEVİN, A. A survey on communication protocols and performance evaluations for Internet of Things. *Digital Communications and Networks*, v. 8, n. 6, p. 1094–1104, 2022.

BOVEA, M. D.; IBÁÑEZ-FORÉS, V.; PÉREZ-BELIS, V. Repair vs. replacement: selection of the best end-of-life scenario for small household electric and electronic equipment based on life cycle assessment. *Journal of Environmental Management*, v. 254, art. 109679, 2020. DOI: 10.1016/j.jenvman.2019.109679.

EGARTER, D.; MONACCHI, A.; KHATIB, T.; ELMENREICH, W. *Integration of Legacy Appliances into Home Energy Management Systems*. arXiv:1406.3252, 2014.

GENTILE, A. F.; MACRÌ, D.; CARNÌ, D. L.; GRECO, E.; LAMONACA, F. A Performance Analysis of Security Protocols for Distributed Measurement Systems Based on Internet of Things with Constrained Hardware and Open Source Infrastructures. *Sensors*, v. 24, n. 9, art. 2781, 2024. DOI: 10.3390/s24092781.

KHOMENKO, Y.; BABICHEV, S. Modular IoT Architecture for Monitoring and Control of Office Environments Based on Home Assistant. *IoT (MDPI)*, v. 6, n. 4, art. 69, 2025. DOI: 10.3390/iot6040069.

MADADI-BAROUGH, S.; RUIZ-BLANCO, P.; LIN, J.; VIDAL, R.; GOMEZ, C. *Matter: IoT Interoperability for Smart Homes*. arXiv:2405.01618, 2024. Universitat Politècnica de Catalunya.

RUSSO, D.; MIORI, V.; TOLOMEI, G.; BELLI, D. Home automation interoperability: two decades of lessons learned and future prospects into the development of IoT ecosystems. *Internet of Things*, v. 37, art. 101906, 2026. DOI: 10.1016/j.iot.2026.101906.

SAMEK, M. *Practical UML Statecharts in C/C++: Event-Driven Programming for Embedded Systems*. 2. ed. Oxford: Newnes/Elsevier, 2008. 728 p. ISBN 978-0-7506-8706-5.

SANTOS, A.; DUGGAN, G. P.; DAVIS, J.; ZIMMERLE, D. A cautionary note on using smart plugs for research data acquisition. *e-Prime — Advances in Electrical Engineering, Electronics and Energy*, v. 4, art. 100137, 2023. DOI: 10.1016/j.prime.2023.100137.

SLADE, G. *Made to Break: Technology and Obsolescence in America*. Cambridge, MA: Harvard University Press, 2006. 330 p. ISBN 978-0-674-02203-4.

---

**Declaração de responsabilidade**

Os conteúdos expressos no trabalho, assim como os direitos autorais de figuras e dados, bem como sua revisão ortográfica e das normas são de inteira responsabilidade dos autores.

**Declaração de uso de Inteligência Artificial**

Durante a preparação do manuscrito, foram utilizadas ferramentas de Inteligência Artificial, incluindo o Claude Code (Anthropic), como apoio à redação e à formatação a partir da documentação técnica do projeto. Após a utilização dessas ferramentas, os autores revisaram e editaram o conteúdo conforme necessário e assumem total responsabilidade pelo conteúdo da publicação.

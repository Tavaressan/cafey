Preâmbulo (não numerado)
 
Título em português (Calibri 16, negrito), inglês e espanhol (14, itálico, sem negrito), até 3 linhas cada. Autores: você primeiro, Jeferson de Souza Dias / jeferson.dias01@cps.sp.gov.br por último, conforme a orientação 4. Quadro lateral com 4 palavras-chave nos três idiomas. Resumo, Abstract e Resumen, no máximo 180 palavras cada.
 
1. Introdução — sem subtítulos, o template proíbe
 
Contexto do parque instalado de cafeteiras não programáveis; a alternativa usual (trocar o aparelho) e por que ela é insatisfatória [3]; a lacuna técnica que a tomada inteligente genérica não fecha [1]; objetivo; contribuição declarada; organização do texto.
 
2. Fundamentação Teórica
 
2.1 Integração de aparelhos legados e limites do chaveamento de fase [1, 2]
2.2 Prolongamento de vida útil como alternativa à substituição [3]
2.3 Fragmentação e interoperabilidade em automação residencial [4, 5]
2.4 Comunicação em dispositivos restritos: MQTT, QoS e TLS [7, 8, 10]
2.5 Autonomia local e dependência de nuvem [6]
 
3. Materiais e Métodos
 
3.1 Critérios de seleção do aparelho-alvo (chave mecânica com travamento, potência, plugue NBR 14136)
3.2 Arquitetura do módulo: ESP32, relé, fonte AC-DC embarcada, LED, botão
3.3 Projeto elétrico e dimensionamento do relé
3.4 Firmware: Active Objects, persistência em NVS, agendamento local
3.5 Contrato de tópicos MQTT e idempotência da lista de agendamentos
3.6 Procedimento de verificação em bancada
 
4. Resultados e Discussões
 
4.1 Esquemático consolidado e verificação por ERC/netlist
4.2 Acionamento da carga por comando do ESP32
4.3 Comparação com tomadas inteligentes comerciais [2, 9]
4.4 Limitações e ameaças à validade
 
5. Considerações Finais
 
Referências — ABNT ou APA, escolhido uma vez e mantido no texto todo. Depois delas, com duas linhas de espaço, a declaração de responsabilidade sobre conteúdo e direitos autorais, e uma das duas declarações de uso de IA (apague a que não usar, e apague o parágrafo de fundo amarelo em qualquer caso).
-- Vínculo por janela do botão físico e código de uso único (FW-24, #275).
-- O firmware publica o código em `dispositivos/<thing>/vinculo`; o backend
-- guarda o valor e o prazo calculado no recebimento da mensagem. O código é
-- apagado assim que o vínculo é concluído.
ALTER TABLE dispositivos
  ADD COLUMN codigo_vinculo varchar(16),
  ADD COLUMN codigo_vinculo_expira_em timestamptz;

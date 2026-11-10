-- Horário em que o preparo atual começou, reportado pela cafeteira (payload de estado, campo `desde`).
-- Usado pelo app para calcular o anel de progresso durante PREPARANDO sem depender de cronômetro local.
-- Nulo quando não há preparo em andamento.
ALTER TABLE dispositivos
    ADD COLUMN preparo_iniciado_em timestamptz;

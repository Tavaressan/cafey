-- Temperatura e umidade lidas pelo DHT11 da placa e enviadas na `saude`.
-- Nulas enquanto o firmware nao reportar (ou com o sensor indisponivel).
ALTER TABLE dispositivos
    ADD COLUMN temperatura_c DOUBLE PRECISION,
    ADD COLUMN umidade INTEGER,
    ADD COLUMN ambiente_atualizado_em TIMESTAMPTZ;

ALTER TABLE dispositivos
    ADD COLUMN ultimo_reset VARCHAR(50),
    ADD COLUMN resets_anormais INTEGER;

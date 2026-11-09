-- Issue #321: dados de diagnóstico recebidos via MQTT (saúde/estado), expostos no painel.
ALTER TABLE dispositivos ADD COLUMN rssi INTEGER;
ALTER TABLE dispositivos ADD COLUMN uptime_s BIGINT;
ALTER TABLE dispositivos ADD COLUMN firmware VARCHAR(100);

CREATE TABLE eventos_preparo (
  id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  evento_id      varchar(100) NOT NULL,
  dispositivo_id uuid         NOT NULL REFERENCES dispositivos(id) ON DELETE CASCADE,
  tipo           varchar(50)  NOT NULL DEFAULT 'PREPARO',
  resultado      varchar(50)  NOT NULL,
  origem         varchar(50)  NOT NULL,
  duracao_s      integer      NOT NULL,
  timestamp      timestamptz  NOT NULL,
  detalhe_erro   varchar(255),
  criado_em      timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_evento_dispositivo UNIQUE (dispositivo_id, evento_id)
);

CREATE INDEX ix_eventos_dispositivo_timestamp ON eventos_preparo (dispositivo_id, timestamp DESC);
CREATE INDEX ix_eventos_origem ON eventos_preparo (origem);

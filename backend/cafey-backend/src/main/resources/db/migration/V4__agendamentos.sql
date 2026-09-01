CREATE TABLE agendamentos (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  dispositivo_id uuid        NOT NULL REFERENCES dispositivos(id) ON DELETE CASCADE,
  hora           time        NOT NULL,
  dias_semana    smallint    NOT NULL,
  ativo          boolean     NOT NULL DEFAULT true,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  atualizado_em  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_agendamentos_dispositivo ON agendamentos (dispositivo_id);

CREATE TABLE dispositivos (
  id                      uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  nome                    varchar(255) NOT NULL,
  timezone                varchar(50) NOT NULL DEFAULT 'America/Sao_Paulo',
  estado                  varchar(50) NOT NULL DEFAULT 'DESLIGADO',
  online                  boolean     NOT NULL DEFAULT false,
  ultimo_visto            timestamptz,
  versao_agendamentos     integer     NOT NULL DEFAULT 1,
  duracao_preparo_s       integer     NOT NULL DEFAULT 300,
  limiar_descalcificacao  integer     NOT NULL DEFAULT 200,
  contador_preparos       integer     NOT NULL DEFAULT 0,
  criado_em               timestamptz NOT NULL DEFAULT now(),
  atualizado_em           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuario_dispositivo (
  usuario_id     uuid        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  dispositivo_id uuid        NOT NULL REFERENCES dispositivos(id) ON DELETE CASCADE,
  papel          varchar(50) NOT NULL,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (usuario_id, dispositivo_id),
  CONSTRAINT ck_usuario_dispositivo_papel CHECK (papel IN ('PROPRIETARIO', 'CONVIDADO'))
);

CREATE INDEX ix_usuario_dispositivo_usuario ON usuario_dispositivo (usuario_id);
CREATE INDEX ix_usuario_dispositivo_dispositivo ON usuario_dispositivo (dispositivo_id);

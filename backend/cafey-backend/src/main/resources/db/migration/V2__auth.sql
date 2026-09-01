CREATE TABLE refresh_tokens (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id  uuid        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  token_hash  varchar(255) NOT NULL UNIQUE,
  familia_id  uuid        NOT NULL,
  revogado    boolean     NOT NULL DEFAULT false,
  expira_em   timestamptz NOT NULL,
  criado_em   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens (usuario_id);
CREATE INDEX ix_refresh_tokens_familia ON refresh_tokens (familia_id);

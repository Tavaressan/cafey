CREATE TABLE password_reset_tokens (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id     uuid        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  token_hash     varchar(64) NOT NULL,
  expira_em      timestamptz NOT NULL,
  utilizado      boolean     NOT NULL DEFAULT false,
  criado_em      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_usuario ON password_reset_tokens (usuario_id);
CREATE INDEX ix_password_reset_hash ON password_reset_tokens (token_hash);

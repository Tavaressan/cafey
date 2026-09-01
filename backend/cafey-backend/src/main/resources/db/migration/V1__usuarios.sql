CREATE EXTENSION IF NOT EXISTS "citext";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE usuarios (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  nome          varchar(255) NOT NULL,
  email         citext      NOT NULL UNIQUE,
  senha_hash    varchar(255) NOT NULL,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now()
);

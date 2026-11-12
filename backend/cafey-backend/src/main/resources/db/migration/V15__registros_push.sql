-- Destinos de push por usuário (#360, épico #318): token FCM (Android) ou assinatura Web Push.
-- `destino` guarda o token FCM ou o endpoint da assinatura; é único porque um mesmo aparelho/navegador
-- só pode pertencer a um usuário por vez (o re-registro após outro login transfere o dono).
CREATE TABLE registros_push (
  id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id    uuid          NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  plataforma    varchar(20)   NOT NULL,
  destino       varchar(2048) NOT NULL,
  chave_p256dh  varchar(255),
  chave_auth    varchar(255),
  criado_em     timestamptz   NOT NULL DEFAULT now(),
  ultimo_uso_em timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_registros_push_destino UNIQUE (destino),
  CONSTRAINT ck_registros_push_plataforma CHECK (plataforma IN ('ANDROID', 'WEB'))
);

CREATE INDEX ix_registros_push_usuario ON registros_push (usuario_id);
CREATE INDEX ix_registros_push_ultimo_uso ON registros_push (ultimo_uso_em);

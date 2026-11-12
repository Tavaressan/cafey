-- Correções do 2FA:
-- 1) `mfa_secret_pendente` guarda o segredo recém-gerado em /auth/2fa/configurar até o usuário confirmar
--    o primeiro código. Assim, reconfigurar o 2FA não invalida o segredo em uso: se o usuário desistir no
--    meio, o autenticador antigo continua valendo.
-- 2) `mfa_codigos_recuperacao` guarda os códigos de uso único (só o hash SHA-256) que permitem entrar ou
--    desativar o 2FA sem o celular.
ALTER TABLE usuarios ADD COLUMN mfa_secret_pendente VARCHAR(512);

-- Segredos configurados e nunca ativados passam a ser pendentes (o ativo fica vazio).
UPDATE usuarios
   SET mfa_secret_pendente = mfa_secret,
       mfa_secret = NULL
 WHERE mfa_habilitado = FALSE
   AND mfa_secret IS NOT NULL;

CREATE TABLE mfa_codigos_recuperacao (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id  uuid        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  codigo_hash varchar(64) NOT NULL,
  criado_em   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_mfa_codigos_recuperacao UNIQUE (usuario_id, codigo_hash)
);

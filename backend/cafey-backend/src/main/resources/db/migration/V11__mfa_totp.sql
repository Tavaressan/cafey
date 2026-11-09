-- Adiciona suporte a 2FA/TOTP (RFC 6238, issue #260).
-- mfa_secret armazena o segredo TOTP cifrado (AES-GCM, Base64 de IV + ciphertext+tag),
-- nunca em texto claro. A chave de cifra é configurada separadamente (cafey.mfa.encryption-key).
ALTER TABLE usuarios
    ADD COLUMN mfa_habilitado BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret     VARCHAR(512);

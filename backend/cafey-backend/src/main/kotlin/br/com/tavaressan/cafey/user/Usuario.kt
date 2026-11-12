package br.com.tavaressan.cafey.user

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "usuarios")
class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    var email: String,

    @Column(name = "senha_hash", nullable = false)
    var senhaHash: String,

    @Column(name = "mfa_habilitado", nullable = false)
    var mfaHabilitado: Boolean = false,

    /** Segredo TOTP cifrado (AES-GCM). Nunca em texto claro. */
    @Column(name = "mfa_secret", length = 512)
    var mfaSecret: String? = null,

    /**
     * Segredo TOTP gerado em /auth/2fa/configurar e ainda não confirmado (cifrado). Só substitui
     * [mfaSecret] quando o usuário valida o primeiro código em /auth/2fa/ativar.
     */
    @Column(name = "mfa_secret_pendente", length = 512)
    var mfaSecretPendente: String? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: Instant = Instant.now()
)

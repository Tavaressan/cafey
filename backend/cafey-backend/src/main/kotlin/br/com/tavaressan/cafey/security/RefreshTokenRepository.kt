package br.com.cafey.security

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.familiaId = :familiaId")
    fun revokeFamily(familiaId: UUID): Int
}

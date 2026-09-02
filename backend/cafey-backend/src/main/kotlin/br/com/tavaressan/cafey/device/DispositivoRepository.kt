package br.com.cafey.device

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DispositivoRepository : JpaRepository<Dispositivo, UUID>

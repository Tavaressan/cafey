package br.com.tavaressan.cafey.device

import jakarta.persistence.Column
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * O `ddl-auto: validate` do Hibernate não compara o tamanho das colunas, então nenhum outro teste
 * pega um `length` diferente da migration V3 (`nome` varchar(255), `timezone` e `estado` varchar(50)).
 */
class DispositivoColumnLengthTest {

    private fun lengthOf(field: String): Int =
        Dispositivo::class.java.getDeclaredField(field).getAnnotation(Column::class.java).length

    @Test
    fun `nome declara o tamanho 255 da coluna`() {
        assertEquals(255, lengthOf("nome"))
    }

    @Test
    fun `timezone declara o tamanho 50 da coluna`() {
        assertEquals(50, lengthOf("timezone"))
    }

    @Test
    fun `estado declara o tamanho 50 da coluna`() {
        assertEquals(50, lengthOf("estado"))
    }
}

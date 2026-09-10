package br.com.tavaressan.cafey.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Máscara de bits de `diasSemana`: bit 0 = domingo … bit 6 = sábado (mesma convenção do backend). */
class ScheduleModelsTest {

    @Test
    fun diaAtivo_readsEachBitInOrder() {
        val segASex: Short = 62 // 0b0111110
        assertFalse(segASex.diaAtivo(0)) // domingo
        assertTrue(segASex.diaAtivo(1)) // segunda
        assertTrue(segASex.diaAtivo(5)) // sexta
        assertFalse(segASex.diaAtivo(6)) // sábado
    }

    @Test
    fun diasSemanaMask_encodesSelectedDays() {
        val diasAtivos = listOf(false, true, true, true, true, true, false) // seg-sex
        assertEquals<Short>(62, diasSemanaMask(diasAtivos))
    }

    @Test
    fun diasSemanaMask_roundTripsWithDiaAtivo() {
        val diasAtivos = listOf(true, false, true, false, false, false, true) // dom, ter, sáb
        val mask = diasSemanaMask(diasAtivos)
        val decoded = (0..6).map { mask.diaAtivo(it) }
        assertEquals(diasAtivos, decoded)
    }
}

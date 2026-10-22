package br.com.tavaressan.cafey.device

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** Fusos reconhecidos pelo firmware em `firmware/main/app/timezone_offset.cpp`. */
private val TIMEZONES_SUPORTADOS_PELO_FIRMWARE = setOf(
    "America/Noronha",
    "America/Sao_Paulo",
    "America/Bahia",
    "America/Fortaleza",
    "America/Recife",
    "America/Maceio",
    "America/Belem",
    "America/Araguaina",
    "America/Santarem",
    "America/Manaus",
    "America/Cuiaba",
    "America/Campo_Grande",
    "America/Porto_Velho",
    "America/Boa_Vista",
    "America/Rio_Branco",
    "America/Eirunepe",
    "UTC",
    "Etc/UTC"
)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TimezoneSuportadoValidator::class])
annotation class TimezoneSuportado(
    val message: String = "Timezone não suportado pelo dispositivo",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class TimezoneSuportadoValidator : ConstraintValidator<TimezoneSuportado, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.isBlank() || value.length > 50 || value.trim() in TIMEZONES_SUPORTADOS_PELO_FIRMWARE
}

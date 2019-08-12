import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.mottak.v1.SoknadV1Incoming

internal fun SoknadV1Incoming.validate() {
    val violations = mutableSetOf<Violation>()
    if (vedlegg.isEmpty()) {
        violations.add(
            Violation(
                parameterName = "vedlegg",
                parameterType = ParameterType.ENTITY,
                reason = "Det må sendes minst et vedlegg.",
                invalidValue = vedlegg
            )
        )
    }

    if (sokerFodselsNummer.length != 11 || !sokerFodselsNummer.erKunSiffer()) {
        violations.add(
            Violation(
                parameterName = "soker.fodselsnummer",
                parameterType = ParameterType.ENTITY,
                reason = "Ikke gyldig fødselsnummer.",
                invalidValue = sokerFodselsNummer
            )
        )
    }
    if (violations.isNotEmpty()) {
        throw Throwblem(ValidationProblemDetails(violations))
    }
}
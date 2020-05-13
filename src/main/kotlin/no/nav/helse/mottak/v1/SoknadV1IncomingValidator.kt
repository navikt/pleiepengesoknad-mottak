import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.mottak.v1.SoknadV1Incoming

internal fun SoknadV1Incoming.validate() {
    val violations = mutableSetOf<Violation>()
    /*if (vedlegg.isEmpty()) {
        violations.add(
            Violation(
                parameterName = "vedlegg",
                parameterType = ParameterType.ENTITY,
                reason = "Det må sendes minst et vedlegg.",
                invalidValue = vedlegg
            )
        )
    } TODO: Fjern kommentar når det er krav om validering av vedlegg er påkrevd igjen */

    if (sokerFodselsNummer.length != 11 || !sokerFodselsNummer.erKunSiffer()) {
        violations.add(
            Violation(
                parameterName = "søker.fødselsnummer",
                parameterType = ParameterType.ENTITY,
                reason = "Ikke gyldig fødselsnummer.",
                invalidValue = sokerFodselsNummer
            )
        )
    }
    if (!sokerAktoerId.id.erKunSiffer()) {
        violations.add(
            Violation(
                parameterName = "søker.aktørId",
                parameterType = ParameterType.ENTITY,
                reason = "Ikke gyldig Aktør ID.",
                invalidValue = sokerAktoerId.id
            )
        )
    }

    if (violations.isNotEmpty()) {
        throw Throwblem(ValidationProblemDetails(violations))
    }
}

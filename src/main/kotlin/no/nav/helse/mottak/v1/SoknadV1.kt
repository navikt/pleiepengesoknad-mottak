package no.nav.helse.mottak.v1

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.SoknadId
import no.nav.helse.aktoer.AktoerId
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class SoknadV1Incoming(
    val mottatt: ZonedDateTime,
    val fraOgMed : LocalDate,
    val tilOgMed : LocalDate,
    val soker : SokerIncoming,
    val barn : Barn,
    val relasjonTilBarnet : String,
    val arbeidsgivere: Arbeidsgivere,
    var vedlegg : List<Vedlegg> = listOf(),
    val medlemskap: Medlemskap,
    val grad : Int,
    val harMedsoker : Boolean,
    val harForstattRettigheterOgPlikter : Boolean,
    val harBekreftetOpplysninger : Boolean
)

data class SoknadV1Outgoing (
    val soknadId : String,
    val mottatt: ZonedDateTime,
    val fraOgMed : LocalDate,
    val tilOgMed : LocalDate,
    val soker : SokerOutgoing,
    val barn : Barn,
    val relasjonTilBarnet : String,
    val arbeidsgivere: Arbeidsgivere,
    var vedleggUrls : List<URI> = listOf(),
    val medlemskap: Medlemskap,
    val grad : Int,
    val harMedsoker : Boolean,
    val harForstattRettigheterOgPlikter : Boolean,
    val harBekreftetOpplysninger : Boolean
) {
    constructor(
        incoming: SoknadV1Incoming,
        aktoerId: AktoerId,
        soknadId: SoknadId,
        vedleggUrls: List<URI>
    ) : this(
        soknadId = soknadId.id,
        mottatt = incoming.mottatt,
        fraOgMed = incoming.fraOgMed,
        tilOgMed = incoming.tilOgMed,
        soker = SokerOutgoing(
            incoming = incoming.soker,
            aktoerId = aktoerId
        ),
        barn = incoming.barn,
        relasjonTilBarnet = incoming.relasjonTilBarnet,
        arbeidsgivere = incoming.arbeidsgivere,
        vedleggUrls = vedleggUrls,
        medlemskap = incoming.medlemskap,
        grad = incoming.grad,
        harMedsoker = incoming.harMedsoker,
        harForstattRettigheterOgPlikter = incoming.harForstattRettigheterOgPlikter,
        harBekreftetOpplysninger = incoming.harBekreftetOpplysninger
    )
}

data class SokerIncoming(
    val fodselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class SokerOutgoing(
    val aktoerId: String,
    val fodselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
) {
    constructor(
        incoming: SokerIncoming,
        aktoerId: AktoerId
    ) : this(
        aktoerId = aktoerId.id,
        fodselsnummer = incoming.fodselsnummer,
        fornavn = incoming.fornavn,
        mellomnavn = incoming.mellomnavn,
        etternavn = incoming.etternavn
    )
}

data class Barn(
    val fodselsnummer: String?,
    val navn : String?,
    val alternativId: String?
)

data class Arbeidsgivere(
    val organisasjoner : List<Organisasjon>
)

data class Organisasjon(
    val organisasjonsnummer: String,
    val navn: String?
)

data class Medlemskap(
    @JsonProperty("har_bodd_i_utlandet_siste_12_mnd")
    val harBoddIUtlandetSiste12Mnd : Boolean,
    @JsonProperty("skal_bo_i_utlandet_neste_12_mnd")
    val skalBoIUtlandetNeste12Mnd : Boolean
)

data class Vedlegg (
    val content : ByteArray,
    val contentType : String,
    val title : String
)

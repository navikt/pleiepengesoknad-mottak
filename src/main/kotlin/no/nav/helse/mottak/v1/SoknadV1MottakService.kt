package no.nav.helse.mottak.v1

import no.nav.helse.CorrelationId
import no.nav.helse.Metadata
import no.nav.helse.SoknadId
import no.nav.helse.aktoer.AktoerGateway
import no.nav.helse.aktoer.AktoerId
import no.nav.helse.aktoer.Ident
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentGateway
import org.slf4j.LoggerFactory
import java.net.URI

internal class SoknadV1MottakService(
    private val dokumentGateway: DokumentGateway,
    private val aktoerGateway: AktoerGateway,
    private val soknadV1KafkaProducer: SoknadV1KafkaProducer
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(SoknadV1MottakService::class.java)
    }

    internal suspend fun leggTilProsessering(
        soknadId: SoknadId,
        metadata: Metadata,
        soknad: SoknadV1Incoming
    ) : SoknadId {
        val correlationId = CorrelationId(metadata.correlationId)
        logger.trace("Henter aktørID")
        val aktoerId = aktoerGateway.getAktoerId(
            ident = Ident(soknad.soker.fodselsnummer),
            correlationId = correlationId
        )

        logger.trace("Lagrer vedlegg")
        val vedleggUrls = lagreVedleg(
            aktoerId = aktoerId,
            vedlegg = soknad.vedlegg,
            correlationId = correlationId
        )

        logger.trace("Legger på kø")
        soknadV1KafkaProducer.produce(
            metadata = metadata,
            soknad = SoknadV1Outgoing(
                soknadId = soknadId,
                incoming = soknad,
                aktoerId = aktoerId,
                vedleggUrls = vedleggUrls
            )
        )

        return soknadId
    }

    private suspend fun lagreVedleg(
        aktoerId: AktoerId,
        correlationId: CorrelationId,
        vedlegg: List<Vedlegg>
    ) : List<URI> {
        logger.info("Lagrer ${vedlegg.size} vedlegg.")
        return dokumentGateway.lagreDokmenter(
            dokumenter = vedlegg.somDokumenter(),
            correlationId = correlationId,
            aktoerId = aktoerId
        )
    }
}

private fun List<Vedlegg>.somDokumenter() = map {
    Dokument(
        content = it.content,
        contentType = it.contentType,
        title = it.title
    )
}.toSet()
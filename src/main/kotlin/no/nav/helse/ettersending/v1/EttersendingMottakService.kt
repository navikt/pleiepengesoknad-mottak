package no.nav.helse.mottakEttersending.v1

import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Metadata
import no.nav.helse.SoknadId
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentGateway
import org.slf4j.LoggerFactory
import java.net.URI

internal class EttersendingV1MottakService(
    private val dokumentGateway: DokumentGateway,
    private val ettersendingKafkaProducer: EttersendingKafkaProducer
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(EttersendingV1MottakService::class.java)
    }

    internal suspend fun leggTilProsessering(
        soknadId: SoknadId,
        metadata: Metadata,
        soknad: EttersendingIncoming
    ) : SoknadId {
        val correlationId = CorrelationId(metadata.correlationId)

        logger.trace("Lagrer vedlegg for ettersending.")
        val vedleggUrls = lagreVedlegg(
            aktoerId = soknad.søkerAktørId,
            vedlegg = soknad.vedlegg,
            correlationId = correlationId
        )

        val outgoing = soknad
            .medVedleggUrls(vedleggUrls)
            .medSoknadId(soknadId)
            .somOutgoing()

        logger.trace("Legger ettersending på kø.")
        ettersendingKafkaProducer.produce(
            metadata = metadata,
            ettersending = outgoing
        )

        return soknadId
    }

    private suspend fun lagreVedlegg(
        aktoerId: AktoerId,
        correlationId: CorrelationId,
        vedlegg: List<Vedlegg>
    ) : List<URI> {
        logger.info("Lagrer ${vedlegg.size} vedlegg for ettersending.")
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

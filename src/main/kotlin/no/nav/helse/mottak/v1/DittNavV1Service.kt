package no.nav.helse.mottak.v1

import org.slf4j.LoggerFactory

internal class DittNavV1Service(
    private val soknadV1KafkaProducer: SoknadV1KafkaProducer
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(SoknadV1MottakService::class.java)
    }

    fun sendSoknadMottattMeldingTilDittNav(
        dto: ProduceBeskjedDto,
        søkersNorskeIdent: String
    ): String {

        logger.trace("DittNavV1Service. Next: Produce dittnav kafka melding.")

        soknadV1KafkaProducer.produceDittnavMelding(
            dto,
            søkersNorskeIdent
        )

        return "Kafkaproducer aktivert."
    }
}
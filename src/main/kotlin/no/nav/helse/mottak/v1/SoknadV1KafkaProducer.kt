package no.nav.helse.mottak.v1

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.Metadata
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.TopicEntry
import no.nav.helse.kafka.TopicUse
import no.nav.helse.kafka.Topics
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory

internal class SoknadV1KafkaProducer(
    kafkaConfig: KafkaConfig
) {
    private companion object {
        private val NAME = "SoknadV1Producer"
        private val TOPIC_USE = TopicUse(
            name = Topics.MOTTATT,
            valueSerializer = SoknadV1OutgoingSerialier()
        )
        private val logger = LoggerFactory.getLogger(SoknadV1KafkaProducer::class.java)
    }

    private val producer = KafkaProducer<String, TopicEntry<SoknadV1Outgoing>>(
        kafkaConfig.producer(NAME),
        TOPIC_USE.keySerializer(),
        TOPIC_USE.valueSerializer
    )

    internal fun produce(
        soknad: SoknadV1Outgoing,
        metadata: Metadata
    ) {
        if (metadata.version != 1) throw IllegalStateException("Kan ikke legge søknad på versjon ${metadata.version} til prosessering.")

        val recordMetaData = producer.send(
            ProducerRecord(
                Topics.MOTTATT,
                soknad.soknadId,
                TopicEntry(
                    metadata = metadata,
                    data = soknad
                )
            )
        ).get()

        logger.info("Søknad '${soknad.soknadId}' sendt til Topic '${Topics.MOTTATT}' med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'")
    }
}

private class SoknadV1OutgoingSerialier : Serializer<TopicEntry<SoknadV1Outgoing>> {
    private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
    override fun serialize(topic: String, data: TopicEntry<SoknadV1Outgoing>) = objectMapper.writeValueAsBytes(data)
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}
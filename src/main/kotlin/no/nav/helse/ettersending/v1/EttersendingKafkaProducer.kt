package no.nav.helse.mottakEttersending.v1

import no.nav.helse.Metadata
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.TopicEntry
import no.nav.helse.kafka.TopicUse
import no.nav.helse.kafka.Topics
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal class EttersendingKafkaProducer(
    kafkaConfig: KafkaConfig
) : HealthCheck {
    private companion object {
        private val NAME = "EttersendingProducer"
        private val TOPIC_USE = TopicUse(
            name = Topics.ETTERSENDING_MOTTATT,
            valueSerializer = EttersendingOutgoingSerialier()
        )
        private val logger = LoggerFactory.getLogger(EttersendingKafkaProducer::class.java)
    }

    private val producer = KafkaProducer(
        kafkaConfig.producer(NAME),
        TOPIC_USE.keySerializer(),
        TOPIC_USE.valueSerializer
    )

    internal fun produce(
        ettersending: EttersendingOutgoing,
        metadata: Metadata
    ) {
        if (metadata.version != 1) throw IllegalStateException("Kan ikke legge ettersending med versjon ${metadata.version} til prosessering.")

        val recordMetaData = producer.send(
            ProducerRecord(
                TOPIC_USE.name,
                ettersending.soknadId.id,
                TopicEntry(
                    metadata = metadata,
                    data = ettersending.jsonObject
                )
            )
        ).get()

        logger.info("Ettersending sendt til Topic '${TOPIC_USE.name}' med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'")
    }


    internal fun stop() = producer.close()

    override suspend fun check(): Result {
        return try {
            producer.partitionsFor(TOPIC_USE.name)
            Healthy(NAME, "Tilkobling til Kafka OK!")
        } catch (cause: Throwable) {
            logger.error("Feil ved tilkobling til Kafka", cause)
            UnHealthy(NAME, "Feil ved tilkobling mot Kafka. ${cause.message}")
        }
    }
}

private class EttersendingOutgoingSerialier : Serializer<TopicEntry<JSONObject>> {
    override fun serialize(topic: String, data: TopicEntry<JSONObject>) : ByteArray {
        val metadata = JSONObject()
            .put("correlationId", data.metadata.correlationId)
            .put("requestId", data.metadata.requestId)
            .put("version", data.metadata.version)

        return JSONObject()
            .put("metadata", metadata)
            .put("data", data.data)
            .toString()
            .toByteArray()
    }
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

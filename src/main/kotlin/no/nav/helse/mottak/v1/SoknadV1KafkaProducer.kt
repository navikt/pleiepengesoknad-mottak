package no.nav.helse.mottak.v1

import kotlinx.io.core.toByteArray
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.helse.Metadata
import no.nav.helse.SoknadId
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

internal class SoknadV1KafkaProducer(
    val kafkaConfig: KafkaConfig
) : HealthCheck {

    private companion object {
        private val NAME = "SoknadV1Producer"
        private val TOPIC_USE = TopicUse(
            name = Topics.MOTTATT,
            valueSerializer = SoknadV1OutgoingSerialier()
        )
        private val TOPIC_USE_DITT_NAV_MELDING = TopicUse(
            name = Topics.DITT_NAV_BESKJED,
            valueSerializer = DittNavBeskjedSerializer()
        )
        private val logger = LoggerFactory.getLogger(SoknadV1KafkaProducer::class.java)
    }

    private val producer = KafkaProducer<String, TopicEntry<JSONObject>>(
        kafkaConfig.producer(NAME),
        TOPIC_USE.keySerializer(),
        TOPIC_USE.valueSerializer
    )

    private val producerAvDittNavMelding = KafkaProducer<Nokkel, Beskjed>(
        kafkaConfig
            .producerDittNavMelding(NAME)
    )


    fun createKeyForEvent(eventId: String): Nokkel {
        val systemuser = kafkaConfig.credentials.first
        return Nokkel.newBuilder()
            .setEventId(eventId)
            .setSystembruker(systemuser)
            .build()
    }

    internal fun produce(
        soknad: SoknadV1Outgoing,
        metadata: Metadata
    ) {
        if (metadata.version != 1) throw IllegalStateException("Kan ikke legge søknad på versjon ${metadata.version} til prosessering.")

        val recordMetaData = producer.send(
            ProducerRecord(
                TOPIC_USE.name,
                soknad.soknadId.id,
                TopicEntry(
                    metadata = metadata,
                    data = soknad.jsonObject
                )
            )
        ).get()

        logger.info("Søknad sendt til Topic '${TOPIC_USE.name}' med offset '${recordMetaData.offset()}' til partition '${recordMetaData.partition()}'")
    }

    internal fun produceDittnavMelding(
        dto: ProduceBeskjedDto,
        søkersNorskeIdent: String,
        soknadId: SoknadId
    ) {
        val eventId = UUID.randomUUID().toString()
        val nokkel: Nokkel = createKeyForEvent(
            eventId = eventId
        )
        val beskjed: Beskjed = createBeskjedForIdent(
            ident = søkersNorskeIdent,
            dto = dto,
            grupperingsId = soknadId.id
        )

        val producerRecord: ProducerRecord<Nokkel, Beskjed> = ProducerRecord(
            TOPIC_USE_DITT_NAV_MELDING.name,
            nokkel,
            beskjed
        )
        val recordMetaData = producerAvDittNavMelding.send(
            producerRecord
        ).get()
        logger.info("SoknadV1KafkaProducer produceDittnavMelding. Returnvalue, if any: ${recordMetaData}")
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

private class SoknadV1OutgoingSerialier : Serializer<TopicEntry<JSONObject>> {
    override fun serialize(topic: String, data: TopicEntry<JSONObject>): ByteArray {
        val metadata = JSONObject()
            .put("correlation_id", data.metadata.correlationId)
            .put("request_id", data.metadata.requestId)
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

private class DittNavBeskjedSerializer : Serializer<TopicEntry<ProduceBeskjedDto>> {
    override fun serialize(topic: String, data: TopicEntry<ProduceBeskjedDto>): ByteArray {
        return ProduceBeskjedDto(data.data.tekst, data.data.link).toString().toByteArray()
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}

private fun createBeskjedForIdent(ident: String, dto: ProduceBeskjedDto, grupperingsId: String): Beskjed {
    val nowInMs = Instant.now().toEpochMilli()
    val weekFromNowInMs = Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli()
    val build = Beskjed.newBuilder()
        .setFodselsnummer(ident)
        .setGrupperingsId(grupperingsId)
        .setLink(dto.link)
        .setTekst(dto.tekst)
        .setTidspunkt(nowInMs)
        .setSynligFremTil(weekFromNowInMs)
    return build.build()
}


class ProduceBeskjedDto(val tekst: String, val link: String) {
    override fun toString(): String {
        return "ProduceBeskjedDto{tekst='$tekst', link='$link'}"
    }
}
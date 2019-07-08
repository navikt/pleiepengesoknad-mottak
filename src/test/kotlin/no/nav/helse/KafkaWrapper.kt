package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.kafka.TopicEntry
import no.nav.helse.kafka.Topics
import no.nav.helse.mottak.v1.SoknadV1Outgoing
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import kotlin.test.assertEquals

private const val username = "srvkafkaclient"
private const val password = "kafkaclient"

object KafkaWrapper {
    fun bootstrap() : KafkaEnvironment {
        val kafkaEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = true,
            withSchemaRegistry = false,
            withSecurity = true,
            topicNames= listOf(
                Topics.MOTTATT
            )
        )
        return kafkaEnvironment
    }
}

private fun KafkaEnvironment.testConsumerProperties() : MutableMap<String, Any>?  {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
        put(ConsumerConfig.GROUP_ID_CONFIG, "PleiepengesoknadProsesseringTest")
    }
}

internal fun KafkaEnvironment.testConsumer() : KafkaConsumer<String, TopicEntry<SoknadV1Outgoing>> {
    val consumer = KafkaConsumer<String, TopicEntry<SoknadV1Outgoing>>(
        testConsumerProperties(),
        StringDeserializer(),
        SoknadV1OutgoingDeserialiser()
    )
    consumer.subscribe(listOf(Topics.MOTTATT))
    return consumer
}

internal fun KafkaConsumer<String, TopicEntry<SoknadV1Outgoing>>.hentSoknad(
    soknadId: String,
    maxWaitInSeconds: Long = 20
) : TopicEntry<SoknadV1Outgoing> {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(Topics.MOTTATT)
            .filter { it.key().equals(soknadId) }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaEnvironment.username() = username
fun KafkaEnvironment.password() = password

private class SoknadV1OutgoingDeserialiser : Deserializer<TopicEntry<SoknadV1Outgoing>> {
    private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun deserialize(topic: String, data: ByteArray): TopicEntry<SoknadV1Outgoing> = objectMapper.readValue(data)
    override fun close() {}

}
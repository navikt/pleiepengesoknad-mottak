package no.nav.helse

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.jackson.jackson
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.Routing
import io.ktor.util.AttributeKey
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.auth.AccessTokenClientResolver
import no.nav.helse.dokument.DokumentGateway
import no.nav.helse.dusseldorf.ktor.auth.AuthStatusPages
import no.nav.helse.dusseldorf.ktor.auth.allIssuers
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.auth.multipleJwtIssuers
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.mottak.v1.DittNavV1Service
import no.nav.helse.mottak.v1.SoknadV1Api
import no.nav.helse.mottak.v1.SoknadV1KafkaProducer
import no.nav.helse.mottak.v1.SoknadV1MottakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("no.nav.PleiepengesoknadMottak")
private const val soknadIdKey = "soknad_id"
private val soknadIdAttributeKey = AttributeKey<String>(soknadIdKey)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengesoknadMottak() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val issuers = configuration.issuers()
    val accessTokenClientResolver = AccessTokenClientResolver(environment.config.clients())

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson { dusseldorfConfigured()
            .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    install(CallIdRequired)

    install(MicrometerMetrics) {
        init(appId)
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
        mdc(soknadIdKey) { it.setSoknadItAsAttributeAndGet() }
    }

    val soknadV1KafkaProducer = SoknadV1KafkaProducer(
        kafkaConfig = configuration.getKafkaConfig()
    )

    environment.monitor.subscribe(ApplicationStopping) {
        logger.info("Stopper Kafka Producer.")
        soknadV1KafkaProducer.stop()
        logger.info("Kafka Producer Stoppet.")
    }

    val dokumentGateway = DokumentGateway(
        accessTokenClient = accessTokenClientResolver.dokumentAccessTokenClient(),
        baseUrl = configuration.getK9DokumentBaseUrl(),
        lagreDokumentScopes = configuration.getLagreDokumentScopes()
    )

    install(Routing) {
        HealthRoute(
            healthService = HealthService(
                healthChecks = setOf(
                    soknadV1KafkaProducer,
                    dokumentGateway
                )
            )
        )
        MetricsRoute()
        DefaultProbeRoutes()
        authenticate(*issuers.allIssuers()) {
            requiresCallId {
                SoknadV1Api(
                    soknadV1MottakService = SoknadV1MottakService(
                        dokumentGateway = dokumentGateway,
                        soknadV1KafkaProducer = soknadV1KafkaProducer
                    ),
                    dittNavV1Service = DittNavV1Service(
                        soknadV1KafkaProducer = soknadV1KafkaProducer
                    )
                )
            }
        }
    }
}

private fun ApplicationCall.setSoknadItAsAttributeAndGet(): String {
    val soknadId = UUID.randomUUID().toString()
    attributes.put(soknadIdAttributeKey, soknadId)
    return soknadId
}

internal fun ApplicationCall.getSoknadId() = SoknadId(attributes[soknadIdAttributeKey])

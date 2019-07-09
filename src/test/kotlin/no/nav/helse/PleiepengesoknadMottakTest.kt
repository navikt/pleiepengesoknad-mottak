package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import no.nav.common.KafkaEnvironment
import no.nav.helse.WiremockWrapper.gyldigFodselsnummerA
import no.nav.helse.WiremockWrapper.gyldigFodselsnummerB
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.mottak.v1.*
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@KtorExperimentalAPI
class PleiepengesoknadMottakTest {

    @KtorExperimentalAPI
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(PleiepengesoknadMottakTest::class.java)

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaTestConsumer = kafkaEnvironment.testConsumer()
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        private val authorizedAccessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), wireMockServer.getSubject())
        private val unAauthorizedAccessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), "srvikketilgang")

        private var engine = newEngine(kafkaEnvironment)

        private fun getConfig(kafkaEnvironment: KafkaEnvironment) : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(wireMockServer = wireMockServer, kafkaEnvironment = kafkaEnvironment))
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaEnvironment: KafkaEnvironment) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(kafkaEnvironment)
        })

        @BeforeClass
        @JvmStatic
        fun buildUp() {
            logger.info("Building up")
            engine.start(wait = true)
            logger.info("Buildup complete")
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            engine.stop(5, 60, TimeUnit.SECONDS)
            kafkaEnvironment.tearDown()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Gyldig søknad blir lagt til prosessering`() {
        val soknad = gyldigSoknad(
            fodselsnummerSoker = gyldigFodselsnummerA,
            fodselsnummerBarn = gyldigFodselsnummerB
        )

        val soknadId = requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = null
        )
        assertSoknadSendtTilProsessering(soknadId)
    }

    // Utils
    private fun requestAndAssert(soknad : SoknadV1Incoming,
                                 expectedResponse : String?,
                                 expectedCode : HttpStatusCode,
                                 leggTilCorrelationId : Boolean = true,
                                 leggTilAuthorization : Boolean = true,
                                 accessToken : String = authorizedAccessToken) : String? {
        with(engine) {
            handleRequest(HttpMethod.Post, "/v1/soknad") {
                if (leggTilAuthorization) {
                    addHeader(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                if (leggTilCorrelationId) {
                    addHeader(HttpHeaders.XCorrelationId, "123156")
                }
                addHeader(HttpHeaders.ContentType, "application/json")
                val requestEntity = objectMapper.writeValueAsString(soknad)
                logger.info("Request Entity = $requestEntity")
                setBody(objectMapper.writeValueAsString(soknad))
            }.apply {
                logger.info("Response Entity = ${response.content}")
                logger.info("Expected Entity = $expectedResponse")
                assertEquals(expectedCode, response.status())
                when {
                    expectedResponse != null -> JSONAssert.assertEquals(expectedResponse, response.content!!, true)
                    HttpStatusCode.Accepted == response.status() -> {
                        val json = JSONObject(response.content!!)
                        assertEquals(1, json.keySet().size)
                        val soknadId = json.getString("id")
                        assertNotNull(soknadId)
                        return soknadId
                    }
                    else -> assertEquals(expectedResponse, response.content)
                }

            }
        }
        return null
    }


    private fun gyldigSoknad(
        fodselsnummerSoker : String,
        fodselsnummerBarn: String,
        vedlegg : Vedlegg = Vedlegg(
            contentType = "image/jpeg",
            title = "iPhone",
            content = "iPhone_6.jpg".fromResources().readBytes()
        )
    ) : SoknadV1Incoming = SoknadV1Incoming(
        mottatt = ZonedDateTime.now(),
        fraOgMed = LocalDate.now(),
        tilOgMed = LocalDate.now().plusWeeks(1),
        soker = SokerIncoming(
            fodselsnummer = fodselsnummerSoker,
            etternavn = "Nordmann",
            mellomnavn = "Mellomnavn",
            fornavn = "Ola"
        ),
        barn = Barn(
            navn = "Kari",
            fodselsnummer = fodselsnummerBarn,
            alternativId = null
        ),
        relasjonTilBarnet = "Mor",
        arbeidsgivere = Arbeidsgivere(
            organisasjoner = listOf(
                Organisasjon("917755736", "Gyldig")
            )
        ),
        vedlegg = listOf(vedlegg),
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true
        ),
        harMedsoker = true,
        grad = 70,
        harBekreftetOpplysninger = true,
        harForstattRettigheterOgPlikter = true
    )

    private fun assertSoknadSendtTilProsessering(soknadId: String?) {
        assertNotNull(soknadId)
        kafkaTestConsumer.hentSoknad(soknadId)
    }
}
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
import no.nav.helse.aktoer.AktoerId
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.testsupport.jws.NaisSts
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import no.nav.helse.mottak.v1.*
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@KtorExperimentalAPI
class PleiepengesoknadMottakTest {

    @KtorExperimentalAPI
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(PleiepengesoknadMottakTest::class.java)

        // Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
        private val gyldigFodselsnummerA = "02119970078"
        private val gyldigFodselsnummerB = "19066672169"
        private val gyldigFodselsnummerC = "20037473937"
        private val dNummerA = "55125314561"

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withAzureSupport()
            .withNaisStsSupport()
            .build()
            .stubPleiepengerDokumentHealth()
            .stubLagreDokument()
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerA, "1234561")
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerB, "1234562")
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerC, "1234563")
            .stubAktoerRegisterGetAktoerId(dNummerA, "1234564")


        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaTestConsumer = kafkaEnvironment.testConsumer()
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()

        private val authorizedAccessToken = NaisSts.generateJwt(application = "srvpleiepengesokna")
        private val unAauthorizedAccessToken = NaisSts.generateJwt(application = "srvnotauthorized")

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

        val sendtTilProsessering  = hentSoknadSendtTilProsessering(soknadId)
        verifiserSoknadLagtTilProsessering(
            incoming = soknad,
            outgoing = sendtTilProsessering
        )
    }

    @Test
    fun `Gyldig søknad fra D-nummer blir lagt til prosessering`() {
        val soknad = gyldigSoknad(
            fodselsnummerSoker = dNummerA,
            fodselsnummerBarn = gyldigFodselsnummerB
        )

        val soknadId = requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = null
        )

        val sendtTilProsessering  = hentSoknadSendtTilProsessering(soknadId)
        verifiserSoknadLagtTilProsessering(
            incoming = soknad,
            outgoing = sendtTilProsessering
        )
    }

    @Test
    fun `Request fra ikke autorisert system feiler`() {
        val soknad = gyldigSoknad(
            fodselsnummerSoker = gyldigFodselsnummerA,
            fodselsnummerBarn = gyldigFodselsnummerB
        )

        requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Forbidden,
            expectedResponse = """
            {
                "type": "/problem-details/unauthorized",
                "title": "unauthorized",
                "status": 403,
                "detail": "Requesten inneholder ikke tilstrekkelige tilganger.",
                "instance": "about:blank"
            }
            """.trimIndent(),
            accessToken = unAauthorizedAccessToken
        )
    }

    @Test
    fun `Request uten corelation id feiler`() {
        val soknad = gyldigSoknad(
            fodselsnummerSoker = gyldigFodselsnummerA,
            fodselsnummerBarn = gyldigFodselsnummerB
        )

        requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "status": 400,
                    "instance": "about:blank",
                    "invalid_parameters" : [
                        {
                            "name" : "X-Correlation-ID",
                            "reason" : "Correlation ID må settes.",
                            "type": "header",
                            "invalid_value": null
                        }
                    ]
                }
            """.trimIndent(),
            leggTilCorrelationId = false
        )
    }

    @Test
    fun `En ugyldig melding gir valideringsfeil`() {
        val fraOgMedString = "2019-04-02"
        val tilOgMedString = "2019-04-01"
        val gyldigOrgnr = "917755736"
        val kortOrgnr = "123456"
        val ugyldigOrgnr = "987654321"
        val fodselsnummerSoker = "290990123451"
        val fodselsnummerBarn = "29099054321"
        val alternativIdBarn = "123F"

        val soknad = SoknadV1Incoming(
            mottatt = ZonedDateTime.now(),
            fraOgMed = LocalDate.parse(fraOgMedString),
            tilOgMed = LocalDate.parse(tilOgMedString),
            soker = SokerIncoming(
                fodselsnummer = fodselsnummerSoker,
                etternavn = "Nordmann",
                mellomnavn = "Mellomnavn",
                fornavn = "Ola"
            ),
            barn = Barn(
                navn = "Kari",
                fodselsnummer = fodselsnummerBarn,
                alternativId = alternativIdBarn
            ),
            relasjonTilBarnet = "Mor",
            arbeidsgivere = Arbeidsgivere(
                organisasjoner = listOf(
                    Organisasjon(gyldigOrgnr, "Gyldig"),
                    Organisasjon(kortOrgnr, "ForKort"),
                    Organisasjon(ugyldigOrgnr, "Ugyldig")
                )
            ),
            vedlegg = listOf(),
            medlemskap = Medlemskap(
                harBoddIUtlandetSiste12Mnd = true,
                skalBoIUtlandetNeste12Mnd = true
            ),
            harMedsoker = true,
            grad = 120,
            harBekreftetOpplysninger = false,
            harForstattRettigheterOgPlikter = false
        )
        requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "status": 400,
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "instance": "about:blank",
                    "invalid_parameters": [{
                        "type": "entity",
                        "name": "vedlegg",
                        "reason": "Det må sendes minst et vedlegg.",
                        "invalid_value": []
                    }, {
                        "type": "entity",
                        "name": "soker.fodselsnummer",
                        "reason": "Ikke gyldig fødselsnummer.",
                        "invalid_value": "$fodselsnummerSoker"
                    }, {
                        "type": "entity",
                        "name": "barn.fodselsnummer",
                        "reason": "Ikke gyldig fødselsnummer.",
                        "invalid_value": "$fodselsnummerBarn"
                    }, {
                        "type": "entity",
                        "name": "barn.alternativ_id",
                        "reason": "Ikke gyldig alternativ id. Kan kun inneholde tall.",
                        "invalid_value": "$alternativIdBarn"
                    }, {
                        "type": "entity",
                        "name": "arbeidsgivere.organisasjoner[1].organisasjonsnummer",
                        "reason": "Ikke gyldig organisasjonsnummer.",
                        "invalid_value": "$kortOrgnr"
                    }, {
                        "type": "entity",
                        "name": "arbeidsgivere.organisasjoner[2].organisasjonsnummer",
                        "reason": "Ikke gyldig organisasjonsnummer.",
                        "invalid_value": "$ugyldigOrgnr"
                    },{
                        "type": "entity",
                        "name": "har_bekreftet_opplysninger",
                        "reason": "Opplysningene må bekreftes for å legge søknad til prosessering.",
                        "invalid_value": false
                    },{
                        "type": "entity",
                        "name": "har_forstatt_rettigheter_og_plikter",
                        "reason": "Må ha forstått rettigheter og plikter for å legge søknad til prosessering.",
                        "invalid_value": false
                    },{
                        "type": "entity",
                        "name": "grad",
                        "reason": "Grad må være mellom 20 og 100.",
                        "invalid_value": 120
                    },{
                        "type": "entity",
                        "name": "fra_og_med",
                        "reason": "Fra og med må være før eller lik til og med.",
                        "invalid_value": "$fraOgMedString"
                    }, {
                        "type": "entity",
                        "name": "til_og_med",
                        "reason": "Til og med må være etter eller lik fra og med.",
                        "invalid_value": "$tilOgMedString"
                    }]
                }
            """.trimIndent()
        )
    }

    // Utils
    private fun verifiserSoknadLagtTilProsessering(
        incoming: SoknadV1Incoming,
        outgoing: SoknadV1Outgoing
    ) {
        val outgoingFromIncoming = SoknadV1Outgoing(
            incoming = incoming,
            aktoerId = AktoerId(outgoing.soker.aktoerId),
            soknadId = SoknadId(outgoing.soknadId),
            vedleggUrls = outgoing.vedleggUrls
        )

        assertEquals(outgoingFromIncoming, outgoing)
    }


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
        mottatt = ZonedDateTime.ofInstant(
            LocalDateTime.now(),
            ZoneOffset.UTC,
            ZoneId.of("UTC")
        ),
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

    private fun hentSoknadSendtTilProsessering(soknadId: String?) : SoknadV1Outgoing {
        assertNotNull(soknadId)
        return kafkaTestConsumer.hentSoknad(soknadId).data
    }
}
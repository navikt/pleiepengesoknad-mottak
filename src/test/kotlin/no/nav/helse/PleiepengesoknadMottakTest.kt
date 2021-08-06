package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.stop
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.mottak.v1.JsonKeys
import no.nav.helse.mottak.v1.SoknadV1Incoming
import no.nav.helse.mottak.v1.SoknadV1Outgoing
import org.apache.commons.codec.binary.Base64
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            .build()
            .stubK9DokumentHealth()
            .stubLagreDokument()
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerA, "1234561")
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerB, "1234562")
            .stubAktoerRegisterGetAktoerId(gyldigFodselsnummerC, "1234563")
            .stubAktoerRegisterGetAktoerId(dNummerA, "1234564")


        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaTestConsumer = kafkaEnvironment.testConsumer()

        private val objectMapper = jacksonObjectMapper().pleiepengesøknadMottakKonfigurert()

        private val authorizedAccessToken = Azure.V1_0.generateJwt(clientId = "pleiepengesoknad-api", audience = "pleiepengesoknad-mottak")
        private val unAauthorizedAccessToken = Azure.V2_0.generateJwt(clientId = "ikke-authorized-client", audience = "pleiepengesoknad-mottak", accessAsApplication = false)

        private var engine = newEngine(kafkaEnvironment).apply {
            start(wait = true)
        }

        private fun getConfig(kafkaEnvironment: KafkaEnvironment) : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                kafkaEnvironment = kafkaEnvironment,
                pleiepengersoknadMottakAzureClientId = "pleiepengesoknad-mottak",
                azureAuthorizedClients = setOf("pleiepengesoknad-api")
            ))
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaEnvironment: KafkaEnvironment) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(kafkaEnvironment)
        })

        @BeforeAll
        @JvmStatic
        fun buildUp() {
            logger.info("Building up")
            CollectorRegistry.defaultRegistry.clear()
            engine.start(wait = true)
            logger.info("Buildup complete")
        }

        @AfterAll
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
        gyldigSoknadBlirLagtTilProsessering(
            Azure.V1_0.generateJwt(
                clientId = "pleiepengesoknad-api",
                audience = "pleiepengesoknad-mottak"
            )
        )
        gyldigSoknadBlirLagtTilProsessering(
            Azure.V2_0.generateJwt(
                clientId = "pleiepengesoknad-api",
                audience = "pleiepengesoknad-mottak"
            )
        )
    }

    @Test
    fun `Gyldig søknad med søknadId på request body`() {
        val forventetSøknadId = UUID.randomUUID().toString()
        val soknad = gyldigSøknad(
            fødselsnummerSøker = dNummerA,
            søknadId = forventetSøknadId
        )

        requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = """{"id":"$forventetSøknadId"}"""
        )
    }

    @Test
    fun `Gyldig søknad uten søknadId på request body`() {
        val soknad = gyldigSøknad(
            fødselsnummerSøker = dNummerA,
            søknadId = null
        )

        val soknadId = requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = null
        )

        assertNotNull(soknadId)
        assertTrue(soknadId.isNotBlank())
    }

    private fun gyldigSoknadBlirLagtTilProsessering(accessToken: String) {
        val soknad = gyldigSøknad(
            fødselsnummerSøker = gyldigFodselsnummerA
        )

        val soknadId = requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = null,
            accessToken = accessToken
        )

        val sendtTilProsessering = hentSoknadSendtTilProsessering(soknadId)
        verifiserSoknadLagtTilProsessering(
            incomingJsonString = soknad,
            outgoingJsonObject = sendtTilProsessering
        )
    }

    @Test
    fun `Gyldig søknad fra D-nummer blir lagt til prosessering`() {
        val soknad = gyldigSøknad(
            fødselsnummerSøker = dNummerA
        )

        val soknadId = requestAndAssert(
            soknad = soknad,
            expectedCode = HttpStatusCode.Accepted,
            expectedResponse = null
        )

        val sendtTilProsessering  = hentSoknadSendtTilProsessering(soknadId)
        verifiserSoknadLagtTilProsessering(
            incomingJsonString = soknad,
            outgoingJsonObject = sendtTilProsessering
        )
    }

    @Test
    fun `Request fra ikke autorisert system feiler`() {
        val soknad = gyldigSøknad(
            fødselsnummerSøker = gyldigFodselsnummerA
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
        val soknad = gyldigSøknad(
            fødselsnummerSøker = gyldigFodselsnummerA
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
                            "type": "header"
                        }
                    ]
                }
            """.trimIndent(),
            leggTilCorrelationId = false
        )
    }

    @Test
    fun `En ugyldig melding gir valideringsfeil`() {
        val ugyldigFnr = "290990123451"
        val soknad = """
        {
            "søker": {
                "fødselsnummer": "$ugyldigFnr",
                "aktørId": "ABC"
            },
            vedlegg: []
        }
        """.trimIndent()

        /*
        {
            "type": "entity",
            "name": "vedlegg",
            "reason": "Det må sendes minst et vedlegg.",
            "invalid_value": []
        },
         */
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
                        "name": "søker.fødselsnummer",
                        "reason": "Ikke gyldig fødselsnummer.",
                        "invalid_value": "$ugyldigFnr"
                    }, {
                        "type": "entity",
                        "name": "søker.aktørId",
                        "reason": "Ikke gyldig Aktør ID.",
                        "invalid_value": "ABC"
                    }]
                }
            """.trimIndent()
        )
    }

    // Utils
    private fun verifiserSoknadLagtTilProsessering(
        incomingJsonString: String,
        outgoingJsonObject: JSONObject
    ) {
        val outgoing = SoknadV1Outgoing(outgoingJsonObject)

        val outgoingFromIncoming = SoknadV1Incoming(incomingJsonString)
            .medSøknadId(outgoing.soknadId)
            .medVedleggUrls(outgoing.vedleggUrls)
            .somOutgoing()

        JSONAssert.assertEquals(outgoingFromIncoming.jsonObject.toString(), outgoing.jsonObject.toString(), true)
    }


    private fun requestAndAssert(soknad : String,
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
                setBody(soknad)
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


    private fun gyldigSøknad(fødselsnummerSøker : String, søknadId: String? = null) : String =
        """
        
        {
            "${JsonKeys.søknadId}": ${
                    when (søknadId) {
                        null -> null
                        else -> "$søknadId"
                    }
            },
            "søker": {
                "fødselsnummer": "$fødselsnummerSøker",
                "aktørId": "123456"
            },
            vedlegg: [{
                "content": "${Base64.encodeBase64String("iPhone_6.jpg".fromResources().readBytes())}",
                "contentType": "image/jpeg",
                "title": "Et fint bilde"
            }],
            "hvilkeSomHelstSndreAtributter": {
                "enabled": true,
                "norsk": "Sære Åreknuter"
            }
        }
        """.trimIndent()

    private fun hentSoknadSendtTilProsessering(soknadId: String?) : JSONObject {
        assertNotNull(soknadId)
        return kafkaTestConsumer.hentSoknad(soknadId).data
    }
}

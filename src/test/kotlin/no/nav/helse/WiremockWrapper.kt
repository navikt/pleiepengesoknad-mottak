package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Extension
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("no.nav.WiremockWrapper")
private const val jwkSetPath = "/auth-mock/jwk-set"
private const val tokenPath = "/auth-mock/token"
private const val subject = "srvpleiepengesokna"
private const val aktoerRegisterBasePath = "/aktoerregister-mock"
private const val pleiepengerDokumentBasePath = "/pleiepenger-dokument-mock"

object WiremockWrapper {

    fun bootstrap(
        port: Int? = null,
        extensions : Array<Extension> = arrayOf()) : WireMockServer {

        val wireMockConfiguration = WireMockConfiguration.options()

        extensions.forEach {
            wireMockConfiguration.extensions(it)
        }

        if (port == null) {
            wireMockConfiguration.dynamicPort()
        } else {
            wireMockConfiguration.port(port)
        }

        val wireMockServer = WireMockServer(wireMockConfiguration)

        wireMockServer.start()
        WireMock.configureFor(wireMockServer.port())

        stubGetSystembrukerToken()
        stubJwkSet()

        stubHealthEndpoint("$pleiepengerDokumentBasePath/health")

        stubLagreDokument(wireMockServer.getPleiepengerDokumentBaseUrl())
        stubAktoerRegisterGetAktoerId("29099012345", "123456")

        logger.info("Mock available on '{}'", wireMockServer.baseUrl())
        return wireMockServer
    }

    private fun stubGetSystembrukerToken() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$tokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "access_token": "i-am-a-access-token",
                                "token_type": "Bearer",
                                "expires_in": 1000
                            }
                        """.trimIndent())
                )
        )
    }

    private fun stubJwkSet() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$jwkSetPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody(WiremockWrapper::class.java.getResource("/jwkset.json").readText())
                )
        )
    }

    fun stubAktoerRegisterGetAktoerIdNotFound(
        fnr: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$aktoerRegisterBasePath/.*")).withHeader("Nav-Personidenter", EqualToPattern(fnr)).willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                    {
                      "$fnr": {
                        "identer": null,
                        "feilmelding": "Den angitte personidenten finnes ikke"
                      }
                    }
                    """.trimIndent())
                    .withStatus(200)
            )
        )
    }


    fun stubAktoerRegisterGetAktoerId(
        fnr: String,
        aktoerId: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$aktoerRegisterBasePath/.*")).withHeader("Nav-Personidenter", EqualToPattern(fnr)).willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                    {
                      "$fnr": {
                        "identer": [
                          {
                            "ident": "$aktoerId",
                            "identgruppe": "AktoerId",
                            "gjeldende": true
                          }
                        ],
                        "feilmelding": null
                      }
                    }
                    """.trimIndent())
                    .withStatus(200)
            )
        )
    }

    private fun stubLagreDokument(baseUrl : String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathMatching(".*$pleiepengerDokumentBasePath.*")).willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Location", "$baseUrl/v1/dokument/${UUID.randomUUID()}")
                    .withStatus(201)
            )
        )
    }

    private fun stubHealthEndpoint(
        path : String
    ) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$path")).willReturn(
                WireMock.aResponse()
                    .withStatus(200)
            )
        )
    }
}

fun WireMockServer.getJwksUrl() = baseUrl() + jwkSetPath
fun WireMockServer.getTokenUrl() = baseUrl() + tokenPath
fun WireMockServer.getAktoerRegisterBaseUrl() = baseUrl() + aktoerRegisterBasePath
fun WireMockServer.getPleiepengerDokumentBaseUrl() = baseUrl() + pleiepengerDokumentBasePath
fun WireMockServer.getSubject() = subject
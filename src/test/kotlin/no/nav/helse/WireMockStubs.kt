package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import java.util.*

private const val aktoerRegisterBasePath = "/aktoerregister-mock"
private const val pleiepengerDokumentBasePath = "/pleiepenger-dokument-mock"

internal fun WireMockServer.stubAktoerRegisterGetAktoerId(
    fnr: String,
    aktoerId: String) : WireMockServer {
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
    return this
}

internal fun WireMockServer.stubLagreDokument() : WireMockServer {
    WireMock.stubFor(
        WireMock.post(WireMock.urlPathMatching(".*$pleiepengerDokumentBasePath.*")).willReturn(
            WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", "${getPleiepengerDokumentBaseUrl()}/v1/dokument/${UUID.randomUUID()}")
                .withStatus(201)
        )
    )
    return this
}

private fun WireMockServer.stubHealthEndpoint(
    path : String
) : WireMockServer{
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching(".*$path")).willReturn(
            WireMock.aResponse()
                .withStatus(200)
        )
    )
    return this
}

internal fun WireMockServer.stubPleiepengerDokumentHealth() = stubHealthEndpoint("$pleiepengerDokumentBasePath/health")

internal fun WireMockServer.getAktoerRegisterBaseUrl() = baseUrl() + aktoerRegisterBasePath
internal fun WireMockServer.getPleiepengerDokumentBaseUrl() = baseUrl() + pleiepengerDokumentBasePath
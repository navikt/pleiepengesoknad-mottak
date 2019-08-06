package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.ktor.testsupport.jws.ClientCredentials
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV1WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getNaisStsWellKnownUrl

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        kafkaEnvironment: KafkaEnvironment? = null,
        port : Int = 8080,
        aktoerRegisterBaseUrl : String? = wireMockServer?.getAktoerRegisterBaseUrl(),
        pleiepeingerDokumentBaseUrl : String? = wireMockServer?.getPleiepengerDokumentBaseUrl(),
        naisStsAuthoriedClients: Set<String> = setOf("srvpleiepengesokna"),
        pleiepengersoknadMottakAzureClientId: String = "pliepengesoknad-mottak",
        azureAuthorizedClients: Set<String> = setOf("azure-client-1", "azure-client-2","azure-client-3")
    ) : Map<String, String>{
        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.aktoer_register_base_url","$aktoerRegisterBaseUrl"),
            Pair("nav.pleiepenger_dokument_base_url","$pleiepeingerDokumentBaseUrl")
        )

        // Kafka
        kafkaEnvironment?.let {
            map["nav.kafka.bootstrap_servers"] = it.brokersURL
            map["nav.kafka.username"] = it.username()
            map["nav.kafka.password"] = it.password()
        }

        // Clients
        if (wireMockServer != null) {
            map["nav.auth.clients.0.alias"] = "nais-sts"
            map["nav.auth.clients.0.client_id"] = "srvpps-mottak"
            map["nav.auth.clients.0.client_secret"] = "very-secret"
            map["nav.auth.clients.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
        }

        if (wireMockServer != null) {
            map["nav.auth.clients.1.alias"] = "azure-v2"
            map["nav.auth.clients.1.client_id"] = "pleiepengesoknad-mottak"
            map["nav.auth.clients.1.private_key_jwk"] = ClientCredentials.ClientA.privateKeyJwk
            map["nav.auth.clients.1.certificate_hex_thumbprint"] = ClientCredentials.ClientA.certificateHexThumbprint
            map["nav.auth.clients.1.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.scopes.lagre-dokument"] = "pleiepenger-dokument/.default"
        }

        // Issuers
        if (wireMockServer != null) {
            map["nav.auth.issuers.0.alias"] = "nais-sts"
            map["nav.auth.issuers.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
            map["nav.auth.nais-sts.authorized_clients"] = naisStsAuthoriedClients.joinToString(", ")
        }
        if (wireMockServer != null) {
            map["nav.auth.issuers.1.type"] = "azure"
            map["nav.auth.issuers.1.alias"] = "azure-v1"
            map["nav.auth.issuers.1.discovery_endpoint"] = wireMockServer.getAzureV1WellKnownUrl()
            map["nav.auth.issuers.1.audience"] = pleiepengersoknadMottakAzureClientId
            map["nav.auth.issuers.1.azure.require_certificate_client_authentication"] = "true"
            map["nav.auth.issuers.1.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")

            map["nav.auth.issuers.2.type"] = "azure"
            map["nav.auth.issuers.2.alias"] = "azure-v2"
            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.issuers.2.audience"] = pleiepengersoknadMottakAzureClientId
            map["nav.auth.issuers.2.azure.require_certificate_client_authentication"] = "true"
            map["nav.auth.issuers.2.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")
        }

        return map.toMap()
    }
}
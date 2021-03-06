package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import no.nav.helse.kafka.KafkaConfig
import java.net.URI

@KtorExperimentalAPI
data class Configuration(private val config : ApplicationConfig) {

    private val issuers = config.issuers()

    init {
        if (issuers.isEmpty()) throw IllegalStateException("Må konfigureres opp minst en issuer.")
    }

    internal fun issuers() = issuers.withoutAdditionalClaimRules()
    internal fun getK9DokumentBaseUrl() = URI(config.getRequiredString("nav.k9_dokument_base_url", secret = false))
    internal fun getKafkaConfig() = config.getRequiredString("nav.kafka.bootstrap_servers", secret = false).let { bootstrapServers ->
        val trustStore = config.getOptionalString("nav.trust_store.path", secret = false)?.let { trustStorePath ->
            config.getOptionalString("nav.trust_store.password", secret = true)?.let { trustStorePassword ->
                Pair(trustStorePath, trustStorePassword)
            }
        }

        KafkaConfig(
            bootstrapServers = bootstrapServers,
            credentials = Pair(config.getRequiredString("nav.kafka.username", secret = false), config.getRequiredString("nav.kafka.password", secret = true)),
            trustStore = trustStore
        )
    }

    private fun getScopesFor(operation: String) = config.getRequiredList("nav.auth.scopes.$operation", secret = false, builder = { it }).toSet()
    internal fun getLagreDokumentScopes() = getScopesFor("lagre-dokument")

}
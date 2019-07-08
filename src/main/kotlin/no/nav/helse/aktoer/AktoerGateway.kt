package no.nav.helse.aktoer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import no.nav.helse.CorrelationId
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

internal class AktoerGateway(
    baseUrl: URI,
    private val accessTokenClient: CachedAccessTokenClient
) {
    private companion object {
        private const val HENTE_AKTOER_ID_OPERATION = "hente-aktoer-id"
        private val logger: Logger = LoggerFactory.getLogger(AktoerGateway::class.java)
    }

    private val completeUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("api","v1","identer"),
        queryParameters = mapOf(
            "gjeldende" to listOf("true"),
            "identgruppe" to listOf("AktoerId")
        )
    ).toString()

    private val objectMapper = configuredObjectMapper()

    internal suspend fun getAktoerId(
        ident: Ident,
        correlationId: CorrelationId
    ) : AktoerId {

        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()

        val httpRequest = completeUrl
            .httpGet()
            .header(
                HttpHeaders.Authorization to authorizationHeader,
                HttpHeaders.Accept to "application/json",
                "Nav-Consumer-Id" to "pleiepengesoknad-mottak",
                "Nav-Personidenter" to ident.value,
                "Nav-Call-Id" to correlationId.id
            )

        val httpResponse = Retry.retry(
            operation = HENTE_AKTOER_ID_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0
        ) {
            val (request, response, result) = Operation.monitored(
                app = "pleiepengesoknad-mottak",
                operation = HENTE_AKTOER_ID_OPERATION,
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }
            result.fold(
                { success -> objectMapper.readValue<Map<String,AktoerRegisterIdentResponse>>(success)},
                { error ->
                    logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                    logger.error(error.toString())
                    throw IllegalStateException("HTTP ${response.statusCode} -> Feil ved henting av Aktør ID.")
                }
            )
        }


        if (!httpResponse.containsKey(ident.value)) {
            throw IllegalStateException("Svar fra '$completeUrl' inneholdt ikke data om det forsespurte fødselsnummeret.")
        }

        val identResponse =  httpResponse.get(key = ident.value)

        if (identResponse!!.feilmelding!= null) {
            logger.warn("Mottok feilmelding fra AktørRegister : '${identResponse.feilmelding}'")
        }

        if (identResponse.identer == null) {
            throw IllegalStateException("Fikk 0 AktørID'er for det forsespurte fødselsnummeret mot '$completeUrl'")
        }

        if (identResponse.identer.size != 1) {
            throw IllegalStateException("Fikk ${identResponse.identer.size} AktørID'er for det forsespurte fødselsnummeret mot '$completeUrl'")
        }

        val aktoerId = AktoerId(identResponse.identer[0].ident)
        logger.trace("Resolved AktørID $aktoerId")
        return aktoerId
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }
}

data class AktoerRegisterdent(val ident: String, val identgruppe: String)
data class AktoerRegisterIdentResponse(val feilmelding : String?, val identer : List<AktoerRegisterdent>?)
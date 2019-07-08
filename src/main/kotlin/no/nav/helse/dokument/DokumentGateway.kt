package no.nav.helse.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktoerId
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.helse.mottak.v1.Vedlegg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration

internal class DokumentGateway(
    private val accessTokenClient: CachedAccessTokenClient,
    baseUrl : URI
){

    private companion object {
        private const val LAGRE_DOKUMENT_OPERATION = "lagre-dokument"
        private val logger: Logger = LoggerFactory.getLogger(DokumentGateway::class.java)
    }

    private val completeUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("v1", "dokument")
    )

    private val objectMapper = configuredObjectMapper()

    internal suspend fun lagreDokmenter(
        dokumenter: Set<Dokument>,
        aktoerId: AktoerId,
        correlationId: CorrelationId
    ) : List<URI> {
        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()

        return coroutineScope {
            val deferred = mutableListOf<Deferred<URI>>()
            dokumenter.forEach {
                deferred.add(async {
                    requestLagreDokument(
                        dokument = it,
                        correlationId = correlationId,
                        aktoerId = aktoerId,
                        authorizationHeader = authorizationHeader
                    )
                })
            }
            deferred.awaitAll()
        }
    }


    private suspend fun requestLagreDokument(
        dokument: Dokument,
        aktoerId: AktoerId,
        correlationId: CorrelationId,
        authorizationHeader: String
    ) : URI {

        val urlMedEier = Url.buildURL(
            baseUrl = completeUrl,
            queryParameters = mapOf("eier" to listOf(aktoerId.id))
        ).toString()

        val body = objectMapper.writeValueAsBytes(dokument)
        val contentStream = { ByteArrayInputStream(body) }

        return Retry.retry(
            operation = LAGRE_DOKUMENT_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0
        ) {
            val (request, response, result) = Operation.monitored(
                app = "pleiepengesoknad-mottak",
                operation = LAGRE_DOKUMENT_OPERATION,
                resultResolver = { 201 == it.second.statusCode }
            ) {
                urlMedEier
                    .httpPost()
                    .body(contentStream)
                    .header(
                        HttpHeaders.Authorization to authorizationHeader,
                        HttpHeaders.XCorrelationId to correlationId.id,
                        HttpHeaders.ContentType to "application/json"
                    )
                    .awaitStringResponseResult()
            }
            result.fold(
                { URI(response.header(HttpHeaders.Location).first()) },
                { error ->
                    logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                    logger.error(error.toString())
                    throw IllegalStateException("HTTP {$response.statusCode} -> Feil ved lagring av dokument.")
                }
            )
        }
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        return objectMapper
    }
}
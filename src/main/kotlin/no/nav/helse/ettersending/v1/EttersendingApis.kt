package no.nav.helse.ettersending.v1

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.request.receiveStream
import io.ktor.response.ApplicationResponse
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.helse.Metadata
import no.nav.helse.getSoknadId
import no.nav.helse.mottakEttersending.v1.EttersendingIncoming
import no.nav.helse.mottakEttersending.v1.EttersendingV1MottakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import validate

private val logger: Logger = LoggerFactory.getLogger("no.nav.EttersendingApis")

internal fun Route.EttersendingApis(
    ettersendingV1MottakService: EttersendingV1MottakService
) {

    post("v1/ettersend") {
        val soknadId = call.getSoknadId()
        val metadata = call.metadata()
        val soknad = call.ettersending()

        ettersendingV1MottakService.leggTilProsessering(
            soknadId = soknadId,
            metadata = metadata,
            soknad = soknad
        )
        call.respond(HttpStatusCode.Accepted, mapOf("id" to soknadId.id))
    }
}

private suspend fun ApplicationCall.ettersending() : EttersendingIncoming {
    val json = receiveStream().use { String(it.readAllBytes(), Charsets.UTF_8) }
    val incoming = EttersendingIncoming(json)
    incoming.validate()
    return incoming
}

private fun ApplicationCall.metadata() = Metadata(
    version = 1,
    correlationId = request.getCorrelationId(),
    requestId = response.getRequestId()
)


private fun ApplicationRequest.getCorrelationId(): String {
    return header(HttpHeaders.XCorrelationId) ?: throw IllegalStateException("Correlation Id ikke satt")
}

private fun ApplicationResponse.getRequestId(): String {
    return headers[HttpHeaders.XRequestId] ?: throw IllegalStateException("Request Id ikke satt")
}

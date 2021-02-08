package no.nav.helse.mottak.v1

import no.nav.helse.AktoerId
import no.nav.helse.SoknadId
import org.json.JSONObject
import java.net.URI
import java.util.*

object JsonKeys {
    internal const val vedlegg = "vedlegg"
    internal const val søker = "søker"
    internal const val aktørId = "aktørId"
    internal const val vedleggUrls = "vedleggUrls"
    internal const val søknadId = "søknadId"
    internal const val fødselsnummer = "fødselsnummer"
    internal const val content = "content"
    internal const val contentType = "contentType"
    internal const val title = "title"
}

internal class SoknadV1Incoming(json: String) {
    private val jsonObject = JSONObject(json)
    internal val sokerFodselsNummer : String
    internal val vedlegg: List<Vedlegg>
    internal val søknadId: SoknadId?


    private fun hentVedlegg() : List<Vedlegg> {
        val vedlegg = mutableListOf<Vedlegg>()
        jsonObject.getJSONArray(JsonKeys.vedlegg).forEach {
            val vedleggJson = it as JSONObject
            vedlegg.add(Vedlegg(
                content = Base64.getDecoder().decode(vedleggJson.getString(JsonKeys.content)),
                contentType = vedleggJson.getString(JsonKeys.contentType),
                title = vedleggJson.getString(JsonKeys.title)
            ))
        }
        return vedlegg.toList()
    }

    private fun hentSøknadId(): SoknadId? = when(val søknadId = jsonObject.optString(JsonKeys.søknadId, "")) {
        "" -> null
        else -> SoknadId(søknadId)
    }

    init {
        sokerFodselsNummer = jsonObject.getJSONObject(JsonKeys.søker).getString(JsonKeys.fødselsnummer)
        vedlegg = hentVedlegg()
        jsonObject.remove(JsonKeys.vedlegg)
        søknadId = hentSøknadId()
    }

    internal val sokerAktoerId = AktoerId(jsonObject.getJSONObject(JsonKeys.søker).getString(JsonKeys.aktørId))

    internal fun medVedleggUrls(vedleggUrls: List<URI>) : SoknadV1Incoming {
        jsonObject.put(JsonKeys.vedleggUrls, vedleggUrls)
        return this
    }

    internal fun medSøknadId(soknadId: SoknadId) : SoknadV1Incoming {
        when (søknadId) {
            null -> jsonObject.put(JsonKeys.søknadId, soknadId.id)
            else -> jsonObject.put(JsonKeys.søknadId, søknadId.id)
        }
        return this
    }

    internal fun somOutgoing() = SoknadV1Outgoing(jsonObject)

}

internal class SoknadV1Outgoing(internal val jsonObject: JSONObject) {
    internal val soknadId = SoknadId(jsonObject.getString(JsonKeys.søknadId))
    internal val vedleggUrls = hentVedleggUrls()
    private fun hentVedleggUrls() : List<URI> {
        val vedleggUrls = mutableListOf<URI>()
        jsonObject.getJSONArray(JsonKeys.vedleggUrls).forEach {
            vedleggUrls.add(URI(it as String))
        }
        return vedleggUrls.toList()
    }
}

data class Vedlegg (
    val content : ByteArray,
    val contentType : String,
    val title : String
)
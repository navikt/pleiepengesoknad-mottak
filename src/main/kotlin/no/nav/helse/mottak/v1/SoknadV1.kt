package no.nav.helse.mottak.v1

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.AktoerId
import no.nav.helse.SoknadId
import org.apache.commons.codec.binary.Base64
import org.json.JSONObject
import java.net.URI

private object JsonKeys {
    internal const val vedlegg = "vedlegg"
    @JsonAlias("soker") internal const val søker = "søker"
    @JsonAlias("aktoer_id") internal const val aktørId = "aktørId"
    internal const val vedleggUrls = "vedlegg_urls"
    @JsonAlias("soknad_id") internal const val søknadId = "søknadId"
    @JsonAlias("content_type") internal const val fødselsnummer = "fødselsnummer"
    internal const val content = "content"
    @JsonAlias("content_type") internal const val contentType = "contentType"
    internal const val title = "title"
}

internal class SoknadV1Incoming(json: String) {
    private val jsonObject = JSONObject(json)
    internal val sokerFodselsNummer : String
    internal val vedlegg: List<Vedlegg>

    private fun hentVedlegg() : List<Vedlegg> {
        val vedlegg = mutableListOf<Vedlegg>()
        jsonObject.getJSONArray(JsonKeys.vedlegg).forEach {
            val vedleggJson = it as JSONObject
            vedlegg.add(Vedlegg(
                content = Base64.decodeBase64(vedleggJson.getString(JsonKeys.content)),
                contentType = vedleggJson.getString(JsonKeys.contentType),
                title = vedleggJson.getString(JsonKeys.title)
            ))
        }
        return vedlegg.toList()
    }

    init {
        sokerFodselsNummer = jsonObject.getJSONObject(JsonKeys.søker).getString(JsonKeys.fødselsnummer)
        vedlegg = hentVedlegg()
        jsonObject.remove(JsonKeys.vedlegg)
    }

    internal val sokerAktoerId = AktoerId(jsonObject.getJSONObject(JsonKeys.søker).getString(JsonKeys.aktørId))

    internal fun medVedleggUrls(vedleggUrls: List<URI>) : SoknadV1Incoming {
        jsonObject.put(JsonKeys.vedleggUrls, vedleggUrls)
        return this
    }

    internal fun medSoknadId(soknadId: SoknadId) : SoknadV1Incoming {
        jsonObject.put(JsonKeys.søknadId, soknadId.id)
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
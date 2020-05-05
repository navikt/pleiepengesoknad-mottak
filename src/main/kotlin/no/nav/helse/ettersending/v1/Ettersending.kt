package no.nav.helse.mottakEttersending.v1

import no.nav.helse.AktoerId
import no.nav.helse.SoknadId
import org.json.JSONObject
import java.net.URI
import java.util.*

private object JsonKeys {
    internal const val søker = "soker"
    internal const val aktørId = "aktoer_id"
    internal const val søknadId = "soknad_id"
    internal const val fødselsnummer = "fodselsnummer"
    internal const val vedleggUrls = "vedlegg_urls"
    internal const val vedlegg = "vedlegg"
    internal const val content = "content"
    internal const val contentType = "content_type"
    internal const val title = "title"
    internal const val titler = "titler"
}

internal class EttersendingIncoming(json: String) {
    internal val sokerFodselsNummer : String
    private val jsonObject = JSONObject(json)
    internal val vedlegg: List<Vedlegg>

    private fun hentVedlegg() : List<Vedlegg> {
        val vedlegg = mutableListOf<Vedlegg>()
        jsonObject.getJSONArray(JsonKeys.vedlegg).forEach {
            val vedleggJson = it as JSONObject
            vedlegg.add(
                Vedlegg(
                    content = Base64.getDecoder().decode(vedleggJson.getString(JsonKeys.content)),
                    contentType = vedleggJson.getString(JsonKeys.contentType),
                    title = vedleggJson.getString(JsonKeys.title)
                )
            )
        }
        return vedlegg.toList()
    }

    init {
        sokerFodselsNummer = jsonObject.getJSONObject(JsonKeys.søker).getString(JsonKeys.fødselsnummer)
        vedlegg = hentVedlegg()
        jsonObject.remove(JsonKeys.vedlegg)
    }

    internal val søkerAktørId = AktoerId(jsonObject.getJSONObject(JsonKeys.søker).getString(
        JsonKeys.aktørId
    ))


    internal fun medSoknadId(soknadId: SoknadId): EttersendingIncoming {
        jsonObject.put(JsonKeys.søknadId, soknadId.id)
        return this
    }

    internal fun medVedleggUrls(vedleggUrls: List<URI>) : EttersendingIncoming {
        jsonObject.put(JsonKeys.vedleggUrls, vedleggUrls)
        return this
    }

    internal fun medVedleggTitler() : EttersendingIncoming{
        val listeOverTitler = mutableListOf<String>()
        for(vedlegg in vedlegg){
            listeOverTitler.add(vedlegg.title)
        }
        jsonObject.put(JsonKeys.titler, listeOverTitler)
        return this
    }

    internal fun somOutgoing() = EttersendingOutgoing(jsonObject)

}

internal class EttersendingOutgoing(internal val jsonObject: JSONObject) {
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

data class Vedlegg(
    val content: ByteArray,
    val contentType: String,
    val title: String
)
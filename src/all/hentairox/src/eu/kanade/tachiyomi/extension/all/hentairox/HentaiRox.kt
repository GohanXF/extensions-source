package eu.kanade.tachiyomi.extension.all.hentairox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.imgAttr
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import java.io.IOException

class HentaiRox(
    lang: String = "all",
    override val mangaLang: String = LANGUAGE_MULTI,
) : GalleryAdults(
    "HentaiRox",
    "https://hentairox.com",
    lang = lang,
) {
    override val supportsLatest = true
    override val useIntermediateSearch: Boolean = true
    override val supportAdvancedSearch: Boolean = true
    override val supportSpeechless: Boolean = true

    override fun Element.mangaLang() =
        select("a:has(.thumb_flag)").attr("href")
            .removeSuffix("/").substringAfterLast("/")
            .let {
                // Include Speechless in search results
                if (it == LANGUAGE_SPEECHLESS) mangaLang else it
            }

    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("Accept-Encoding")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .addInterceptor(
            fun(chain): Response {
                val response = chain.proceed(chain.request())
                if (!response.headers("Content-Type").toString().contains("text/html")) return response

                val responseContentType = response.body.contentType()
                val responseString = response.body.string()

                if (responseString.contains("Overload... Please use the advanced search")) {
                    response.close()
                    throw IOException("HentaiRox search is overloaded try again later")
                }

                return response.newBuilder()
                    .body(responseString.toResponseBody(responseContentType))
                    .build()
            },
        ).build()

    /* Details */
    override fun Element.getInfo(tag: String): String {
        val prefix = when (tag.lowercase()) {
            "tags" -> "tag"
            "artist", "artists" -> "artist"
            "language", "languages" -> "language"
            "category", "categories" -> "category"
            "parody", "parodies" -> "parody"
            else -> return ""
        }

        return select("li:has(.tags_text:matchesOwn(^$tag:?)) a.tag[href^=\"/$prefix/\"]")
            .mapNotNull { el ->
                val name = el.selectFirst(".item_name")?.text()?.trim() ?: return@mapNotNull null

                if (prefix == "tag") {
                    genres[name] = el.attr("href").removePrefix("/$prefix/").removeSuffix("/")
                }

            name
            }
        .joinToString()
    }

    override fun Element.getCover() =
        selectFirst(".left_cover img")?.imgAttr()

    override val mangaDetailInfoSelector = ".gallery_first"

    /* Pages */
    override val thumbnailSelector = ".gthumb"
    override val pageUri = "view"
}

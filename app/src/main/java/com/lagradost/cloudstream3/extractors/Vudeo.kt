package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup


open class Vudeo : ExtractorApi() {
    override val name: String = "Vudeo"
    override val mainUrl: String = "https://www.vudeo.io"
    private val srcRegex =
        Regex("sources: \\[\"(.*?)\"]")
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?, additionalInfo: List<String?>?): List<ExtractorLink>? {

        val quality = getQualityFromName(additionalInfo?.get(0))
        val lang = additionalInfo?.get(1) ?: "unknown language"
        with(app.get(url).text) {  // raised error ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) is due to the response: "error_nofile"
            val document = Jsoup.parse(this)
            srcRegex.find(document.html())?.groupValues?.get(1)?.let { link ->
                return listOf(
                    ExtractorLink(
                        name,
                        "$name $lang",
                        link,
                        url,
                        quality,
                    )
                )
            }
        }
        return null
    }
}
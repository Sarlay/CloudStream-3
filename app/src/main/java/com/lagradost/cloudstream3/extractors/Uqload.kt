package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Uqload1 : Uqload() {
    override var mainUrl = "https://uqload.com"
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    private val srcRegex = Regex("""sources:.\[(.*?)\]""")  // would be possible to use the parse and find src attribute
    override val requiresReferer = true


    override suspend fun getUrl(url: String, referer: String?, additionalInfo: List<String?>?): List<ExtractorLink>? {
        val quality = getQualityFromName(additionalInfo?.get(0))
        val lang = additionalInfo?.get(1) ?: ""

        with(app.get(url)) {  // raised error ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) is due to the response: "error_nofile"
            srcRegex.find(this.text)?.groupValues?.get(1)?.replace("\"", "")?.let { directLink ->
                return listOf(
                    ExtractorLink(
                        name,
                        "$name $lang",
                        directLink,
                        url,
                        quality,
                    )
                )
            }
        }
        return null
    }
}

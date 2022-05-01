package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.animeproviders.AllAnimeProvider
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.lang.Exception
import java.net.URI

class RadarrProvider : MainAPI() {
    override var name = "Radarr"
    override val hasQuickSearch = false
    override val hasMainPage = false
    override var mainUrl = "https://sarlay.oberon.usbx.me/radarr"
    val apiKey = "508ce893b13943aa9017121e99a418cd"
    override val supportedTypes = setOf(TvType.Movie, TvType.Documentary, TvType.AnimeMovie, TvType.AnimeMovie)



    fun getAuthHeader(storedCredentials: String?): Map<String, String> {
        if (storedCredentials == null) {
            return mapOf(Pair("Authorization", "Basic "))  // no Authorization headers
        }
        val basicAuthToken = base64Encode(storedCredentials.toByteArray())  // will this be loaded when not using the provider ??? can increase load
        return mapOf(Pair("Authorization", "Basic $basicAuthToken"))
    }


    data class lookupJson (
        @JsonProperty("tmdbId") var tmdbId:String,
        @JsonProperty("title") var title: String,
        @JsonProperty("remotePoster") var posterUrl: String?,
        @JsonProperty("year") var year: Int?,
    )

    data class loadJson (
        //@JsonProperty("id") var id:String,
        //@JsonProperty("tmdbId") var tmdbId:String,
        @JsonProperty("title") var title: String,
        @JsonProperty("overview") var plot: String,
        //@JsonProperty("images") var images: List<String?>?,
        //@JsonProperty("status") var status: String?,
        @JsonProperty("year") var year: Int?,
        //@JsonProperty("hasFile") var hasFile: Boolean?,
        @JsonProperty("remotePoster") var posterUrl: String?,
        //@JsonProperty("genres") var genres: List<String?>?,
        )

    override suspend fun load(tmdbId: String): LoadResponse {
        println("received2: $tmdbId")
        val loadResponse = app.get("$mainUrl/api/v3/movie/lookup/tmdb?tmdbId=$tmdbId&apikey=$apiKey").text
        val resultsResponse: loadJson = mapper.readValue(loadResponse)
        return newMovieLoadResponse(resultsResponse.title, tmdbId, TvType.Movie, tmdbId) {  // here url = tmdbId
                this.year = resultsResponse.year
                this.plot = resultsResponse.plot
                this.posterUrl = resultsResponse.posterUrl
        }
    }




    // Searching returns a SearchResponse, which can be one of the following: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    // Each of the classes requires some different data, but always has some critical things like name, poster and url.
    override suspend fun search(query: String): List<SearchResponse> {
        // Simply looking at devtools network is enough to spot a request like:
        // https://vidembed.cc/search.html?keyword=neverland where neverland is the query, can be written as below.
        val searchResponse = app.get("$mainUrl/api/v3/movie/lookup?term=$query&apikey=$apiKey").text
        val results: List<lookupJson> = mapper.readValue(searchResponse)

        return results.map {
            println(it.tmdbId)
            newMovieSearchResponse(it.title, it.tmdbId, TvType.Movie, false) {  // here url = tmdbId
                this.year = it.year
                this.posterUrl = it.posterUrl
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val radarrHeaders = mapOf(
            "Host" to "mcloud.to",
            "tmdbId" to "121856",
            "title" to "Assassin's Creed",
            "monitored" to "false",
            "qualityProfileId" to "3", // HD-720p
            "rootFolderPath" to "/home/sarlay/media/Movies",
        )
        val test = app.post(mainUrl + "/api/v3/movie/", headers = radarrHeaders).text
        println(test)
        return true
    }


}
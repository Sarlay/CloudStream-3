package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class NginxProvider : MainAPI() {
    override var name = "Nginx"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)



    fun getAuthHeader(storedCredentials: String?): Map<String, String> {
        if (storedCredentials == null) {
            return mapOf(Pair("Authorization", "Basic "))  // no Authorization headers
        }
        val basicAuthToken = base64Encode(storedCredentials.toByteArray())  // will this be loaded when not using the provider ??? can increase load
        return mapOf(Pair("Authorization", "Basic $basicAuthToken"))
    }

    override suspend fun load(url: String): LoadResponse? {

        val authHeader = getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after
        // url can be tvshow.nfo for series or mediaRootUrl for movies

        val mediaRootDocument = app.get(url, authHeader).document
        val isAFolder = url.endsWith("/")

        val metadataFile = mediaRootDocument.getElementsByAttributeValueContaining("href", ".nfo").attr("href")
        val nfoUrl = url + metadataFile  // metadata url file

        val isSerie = nfoUrl.contains("tvshow.nfo")

        val metadataDocument =  if (metadataFile != null) {  // if metadata exist
            app.get(nfoUrl, authHeader).document  // get the metadata nfo file
        } else {
            null
        }

        val title = metadataDocument?.selectFirst("title")?.text() ?: url.substring(url.lastIndexOf("/") + 1)

        val description = metadataDocument?.selectFirst("plot")?.text()

        if (!isSerie) {  // it's not a serie, its a movie (or smthing else idk)
            if (metadataFile != null && isAFolder) {  // There is metadata and library is correctly organised!
                val poster = metadataDocument?.selectFirst("thumb")?.text()
                val trailer = metadataDocument?.select("trailer")?.mapNotNull {
                    it?.text()?.replace(
                        "plugin://plugin.video.youtube/play/?video_id=",
                        "https://www.youtube.com/watch?v="
                    )
                }

                val date = metadataDocument?.selectFirst("year")?.text()?.toIntOrNull()
                val durationInMinutes = metadataDocument?.selectFirst("duration")?.text()?.toIntOrNull()
                val ratingAverage = metadataDocument?.selectFirst("value")?.text()?.toIntOrNull()
                val tagsList = metadataDocument?.select("genre")
                    ?.mapNotNull {   // all the tags like action, thriller ...
                        it?.text()
                    }
                val actors = metadataDocument?.select("actor")?.mapNotNull {
                    val name = it?.selectFirst("name")?.text() ?: return@mapNotNull null
                    val image = it.selectFirst("thumb")?.text() ?: return@mapNotNull null
                    Actor(name, image)
                }

                val mkvElementsResult = mediaRootDocument.getElementsByAttributeValueContaining(  // list of all urls of the mkv url in the webpage
                    "href",
                    ".mkv"
                )

                val mp4ElementsResult = mediaRootDocument.getElementsByAttributeValueContaining(  // list of all urls of the webpage
                    "href",
                    ".mp4"
                )


                val dataList = if (mkvElementsResult.count() > 0) {  // there is probably a better way to do it
                    mkvElementsResult[0].attr("href").toString()
                } else {
                    if(mp4ElementsResult.count() > 0) {
                        mp4ElementsResult[0].attr("href").toString()
                    } else {
                        null
                    }
                }


                if (dataList != null) {
                    return newMovieLoadResponse(
                        title,
                        url,
                        TvType.Movie,
                        url + dataList,
                    ) {
                        this.year = date
                        this.plot = description
                        this.rating = ratingAverage
                        this.tags = tagsList
                        this.trailers = trailer
                        this.duration = durationInMinutes
                        addPoster(poster, authHeader)
                        addActors(actors)
                    }

                }
            } else {
                if (".mp4" in url || ".mkv" in url) {
                    val fixedUrl = fixUrl(url)
                    return newMovieLoadResponse(
                        title,
                        fixedUrl,
                        TvType.Movie,
                        fixedUrl,
                    )
                }
            }
            return null
        } else  // a tv serie
        {

            val list = ArrayList<Pair<Int, String>>()
            val mediaRootUrl = url.replace("tvshow.nfo", "")
            val posterUrl = mediaRootUrl + "poster.jpg"
            val mediaRootDocument = app.get(mediaRootUrl, authHeader).document
            val seasons =
                mediaRootDocument.getElementsByAttributeValueContaining("href", "Season%20")


            val tagsList = metadataDocument?.select("genre")
                ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
                    it?.text()
                }

            //val actorsList = document.select("actor")
            //    ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
            //        it?.text()
            //    }

            seasons.forEach { element ->
                val season =
                    element.attr("href")?.replace("Season%20", "")?.replace("/", "")?.toIntOrNull()
                val href = mediaRootUrl + element.attr("href")
                if (season != null && season > 0 && href.isNotBlank()) {
                    list.add(Pair(season, href))
                }
            }

            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()


            list.apmap { (seasonInt, seasonString) ->
                val seasonDocument = app.get(seasonString, authHeader).document
                val episodes = seasonDocument.getElementsByAttributeValueContaining(
                    "href",
                    ".nfo"
                ) // get metadata
                    episodes.forEach { episode ->
                        val nfoDocument = app.get(seasonString + episode.attr("href"), authHeader).document // get episode metadata file
                        val epNum = nfoDocument.selectFirst("episode")?.text()?.toIntOrNull()
                        val poster =
                            seasonString + episode.attr("href").replace(".nfo", "-thumb.jpg")
                        val name = nfoDocument.selectFirst("title").text()
                        // val seasonInt = nfoDocument.selectFirst("season").text().toIntOrNull()
                        val date = nfoDocument.selectFirst("aired")?.text()
                        val plot = nfoDocument.selectFirst("plot")?.text()

                        val dataList = seasonDocument.getElementsByAttributeValueContaining(
                            "href",
                            episode.attr("href").replace(".nfo", "")
                        )
                        val data = seasonString + dataList.firstNotNullOf { item -> item.takeIf { (!it.attr("href").contains(".nfo") &&  !it.attr("href").contains(".jpg"))} }.attr("href").toString()  // exclude poster and nfo (metadata) file

                        episodeList.add(
                            newEpisode(data) {
                                    this.name = name
                                    this.season = seasonInt
                                    this.episode = epNum
                                    this.posterUrl = poster  // will require headers too
                                    this.description = plot
                                    addDate(date)
                            }
                        )
                    }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.name = title
                this.url = url
                this.episodes = episodeList
                this.plot = description
                this.tags = tagsList
                addPoster(posterUrl, authHeader)
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val authHeader =
            getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after

        try {
            val mediaRootUrl = data.substringBeforeLast("/")
            val mediaRootDocument = app.get(mediaRootUrl, authHeader).document

            val subtitlesElements = mediaRootDocument.getElementsByAttributeValueContaining(
                // list of all urls of the mkv url in the webpage
                "href",
                ".srt", // all of subtitles files in the folder
            )

            subtitlesElements.forEach {
                subtitleCallback.invoke(
                    SubtitleFile(
                        SubtitleHelper.fromTwoLettersToLanguage(it.attr("href").replace(".srt", "").substringAfterLast(".")) ?: "English",
                        mediaRootUrl + "/" + it.attr("href")
                    )
                )
            }
        } catch (e: Exception) {
            logError(e)
        }

        callback.invoke (
            ExtractorLink(
                name,
                name,
                data,
                data,  // referer not needed
                Qualities.Unknown.value,
                false,
                authHeader,
            )
        )
        return true
    }


    private fun cleanElement(elementUrl: String): String {
         return if (elementUrl[0] == '/') {  // if starts by "/", remove it
            elementUrl.drop(1)
        } else {
             elementUrl
         }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val authHeader = getAuthHeader(storedCredentials)  // call again because it isn't reloaded if in main class and storedCredentials loads after
        if (mainUrl == "NONE" || mainUrl == "" || mainUrl == "nginx_url_key"){
            throw ErrorLoadingException("No nginx url specified in the settings: Nginx Settigns > Nginx server url, try again in a few seconds") // getString(R.string.nginx_load_exception) or @strings/nginx_load_exception
        }
        val document = app.get(mainUrl, authHeader).document
        val categories = document.select("a") // select all of the categories
        val returnList = categories.mapNotNull {  // for each category
            val categoryPath = mainUrl + cleanElement(it.attr("href")) // ?: return@mapNotNull null // get the url of the category; like http://192.168.1.10/media/Movies/
            val categoryTitle = it.text()  // get the category title like Movies/ or Series/
            if (categoryTitle != "../" && categoryTitle != "/") {  // exclude parent dir
                val categoryDocument = app.get(categoryPath, authHeader).document // queries the page http://192.168.1.10/media/Movies/
                val contentLinks = categoryDocument.select("a")  // select all links
                val currentList = contentLinks.mapNotNull { head ->  // for each of those elements
                    val linkToElement = head.attr("href")
                    if (linkToElement != "../" && linkToElement != "/") {
                        val isAFolderBool = linkToElement.endsWith("/")
                        try {
                            val mediaRootUrl =
                                categoryPath + cleanElement(linkToElement) // like http://192.168.1.10/Series/Chernobyl/ or http://192.168.1.10/Movies/zjfbfvz.mp4
                            if (isAFolderBool) {  // content is organised in folders for each element

                                /*
                                ├── Movies
                                     ├── Eternals (2021)   // the media root folder will be 'http://192.168.1.10/media/Movies/Eternals (2021)/'
                                          ├── Eternals.2021.MULTi.WiTH.TRUEFRENCH.iMAX.1080p.DSNP.WEB-DL.DDP5.1.H264-FRATERNiTY.mkv
                                          ├── Eternals.2021.MULTi.WiTH.TRUEFRENCH.iMAX.1080p.DSNP.WEB-DL.DDP5.1.H264-FRATERNiTY.en.srt
                                          ├── Eternals.2021.MULTi.WiTH.TRUEFRENCH.iMAX.1080p.DSNP.WEB-DL.DDP5.1.H264-FRATERNiTY.nfo
                                          ├── fanart.jpg
                                          └── poster.jpg
                                */

                                val mediaDocument = app.get(mediaRootUrl, authHeader).document
                                val nfoFilename = try {
                                    mediaDocument.getElementsByAttributeValueContaining(
                                        "href",
                                        ".nfo"
                                    )[0]?.attr("href")
                                } catch (e: Exception) {
                                    logError(e)
                                    null
                                }

                                val isSerieType =
                                    nfoFilename.toString() == "tvshow.nfo" // will be a movie if no metadata


                                val nfoPath = if (nfoFilename != null) {
                                    mediaRootUrl + nfoFilename // must exist or will raise errors, only the first one is taken
                                } else {
                                    null
                                }

                                val nfoContent = if (nfoPath != null) {
                                    app.get(nfoPath, authHeader).document  // get all the metadata
                                } else {
                                    null
                                }


                                if (isSerieType) {
                                    val serieName = nfoContent?.select("title")?.text() ?: linkToElement  // name of the media root foler

                                    val posterUrl = mediaRootUrl + "poster.jpg"  // poster.jpg in the same folder

                                    newTvSeriesSearchResponse(
                                        serieName,
                                        mediaRootUrl,
                                        TvType.TvSeries,
                                    ) {
                                        addPoster(posterUrl, authHeader)
                                    }
                                } else {  // Movie
                                    val movieName = nfoContent?.select("title")?.text() ?: linkToElement
                                    val posterUrl = mediaRootUrl + "poster.jpg" // poster should be stored in the same folder
                                    return@mapNotNull newMovieSearchResponse(
                                        movieName,
                                        mediaRootUrl,
                                        TvType.Movie,
                                    ) {
                                        addPoster(posterUrl, authHeader)
                                    }
                                }
                            } else {  // return direct file

                                /*
                                ├── Movies
                                     ├── Eternals.2021.MULTi.WiTH.TRUEFRENCH.iMAX.1080p.DSNP.WEB-DL.DDP5.1.H264-FRATERNiTY.mkv  // the media root folder
                                     ├── Juste la fin du monde (2016) VOF 1080p mHD x264 AC3-SANTACRUZ.mkv
                                */
                                return@mapNotNull newMovieSearchResponse(
                                    linkToElement,
                                    mediaRootUrl,
                                    TvType.Movie,
                                )
                            }

                        } catch (e: Exception) {  // can cause issues invisible errors
                            null
                            //logError(e) // not working because it changes the return type of currentList to Any
                        }


                    } else null
                }
                if (currentList.isNotEmpty() && categoryTitle != "../" && categoryTitle != "/") {  // exclude upper dir
                    HomePageList(categoryTitle.replace("/", " "), currentList)
                } else null
            } else null  // the path is ../ which is parent directory
        }
        // if (returnList.isEmpty()) return null // maybe doing nothing idk
        return HomePageResponse(returnList)
    }
}

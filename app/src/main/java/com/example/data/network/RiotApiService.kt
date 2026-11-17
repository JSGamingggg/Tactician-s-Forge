package com.example.data.network

import com.example.BuildConfig
import com.example.data.model.RiotAccountDto
import com.example.data.model.RiotPlatform
import com.example.data.model.RiotRegion
import com.example.data.model.TftLeagueEntryDto
import com.example.data.model.TftMatchDto
import com.example.data.model.TftSummonerDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RiotRegionalApi {
    @GET("riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
    suspend fun getAccountByRiotId(
        @Path("gameName") gameName: String,
        @Path("tagLine") tagLine: String
    ): Response<RiotAccountDto>

    @GET("tft/match/v1/matches/by-puuid/{puuid}/ids")
    suspend fun getMatchIdsByPuuid(
        @Path("puuid") puuid: String,
        @Query("start") start: Int = 0,
        @Query("count") count: Int = 10
    ): Response<List<String>>

    @GET("tft/match/v1/matches/{matchId}")
    suspend fun getMatchById(
        @Path("matchId") matchId: String
    ): Response<TftMatchDto>
}

interface RiotPlatformApi {
    @GET("tft/summoner/v1/summoners/by-puuid/{puuid}")
    suspend fun getSummonerByPuuid(
        @Path("puuid") puuid: String
    ): Response<TftSummonerDto>

    @GET("tft/league/v1/by-puuid/{puuid}")
    suspend fun getLeagueEntriesByPuuid(
        @Path("puuid") puuid: String
    ): Response<List<TftLeagueEntryDto>>

    @GET("tft/league/v1/entries/by-summoner/{summonerId}")
    suspend fun getLeagueEntriesBySummoner(
        @Path("summonerId") summonerId: String
    ): Response<List<TftLeagueEntryDto>>
}

interface DDragonApi {
    @GET("api/versions.json")
    suspend fun getVersions(): Response<List<String>>

    @GET("cdn/{version}/data/en_US/tft-champion.json")
    suspend fun getTftChampionsJson(
        @Path("version") version: String
    ): Response<okhttp3.ResponseBody>

    @GET("cdn/{version}/data/en_US/tft-item.json")
    suspend fun getTftItemsJson(
        @Path("version") version: String
    ): Response<okhttp3.ResponseBody>

    @GET("cdn/{version}/data/en_US/tft-augments.json")
    suspend fun getTftAugmentsJson(
        @Path("version") version: String
    ): Response<okhttp3.ResponseBody>
}

object RiotApiClientFactory {
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val riotAuthInterceptor = Interceptor { chain ->
        val apiKey = BuildConfig.RIOT_API_KEY
        val reqBuilder = chain.request().newBuilder()
        if (apiKey.isNotBlank() && apiKey != "MY_RIOT_API_KEY") {
            reqBuilder.addHeader("X-Riot-Token", apiKey)
        }
        chain.proceed(reqBuilder.build())
    }

    private val riotOkHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(riotAuthInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val publicOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun createRegionalApi(region: RiotRegion): RiotRegionalApi =
        Retrofit.Builder()
            .baseUrl(region.host)
            .client(riotOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RiotRegionalApi::class.java)

    fun createPlatformApi(platform: RiotPlatform): RiotPlatformApi =
        Retrofit.Builder()
            .baseUrl(platform.host)
            .client(riotOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RiotPlatformApi::class.java)

    val ddragonApi: DDragonApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ddragon.leagueoflegends.com/")
            .client(publicOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DDragonApi::class.java)
    }
}

package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

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

    @GET("cdn/{version}/data/en_US/tft-trait.json")
    suspend fun getTftTraitsJson(
        @Path("version") version: String
    ): Response<okhttp3.ResponseBody>
}

object RiotApiClientFactory {
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * High-concurrency shared OkHttpClient with HTTP/2 multiplexing, warm connection pooling,
     * and elevated per-host concurrency (24 vs OkHttp's default 5) so parallel comp scrapes,
     * Data Dragon JSON syncs, and Coil icon prefetches finish dramatically faster.
     */
    val publicOkHttp: OkHttpClient by lazy {
        val fastDispatcher = Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 24
        }
        OkHttpClient.Builder()
            .dispatcher(fastDispatcher)
            .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Dedicated streaming download client sharing the same connection pool & dispatcher,
     * tuned with extended read/write timeouts for fast multi-megabyte GitHub APK downloads.
     */
    val downloadOkHttp: OkHttpClient by lazy {
        publicOkHttp.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val ddragonApi: DDragonApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ddragon.leagueoflegends.com/")
            .client(publicOkHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DDragonApi::class.java)
    }
}

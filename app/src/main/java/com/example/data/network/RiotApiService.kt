package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

    val publicOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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

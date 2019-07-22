package ir.zahrasadeghi.worldaround.api

import ir.zahrasadeghi.worldaround.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object APIService {

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
    private val client: OkHttpClient by lazy {
        val httpClient = OkHttpClient().newBuilder()
        httpClient.build()
    }
}


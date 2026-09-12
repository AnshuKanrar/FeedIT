package com.example.distll.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds the Retrofit client used to talk to backend/main.py's live
 * FastAPI server (POST /classify).
 */
object RetrofitClient {

    // TODO: fill in with wherever `uvicorn main:app` is actually reachable
    // from this device/emulator, e.g. "http://10.0.2.2:8000/" for the
    // Android emulator talking to a server on the host machine's localhost,
    // or your machine's LAN IP for a physical device on the same network.
    private const val BASE_URL = "http://0.0.0.0:8000"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val backendApi: BackendApi by lazy { retrofit.create(BackendApi::class.java) }
}

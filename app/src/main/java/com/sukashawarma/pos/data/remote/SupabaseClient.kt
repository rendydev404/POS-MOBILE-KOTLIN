package com.sukashawarma.pos.data.remote

import com.sukashawarma.pos.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SupabaseClient {
    const val BASE_URL = "https://khpkoreaaucvyqfhynfq.supabase.co/"
    private const val ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    // Every request carries the anon key as `apikey` (required by PostgREST/GoTrue),
    // and Authorization is the logged-in user's access token when available so RLS
    // (auth.uid()-based policies) applies exactly like the web app. Falls back to the
    // anon key itself pre-login, i.e. same as an unauthenticated web visitor.
    private val authInterceptor = Interceptor { chain ->
        val bearer = SessionTokenHolder.accessToken ?: ANON_KEY
        val request = chain.request().newBuilder()
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: SupabaseApi by lazy { retrofit.create(SupabaseApi::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
}

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
        val original = chain.request()
        // Header ini cuma buat host project POS sendiri — kalau dipaksa ke semua host,
        // header Authorization/apikey yang sengaja di-set pemanggil buat project lain
        // (misal Edge Function Order-Online) akan tertimpa diam-diam.
        if (original.url.host != "khpkoreaaucvyqfhynfq.supabase.co") {
            return@Interceptor chain.proceed(original)
        }
        val bearer = SessionTokenHolder.accessToken ?: ANON_KEY
        val requestBuilder = original.newBuilder()
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $bearer")
            // Tanpa versi di User-Agent, log server tidak dapat membuktikan APK
            // mana yang sudah aktif di tablet setelah self-update.
            .header(
                "User-Agent",
                "SukaShawarmaPOS/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.SDK_INT})"
            )
        
        if (original.header("Content-Type") == null) {
            requestBuilder.header("Content-Type", "application/json")
        }
        
        chain.proceed(requestBuilder.build())
    }

    // 401 = access token kedaluwarsa. Semua pemanggil REST lewat sini, jadi satu
    // authenticator cukup: refresh sekali, ulangi request. Tanpa ini kegagalan
    // ditelan diam-diam oleh `isSuccessful` di tiap ViewModel.
    private val tokenAuthenticator = okhttp3.Authenticator { _, response ->
        val path = response.request.url.encodedPath
        // Endpoint refresh-nya sendiri jangan ikut diretry — nanti berputar.
        if (path.contains("auth/v1/token") || response.request.header(RETRY_HEADER) != null) {
            return@Authenticator null
        }
        val refreshed = kotlinx.coroutines.runBlocking { AuthSessionManager.refresh() }
        if (!refreshed) return@Authenticator null
        // Setelah refresh, ambil token BARU dari SessionTokenHolder — jangan reuse
        // token lama dari request original, karena itulah yang menyebabkan 401 tadi.
        val newToken = SessionTokenHolder.accessToken ?: ANON_KEY
        response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header(RETRY_HEADER, "1")
            .build()
    }

    private const val RETRY_HEADER = "X-Token-Retry"

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
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

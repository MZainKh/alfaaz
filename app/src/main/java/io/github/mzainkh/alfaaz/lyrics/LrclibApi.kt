package io.github.mzainkh.alfaaz.lyrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class LrclibTrack(
    val trackName: String = "",
    val artistName: String = "",
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/** https://lrclib.net/docs. Null query parameters are omitted. */
interface LrclibApi {
    @GET("api/get")
    suspend fun get(
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
        @Query("album_name") album: String?,
        @Query("duration") durationSec: Long?,
    ): Response<LrclibTrack>

    @GET("api/search")
    suspend fun search(
        @Query("track_name") track: String? = null,
        @Query("artist_name") artist: String? = null,
        @Query("q") q: String? = null,
    ): Response<List<LrclibTrack>>
}

private const val TIMEOUT_SEC = 10L

/** Shared by all requests so they reuse connections. */
fun httpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
    .build()

fun lrclibApi(userAgent: String, baseUrl: String = "https://lrclib.net/", http: OkHttpClient = httpClient()): LrclibApi {
    val client = http.newBuilder()
        // LRCLIB requires every client to identify itself.
        .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LrclibApi::class.java)
}

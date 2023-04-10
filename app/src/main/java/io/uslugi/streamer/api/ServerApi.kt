package io.uslugi.streamer.api

import com.google.gson.GsonBuilder
import io.uslugi.streamer.BuildConfig
import io.uslugi.streamer.data.RTMPUrlResult
import io.uslugi.streamer.data.Section
import io.uslugi.streamer.helper.Constants
import io.uslugi.streamer.helper.Constants.HTTPConfig.CONNECT_TIMEOUT
import io.uslugi.streamer.helper.Constants.HTTPConfig.READ_TIMEOUT
import io.uslugi.streamer.helper.Constants.HTTPConfig.WRITE_TIMEOUT
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ServerApi {
    @Headers("Content-Type: application/json")
    @POST("/auth.php")
    suspend fun getRtmpUrl(@Body body: Section?): Result<RTMPUrlResult?>?

    companion object {
        fun getInstance(): ServerApi {
            val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:sssZ").create()

            val okHttpBuilder = OkHttpClient()
                .newBuilder()
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)

            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor()
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                okHttpBuilder.addInterceptor(loggingInterceptor)
            }

            val okHttpClient = okHttpBuilder.build()

            return Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(Constants.HTTPConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(ResultCallAdapterFactory())
                .build()
                .create(ServerApi::class.java)
        }
    }
}
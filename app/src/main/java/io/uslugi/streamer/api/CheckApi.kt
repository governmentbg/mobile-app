package io.uslugi.streamer.api

import com.google.gson.GsonBuilder
import io.uslugi.streamer.BuildConfig
import io.uslugi.streamer.data.TestCheckResultRequest
import io.uslugi.streamer.data.TestCheckResultResponse
import io.uslugi.streamer.helper.Constants
import io.uslugi.streamer.helper.Constants.HTTPConfig.BASE_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface CheckApi {
    @POST("/check.php")
    suspend fun checkTestResult(@Body body: TestCheckResultRequest): Result<TestCheckResultResponse?>?

    companion object {
        fun getInstance(): CheckApi {
            val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:sssZ").create()

            val okHttpBuilder = OkHttpClient()
                .newBuilder()
                .readTimeout(Constants.HTTPConfig.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Constants.HTTPConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(Constants.HTTPConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)

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
                .create(CheckApi::class.java)
        }
    }
}
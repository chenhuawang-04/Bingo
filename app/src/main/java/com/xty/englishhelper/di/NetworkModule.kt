package com.xty.englishhelper.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xty.englishhelper.BuildConfig
import com.xty.englishhelper.data.remote.AnthropicApiService
import com.xty.englishhelper.data.remote.OpenAiApiService
import com.xty.englishhelper.data.remote.interceptor.AnthropicHeaderInterceptor
import com.xty.englishhelper.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            val sanitized = message.replace(
                Regex("Authorization:\\s*Bearer\\s*\\S+", RegexOption.IGNORE_CASE),
                "Authorization: Bearer [REDACTED]"
            ).replace(
                Regex("x-api-key:\\s*\\S+", RegexOption.IGNORE_CASE),
                "x-api-key: [REDACTED]"
            )
            HttpLoggingInterceptor.Logger.DEFAULT.log(sanitized)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    @Provides
    @Singleton
    @Named("anthropic")
    fun provideAnthropicOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AnthropicHeaderInterceptor(Constants.ANTHROPIC_API_VERSION))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("openai")
    fun provideOpenAiOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("anthropic")
    fun provideAnthropicRetrofit(
        @Named("anthropic") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.ANTHROPIC_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @Named("openai")
    fun provideOpenAiRetrofit(
        @Named("openai") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.OPENAI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAnthropicApiService(@Named("anthropic") retrofit: Retrofit): AnthropicApiService {
        return retrofit.create(AnthropicApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenAiApiService(@Named("openai") retrofit: Retrofit): OpenAiApiService {
        return retrofit.create(OpenAiApiService::class.java)
    }
}

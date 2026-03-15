package com.xty.englishhelper.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xty.englishhelper.BuildConfig
import com.xty.englishhelper.data.remote.AnthropicApiService
import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.data.remote.OpenAiApiService
import com.xty.englishhelper.data.remote.interceptor.AiDebugInterceptor
import com.xty.englishhelper.data.remote.interceptor.AnthropicHeaderInterceptor
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorHtmlParser
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorService
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorServiceImpl
import com.xty.englishhelper.data.remote.guardian.GuardianHtmlParser
import com.xty.englishhelper.data.remote.guardian.GuardianService
import com.xty.englishhelper.data.remote.guardian.GuardianServiceImpl
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
    fun provideAnthropicOkHttpClient(
        aiDebugInterceptor: AiDebugInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AnthropicHeaderInterceptor(Constants.ANTHROPIC_API_VERSION))
            .addInterceptor(aiDebugInterceptor)
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
    fun provideOpenAiOkHttpClient(
        aiDebugInterceptor: AiDebugInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(aiDebugInterceptor)
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

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "EnglishHelper-Android")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(
        @Named("github") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }

    // Guardian
    @Provides
    @Singleton
    @Named("guardian")
    fun provideGuardianOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGuardianHtmlParser(): GuardianHtmlParser = GuardianHtmlParser()

    @Provides
    @Singleton
    fun provideGuardianService(@Named("guardian") client: OkHttpClient): GuardianService {
        return GuardianServiceImpl(client)
    }

    // CSMonitor
    @Provides
    @Singleton
    @Named("csmonitor")
    fun provideCsMonitorOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(createLoggingInterceptor())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideCsMonitorHtmlParser(): CsMonitorHtmlParser = CsMonitorHtmlParser()

    @Provides
    @Singleton
    fun provideCsMonitorService(@Named("csmonitor") client: OkHttpClient): CsMonitorService {
        return CsMonitorServiceImpl(client)
    }
}

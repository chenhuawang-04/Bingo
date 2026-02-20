package com.xty.englishhelper.data.repository;

import com.squareup.moshi.Moshi;
import com.xty.englishhelper.data.remote.AnthropicApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AiRepositoryImpl_Factory implements Factory<AiRepositoryImpl> {
  private final Provider<AnthropicApiService> apiServiceProvider;

  private final Provider<Moshi> moshiProvider;

  public AiRepositoryImpl_Factory(Provider<AnthropicApiService> apiServiceProvider,
      Provider<Moshi> moshiProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.moshiProvider = moshiProvider;
  }

  @Override
  public AiRepositoryImpl get() {
    return newInstance(apiServiceProvider.get(), moshiProvider.get());
  }

  public static AiRepositoryImpl_Factory create(Provider<AnthropicApiService> apiServiceProvider,
      Provider<Moshi> moshiProvider) {
    return new AiRepositoryImpl_Factory(apiServiceProvider, moshiProvider);
  }

  public static AiRepositoryImpl newInstance(AnthropicApiService apiService, Moshi moshi) {
    return new AiRepositoryImpl(apiService, moshi);
  }
}

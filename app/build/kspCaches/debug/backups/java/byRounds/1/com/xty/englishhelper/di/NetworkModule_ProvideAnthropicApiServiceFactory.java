package com.xty.englishhelper.di;

import com.xty.englishhelper.data.remote.AnthropicApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class NetworkModule_ProvideAnthropicApiServiceFactory implements Factory<AnthropicApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideAnthropicApiServiceFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public AnthropicApiService get() {
    return provideAnthropicApiService(retrofitProvider.get());
  }

  public static NetworkModule_ProvideAnthropicApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideAnthropicApiServiceFactory(retrofitProvider);
  }

  public static AnthropicApiService provideAnthropicApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideAnthropicApiService(retrofit));
  }
}

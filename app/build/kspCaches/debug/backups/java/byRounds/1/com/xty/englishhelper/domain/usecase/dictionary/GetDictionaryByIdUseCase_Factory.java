package com.xty.englishhelper.domain.usecase.dictionary;

import com.xty.englishhelper.domain.repository.DictionaryRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class GetDictionaryByIdUseCase_Factory implements Factory<GetDictionaryByIdUseCase> {
  private final Provider<DictionaryRepository> repositoryProvider;

  public GetDictionaryByIdUseCase_Factory(Provider<DictionaryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetDictionaryByIdUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetDictionaryByIdUseCase_Factory create(
      Provider<DictionaryRepository> repositoryProvider) {
    return new GetDictionaryByIdUseCase_Factory(repositoryProvider);
  }

  public static GetDictionaryByIdUseCase newInstance(DictionaryRepository repository) {
    return new GetDictionaryByIdUseCase(repository);
  }
}

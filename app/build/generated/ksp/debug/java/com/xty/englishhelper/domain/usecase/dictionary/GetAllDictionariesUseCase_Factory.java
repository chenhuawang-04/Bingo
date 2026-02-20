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
public final class GetAllDictionariesUseCase_Factory implements Factory<GetAllDictionariesUseCase> {
  private final Provider<DictionaryRepository> repositoryProvider;

  public GetAllDictionariesUseCase_Factory(Provider<DictionaryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetAllDictionariesUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetAllDictionariesUseCase_Factory create(
      Provider<DictionaryRepository> repositoryProvider) {
    return new GetAllDictionariesUseCase_Factory(repositoryProvider);
  }

  public static GetAllDictionariesUseCase newInstance(DictionaryRepository repository) {
    return new GetAllDictionariesUseCase(repository);
  }
}

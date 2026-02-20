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
public final class UpdateDictionaryUseCase_Factory implements Factory<UpdateDictionaryUseCase> {
  private final Provider<DictionaryRepository> repositoryProvider;

  public UpdateDictionaryUseCase_Factory(Provider<DictionaryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public UpdateDictionaryUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static UpdateDictionaryUseCase_Factory create(
      Provider<DictionaryRepository> repositoryProvider) {
    return new UpdateDictionaryUseCase_Factory(repositoryProvider);
  }

  public static UpdateDictionaryUseCase newInstance(DictionaryRepository repository) {
    return new UpdateDictionaryUseCase(repository);
  }
}

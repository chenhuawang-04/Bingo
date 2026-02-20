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
public final class CreateDictionaryUseCase_Factory implements Factory<CreateDictionaryUseCase> {
  private final Provider<DictionaryRepository> repositoryProvider;

  public CreateDictionaryUseCase_Factory(Provider<DictionaryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public CreateDictionaryUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static CreateDictionaryUseCase_Factory create(
      Provider<DictionaryRepository> repositoryProvider) {
    return new CreateDictionaryUseCase_Factory(repositoryProvider);
  }

  public static CreateDictionaryUseCase newInstance(DictionaryRepository repository) {
    return new CreateDictionaryUseCase(repository);
  }
}

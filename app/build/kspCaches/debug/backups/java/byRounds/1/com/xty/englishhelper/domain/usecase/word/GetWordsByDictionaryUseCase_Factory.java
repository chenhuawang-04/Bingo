package com.xty.englishhelper.domain.usecase.word;

import com.xty.englishhelper.domain.repository.WordRepository;
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
public final class GetWordsByDictionaryUseCase_Factory implements Factory<GetWordsByDictionaryUseCase> {
  private final Provider<WordRepository> repositoryProvider;

  public GetWordsByDictionaryUseCase_Factory(Provider<WordRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetWordsByDictionaryUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetWordsByDictionaryUseCase_Factory create(
      Provider<WordRepository> repositoryProvider) {
    return new GetWordsByDictionaryUseCase_Factory(repositoryProvider);
  }

  public static GetWordsByDictionaryUseCase newInstance(WordRepository repository) {
    return new GetWordsByDictionaryUseCase(repository);
  }
}

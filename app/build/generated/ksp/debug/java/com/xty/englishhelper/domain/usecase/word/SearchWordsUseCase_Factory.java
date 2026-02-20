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
public final class SearchWordsUseCase_Factory implements Factory<SearchWordsUseCase> {
  private final Provider<WordRepository> repositoryProvider;

  public SearchWordsUseCase_Factory(Provider<WordRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public SearchWordsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static SearchWordsUseCase_Factory create(Provider<WordRepository> repositoryProvider) {
    return new SearchWordsUseCase_Factory(repositoryProvider);
  }

  public static SearchWordsUseCase newInstance(WordRepository repository) {
    return new SearchWordsUseCase(repository);
  }
}

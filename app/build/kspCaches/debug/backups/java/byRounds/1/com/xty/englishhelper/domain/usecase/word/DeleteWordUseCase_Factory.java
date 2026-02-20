package com.xty.englishhelper.domain.usecase.word;

import com.xty.englishhelper.domain.repository.DictionaryRepository;
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
public final class DeleteWordUseCase_Factory implements Factory<DeleteWordUseCase> {
  private final Provider<WordRepository> wordRepositoryProvider;

  private final Provider<DictionaryRepository> dictionaryRepositoryProvider;

  public DeleteWordUseCase_Factory(Provider<WordRepository> wordRepositoryProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider) {
    this.wordRepositoryProvider = wordRepositoryProvider;
    this.dictionaryRepositoryProvider = dictionaryRepositoryProvider;
  }

  @Override
  public DeleteWordUseCase get() {
    return newInstance(wordRepositoryProvider.get(), dictionaryRepositoryProvider.get());
  }

  public static DeleteWordUseCase_Factory create(Provider<WordRepository> wordRepositoryProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider) {
    return new DeleteWordUseCase_Factory(wordRepositoryProvider, dictionaryRepositoryProvider);
  }

  public static DeleteWordUseCase newInstance(WordRepository wordRepository,
      DictionaryRepository dictionaryRepository) {
    return new DeleteWordUseCase(wordRepository, dictionaryRepository);
  }
}

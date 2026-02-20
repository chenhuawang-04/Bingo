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
public final class SaveWordUseCase_Factory implements Factory<SaveWordUseCase> {
  private final Provider<WordRepository> wordRepositoryProvider;

  private final Provider<DictionaryRepository> dictionaryRepositoryProvider;

  public SaveWordUseCase_Factory(Provider<WordRepository> wordRepositoryProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider) {
    this.wordRepositoryProvider = wordRepositoryProvider;
    this.dictionaryRepositoryProvider = dictionaryRepositoryProvider;
  }

  @Override
  public SaveWordUseCase get() {
    return newInstance(wordRepositoryProvider.get(), dictionaryRepositoryProvider.get());
  }

  public static SaveWordUseCase_Factory create(Provider<WordRepository> wordRepositoryProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider) {
    return new SaveWordUseCase_Factory(wordRepositoryProvider, dictionaryRepositoryProvider);
  }

  public static SaveWordUseCase newInstance(WordRepository wordRepository,
      DictionaryRepository dictionaryRepository) {
    return new SaveWordUseCase(wordRepository, dictionaryRepository);
  }
}

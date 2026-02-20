package com.xty.englishhelper.ui.screen.dictionary;

import androidx.lifecycle.SavedStateHandle;
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase;
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase;
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.word.SearchWordsUseCase;
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
public final class DictionaryViewModel_Factory implements Factory<DictionaryViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetDictionaryByIdUseCase> getDictionaryByIdProvider;

  private final Provider<GetWordsByDictionaryUseCase> getWordsByDictionaryProvider;

  private final Provider<SearchWordsUseCase> searchWordsProvider;

  private final Provider<DeleteWordUseCase> deleteWordProvider;

  public DictionaryViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDictionaryByIdUseCase> getDictionaryByIdProvider,
      Provider<GetWordsByDictionaryUseCase> getWordsByDictionaryProvider,
      Provider<SearchWordsUseCase> searchWordsProvider,
      Provider<DeleteWordUseCase> deleteWordProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getDictionaryByIdProvider = getDictionaryByIdProvider;
    this.getWordsByDictionaryProvider = getWordsByDictionaryProvider;
    this.searchWordsProvider = searchWordsProvider;
    this.deleteWordProvider = deleteWordProvider;
  }

  @Override
  public DictionaryViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getDictionaryByIdProvider.get(), getWordsByDictionaryProvider.get(), searchWordsProvider.get(), deleteWordProvider.get());
  }

  public static DictionaryViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDictionaryByIdUseCase> getDictionaryByIdProvider,
      Provider<GetWordsByDictionaryUseCase> getWordsByDictionaryProvider,
      Provider<SearchWordsUseCase> searchWordsProvider,
      Provider<DeleteWordUseCase> deleteWordProvider) {
    return new DictionaryViewModel_Factory(savedStateHandleProvider, getDictionaryByIdProvider, getWordsByDictionaryProvider, searchWordsProvider, deleteWordProvider);
  }

  public static DictionaryViewModel newInstance(SavedStateHandle savedStateHandle,
      GetDictionaryByIdUseCase getDictionaryById, GetWordsByDictionaryUseCase getWordsByDictionary,
      SearchWordsUseCase searchWords, DeleteWordUseCase deleteWord) {
    return new DictionaryViewModel(savedStateHandle, getDictionaryById, getWordsByDictionary, searchWords, deleteWord);
  }
}

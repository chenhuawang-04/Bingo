package com.xty.englishhelper.ui.screen.word;

import androidx.lifecycle.SavedStateHandle;
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase;
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase;
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
public final class WordDetailViewModel_Factory implements Factory<WordDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetWordByIdUseCase> getWordByIdProvider;

  private final Provider<DeleteWordUseCase> deleteWordProvider;

  public WordDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetWordByIdUseCase> getWordByIdProvider,
      Provider<DeleteWordUseCase> deleteWordProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getWordByIdProvider = getWordByIdProvider;
    this.deleteWordProvider = deleteWordProvider;
  }

  @Override
  public WordDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getWordByIdProvider.get(), deleteWordProvider.get());
  }

  public static WordDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetWordByIdUseCase> getWordByIdProvider,
      Provider<DeleteWordUseCase> deleteWordProvider) {
    return new WordDetailViewModel_Factory(savedStateHandleProvider, getWordByIdProvider, deleteWordProvider);
  }

  public static WordDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      GetWordByIdUseCase getWordById, DeleteWordUseCase deleteWord) {
    return new WordDetailViewModel(savedStateHandle, getWordById, deleteWord);
  }
}

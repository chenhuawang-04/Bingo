package com.xty.englishhelper.ui.screen.addword;

import androidx.lifecycle.SavedStateHandle;
import com.xty.englishhelper.data.preferences.SettingsDataStore;
import com.xty.englishhelper.domain.usecase.ai.OrganizeWordWithAiUseCase;
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase;
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase;
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
public final class AddWordViewModel_Factory implements Factory<AddWordViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetWordByIdUseCase> getWordByIdProvider;

  private final Provider<SaveWordUseCase> saveWordProvider;

  private final Provider<OrganizeWordWithAiUseCase> organizeWordWithAiProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  public AddWordViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetWordByIdUseCase> getWordByIdProvider, Provider<SaveWordUseCase> saveWordProvider,
      Provider<OrganizeWordWithAiUseCase> organizeWordWithAiProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getWordByIdProvider = getWordByIdProvider;
    this.saveWordProvider = saveWordProvider;
    this.organizeWordWithAiProvider = organizeWordWithAiProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public AddWordViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getWordByIdProvider.get(), saveWordProvider.get(), organizeWordWithAiProvider.get(), settingsDataStoreProvider.get());
  }

  public static AddWordViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetWordByIdUseCase> getWordByIdProvider, Provider<SaveWordUseCase> saveWordProvider,
      Provider<OrganizeWordWithAiUseCase> organizeWordWithAiProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new AddWordViewModel_Factory(savedStateHandleProvider, getWordByIdProvider, saveWordProvider, organizeWordWithAiProvider, settingsDataStoreProvider);
  }

  public static AddWordViewModel newInstance(SavedStateHandle savedStateHandle,
      GetWordByIdUseCase getWordById, SaveWordUseCase saveWord,
      OrganizeWordWithAiUseCase organizeWordWithAi, SettingsDataStore settingsDataStore) {
    return new AddWordViewModel(savedStateHandle, getWordById, saveWord, organizeWordWithAi, settingsDataStore);
  }
}

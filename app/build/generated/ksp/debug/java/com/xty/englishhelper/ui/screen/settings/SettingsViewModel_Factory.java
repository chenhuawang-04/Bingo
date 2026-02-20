package com.xty.englishhelper.ui.screen.settings;

import com.xty.englishhelper.data.preferences.SettingsDataStore;
import com.xty.englishhelper.domain.usecase.ai.TestAiConnectionUseCase;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private final Provider<TestAiConnectionUseCase> testAiConnectionProvider;

  public SettingsViewModel_Factory(Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<TestAiConnectionUseCase> testAiConnectionProvider) {
    this.settingsDataStoreProvider = settingsDataStoreProvider;
    this.testAiConnectionProvider = testAiConnectionProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsDataStoreProvider.get(), testAiConnectionProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<SettingsDataStore> settingsDataStoreProvider,
      Provider<TestAiConnectionUseCase> testAiConnectionProvider) {
    return new SettingsViewModel_Factory(settingsDataStoreProvider, testAiConnectionProvider);
  }

  public static SettingsViewModel newInstance(SettingsDataStore settingsDataStore,
      TestAiConnectionUseCase testAiConnection) {
    return new SettingsViewModel(settingsDataStore, testAiConnection);
  }
}

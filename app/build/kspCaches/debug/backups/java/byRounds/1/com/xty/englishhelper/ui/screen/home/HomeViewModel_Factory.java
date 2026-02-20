package com.xty.englishhelper.ui.screen.home;

import com.xty.englishhelper.domain.usecase.dictionary.CreateDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.DeleteDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase;
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<GetAllDictionariesUseCase> getAllDictionariesProvider;

  private final Provider<CreateDictionaryUseCase> createDictionaryProvider;

  private final Provider<DeleteDictionaryUseCase> deleteDictionaryProvider;

  public HomeViewModel_Factory(Provider<GetAllDictionariesUseCase> getAllDictionariesProvider,
      Provider<CreateDictionaryUseCase> createDictionaryProvider,
      Provider<DeleteDictionaryUseCase> deleteDictionaryProvider) {
    this.getAllDictionariesProvider = getAllDictionariesProvider;
    this.createDictionaryProvider = createDictionaryProvider;
    this.deleteDictionaryProvider = deleteDictionaryProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(getAllDictionariesProvider.get(), createDictionaryProvider.get(), deleteDictionaryProvider.get());
  }

  public static HomeViewModel_Factory create(
      Provider<GetAllDictionariesUseCase> getAllDictionariesProvider,
      Provider<CreateDictionaryUseCase> createDictionaryProvider,
      Provider<DeleteDictionaryUseCase> deleteDictionaryProvider) {
    return new HomeViewModel_Factory(getAllDictionariesProvider, createDictionaryProvider, deleteDictionaryProvider);
  }

  public static HomeViewModel newInstance(GetAllDictionariesUseCase getAllDictionaries,
      CreateDictionaryUseCase createDictionary, DeleteDictionaryUseCase deleteDictionary) {
    return new HomeViewModel(getAllDictionaries, createDictionary, deleteDictionary);
  }
}

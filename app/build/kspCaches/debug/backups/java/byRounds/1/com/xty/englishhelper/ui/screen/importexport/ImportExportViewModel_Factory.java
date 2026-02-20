package com.xty.englishhelper.ui.screen.importexport;

import com.xty.englishhelper.data.json.JsonImportExporter;
import com.xty.englishhelper.domain.repository.DictionaryRepository;
import com.xty.englishhelper.domain.repository.WordRepository;
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
public final class ImportExportViewModel_Factory implements Factory<ImportExportViewModel> {
  private final Provider<GetAllDictionariesUseCase> getAllDictionariesProvider;

  private final Provider<DictionaryRepository> dictionaryRepositoryProvider;

  private final Provider<WordRepository> wordRepositoryProvider;

  private final Provider<JsonImportExporter> jsonImportExporterProvider;

  public ImportExportViewModel_Factory(
      Provider<GetAllDictionariesUseCase> getAllDictionariesProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider,
      Provider<WordRepository> wordRepositoryProvider,
      Provider<JsonImportExporter> jsonImportExporterProvider) {
    this.getAllDictionariesProvider = getAllDictionariesProvider;
    this.dictionaryRepositoryProvider = dictionaryRepositoryProvider;
    this.wordRepositoryProvider = wordRepositoryProvider;
    this.jsonImportExporterProvider = jsonImportExporterProvider;
  }

  @Override
  public ImportExportViewModel get() {
    return newInstance(getAllDictionariesProvider.get(), dictionaryRepositoryProvider.get(), wordRepositoryProvider.get(), jsonImportExporterProvider.get());
  }

  public static ImportExportViewModel_Factory create(
      Provider<GetAllDictionariesUseCase> getAllDictionariesProvider,
      Provider<DictionaryRepository> dictionaryRepositoryProvider,
      Provider<WordRepository> wordRepositoryProvider,
      Provider<JsonImportExporter> jsonImportExporterProvider) {
    return new ImportExportViewModel_Factory(getAllDictionariesProvider, dictionaryRepositoryProvider, wordRepositoryProvider, jsonImportExporterProvider);
  }

  public static ImportExportViewModel newInstance(GetAllDictionariesUseCase getAllDictionaries,
      DictionaryRepository dictionaryRepository, WordRepository wordRepository,
      JsonImportExporter jsonImportExporter) {
    return new ImportExportViewModel(getAllDictionaries, dictionaryRepository, wordRepository, jsonImportExporter);
  }
}

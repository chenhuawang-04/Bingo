package com.xty.englishhelper.di;

import com.xty.englishhelper.data.local.AppDatabase;
import com.xty.englishhelper.data.local.dao.DictionaryDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideDictionaryDaoFactory implements Factory<DictionaryDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideDictionaryDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DictionaryDao get() {
    return provideDictionaryDao(dbProvider.get());
  }

  public static AppModule_ProvideDictionaryDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideDictionaryDaoFactory(dbProvider);
  }

  public static DictionaryDao provideDictionaryDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDictionaryDao(db));
  }
}

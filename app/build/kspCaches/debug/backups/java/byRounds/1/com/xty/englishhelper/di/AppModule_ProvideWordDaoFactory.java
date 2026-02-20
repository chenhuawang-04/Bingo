package com.xty.englishhelper.di;

import com.xty.englishhelper.data.local.AppDatabase;
import com.xty.englishhelper.data.local.dao.WordDao;
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
public final class AppModule_ProvideWordDaoFactory implements Factory<WordDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideWordDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public WordDao get() {
    return provideWordDao(dbProvider.get());
  }

  public static AppModule_ProvideWordDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideWordDaoFactory(dbProvider);
  }

  public static WordDao provideWordDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideWordDao(db));
  }
}

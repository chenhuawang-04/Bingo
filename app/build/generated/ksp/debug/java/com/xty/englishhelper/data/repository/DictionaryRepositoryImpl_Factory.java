package com.xty.englishhelper.data.repository;

import com.xty.englishhelper.data.local.dao.DictionaryDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DictionaryRepositoryImpl_Factory implements Factory<DictionaryRepositoryImpl> {
  private final Provider<DictionaryDao> daoProvider;

  public DictionaryRepositoryImpl_Factory(Provider<DictionaryDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public DictionaryRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static DictionaryRepositoryImpl_Factory create(Provider<DictionaryDao> daoProvider) {
    return new DictionaryRepositoryImpl_Factory(daoProvider);
  }

  public static DictionaryRepositoryImpl newInstance(DictionaryDao dao) {
    return new DictionaryRepositoryImpl(dao);
  }
}

package com.xty.englishhelper.data.repository;

import com.xty.englishhelper.data.local.dao.WordDao;
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
public final class WordRepositoryImpl_Factory implements Factory<WordRepositoryImpl> {
  private final Provider<WordDao> wordDaoProvider;

  public WordRepositoryImpl_Factory(Provider<WordDao> wordDaoProvider) {
    this.wordDaoProvider = wordDaoProvider;
  }

  @Override
  public WordRepositoryImpl get() {
    return newInstance(wordDaoProvider.get());
  }

  public static WordRepositoryImpl_Factory create(Provider<WordDao> wordDaoProvider) {
    return new WordRepositoryImpl_Factory(wordDaoProvider);
  }

  public static WordRepositoryImpl newInstance(WordDao wordDao) {
    return new WordRepositoryImpl(wordDao);
  }
}

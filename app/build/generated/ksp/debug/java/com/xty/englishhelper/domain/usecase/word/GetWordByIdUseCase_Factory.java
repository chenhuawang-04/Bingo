package com.xty.englishhelper.domain.usecase.word;

import com.xty.englishhelper.domain.repository.WordRepository;
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
public final class GetWordByIdUseCase_Factory implements Factory<GetWordByIdUseCase> {
  private final Provider<WordRepository> repositoryProvider;

  public GetWordByIdUseCase_Factory(Provider<WordRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetWordByIdUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetWordByIdUseCase_Factory create(Provider<WordRepository> repositoryProvider) {
    return new GetWordByIdUseCase_Factory(repositoryProvider);
  }

  public static GetWordByIdUseCase newInstance(WordRepository repository) {
    return new GetWordByIdUseCase(repository);
  }
}

package com.xty.englishhelper.domain.usecase.ai;

import com.xty.englishhelper.domain.repository.AiRepository;
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
public final class TestAiConnectionUseCase_Factory implements Factory<TestAiConnectionUseCase> {
  private final Provider<AiRepository> repositoryProvider;

  public TestAiConnectionUseCase_Factory(Provider<AiRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public TestAiConnectionUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static TestAiConnectionUseCase_Factory create(Provider<AiRepository> repositoryProvider) {
    return new TestAiConnectionUseCase_Factory(repositoryProvider);
  }

  public static TestAiConnectionUseCase newInstance(AiRepository repository) {
    return new TestAiConnectionUseCase(repository);
  }
}

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
public final class OrganizeWordWithAiUseCase_Factory implements Factory<OrganizeWordWithAiUseCase> {
  private final Provider<AiRepository> repositoryProvider;

  public OrganizeWordWithAiUseCase_Factory(Provider<AiRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public OrganizeWordWithAiUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static OrganizeWordWithAiUseCase_Factory create(
      Provider<AiRepository> repositoryProvider) {
    return new OrganizeWordWithAiUseCase_Factory(repositoryProvider);
  }

  public static OrganizeWordWithAiUseCase newInstance(AiRepository repository) {
    return new OrganizeWordWithAiUseCase(repository);
  }
}

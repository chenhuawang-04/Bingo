package com.xty.englishhelper.data.json;

import com.squareup.moshi.Moshi;
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
public final class JsonImportExporter_Factory implements Factory<JsonImportExporter> {
  private final Provider<Moshi> moshiProvider;

  public JsonImportExporter_Factory(Provider<Moshi> moshiProvider) {
    this.moshiProvider = moshiProvider;
  }

  @Override
  public JsonImportExporter get() {
    return newInstance(moshiProvider.get());
  }

  public static JsonImportExporter_Factory create(Provider<Moshi> moshiProvider) {
    return new JsonImportExporter_Factory(moshiProvider);
  }

  public static JsonImportExporter newInstance(Moshi moshi) {
    return new JsonImportExporter(moshi);
  }
}

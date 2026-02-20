package com.xty.englishhelper;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.squareup.moshi.Moshi;
import com.xty.englishhelper.data.json.JsonImportExporter;
import com.xty.englishhelper.data.local.AppDatabase;
import com.xty.englishhelper.data.local.dao.DictionaryDao;
import com.xty.englishhelper.data.local.dao.WordDao;
import com.xty.englishhelper.data.preferences.SettingsDataStore;
import com.xty.englishhelper.data.remote.AnthropicApiService;
import com.xty.englishhelper.data.repository.AiRepositoryImpl;
import com.xty.englishhelper.data.repository.DictionaryRepositoryImpl;
import com.xty.englishhelper.data.repository.WordRepositoryImpl;
import com.xty.englishhelper.di.AppModule_ProvideDataStoreFactory;
import com.xty.englishhelper.di.AppModule_ProvideDatabaseFactory;
import com.xty.englishhelper.di.AppModule_ProvideDictionaryDaoFactory;
import com.xty.englishhelper.di.AppModule_ProvideWordDaoFactory;
import com.xty.englishhelper.di.NetworkModule_ProvideAnthropicApiServiceFactory;
import com.xty.englishhelper.di.NetworkModule_ProvideMoshiFactory;
import com.xty.englishhelper.di.NetworkModule_ProvideOkHttpClientFactory;
import com.xty.englishhelper.di.NetworkModule_ProvideRetrofitFactory;
import com.xty.englishhelper.domain.usecase.ai.OrganizeWordWithAiUseCase;
import com.xty.englishhelper.domain.usecase.ai.TestAiConnectionUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.CreateDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.DeleteDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase;
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase;
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase;
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase;
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase;
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase;
import com.xty.englishhelper.domain.usecase.word.SearchWordsUseCase;
import com.xty.englishhelper.ui.MainActivity;
import com.xty.englishhelper.ui.screen.addword.AddWordViewModel;
import com.xty.englishhelper.ui.screen.addword.AddWordViewModel_HiltModules;
import com.xty.englishhelper.ui.screen.dictionary.DictionaryViewModel;
import com.xty.englishhelper.ui.screen.dictionary.DictionaryViewModel_HiltModules;
import com.xty.englishhelper.ui.screen.home.HomeViewModel;
import com.xty.englishhelper.ui.screen.home.HomeViewModel_HiltModules;
import com.xty.englishhelper.ui.screen.importexport.ImportExportViewModel;
import com.xty.englishhelper.ui.screen.importexport.ImportExportViewModel_HiltModules;
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel;
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel_HiltModules;
import com.xty.englishhelper.ui.screen.word.WordDetailViewModel;
import com.xty.englishhelper.ui.screen.word.WordDetailViewModel_HiltModules;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class DaggerEnglishHelperApp_HiltComponents_SingletonC {
  private DaggerEnglishHelperApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public EnglishHelperApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements EnglishHelperApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements EnglishHelperApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements EnglishHelperApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements EnglishHelperApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements EnglishHelperApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements EnglishHelperApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements EnglishHelperApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public EnglishHelperApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends EnglishHelperApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends EnglishHelperApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends EnglishHelperApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends EnglishHelperApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(6).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_addword_AddWordViewModel, AddWordViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel, DictionaryViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_home_HomeViewModel, HomeViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel, ImportExportViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_settings_SettingsViewModel, SettingsViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_word_WordDetailViewModel, WordDetailViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_xty_englishhelper_ui_screen_home_HomeViewModel = "com.xty.englishhelper.ui.screen.home.HomeViewModel";

      static String com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel = "com.xty.englishhelper.ui.screen.dictionary.DictionaryViewModel";

      static String com_xty_englishhelper_ui_screen_addword_AddWordViewModel = "com.xty.englishhelper.ui.screen.addword.AddWordViewModel";

      static String com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel = "com.xty.englishhelper.ui.screen.importexport.ImportExportViewModel";

      static String com_xty_englishhelper_ui_screen_settings_SettingsViewModel = "com.xty.englishhelper.ui.screen.settings.SettingsViewModel";

      static String com_xty_englishhelper_ui_screen_word_WordDetailViewModel = "com.xty.englishhelper.ui.screen.word.WordDetailViewModel";

      @KeepFieldType
      HomeViewModel com_xty_englishhelper_ui_screen_home_HomeViewModel2;

      @KeepFieldType
      DictionaryViewModel com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel2;

      @KeepFieldType
      AddWordViewModel com_xty_englishhelper_ui_screen_addword_AddWordViewModel2;

      @KeepFieldType
      ImportExportViewModel com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel2;

      @KeepFieldType
      SettingsViewModel com_xty_englishhelper_ui_screen_settings_SettingsViewModel2;

      @KeepFieldType
      WordDetailViewModel com_xty_englishhelper_ui_screen_word_WordDetailViewModel2;
    }
  }

  private static final class ViewModelCImpl extends EnglishHelperApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AddWordViewModel> addWordViewModelProvider;

    private Provider<DictionaryViewModel> dictionaryViewModelProvider;

    private Provider<HomeViewModel> homeViewModelProvider;

    private Provider<ImportExportViewModel> importExportViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private Provider<WordDetailViewModel> wordDetailViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    private GetWordByIdUseCase getWordByIdUseCase() {
      return new GetWordByIdUseCase(singletonCImpl.wordRepositoryImplProvider.get());
    }

    private SaveWordUseCase saveWordUseCase() {
      return new SaveWordUseCase(singletonCImpl.wordRepositoryImplProvider.get(), singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private OrganizeWordWithAiUseCase organizeWordWithAiUseCase() {
      return new OrganizeWordWithAiUseCase(singletonCImpl.aiRepositoryImplProvider.get());
    }

    private GetDictionaryByIdUseCase getDictionaryByIdUseCase() {
      return new GetDictionaryByIdUseCase(singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private GetWordsByDictionaryUseCase getWordsByDictionaryUseCase() {
      return new GetWordsByDictionaryUseCase(singletonCImpl.wordRepositoryImplProvider.get());
    }

    private SearchWordsUseCase searchWordsUseCase() {
      return new SearchWordsUseCase(singletonCImpl.wordRepositoryImplProvider.get());
    }

    private DeleteWordUseCase deleteWordUseCase() {
      return new DeleteWordUseCase(singletonCImpl.wordRepositoryImplProvider.get(), singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private GetAllDictionariesUseCase getAllDictionariesUseCase() {
      return new GetAllDictionariesUseCase(singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private CreateDictionaryUseCase createDictionaryUseCase() {
      return new CreateDictionaryUseCase(singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private DeleteDictionaryUseCase deleteDictionaryUseCase() {
      return new DeleteDictionaryUseCase(singletonCImpl.dictionaryRepositoryImplProvider.get());
    }

    private TestAiConnectionUseCase testAiConnectionUseCase() {
      return new TestAiConnectionUseCase(singletonCImpl.aiRepositoryImplProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.addWordViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dictionaryViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.homeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.importExportViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.wordDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(6).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_addword_AddWordViewModel, ((Provider) addWordViewModelProvider)).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel, ((Provider) dictionaryViewModelProvider)).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_home_HomeViewModel, ((Provider) homeViewModelProvider)).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel, ((Provider) importExportViewModelProvider)).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_settings_SettingsViewModel, ((Provider) settingsViewModelProvider)).put(LazyClassKeyProvider.com_xty_englishhelper_ui_screen_word_WordDetailViewModel, ((Provider) wordDetailViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_xty_englishhelper_ui_screen_word_WordDetailViewModel = "com.xty.englishhelper.ui.screen.word.WordDetailViewModel";

      static String com_xty_englishhelper_ui_screen_addword_AddWordViewModel = "com.xty.englishhelper.ui.screen.addword.AddWordViewModel";

      static String com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel = "com.xty.englishhelper.ui.screen.dictionary.DictionaryViewModel";

      static String com_xty_englishhelper_ui_screen_home_HomeViewModel = "com.xty.englishhelper.ui.screen.home.HomeViewModel";

      static String com_xty_englishhelper_ui_screen_settings_SettingsViewModel = "com.xty.englishhelper.ui.screen.settings.SettingsViewModel";

      static String com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel = "com.xty.englishhelper.ui.screen.importexport.ImportExportViewModel";

      @KeepFieldType
      WordDetailViewModel com_xty_englishhelper_ui_screen_word_WordDetailViewModel2;

      @KeepFieldType
      AddWordViewModel com_xty_englishhelper_ui_screen_addword_AddWordViewModel2;

      @KeepFieldType
      DictionaryViewModel com_xty_englishhelper_ui_screen_dictionary_DictionaryViewModel2;

      @KeepFieldType
      HomeViewModel com_xty_englishhelper_ui_screen_home_HomeViewModel2;

      @KeepFieldType
      SettingsViewModel com_xty_englishhelper_ui_screen_settings_SettingsViewModel2;

      @KeepFieldType
      ImportExportViewModel com_xty_englishhelper_ui_screen_importexport_ImportExportViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.xty.englishhelper.ui.screen.addword.AddWordViewModel 
          return (T) new AddWordViewModel(viewModelCImpl.savedStateHandle, viewModelCImpl.getWordByIdUseCase(), viewModelCImpl.saveWordUseCase(), viewModelCImpl.organizeWordWithAiUseCase(), singletonCImpl.settingsDataStoreProvider.get());

          case 1: // com.xty.englishhelper.ui.screen.dictionary.DictionaryViewModel 
          return (T) new DictionaryViewModel(viewModelCImpl.savedStateHandle, viewModelCImpl.getDictionaryByIdUseCase(), viewModelCImpl.getWordsByDictionaryUseCase(), viewModelCImpl.searchWordsUseCase(), viewModelCImpl.deleteWordUseCase());

          case 2: // com.xty.englishhelper.ui.screen.home.HomeViewModel 
          return (T) new HomeViewModel(viewModelCImpl.getAllDictionariesUseCase(), viewModelCImpl.createDictionaryUseCase(), viewModelCImpl.deleteDictionaryUseCase());

          case 3: // com.xty.englishhelper.ui.screen.importexport.ImportExportViewModel 
          return (T) new ImportExportViewModel(viewModelCImpl.getAllDictionariesUseCase(), singletonCImpl.dictionaryRepositoryImplProvider.get(), singletonCImpl.wordRepositoryImplProvider.get(), singletonCImpl.jsonImportExporterProvider.get());

          case 4: // com.xty.englishhelper.ui.screen.settings.SettingsViewModel 
          return (T) new SettingsViewModel(singletonCImpl.settingsDataStoreProvider.get(), viewModelCImpl.testAiConnectionUseCase());

          case 5: // com.xty.englishhelper.ui.screen.word.WordDetailViewModel 
          return (T) new WordDetailViewModel(viewModelCImpl.savedStateHandle, viewModelCImpl.getWordByIdUseCase(), viewModelCImpl.deleteWordUseCase());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends EnglishHelperApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends EnglishHelperApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends EnglishHelperApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<AppDatabase> provideDatabaseProvider;

    private Provider<WordRepositoryImpl> wordRepositoryImplProvider;

    private Provider<DictionaryRepositoryImpl> dictionaryRepositoryImplProvider;

    private Provider<OkHttpClient> provideOkHttpClientProvider;

    private Provider<Moshi> provideMoshiProvider;

    private Provider<Retrofit> provideRetrofitProvider;

    private Provider<AnthropicApiService> provideAnthropicApiServiceProvider;

    private Provider<AiRepositoryImpl> aiRepositoryImplProvider;

    private Provider<DataStore<Preferences>> provideDataStoreProvider;

    private Provider<SettingsDataStore> settingsDataStoreProvider;

    private Provider<JsonImportExporter> jsonImportExporterProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private WordDao wordDao() {
      return AppModule_ProvideWordDaoFactory.provideWordDao(provideDatabaseProvider.get());
    }

    private DictionaryDao dictionaryDao() {
      return AppModule_ProvideDictionaryDaoFactory.provideDictionaryDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 1));
      this.wordRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<WordRepositoryImpl>(singletonCImpl, 0));
      this.dictionaryRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<DictionaryRepositoryImpl>(singletonCImpl, 2));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 6));
      this.provideMoshiProvider = DoubleCheck.provider(new SwitchingProvider<Moshi>(singletonCImpl, 7));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 5));
      this.provideAnthropicApiServiceProvider = DoubleCheck.provider(new SwitchingProvider<AnthropicApiService>(singletonCImpl, 4));
      this.aiRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<AiRepositoryImpl>(singletonCImpl, 3));
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 9));
      this.settingsDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<SettingsDataStore>(singletonCImpl, 8));
      this.jsonImportExporterProvider = DoubleCheck.provider(new SwitchingProvider<JsonImportExporter>(singletonCImpl, 10));
    }

    @Override
    public void injectEnglishHelperApp(EnglishHelperApp englishHelperApp) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.xty.englishhelper.data.repository.WordRepositoryImpl 
          return (T) new WordRepositoryImpl(singletonCImpl.wordDao());

          case 1: // com.xty.englishhelper.data.local.AppDatabase 
          return (T) AppModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.xty.englishhelper.data.repository.DictionaryRepositoryImpl 
          return (T) new DictionaryRepositoryImpl(singletonCImpl.dictionaryDao());

          case 3: // com.xty.englishhelper.data.repository.AiRepositoryImpl 
          return (T) new AiRepositoryImpl(singletonCImpl.provideAnthropicApiServiceProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 4: // com.xty.englishhelper.data.remote.AnthropicApiService 
          return (T) NetworkModule_ProvideAnthropicApiServiceFactory.provideAnthropicApiService(singletonCImpl.provideRetrofitProvider.get());

          case 5: // retrofit2.Retrofit 
          return (T) NetworkModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 6: // okhttp3.OkHttpClient 
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient();

          case 7: // com.squareup.moshi.Moshi 
          return (T) NetworkModule_ProvideMoshiFactory.provideMoshi();

          case 8: // com.xty.englishhelper.data.preferences.SettingsDataStore 
          return (T) new SettingsDataStore(singletonCImpl.provideDataStoreProvider.get());

          case 9: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> 
          return (T) AppModule_ProvideDataStoreFactory.provideDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.xty.englishhelper.data.json.JsonImportExporter 
          return (T) new JsonImportExporter(singletonCImpl.provideMoshiProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}

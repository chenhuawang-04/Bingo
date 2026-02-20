package com.xty.englishhelper.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.xty.englishhelper.data.local.dao.DictionaryDao;
import com.xty.englishhelper.data.local.dao.DictionaryDao_Impl;
import com.xty.englishhelper.data.local.dao.WordDao;
import com.xty.englishhelper.data.local.dao.WordDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile DictionaryDao _dictionaryDao;

  private volatile WordDao _wordDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dictionaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `color` INTEGER NOT NULL, `word_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dictionary_id` INTEGER NOT NULL, `spelling` TEXT NOT NULL, `phonetic` TEXT NOT NULL, `meanings_json` TEXT NOT NULL, `root_explanation` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`dictionary_id`) REFERENCES `dictionaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_dictionary_id` ON `words` (`dictionary_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_spelling` ON `words` (`spelling`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `synonyms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `word_id` INTEGER NOT NULL, `synonym` TEXT NOT NULL, `explanation` TEXT NOT NULL, FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_synonyms_word_id` ON `synonyms` (`word_id`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `similar_words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `word_id` INTEGER NOT NULL, `similar_word` TEXT NOT NULL, `meaning` TEXT NOT NULL, `explanation` TEXT NOT NULL, FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_similar_words_word_id` ON `similar_words` (`word_id`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cognates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `word_id` INTEGER NOT NULL, `cognate` TEXT NOT NULL, `meaning` TEXT NOT NULL, `shared_root` TEXT NOT NULL, FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cognates_word_id` ON `cognates` (`word_id`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e780e3905f3aee08eceefd2476fb2926')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `dictionaries`");
        db.execSQL("DROP TABLE IF EXISTS `words`");
        db.execSQL("DROP TABLE IF EXISTS `synonyms`");
        db.execSQL("DROP TABLE IF EXISTS `similar_words`");
        db.execSQL("DROP TABLE IF EXISTS `cognates`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsDictionaries = new HashMap<String, TableInfo.Column>(7);
        _columnsDictionaries.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("color", new TableInfo.Column("color", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("word_count", new TableInfo.Column("word_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDictionaries.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDictionaries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDictionaries = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDictionaries = new TableInfo("dictionaries", _columnsDictionaries, _foreignKeysDictionaries, _indicesDictionaries);
        final TableInfo _existingDictionaries = TableInfo.read(db, "dictionaries");
        if (!_infoDictionaries.equals(_existingDictionaries)) {
          return new RoomOpenHelper.ValidationResult(false, "dictionaries(com.xty.englishhelper.data.local.entity.DictionaryEntity).\n"
                  + " Expected:\n" + _infoDictionaries + "\n"
                  + " Found:\n" + _existingDictionaries);
        }
        final HashMap<String, TableInfo.Column> _columnsWords = new HashMap<String, TableInfo.Column>(8);
        _columnsWords.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("dictionary_id", new TableInfo.Column("dictionary_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("spelling", new TableInfo.Column("spelling", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("phonetic", new TableInfo.Column("phonetic", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("meanings_json", new TableInfo.Column("meanings_json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("root_explanation", new TableInfo.Column("root_explanation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWords.put("updated_at", new TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWords = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysWords.add(new TableInfo.ForeignKey("dictionaries", "CASCADE", "NO ACTION", Arrays.asList("dictionary_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesWords = new HashSet<TableInfo.Index>(2);
        _indicesWords.add(new TableInfo.Index("index_words_dictionary_id", false, Arrays.asList("dictionary_id"), Arrays.asList("ASC")));
        _indicesWords.add(new TableInfo.Index("index_words_spelling", false, Arrays.asList("spelling"), Arrays.asList("ASC")));
        final TableInfo _infoWords = new TableInfo("words", _columnsWords, _foreignKeysWords, _indicesWords);
        final TableInfo _existingWords = TableInfo.read(db, "words");
        if (!_infoWords.equals(_existingWords)) {
          return new RoomOpenHelper.ValidationResult(false, "words(com.xty.englishhelper.data.local.entity.WordEntity).\n"
                  + " Expected:\n" + _infoWords + "\n"
                  + " Found:\n" + _existingWords);
        }
        final HashMap<String, TableInfo.Column> _columnsSynonyms = new HashMap<String, TableInfo.Column>(4);
        _columnsSynonyms.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSynonyms.put("word_id", new TableInfo.Column("word_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSynonyms.put("synonym", new TableInfo.Column("synonym", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSynonyms.put("explanation", new TableInfo.Column("explanation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSynonyms = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysSynonyms.add(new TableInfo.ForeignKey("words", "CASCADE", "NO ACTION", Arrays.asList("word_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesSynonyms = new HashSet<TableInfo.Index>(1);
        _indicesSynonyms.add(new TableInfo.Index("index_synonyms_word_id", false, Arrays.asList("word_id"), Arrays.asList("ASC")));
        final TableInfo _infoSynonyms = new TableInfo("synonyms", _columnsSynonyms, _foreignKeysSynonyms, _indicesSynonyms);
        final TableInfo _existingSynonyms = TableInfo.read(db, "synonyms");
        if (!_infoSynonyms.equals(_existingSynonyms)) {
          return new RoomOpenHelper.ValidationResult(false, "synonyms(com.xty.englishhelper.data.local.entity.SynonymEntity).\n"
                  + " Expected:\n" + _infoSynonyms + "\n"
                  + " Found:\n" + _existingSynonyms);
        }
        final HashMap<String, TableInfo.Column> _columnsSimilarWords = new HashMap<String, TableInfo.Column>(5);
        _columnsSimilarWords.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimilarWords.put("word_id", new TableInfo.Column("word_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimilarWords.put("similar_word", new TableInfo.Column("similar_word", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimilarWords.put("meaning", new TableInfo.Column("meaning", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSimilarWords.put("explanation", new TableInfo.Column("explanation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSimilarWords = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysSimilarWords.add(new TableInfo.ForeignKey("words", "CASCADE", "NO ACTION", Arrays.asList("word_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesSimilarWords = new HashSet<TableInfo.Index>(1);
        _indicesSimilarWords.add(new TableInfo.Index("index_similar_words_word_id", false, Arrays.asList("word_id"), Arrays.asList("ASC")));
        final TableInfo _infoSimilarWords = new TableInfo("similar_words", _columnsSimilarWords, _foreignKeysSimilarWords, _indicesSimilarWords);
        final TableInfo _existingSimilarWords = TableInfo.read(db, "similar_words");
        if (!_infoSimilarWords.equals(_existingSimilarWords)) {
          return new RoomOpenHelper.ValidationResult(false, "similar_words(com.xty.englishhelper.data.local.entity.SimilarWordEntity).\n"
                  + " Expected:\n" + _infoSimilarWords + "\n"
                  + " Found:\n" + _existingSimilarWords);
        }
        final HashMap<String, TableInfo.Column> _columnsCognates = new HashMap<String, TableInfo.Column>(5);
        _columnsCognates.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCognates.put("word_id", new TableInfo.Column("word_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCognates.put("cognate", new TableInfo.Column("cognate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCognates.put("meaning", new TableInfo.Column("meaning", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCognates.put("shared_root", new TableInfo.Column("shared_root", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCognates = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysCognates.add(new TableInfo.ForeignKey("words", "CASCADE", "NO ACTION", Arrays.asList("word_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesCognates = new HashSet<TableInfo.Index>(1);
        _indicesCognates.add(new TableInfo.Index("index_cognates_word_id", false, Arrays.asList("word_id"), Arrays.asList("ASC")));
        final TableInfo _infoCognates = new TableInfo("cognates", _columnsCognates, _foreignKeysCognates, _indicesCognates);
        final TableInfo _existingCognates = TableInfo.read(db, "cognates");
        if (!_infoCognates.equals(_existingCognates)) {
          return new RoomOpenHelper.ValidationResult(false, "cognates(com.xty.englishhelper.data.local.entity.CognateEntity).\n"
                  + " Expected:\n" + _infoCognates + "\n"
                  + " Found:\n" + _existingCognates);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "e780e3905f3aee08eceefd2476fb2926", "0d2d7fdf19f695eb25625bd7331a8c46");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "dictionaries","words","synonyms","similar_words","cognates");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `dictionaries`");
      _db.execSQL("DELETE FROM `words`");
      _db.execSQL("DELETE FROM `synonyms`");
      _db.execSQL("DELETE FROM `similar_words`");
      _db.execSQL("DELETE FROM `cognates`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(DictionaryDao.class, DictionaryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WordDao.class, WordDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public DictionaryDao dictionaryDao() {
    if (_dictionaryDao != null) {
      return _dictionaryDao;
    } else {
      synchronized(this) {
        if(_dictionaryDao == null) {
          _dictionaryDao = new DictionaryDao_Impl(this);
        }
        return _dictionaryDao;
      }
    }
  }

  @Override
  public WordDao wordDao() {
    if (_wordDao != null) {
      return _wordDao;
    } else {
      synchronized(this) {
        if(_wordDao == null) {
          _wordDao = new WordDao_Impl(this);
        }
        return _wordDao;
      }
    }
  }
}

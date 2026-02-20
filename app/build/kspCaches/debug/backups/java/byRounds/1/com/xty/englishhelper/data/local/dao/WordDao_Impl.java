package com.xty.englishhelper.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.xty.englishhelper.data.local.entity.CognateEntity;
import com.xty.englishhelper.data.local.entity.SimilarWordEntity;
import com.xty.englishhelper.data.local.entity.SynonymEntity;
import com.xty.englishhelper.data.local.entity.WordEntity;
import com.xty.englishhelper.data.local.relation.WordWithDetails;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class WordDao_Impl implements WordDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WordEntity> __insertionAdapterOfWordEntity;

  private final EntityInsertionAdapter<SynonymEntity> __insertionAdapterOfSynonymEntity;

  private final EntityInsertionAdapter<SimilarWordEntity> __insertionAdapterOfSimilarWordEntity;

  private final EntityInsertionAdapter<CognateEntity> __insertionAdapterOfCognateEntity;

  private final EntityDeletionOrUpdateAdapter<WordEntity> __updateAdapterOfWordEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteWord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSynonymsByWordId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSimilarWordsByWordId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteCognatesByWordId;

  public WordDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWordEntity = new EntityInsertionAdapter<WordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `words` (`id`,`dictionary_id`,`spelling`,`phonetic`,`meanings_json`,`root_explanation`,`created_at`,`updated_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getDictionaryId());
        statement.bindString(3, entity.getSpelling());
        statement.bindString(4, entity.getPhonetic());
        statement.bindString(5, entity.getMeaningsJson());
        statement.bindString(6, entity.getRootExplanation());
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getUpdatedAt());
      }
    };
    this.__insertionAdapterOfSynonymEntity = new EntityInsertionAdapter<SynonymEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `synonyms` (`id`,`word_id`,`synonym`,`explanation`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SynonymEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getWordId());
        statement.bindString(3, entity.getSynonym());
        statement.bindString(4, entity.getExplanation());
      }
    };
    this.__insertionAdapterOfSimilarWordEntity = new EntityInsertionAdapter<SimilarWordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `similar_words` (`id`,`word_id`,`similar_word`,`meaning`,`explanation`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SimilarWordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getWordId());
        statement.bindString(3, entity.getSimilarWord());
        statement.bindString(4, entity.getMeaning());
        statement.bindString(5, entity.getExplanation());
      }
    };
    this.__insertionAdapterOfCognateEntity = new EntityInsertionAdapter<CognateEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cognates` (`id`,`word_id`,`cognate`,`meaning`,`shared_root`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CognateEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getWordId());
        statement.bindString(3, entity.getCognate());
        statement.bindString(4, entity.getMeaning());
        statement.bindString(5, entity.getSharedRoot());
      }
    };
    this.__updateAdapterOfWordEntity = new EntityDeletionOrUpdateAdapter<WordEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `words` SET `id` = ?,`dictionary_id` = ?,`spelling` = ?,`phonetic` = ?,`meanings_json` = ?,`root_explanation` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WordEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getDictionaryId());
        statement.bindString(3, entity.getSpelling());
        statement.bindString(4, entity.getPhonetic());
        statement.bindString(5, entity.getMeaningsJson());
        statement.bindString(6, entity.getRootExplanation());
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getUpdatedAt());
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteWord = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM words WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSynonymsByWordId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM synonyms WHERE word_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSimilarWordsByWordId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM similar_words WHERE word_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteCognatesByWordId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cognates WHERE word_id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertWord(final WordEntity word, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfWordEntity.insertAndReturnId(word);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSynonyms(final List<SynonymEntity> synonyms,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSynonymEntity.insert(synonyms);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSimilarWords(final List<SimilarWordEntity> similarWords,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSimilarWordEntity.insert(similarWords);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertCognates(final List<CognateEntity> cognates,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCognateEntity.insert(cognates);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateWord(final WordEntity word, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfWordEntity.handle(word);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteWord(final long wordId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteWord.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, wordId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteWord.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSynonymsByWordId(final long wordId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSynonymsByWordId.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, wordId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSynonymsByWordId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSimilarWordsByWordId(final long wordId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSimilarWordsByWordId.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, wordId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSimilarWordsByWordId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteCognatesByWordId(final long wordId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteCognatesByWordId.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, wordId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteCognatesByWordId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<WordWithDetails>> getWordsByDictionary(final long dictionaryId) {
    final String _sql = "SELECT * FROM words WHERE dictionary_id = ? ORDER BY spelling ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dictionaryId);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"synonyms", "similar_words",
        "cognates", "words"}, new Callable<List<WordWithDetails>>() {
      @Override
      @NonNull
      public List<WordWithDetails> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfDictionaryId = CursorUtil.getColumnIndexOrThrow(_cursor, "dictionary_id");
            final int _cursorIndexOfSpelling = CursorUtil.getColumnIndexOrThrow(_cursor, "spelling");
            final int _cursorIndexOfPhonetic = CursorUtil.getColumnIndexOrThrow(_cursor, "phonetic");
            final int _cursorIndexOfMeaningsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "meanings_json");
            final int _cursorIndexOfRootExplanation = CursorUtil.getColumnIndexOrThrow(_cursor, "root_explanation");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
            final LongSparseArray<ArrayList<SynonymEntity>> _collectionSynonyms = new LongSparseArray<ArrayList<SynonymEntity>>();
            final LongSparseArray<ArrayList<SimilarWordEntity>> _collectionSimilarWords = new LongSparseArray<ArrayList<SimilarWordEntity>>();
            final LongSparseArray<ArrayList<CognateEntity>> _collectionCognates = new LongSparseArray<ArrayList<CognateEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSynonyms.containsKey(_tmpKey)) {
                _collectionSynonyms.put(_tmpKey, new ArrayList<SynonymEntity>());
              }
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSimilarWords.containsKey(_tmpKey_1)) {
                _collectionSimilarWords.put(_tmpKey_1, new ArrayList<SimilarWordEntity>());
              }
              final long _tmpKey_2;
              _tmpKey_2 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionCognates.containsKey(_tmpKey_2)) {
                _collectionCognates.put(_tmpKey_2, new ArrayList<CognateEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsynonymsAscomXtyEnglishhelperDataLocalEntitySynonymEntity(_collectionSynonyms);
            __fetchRelationshipsimilarWordsAscomXtyEnglishhelperDataLocalEntitySimilarWordEntity(_collectionSimilarWords);
            __fetchRelationshipcognatesAscomXtyEnglishhelperDataLocalEntityCognateEntity(_collectionCognates);
            final List<WordWithDetails> _result = new ArrayList<WordWithDetails>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final WordWithDetails _item;
              final WordEntity _tmpWord;
              final long _tmpId;
              _tmpId = _cursor.getLong(_cursorIndexOfId);
              final long _tmpDictionaryId;
              _tmpDictionaryId = _cursor.getLong(_cursorIndexOfDictionaryId);
              final String _tmpSpelling;
              _tmpSpelling = _cursor.getString(_cursorIndexOfSpelling);
              final String _tmpPhonetic;
              _tmpPhonetic = _cursor.getString(_cursorIndexOfPhonetic);
              final String _tmpMeaningsJson;
              _tmpMeaningsJson = _cursor.getString(_cursorIndexOfMeaningsJson);
              final String _tmpRootExplanation;
              _tmpRootExplanation = _cursor.getString(_cursorIndexOfRootExplanation);
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpWord = new WordEntity(_tmpId,_tmpDictionaryId,_tmpSpelling,_tmpPhonetic,_tmpMeaningsJson,_tmpRootExplanation,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<SynonymEntity> _tmpSynonymsCollection;
              final long _tmpKey_3;
              _tmpKey_3 = _cursor.getLong(_cursorIndexOfId);
              _tmpSynonymsCollection = _collectionSynonyms.get(_tmpKey_3);
              final ArrayList<SimilarWordEntity> _tmpSimilarWordsCollection;
              final long _tmpKey_4;
              _tmpKey_4 = _cursor.getLong(_cursorIndexOfId);
              _tmpSimilarWordsCollection = _collectionSimilarWords.get(_tmpKey_4);
              final ArrayList<CognateEntity> _tmpCognatesCollection;
              final long _tmpKey_5;
              _tmpKey_5 = _cursor.getLong(_cursorIndexOfId);
              _tmpCognatesCollection = _collectionCognates.get(_tmpKey_5);
              _item = new WordWithDetails(_tmpWord,_tmpSynonymsCollection,_tmpSimilarWordsCollection,_tmpCognatesCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<WordWithDetails>> searchWords(final long dictionaryId, final String query) {
    final String _sql = "SELECT * FROM words WHERE dictionary_id = ? AND spelling LIKE '%' || ? || '%' ORDER BY spelling ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dictionaryId);
    _argIndex = 2;
    _statement.bindString(_argIndex, query);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"synonyms", "similar_words",
        "cognates", "words"}, new Callable<List<WordWithDetails>>() {
      @Override
      @NonNull
      public List<WordWithDetails> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfDictionaryId = CursorUtil.getColumnIndexOrThrow(_cursor, "dictionary_id");
            final int _cursorIndexOfSpelling = CursorUtil.getColumnIndexOrThrow(_cursor, "spelling");
            final int _cursorIndexOfPhonetic = CursorUtil.getColumnIndexOrThrow(_cursor, "phonetic");
            final int _cursorIndexOfMeaningsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "meanings_json");
            final int _cursorIndexOfRootExplanation = CursorUtil.getColumnIndexOrThrow(_cursor, "root_explanation");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
            final LongSparseArray<ArrayList<SynonymEntity>> _collectionSynonyms = new LongSparseArray<ArrayList<SynonymEntity>>();
            final LongSparseArray<ArrayList<SimilarWordEntity>> _collectionSimilarWords = new LongSparseArray<ArrayList<SimilarWordEntity>>();
            final LongSparseArray<ArrayList<CognateEntity>> _collectionCognates = new LongSparseArray<ArrayList<CognateEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSynonyms.containsKey(_tmpKey)) {
                _collectionSynonyms.put(_tmpKey, new ArrayList<SynonymEntity>());
              }
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSimilarWords.containsKey(_tmpKey_1)) {
                _collectionSimilarWords.put(_tmpKey_1, new ArrayList<SimilarWordEntity>());
              }
              final long _tmpKey_2;
              _tmpKey_2 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionCognates.containsKey(_tmpKey_2)) {
                _collectionCognates.put(_tmpKey_2, new ArrayList<CognateEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsynonymsAscomXtyEnglishhelperDataLocalEntitySynonymEntity(_collectionSynonyms);
            __fetchRelationshipsimilarWordsAscomXtyEnglishhelperDataLocalEntitySimilarWordEntity(_collectionSimilarWords);
            __fetchRelationshipcognatesAscomXtyEnglishhelperDataLocalEntityCognateEntity(_collectionCognates);
            final List<WordWithDetails> _result = new ArrayList<WordWithDetails>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final WordWithDetails _item;
              final WordEntity _tmpWord;
              final long _tmpId;
              _tmpId = _cursor.getLong(_cursorIndexOfId);
              final long _tmpDictionaryId;
              _tmpDictionaryId = _cursor.getLong(_cursorIndexOfDictionaryId);
              final String _tmpSpelling;
              _tmpSpelling = _cursor.getString(_cursorIndexOfSpelling);
              final String _tmpPhonetic;
              _tmpPhonetic = _cursor.getString(_cursorIndexOfPhonetic);
              final String _tmpMeaningsJson;
              _tmpMeaningsJson = _cursor.getString(_cursorIndexOfMeaningsJson);
              final String _tmpRootExplanation;
              _tmpRootExplanation = _cursor.getString(_cursorIndexOfRootExplanation);
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpWord = new WordEntity(_tmpId,_tmpDictionaryId,_tmpSpelling,_tmpPhonetic,_tmpMeaningsJson,_tmpRootExplanation,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<SynonymEntity> _tmpSynonymsCollection;
              final long _tmpKey_3;
              _tmpKey_3 = _cursor.getLong(_cursorIndexOfId);
              _tmpSynonymsCollection = _collectionSynonyms.get(_tmpKey_3);
              final ArrayList<SimilarWordEntity> _tmpSimilarWordsCollection;
              final long _tmpKey_4;
              _tmpKey_4 = _cursor.getLong(_cursorIndexOfId);
              _tmpSimilarWordsCollection = _collectionSimilarWords.get(_tmpKey_4);
              final ArrayList<CognateEntity> _tmpCognatesCollection;
              final long _tmpKey_5;
              _tmpKey_5 = _cursor.getLong(_cursorIndexOfId);
              _tmpCognatesCollection = _collectionCognates.get(_tmpKey_5);
              _item = new WordWithDetails(_tmpWord,_tmpSynonymsCollection,_tmpSimilarWordsCollection,_tmpCognatesCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getWordById(final long wordId,
      final Continuation<? super WordWithDetails> $completion) {
    final String _sql = "SELECT * FROM words WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, wordId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, true, _cancellationSignal, new Callable<WordWithDetails>() {
      @Override
      @Nullable
      public WordWithDetails call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfDictionaryId = CursorUtil.getColumnIndexOrThrow(_cursor, "dictionary_id");
            final int _cursorIndexOfSpelling = CursorUtil.getColumnIndexOrThrow(_cursor, "spelling");
            final int _cursorIndexOfPhonetic = CursorUtil.getColumnIndexOrThrow(_cursor, "phonetic");
            final int _cursorIndexOfMeaningsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "meanings_json");
            final int _cursorIndexOfRootExplanation = CursorUtil.getColumnIndexOrThrow(_cursor, "root_explanation");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
            final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
            final LongSparseArray<ArrayList<SynonymEntity>> _collectionSynonyms = new LongSparseArray<ArrayList<SynonymEntity>>();
            final LongSparseArray<ArrayList<SimilarWordEntity>> _collectionSimilarWords = new LongSparseArray<ArrayList<SimilarWordEntity>>();
            final LongSparseArray<ArrayList<CognateEntity>> _collectionCognates = new LongSparseArray<ArrayList<CognateEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSynonyms.containsKey(_tmpKey)) {
                _collectionSynonyms.put(_tmpKey, new ArrayList<SynonymEntity>());
              }
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionSimilarWords.containsKey(_tmpKey_1)) {
                _collectionSimilarWords.put(_tmpKey_1, new ArrayList<SimilarWordEntity>());
              }
              final long _tmpKey_2;
              _tmpKey_2 = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionCognates.containsKey(_tmpKey_2)) {
                _collectionCognates.put(_tmpKey_2, new ArrayList<CognateEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipsynonymsAscomXtyEnglishhelperDataLocalEntitySynonymEntity(_collectionSynonyms);
            __fetchRelationshipsimilarWordsAscomXtyEnglishhelperDataLocalEntitySimilarWordEntity(_collectionSimilarWords);
            __fetchRelationshipcognatesAscomXtyEnglishhelperDataLocalEntityCognateEntity(_collectionCognates);
            final WordWithDetails _result;
            if (_cursor.moveToFirst()) {
              final WordEntity _tmpWord;
              final long _tmpId;
              _tmpId = _cursor.getLong(_cursorIndexOfId);
              final long _tmpDictionaryId;
              _tmpDictionaryId = _cursor.getLong(_cursorIndexOfDictionaryId);
              final String _tmpSpelling;
              _tmpSpelling = _cursor.getString(_cursorIndexOfSpelling);
              final String _tmpPhonetic;
              _tmpPhonetic = _cursor.getString(_cursorIndexOfPhonetic);
              final String _tmpMeaningsJson;
              _tmpMeaningsJson = _cursor.getString(_cursorIndexOfMeaningsJson);
              final String _tmpRootExplanation;
              _tmpRootExplanation = _cursor.getString(_cursorIndexOfRootExplanation);
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              final long _tmpUpdatedAt;
              _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
              _tmpWord = new WordEntity(_tmpId,_tmpDictionaryId,_tmpSpelling,_tmpPhonetic,_tmpMeaningsJson,_tmpRootExplanation,_tmpCreatedAt,_tmpUpdatedAt);
              final ArrayList<SynonymEntity> _tmpSynonymsCollection;
              final long _tmpKey_3;
              _tmpKey_3 = _cursor.getLong(_cursorIndexOfId);
              _tmpSynonymsCollection = _collectionSynonyms.get(_tmpKey_3);
              final ArrayList<SimilarWordEntity> _tmpSimilarWordsCollection;
              final long _tmpKey_4;
              _tmpKey_4 = _cursor.getLong(_cursorIndexOfId);
              _tmpSimilarWordsCollection = _collectionSimilarWords.get(_tmpKey_4);
              final ArrayList<CognateEntity> _tmpCognatesCollection;
              final long _tmpKey_5;
              _tmpKey_5 = _cursor.getLong(_cursorIndexOfId);
              _tmpCognatesCollection = _collectionCognates.get(_tmpKey_5);
              _result = new WordWithDetails(_tmpWord,_tmpSynonymsCollection,_tmpSimilarWordsCollection,_tmpCognatesCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
            _statement.release();
          }
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private void __fetchRelationshipsynonymsAscomXtyEnglishhelperDataLocalEntitySynonymEntity(
      @NonNull final LongSparseArray<ArrayList<SynonymEntity>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipsynonymsAscomXtyEnglishhelperDataLocalEntitySynonymEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `id`,`word_id`,`synonym`,`explanation` FROM `synonyms` WHERE `word_id` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "word_id");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfWordId = 1;
      final int _cursorIndexOfSynonym = 2;
      final int _cursorIndexOfExplanation = 3;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<SynonymEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final SynonymEntity _item_1;
          final long _tmpId;
          _tmpId = _cursor.getLong(_cursorIndexOfId);
          final long _tmpWordId;
          _tmpWordId = _cursor.getLong(_cursorIndexOfWordId);
          final String _tmpSynonym;
          _tmpSynonym = _cursor.getString(_cursorIndexOfSynonym);
          final String _tmpExplanation;
          _tmpExplanation = _cursor.getString(_cursorIndexOfExplanation);
          _item_1 = new SynonymEntity(_tmpId,_tmpWordId,_tmpSynonym,_tmpExplanation);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private void __fetchRelationshipsimilarWordsAscomXtyEnglishhelperDataLocalEntitySimilarWordEntity(
      @NonNull final LongSparseArray<ArrayList<SimilarWordEntity>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipsimilarWordsAscomXtyEnglishhelperDataLocalEntitySimilarWordEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `id`,`word_id`,`similar_word`,`meaning`,`explanation` FROM `similar_words` WHERE `word_id` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "word_id");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfWordId = 1;
      final int _cursorIndexOfSimilarWord = 2;
      final int _cursorIndexOfMeaning = 3;
      final int _cursorIndexOfExplanation = 4;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<SimilarWordEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final SimilarWordEntity _item_1;
          final long _tmpId;
          _tmpId = _cursor.getLong(_cursorIndexOfId);
          final long _tmpWordId;
          _tmpWordId = _cursor.getLong(_cursorIndexOfWordId);
          final String _tmpSimilarWord;
          _tmpSimilarWord = _cursor.getString(_cursorIndexOfSimilarWord);
          final String _tmpMeaning;
          _tmpMeaning = _cursor.getString(_cursorIndexOfMeaning);
          final String _tmpExplanation;
          _tmpExplanation = _cursor.getString(_cursorIndexOfExplanation);
          _item_1 = new SimilarWordEntity(_tmpId,_tmpWordId,_tmpSimilarWord,_tmpMeaning,_tmpExplanation);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private void __fetchRelationshipcognatesAscomXtyEnglishhelperDataLocalEntityCognateEntity(
      @NonNull final LongSparseArray<ArrayList<CognateEntity>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipcognatesAscomXtyEnglishhelperDataLocalEntityCognateEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `id`,`word_id`,`cognate`,`meaning`,`shared_root` FROM `cognates` WHERE `word_id` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "word_id");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfWordId = 1;
      final int _cursorIndexOfCognate = 2;
      final int _cursorIndexOfMeaning = 3;
      final int _cursorIndexOfSharedRoot = 4;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<CognateEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final CognateEntity _item_1;
          final long _tmpId;
          _tmpId = _cursor.getLong(_cursorIndexOfId);
          final long _tmpWordId;
          _tmpWordId = _cursor.getLong(_cursorIndexOfWordId);
          final String _tmpCognate;
          _tmpCognate = _cursor.getString(_cursorIndexOfCognate);
          final String _tmpMeaning;
          _tmpMeaning = _cursor.getString(_cursorIndexOfMeaning);
          final String _tmpSharedRoot;
          _tmpSharedRoot = _cursor.getString(_cursorIndexOfSharedRoot);
          _item_1 = new CognateEntity(_tmpId,_tmpWordId,_tmpCognate,_tmpMeaning,_tmpSharedRoot);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}

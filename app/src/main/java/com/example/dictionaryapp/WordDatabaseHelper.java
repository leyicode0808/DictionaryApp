package com.example.dictionaryapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class WordDatabaseHelper extends SQLiteOpenHelper {
    // 数据库信息（核心修改1：添加调试开关，开发时true，上线前false）
    public static boolean DEBUG = true; // 调试模式：保持数据库连接，方便查看
    private static final String DB_NAME = "WordTranslation.db";
    private static final int DB_VERSION = 3;
    private static final String SP_NAME = "ImportStatus";
    private static final String KEY_IMPORTED = "isCsvImported";

    // 表和字段定义（保持不变）
    public static final String TABLE_WORD_DICT = "word_dict";
    public static final String COL_DICT_ID = "_id";
    public static final String COL_WORD = "word";
    public static final String COL_TRANSLATION = "translation";

    public static final String TABLE_HISTORY = "translation_history";
    public static final String COL_HISTORY_ID = "_id";
    public static final String COL_HISTORY_WORD = "word";
    public static final String COL_HISTORY_TRANSLATION = "translation";
    public static final String COL_HISTORY_TIME = "create_time";

    public static final String TABLE_COLLECTION = "collected_words";
    public static final String COL_COLLECT_ID = "_id";
    public static final String COL_COLLECT_WORD = "word";
    public static final String COL_COLLECT_TRANSLATION = "translation";
    public static final String COL_COLLECT_CREATE_TIME = "collect_time";

    private final Context mContext;

    // 创建表SQL（保持不变）
    private static final String CREATE_TABLE_DICT = "CREATE TABLE IF NOT EXISTS " + TABLE_WORD_DICT + " (" +
            COL_DICT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_WORD + " TEXT UNIQUE NOT NULL, " +
            COL_TRANSLATION + " TEXT NOT NULL);";

    private static final String CREATE_TABLE_HISTORY = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
            COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_HISTORY_WORD + " TEXT NOT NULL, " +
            COL_HISTORY_TRANSLATION + " TEXT NOT NULL, " +
            COL_HISTORY_TIME + " INTEGER NOT NULL);";

    private static final String CREATE_TABLE_COLLECTION = "CREATE TABLE IF NOT EXISTS " + TABLE_COLLECTION + " (" +
            COL_COLLECT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_COLLECT_WORD + " TEXT UNIQUE NOT NULL, " +
            COL_COLLECT_TRANSLATION + " TEXT NOT NULL, " +
            COL_COLLECT_CREATE_TIME + " INTEGER NOT NULL);";

    public WordDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_DICT);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_COLLECTION);

        if (!isCsvImported()) {
            importCsvToDb(db);
            setCsvImported(true);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            String addTimeColumnSql = "ALTER TABLE " + TABLE_COLLECTION +
                    " ADD COLUMN " + COL_COLLECT_CREATE_TIME + " INTEGER NOT NULL DEFAULT " +
                    System.currentTimeMillis();
            db.execSQL(addTimeColumnSql);
            Log.d("DB_UPGRADE", "收藏表成功添加时间字段");
        }
        if (oldVersion < 3) {
            String addTranslationColumnSql = "ALTER TABLE " + TABLE_COLLECTION +
                    " ADD COLUMN " + COL_COLLECT_TRANSLATION + " TEXT NOT NULL DEFAULT ''";
            db.execSQL(addTranslationColumnSql);
            Log.d("DB_UPGRADE", "收藏表成功添加翻译字段，兼容旧数据");
        }
    }

    // CSV导入（保持不变）
    private void importCsvToDb(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(mContext.getAssets().open("word_translation.csv"), "UTF-8"));
            String line;
            String header = br.readLine();
            if (header == null || header.trim().isEmpty()) {
                Log.w("CSV_IMPORT", "CSV 无表头，可能格式错误");
            }

            int count = 0;
            String csvRegex = "\"([^\"]*?)\"|([^,]+)";
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(csvRegex);
                java.util.regex.Matcher matcher = pattern.matcher(line);
                List<String> parts = new ArrayList<>();
                while (matcher.find()) {
                    String group1 = matcher.group(1);
                    String group2 = matcher.group(2);
                    if (group1 != null) parts.add(group1.trim());
                    else if (group2 != null) parts.add(group2.trim());
                }

                if (parts.size() >= 2) {
                    String word = parts.get(0).toLowerCase();
                    String translation = parts.get(1);
                    if (!word.isEmpty() && !translation.isEmpty()) {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(COL_WORD, word);
                        values.put(COL_TRANSLATION, translation);
                        db.insertWithOnConflict(TABLE_WORD_DICT, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                        count++;
                    }
                }
            }
            br.close();
            db.setTransactionSuccessful();
            Log.d("CSV_IMPORT", "CSV导入成功，共" + count + "条单词");

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("CSV_IMPORT", "CSV文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("CSV_IMPORT", "导入异常：" + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    // 工具方法：标记CSV导入状态（保持不变）
    private boolean isCsvImported() {
        SharedPreferences sp = mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IMPORTED, false);
    }

    private void setCsvImported(boolean imported) {
        SharedPreferences sp = mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_IMPORTED, imported).apply();
    }

    // ---------------------- 核心修改2：统一数据库连接管理（调试模式不关闭） ----------------------
    /**
     * 获取数据库连接（统一处理调试模式）
     * @param writable 是否需要可写权限（true=getWritableDatabase，false=getReadableDatabase）
     * @return SQLiteDatabase 连接
     */
    private SQLiteDatabase getDatabase(boolean writable) {
        SQLiteDatabase db = writable ? super.getWritableDatabase() : super.getReadableDatabase();
        if (DEBUG) {
            db.setLockingEnabled(false); // 调试模式禁用锁定，避免多线程冲突
            Log.d("DB_DEBUG", "数据库连接保持打开状态，方便查看");
        }
        return db;
    }

    // ---------------------- 词典表：查询翻译（修改连接获取和关闭逻辑） ----------------------
    public String queryTranslation(String word) {
        if (word.isEmpty()) return null;
        String translation = null;
        SQLiteDatabase db = getDatabase(false); // 改用统一方法获取连接（只读）
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_WORD_DICT,
                    new String[]{COL_TRANSLATION},
                    COL_WORD + " = ?",
                    new String[]{word.toLowerCase()},
                    null, null, null
            );
            if (cursor.moveToFirst()) {
                translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_TRANSLATION));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            // 核心修改：调试模式不关闭数据库，非调试模式正常关闭
            if (!DEBUG) {
                db.close();
            }
        }
        return translation;
    }

    // ---------------------- 历史表：增删查（修改连接逻辑） ----------------------
    public long addHistory(String word, String translation) {
        SQLiteDatabase db = getDatabase(true); // 可写连接
        ContentValues values = new ContentValues();
        values.put(COL_HISTORY_WORD, word);
        values.put(COL_HISTORY_TRANSLATION, translation);
        values.put(COL_HISTORY_TIME, System.currentTimeMillis());
        long id = db.insert(TABLE_HISTORY, null, values);

        // 调试模式不关闭
        if (!DEBUG) {
            db.close();
        }
        return id;
    }

    public List<String> queryAllHistory() {
        List<String> historyList = new ArrayList<>();
        SQLiteDatabase db = getDatabase(false);
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_HISTORY,
                    new String[]{COL_HISTORY_WORD, COL_HISTORY_TRANSLATION},
                    null, null, null, null,
                    COL_HISTORY_TIME + " DESC"
            );
            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_WORD));
                String trans = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_TRANSLATION));
                historyList.add(word + " → " + trans);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (!DEBUG) {
                db.close();
            }
        }
        return historyList;
    }

    public int deleteHistory(String word, String translation) {
        SQLiteDatabase db = getDatabase(true);
        int rows = db.delete(
                TABLE_HISTORY,
                COL_HISTORY_WORD + " = ? AND " + COL_HISTORY_TRANSLATION + " = ?",
                new String[]{word, translation}
        );
        if (!DEBUG) {
            db.close();
        }
        return rows;
    }

    // ---------------------- 收藏表：增删查（修改连接逻辑） ----------------------
    public long addCollection(String word, String translation) {
        if (word.isEmpty() || translation.isEmpty()) {
            Log.e("COLLECTION", "单词或翻译为空，收藏失败");
            return -1;
        }

        SQLiteDatabase db = getDatabase(true);
        ContentValues values = new ContentValues();
        values.put(COL_COLLECT_WORD, word.toLowerCase());
        values.put(COL_COLLECT_TRANSLATION, translation);
        values.put(COL_COLLECT_CREATE_TIME, System.currentTimeMillis());

        long id = db.insertWithOnConflict(
                TABLE_COLLECTION,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        if (!DEBUG) {
            db.close();
        }
        return id;
    }

    public int removeCollection(String word) {
        SQLiteDatabase db = getDatabase(true);
        int rows = db.delete(
                TABLE_COLLECTION,
                COL_COLLECT_WORD + " = ?",
                new String[]{word.toLowerCase()}
        );
        if (!DEBUG) {
            db.close();
        }
        return rows;
    }

    public boolean isCollected(String word) {
        if (word.isEmpty()) return false;
        SQLiteDatabase db = getDatabase(false);
        Cursor cursor = null;
        boolean isCollected = false;
        try {
            cursor = db.query(
                    TABLE_COLLECTION,
                    new String[]{COL_COLLECT_WORD},
                    COL_COLLECT_WORD + " = ?",
                    new String[]{word.toLowerCase()},
                    null, null, null
            );
            isCollected = cursor.moveToFirst();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (!DEBUG) {
                db.close();
            }
        }
        return isCollected;
    }

    public List<WordBean> queryAllCollections(int sortType) {
        List<WordBean> collectList = new ArrayList<>();
        SQLiteDatabase db = getDatabase(false);
        Cursor cursor = null;
        try {
            String orderBy;
            if (sortType == 1) {
                orderBy = COL_COLLECT_WORD + " ASC";
            } else {
                orderBy = COL_COLLECT_CREATE_TIME + " DESC";
            }

            cursor = db.query(
                    TABLE_COLLECTION,
                    new String[]{COL_COLLECT_WORD, COL_COLLECT_TRANSLATION},
                    null, null, null, null,
                    orderBy
            );

            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECT_WORD));
                String translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECT_TRANSLATION));
                collectList.add(new WordBean(word, translation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (!DEBUG) {
                db.close();
            }
        }
        return collectList;
    }

    public List<WordBean> queryAllCollections() {
        return queryAllCollections(0);
    }

    // ---------------------- 词典表：新增方法（修改连接逻辑） ----------------------
    public long addWord(String word, String translation) {
        if (word.isEmpty() || translation.isEmpty()) return -1;
        SQLiteDatabase db = getDatabase(true);
        ContentValues values = new ContentValues();
        values.put(COL_WORD, word.toLowerCase());
        values.put(COL_TRANSLATION, translation);
        long id = db.insertWithOnConflict(TABLE_WORD_DICT, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (!DEBUG) {
            db.close();
        }
        return id;
    }

    public List<WordBean> queryAllWords() {
        List<WordBean> wordList = new ArrayList<>();
        SQLiteDatabase db = getDatabase(false);
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_WORD_DICT,
                    new String[]{COL_WORD, COL_TRANSLATION},
                    null, null, null, null,
                    COL_WORD + " ASC"
            );
            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COL_WORD));
                String translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_TRANSLATION));
                wordList.add(new WordBean(word, translation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (!DEBUG) {
                db.close();
            }
        }
        return wordList;
    }

    public List<WordBean> queryWordsByKeyword(String keyword) {
        List<WordBean> wordList = new ArrayList<>();
        if (keyword.isEmpty()) return wordList;
        SQLiteDatabase db = getDatabase(false);
        Cursor cursor = null;
        try {
            String lowerKeyword = keyword.toLowerCase();
            String selection = COL_WORD + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + lowerKeyword + "%"};

            cursor = db.query(
                    TABLE_WORD_DICT,
                    new String[]{COL_WORD, COL_TRANSLATION},
                    selection, selectionArgs, null, null,
                    COL_WORD + " ASC"
            );
            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COL_WORD));
                String translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_TRANSLATION));
                wordList.add(new WordBean(word, translation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (!DEBUG) {
                db.close();
            }
        }
        return wordList;
    }

    // 辅助实体类（保持不变）
    public static class WordBean {
        private String word;
        private String translation;

        public WordBean(String word, String translation) {
            this.word = word;
            this.translation = translation;
        }

        public String getWord() { return word; }
        public String getTranslation() { return translation; }
    }
}
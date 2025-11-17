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
    // 数据库信息（核心修改1：版本号从2升级到3，触发onUpgrade更新收藏表）
    private static final String DB_NAME = "WordTranslation.db";
    private static final int DB_VERSION = 3; // 关键升级！
    private static final String SP_NAME = "ImportStatus";
    private static final String KEY_IMPORTED = "isCsvImported";

    // 1. 单词词典表（存储CSV导入的单词和翻译）
    public static final String TABLE_WORD_DICT = "word_dict";
    public static final String COL_DICT_ID = "_id";
    public static final String COL_WORD = "word";
    public static final String COL_TRANSLATION = "translation";

    // 2. 翻译历史表
    public static final String TABLE_HISTORY = "translation_history";
    public static final String COL_HISTORY_ID = "_id";
    public static final String COL_HISTORY_WORD = "word";
    public static final String COL_HISTORY_TRANSLATION = "translation";
    public static final String COL_HISTORY_TIME = "create_time"; // 时间戳

    // 3. 收藏单词表（核心修改2：新增翻译字段 COL_COLLECT_TRANSLATION）
    public static final String TABLE_COLLECTION = "collected_words";
    public static final String COL_COLLECT_ID = "_id";
    public static final String COL_COLLECT_WORD = "word";
    public static final String COL_COLLECT_TRANSLATION = "translation"; // 新增：存储翻译
    public static final String COL_COLLECT_CREATE_TIME = "collect_time"; // 原有：收藏时间戳

    private final Context mContext;

    // 创建表的SQL语句（核心修改3：收藏表添加翻译字段）
    private static final String CREATE_TABLE_DICT = "CREATE TABLE IF NOT EXISTS " + TABLE_WORD_DICT + " (" +
            COL_DICT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_WORD + " TEXT UNIQUE NOT NULL, " +
            COL_TRANSLATION + " TEXT NOT NULL);";

    private static final String CREATE_TABLE_HISTORY = "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
            COL_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_HISTORY_WORD + " TEXT NOT NULL, " +
            COL_HISTORY_TRANSLATION + " TEXT NOT NULL, " +
            COL_HISTORY_TIME + " INTEGER NOT NULL);";

    // 收藏表SQL修改：新增 COL_COLLECT_TRANSLATION 字段
    private static final String CREATE_TABLE_COLLECTION = "CREATE TABLE IF NOT EXISTS " + TABLE_COLLECTION + " (" +
            COL_COLLECT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_COLLECT_WORD + " TEXT UNIQUE NOT NULL, " + // 单词唯一，避免重复收藏
            COL_COLLECT_TRANSLATION + " TEXT NOT NULL, " + // 新增：翻译字段（非空）
            COL_COLLECT_CREATE_TIME + " INTEGER NOT NULL);"; // 原有：收藏时间

    public WordDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建3张表（收藏表已包含翻译字段）
        db.execSQL(CREATE_TABLE_DICT);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_COLLECTION);

        // 检查CSV是否已导入，未导入则执行导入
        if (!isCsvImported()) {
            importCsvToDb(db);
            setCsvImported(true); // 标记为已导入
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 核心修改4：数据库升级逻辑（兼容旧数据）
        // 1. 旧版本<2：添加收藏时间字段（原有逻辑保留）
        if (oldVersion < 2) {
            String addTimeColumnSql = "ALTER TABLE " + TABLE_COLLECTION +
                    " ADD COLUMN " + COL_COLLECT_CREATE_TIME + " INTEGER NOT NULL DEFAULT " +
                    System.currentTimeMillis();
            db.execSQL(addTimeColumnSql);
            Log.d("DB_UPGRADE", "收藏表成功添加时间字段");
        }
        // 2. 旧版本<3：添加翻译字段（核心新增）
        if (oldVersion < 3) {
            // 给旧收藏表新增翻译字段，默认值为空字符串（满足NOT NULL约束）
            String addTranslationColumnSql = "ALTER TABLE " + TABLE_COLLECTION +
                    " ADD COLUMN " + COL_COLLECT_TRANSLATION + " TEXT NOT NULL DEFAULT ''";
            db.execSQL(addTranslationColumnSql);
            Log.d("DB_UPGRADE", "收藏表成功添加翻译字段，兼容旧数据");
        }
    }

    // 读取CSV文件并导入到word_dict表（无修改，保持原有逻辑）
    private void importCsvToDb(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(mContext.getAssets().open("word_translation.csv"), "UTF-8"));
            String line;
            String header = br.readLine(); // 跳过表头
            if (header == null || header.trim().isEmpty()) {
                Log.w("CSV_IMPORT", "CSV 无表头，可能格式错误");
            }

            int count = 0;
            String csvRegex = "\"([^\"]*?)\"|([^,]+)"; // 适配带引号格式
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 正则解析 CSV 字段
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

    // ---------------------- 工具方法：标记CSV是否已导入（无修改） ----------------------
    private boolean isCsvImported() {
        SharedPreferences sp = mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IMPORTED, false);
    }

    private void setCsvImported(boolean imported) {
        SharedPreferences sp = mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_IMPORTED, imported).apply();
    }

    // ---------------------- 词典表：查询翻译（无修改） ----------------------
    public String queryTranslation(String word) {
        if (word.isEmpty()) return null;
        String translation = null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            // 不区分大小写查询（lower(word) 与传入的小写单词匹配）
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
            db.close();
        }
        return translation;
    }

    // ---------------------- 历史表：增删查（无修改） ----------------------
    // 添加历史记录
    public long addHistory(String word, String translation) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_HISTORY_WORD, word);
        values.put(COL_HISTORY_TRANSLATION, translation);
        values.put(COL_HISTORY_TIME, System.currentTimeMillis()); // 当前时间戳
        long id = db.insert(TABLE_HISTORY, null, values);
        db.close();
        return id;
    }

    // 查询所有历史记录（按时间倒序：最新的在前面）
    public List<String> queryAllHistory() {
        List<String> historyList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_HISTORY,
                    new String[]{COL_HISTORY_WORD, COL_HISTORY_TRANSLATION},
                    null, null, null, null,
                    COL_HISTORY_TIME + " DESC" // 按时间倒序
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
            db.close();
        }
        return historyList;
    }

    // 删除单条历史记录
    public int deleteHistory(String word, String translation) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(
                TABLE_HISTORY,
                COL_HISTORY_WORD + " = ? AND " + COL_HISTORY_TRANSLATION + " = ?",
                new String[]{word, translation}
        );
        db.close();
        return rows;
    }

    // ---------------------- 收藏表：增删查（核心修改5：支持单词+翻译） ----------------------
    // 添加收藏（核心修改：接收单词+翻译，同时存储）
    public long addCollection(String word, String translation) {
        // 校验参数（避免空翻译）
        if (word.isEmpty() || translation.isEmpty()) {
            Log.e("COLLECTION", "单词或翻译为空，收藏失败");
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_COLLECT_WORD, word.toLowerCase()); // 统一小写存储
        values.put(COL_COLLECT_TRANSLATION, translation); // 存储翻译（核心新增）
        values.put(COL_COLLECT_CREATE_TIME, System.currentTimeMillis()); // 收藏时间

        // 避免重复收藏（单词唯一，冲突时返回-1）
        long id = db.insertWithOnConflict(
                TABLE_COLLECTION,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        db.close();
        return id;
    }

    // 取消收藏（无修改：按单词删除，翻译随记录一起删除）
    public int removeCollection(String word) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(
                TABLE_COLLECTION,
                COL_COLLECT_WORD + " = ?",
                new String[]{word.toLowerCase()}
        );
        db.close();
        return rows;
    }

    // 查询单词是否已收藏（无修改：按单词判断）
    public boolean isCollected(String word) {
        if (word.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
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
            isCollected = cursor.moveToFirst(); // 有数据则已收藏
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return isCollected;
    }

    // 核心修改6：查询所有收藏（返回WordBean列表，含单词+翻译，支持排序）
    public List<WordBean> queryAllCollections(int sortType) {
        List<WordBean> collectList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            // 排序规则（0=时间倒序，1=字母正序）
            String orderBy;
            if (sortType == 1) {
                orderBy = COL_COLLECT_WORD + " ASC"; // 字母A-Z正序
            } else {
                orderBy = COL_COLLECT_CREATE_TIME + " DESC"; // 最新收藏在前
            }

            // 查询：同时获取单词和翻译（核心修改）
            cursor = db.query(
                    TABLE_COLLECTION,
                    new String[]{COL_COLLECT_WORD, COL_COLLECT_TRANSLATION}, // 查询两个字段
                    null, null, null, null,
                    orderBy
            );

            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECT_WORD));
                String translation = cursor.getString(cursor.getColumnIndexOrThrow(COL_COLLECT_TRANSLATION));
                // 封装成WordBean（包含单词+翻译）
                collectList.add(new WordBean(word, translation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return collectList;
    }

    // 兼容旧代码：无参重载（默认时间排序）
    public List<WordBean> queryAllCollections() {
        return queryAllCollections(0); // 0=时间倒序（最新收藏在前）
    }

    // ---------------------- 词典表：新增方法（用于完整词典功能） ----------------------
    // 1. 添加陌生单词（完善词典）
    public long addWord(String word, String translation) {
        if (word.isEmpty() || translation.isEmpty()) return -1;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_WORD, word.toLowerCase()); // 统一小写存储
        values.put(COL_TRANSLATION, translation);
        // 避免重复添加（word字段是UNIQUE）
        long id = db.insertWithOnConflict(TABLE_WORD_DICT, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        return id;
    }

    // 2. 查询所有单词（按字母A-Z正序排序，用于词典列表）
    public List<WordBean> queryAllWords() {
        List<WordBean> wordList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            // 按单词字母正序排序
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
            db.close();
        }
        return wordList;
    }

    // 3. 模糊查询单词（支持输入关键词匹配，不区分大小写）
    public List<WordBean> queryWordsByKeyword(String keyword) {
        List<WordBean> wordList = new ArrayList<>();
        if (keyword.isEmpty()) return wordList;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            String lowerKeyword = keyword.toLowerCase();
            // 模糊查询：匹配单词中包含关键词的记录（%表示任意字符）
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
            db.close();
        }
        return wordList;
    }

    // 辅助实体类：存储单词和翻译（内部类，无需修改）
    public static class WordBean {
        private String word;
        private String translation;

        public WordBean(String word, String translation) {
            this.word = word;
            this.translation = translation;
        }

        // getter（供前端获取数据）
        public String getWord() { return word; }
        public String getTranslation() { return translation; }
    }
}
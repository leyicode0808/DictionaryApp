package com.example.dictionaryapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 已删除：语音相关导入（RecognitionListener、RecognizerIntent、SpeechRecognizer、Manifest.permission）

public class UserActivity extends AppCompatActivity implements View.OnClickListener {
    // 历史记录/收藏页面请求码（保留）
    private static final int REQUEST_HISTORY = 1002;
    private static final int REQUEST_COLLECTION = 1003;

    // 控件声明（已删除：语音按钮相关控件）
    private EditText etInputWord;
    private TextView tvTranslationResult;
    private ImageView ivCollectStatus;
    private RecyclerView rvHistory;

    // 核心工具/数据（已删除：SpeechRecognizer 成员变量、语音权限请求码）
    private TextToSpeech textToSpeech;
    private WordDatabaseHelper dbHelper;
    private HistoryAdapter historyAdapter;
    private boolean isCollected = false;
    private String currentWord = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.useractivity);

        dbHelper = new WordDatabaseHelper(this);
        initViews();
        initTTS(); // 保留文本朗读（TTS）功能（若需删除可一并移除）
        initRecyclerView();

        // 已删除：initSpeechRecognizer() 语音初始化调用
    }

    // 初始化控件+绑定点击事件（无语音按钮相关绑定）
    private void initViews() {
        etInputWord = findViewById(R.id.et_input_word);
        tvTranslationResult = findViewById(R.id.tv_translation_result);
        ivCollectStatus = findViewById(R.id.iv_collect_status);
        rvHistory = findViewById(R.id.rv_history);

        // 绑定核心功能按钮（已删除：语音输入按钮的点击绑定）
        findViewById(R.id.btn_clear).setOnClickListener(this);
        findViewById(R.id.btn_copy_result).setOnClickListener(this);
        findViewById(R.id.btn_voice_read).setOnClickListener(this);
        findViewById(R.id.btn_translate).setOnClickListener(this);
        findViewById(R.id.btn_collect_word).setOnClickListener(this);
        findViewById(R.id.btn_show_history).setOnClickListener(this);
        findViewById(R.id.btn_view_collection).setOnClickListener(this);
        findViewById(R.id.btn_view_dict).setOnClickListener(this);
        findViewById(R.id.btn_back_to_main).setOnClickListener(this);

        ivCollectStatus.setOnClickListener(v -> toggleCollect());
    }

    // 初始化文本朗读（TTS）- 保留（若需删除，直接移除该方法+相关调用+onDestroy释放）
    private void initTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.CHINA);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "不支持中文朗读", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // 已删除：完整的 initSpeechRecognizer() 语音初始化方法

    // 初始化RecyclerView（历史记录）- 保留
    private void initRecyclerView() {
        try {
            List<String> emptyList = new ArrayList<>();
            historyAdapter = new HistoryAdapter(
                    emptyList,
                    position -> {},
                    position -> {}
            );
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            rvHistory.setAdapter(historyAdapter);
            Log.d("RV_FIX", "RecyclerView简化初始化成功");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("RV_FIX", "RecyclerView简化初始化失败：" + e.getMessage());
            Toast.makeText(this, "历史列表初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 翻译核心逻辑（自动保存历史）- 保留
    private void translateWord() {
        String tempCurrentWord = etInputWord.getText().toString().trim();
        final String finalCurrentWord = tempCurrentWord;
        if (finalCurrentWord.isEmpty()) {
            Toast.makeText(this, "请输入单词", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String tempTranslation = null;
            try {
                Log.d("TRANSLATE", "开始查询单词：" + finalCurrentWord);
                tempTranslation = dbHelper.queryTranslation(finalCurrentWord);
                Log.d("TRANSLATE", "查询结果：" + (tempTranslation == null ? "未找到" : tempTranslation));
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("TRANSLATE", "数据库查询异常：" + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(UserActivity.this, "查询失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final String finalTranslation = tempTranslation;
            mainHandler.post(() -> {
                try {
                    currentWord = finalCurrentWord;
                    if (finalTranslation != null) {
                        tvTranslationResult.setText(finalTranslation);
                        checkCollectionStatus(finalCurrentWord);
                        autoSaveHistory(); // 自动保存历史
                    } else {
                        tvTranslationResult.setText("未找到该单词的翻译");
                        isCollected = false;
                        updateCollectIcon();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("TRANSLATE", "UI更新异常：" + e.getMessage());
                    Toast.makeText(this, "显示结果失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // 自动保存历史 - 保留
    private void autoSaveHistory() {
        String result = tvTranslationResult.getText().toString();
        if (currentWord.isEmpty()
                || result.equals("翻译结果将显示在这里")
                || result.equals("未找到该单词的翻译")) {
            return;
        }

        new Thread(() -> {
            long id = dbHelper.addHistory(currentWord, result);
            mainHandler.post(() -> {
                // 自动保存不弹窗，避免打扰
            });
        }).start();
    }

    // 从数据库查询翻译（供历史记录点击使用）- 保留
    private void queryTranslationFromDb(String word) {
        final String finalWord = word;
        new Thread(() -> {
            String tempTranslation = null;
            try {
                tempTranslation = dbHelper.queryTranslation(finalWord);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("QUERY", "查询异常：" + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(UserActivity.this, "查询失败", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final String finalTranslation = tempTranslation;
            mainHandler.post(() -> {
                if (finalTranslation != null) {
                    tvTranslationResult.setText(finalTranslation);
                    checkCollectionStatus(finalWord);
                } else {
                    tvTranslationResult.setText("未找到该单词的翻译");
                    isCollected = false;
                    updateCollectIcon();
                }
            });
        }).start();
    }

    // 检查收藏状态 - 保留
    private void checkCollectionStatus(String word) {
        final String finalWord = word;
        if (finalWord.isEmpty()) return;
        new Thread(() -> {
            boolean collected = false;
            try {
                collected = dbHelper.isCollected(finalWord);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("COLLECT_CHECK", "查询收藏状态异常：" + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(this, "查询收藏状态失败", Toast.LENGTH_SHORT).show();
                });
            }

            final boolean finalCollected = collected;
            mainHandler.post(() -> {
                isCollected = finalCollected;
                updateCollectIcon();
            });
        }).start();
    }

    // 切换收藏状态 - 保留
    private void toggleCollect() {
        if (currentWord.isEmpty()) {
            Toast.makeText(this, "请先输入并翻译单词", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentTranslation = tvTranslationResult.getText().toString().trim();
        if (currentTranslation.equals("翻译结果将显示在这里") || currentTranslation.equals("未找到该单词的翻译")) {
            Toast.makeText(this, "无有效翻译，无法收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            if (isCollected) {
                int rows = dbHelper.removeCollection(currentWord);
                mainHandler.post(() -> {
                    if (rows > 0) {
                        isCollected = false;
                        updateCollectIcon();
                        Toast.makeText(this, "取消收藏", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                long id = dbHelper.addCollection(currentWord, currentTranslation);
                mainHandler.post(() -> {
                    if (id != -1) {
                        isCollected = true;
                        updateCollectIcon();
                        Toast.makeText(this, "收藏成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "该单词已收藏", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // 刷新历史列表 - 保留
    private void refreshHistoryList() {
        new Thread(() -> {
            List<String> newHistoryList = dbHelper.queryAllHistory();
            mainHandler.post(() -> {
                historyAdapter = new HistoryAdapter(
                        newHistoryList,
                        position -> {
                            String history = newHistoryList.get(position);
                            String[] parts = history.split(" → ");
                            if (parts.length >= 2) {
                                etInputWord.setText(parts[0]);
                                currentWord = parts[0];
                                queryTranslationFromDb(currentWord);
                            }
                        },
                        position -> {
                            String history = newHistoryList.get(position);
                            String[] parts = history.split(" → ");
                            if (parts.length >= 2) {
                                new Thread(() -> {
                                    int rows = dbHelper.deleteHistory(parts[0], parts[1]);
                                    mainHandler.post(() -> {
                                        if (rows > 0) {
                                            newHistoryList.remove(position);
                                            historyAdapter.notifyItemRemoved(position);
                                            Toast.makeText(UserActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }).start();
                            }
                        }
                );
                rvHistory.setAdapter(historyAdapter);
                rvHistory.scrollToPosition(0);
            });
        }).start();
    }

    // 查看收藏单词 - 保留（跳转页面，不再弹窗）
    private void viewCollection() {
        Intent intent = new Intent(UserActivity.this, CollectionActivity.class);
        startActivityForResult(intent, REQUEST_COLLECTION);
    }

    // 查看完整词典 - 保留
    private void viewDictionary() {
        Intent intent = new Intent(UserActivity.this, DictionaryActivity.class);
        startActivity(intent);
    }

    // 辅助功能（均保留，无语音相关）
    private void clearInputAndResult() {
        etInputWord.setText("");
        tvTranslationResult.setText("翻译结果将显示在这里");
        currentWord = "";
        isCollected = false;
        updateCollectIcon();
    }

    private void copyResult() {
        String result = tvTranslationResult.getText().toString();
        if (result.equals("翻译结果将显示在这里")) {
            Toast.makeText(this, "暂无结果可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("translation", result);
        cm.setPrimaryClip(clipData);
        Toast.makeText(this, "复制成功", Toast.LENGTH_SHORT).show();
    }

    private void readResult() {
        String result = tvTranslationResult.getText().toString();
        if (result.equals("翻译结果将显示在这里")) {
            Toast.makeText(this, "暂无结果可朗读", Toast.LENGTH_SHORT).show();
            return;
        }
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void toggleHistoryList() {
        if (rvHistory.getVisibility() == View.GONE) {
            rvHistory.setVisibility(View.VISIBLE);
            refreshHistoryList();
        } else {
            rvHistory.setVisibility(View.GONE);
        }
    }

    private void updateCollectIcon() {
        if (isCollected) {
            ivCollectStatus.setImageResource(R.drawable.ic_star_filled);
        } else {
            ivCollectStatus.setImageResource(R.drawable.ic_star_outline);
        }
    }

    // 已删除：语音权限请求回调 onRequestPermissionsResult（仅保留父类调用，无语音相关处理）
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 无任何语音权限相关处理逻辑
    }

    // 生命周期：释放资源（已删除：SpeechRecognizer 释放代码）
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    // 历史记录RecyclerView适配器 - 保留
    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        private List<String> historyList;
        private OnItemClickListener onClickListener;
        private OnItemLongClickListener onLongClickListener;

        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public interface OnItemLongClickListener {
            void onItemLongClick(int position);
        }

        public HistoryAdapter(List<String> historyList, OnItemClickListener onClickListener, OnItemLongClickListener onLongClickListener) {
            this.historyList = historyList;
            this.onClickListener = onClickListener;
            this.onLongClickListener = onLongClickListener;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = View.inflate(parent.getContext(), android.R.layout.simple_list_item_1, null);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            holder.tvHistory.setText(historyList.get(position));
            holder.itemView.setOnClickListener(v -> onClickListener.onItemClick(position));
            holder.itemView.setOnLongClickListener(v -> {
                onLongClickListener.onItemLongClick(position);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            TextView tvHistory;
            public HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHistory = itemView.findViewById(android.R.id.text1);
            }
        }
    }

    // 接收HistoryActivity/CollectionActivity返回结果 - 保留
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_HISTORY && resultCode == RESULT_OK) {
            if (data != null) {
                String selectedWord = data.getStringExtra("selected_word");
                if (selectedWord != null && !selectedWord.isEmpty()) {
                    etInputWord.setText(selectedWord);
                    translateWord();
                }
            }
        } else if (requestCode == REQUEST_COLLECTION && resultCode == RESULT_OK) {
            // 接收收藏页面返回的单词+翻译（优化体验）
            if (data != null) {
                String selectedWord = data.getStringExtra("selected_word");
                String selectedTranslation = data.getStringExtra("selected_translation");
                if (selectedWord != null && !selectedWord.isEmpty()) {
                    etInputWord.setText(selectedWord);
                    currentWord = selectedWord;
                    tvTranslationResult.setText(selectedTranslation != null ? selectedTranslation : "未找到翻译");
                    checkCollectionStatus(selectedWord);
                }
            }
        }
    }

    // 按钮点击事件统一处理（无语音输入按钮分支）
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_clear) {
            clearInputAndResult();
        } else if (id == R.id.btn_copy_result) {
            copyResult();
        } else if (id == R.id.btn_voice_read) {
            readResult(); // 文本朗读（TTS），若需删除可移除该分支
        } else if (id == R.id.btn_translate) {
            translateWord();
        } else if (id == R.id.btn_collect_word) {
            toggleCollect();
        } else if (id == R.id.btn_show_history) {
            Intent intent = new Intent(UserActivity.this, HistoryActivity.class);
            startActivityForResult(intent, REQUEST_HISTORY);
        } else if (id == R.id.btn_view_collection) {
            viewCollection();
        } else if (id == R.id.btn_view_dict) {
            viewDictionary();
        } else if (id == R.id.btn_back_to_main) {
            finish();
        }
    }
}
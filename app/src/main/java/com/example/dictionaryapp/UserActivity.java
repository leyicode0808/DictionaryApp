package com.example.dictionaryapp;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.dictionaryapp.WordDatabaseHelper;

public class UserActivity extends AppCompatActivity implements View.OnClickListener {
    // 历史记录页面请求码（用于接收返回结果）
    private static final int REQUEST_HISTORY = 1002;
    // 收藏页面请求码
    private static final int REQUEST_COLLECTION = 1003;
    // 控件声明
    private EditText etInputWord;
    private TextView tvTranslationResult;
    private ImageView ivCollectStatus;
    private RecyclerView rvHistory;

    // 核心工具/数据
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private WordDatabaseHelper dbHelper;
    private HistoryAdapter historyAdapter;
    private boolean isCollected = false;
    private String currentWord = "";

    // 权限请求码
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.useractivity);

        dbHelper = new WordDatabaseHelper(this);
        initViews();
        initTTS();
        initSpeechRecognizer();
        initRecyclerView();
    }

    // 初始化控件+绑定点击事件（核心修改1：删除btn_save_history绑定）
    private void initViews() {
        etInputWord = findViewById(R.id.et_input_word);
        tvTranslationResult = findViewById(R.id.tv_translation_result);
        ivCollectStatus = findViewById(R.id.iv_collect_status);
        rvHistory = findViewById(R.id.rv_history);


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

    // 初始化文本朗读（TTS）- 保持不变
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

    // 初始化语音输入 - 保持不变
    // 初始化语音输入 - 修复设备兼容、空指针问题
    // 初始化语音输入 - 修复设备兼容、空指针问题
    private void initSpeechRecognizer() {
        // 1. 先检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // 未授权，请求权限（后续通过onRequestPermissionsResult回调处理）
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
            return;
        }

        // 2. 关键：判断设备是否支持语音识别（核心解决“服务繁忙”的原因之一）
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "当前设备不支持语音识别", Toast.LENGTH_SHORT).show();
            speechRecognizer = null; // 标记为null，避免后续调用崩溃
            return;
        }

        // 3. 安全初始化SpeechRecognizer（主线程执行，避免异常）
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onResults(Bundle results) {
                    // 识别成功：获取结果并填充到输入框
                    List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        etInputWord.setText(matches.get(0));
                        // 可选：自动触发翻译（提升体验）
                        // translateWord();
                    } else {
                        Toast.makeText(UserActivity.this, "未识别到有效内容", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(int error) {
                    // 替换为传统switch语句（Java 11支持）
                    String errorMsg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:
                            errorMsg = "音频录制失败，请检查麦克风是否被占用";
                            break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            errorMsg = "未识别到语音，请靠近麦克风清晰说话";
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            errorMsg = "识别服务繁忙，请稍后再试（可重启APP）";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                            errorMsg = "网络错误，请检查网络连接（在线识别需要联网）";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            errorMsg = "网络超时，请切换网络重试";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            errorMsg = "缺少麦克风权限，请在设置中开启";
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            errorMsg = "未检测到语音输入，请重新说话";
                            break;
                        case SpeechRecognizer.ERROR_SERVER:
                            errorMsg = "识别服务器错误，请稍后再试";
                            break;
                        case SpeechRecognizer.ERROR_CLIENT:
                            errorMsg = "客户端错误，请重启APP";
                            break;
                        default:
                            errorMsg = "语音识别失败（错误码：" + error + "）";
                            break;
                    }
                    Toast.makeText(UserActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }

                // 以下方法保持不变（无需修改）
                @Override public void onReadyForSpeech(Bundle params) {
                    Toast.makeText(UserActivity.this, "准备就绪，请说话...", Toast.LENGTH_SHORT).show();
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    Toast.makeText(UserActivity.this, "正在识别...", Toast.LENGTH_SHORT).show();
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "语音识别初始化失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            speechRecognizer = null;
        }
    }

    // 初始化RecyclerView - 保持不变
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

    // ---------------------- 核心修改2：翻译成功自动保存历史 ----------------------
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
                        // 翻译成功 → 自动保存历史（核心新增）
                        autoSaveHistory();
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

    // ---------------------- 核心修改3：自动保存历史（复用原有校验逻辑） ----------------------
    // 复用原saveHistory的校验和数据库操作，去掉手动保存的提示
    private void autoSaveHistory() {
        String result = tvTranslationResult.getText().toString();
        // 原有校验逻辑：无单词/无有效结果不保存
        if (currentWord.isEmpty()
                || result.equals("翻译结果将显示在这里")
                || result.equals("未找到该单词的翻译")) {
            return; // 自动保存不提示，直接返回
        }

        // 子线程插入数据库（和原逻辑一致）
        new Thread(() -> {
            long id = dbHelper.addHistory(currentWord, result);
            mainHandler.post(() -> {
                if (id != -1) {
                    // 自动保存成功后不弹窗提示（避免打扰）
                    // 如需提示，取消下面注释：
                    // Toast.makeText(this, "历史记录已自动保存", Toast.LENGTH_SHORT).show();
                }
                // 无需主动刷新列表，用户点击「历史记录」时会自动刷新
            });
        }).start();
    }

    // 从数据库查询翻译（供历史记录点击使用）- 保持不变
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

    // 检查单词是否已收藏 - 保持不变
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

    // 切换收藏状态 - 保持不变
    private void toggleCollect() {
        if (currentWord.isEmpty()) {
            Toast.makeText(this, "请先输入并翻译单词", Toast.LENGTH_SHORT).show();
            return;
        }
        // 获取当前翻译结果
        String currentTranslation = tvTranslationResult.getText().toString().trim();
        if (currentTranslation.equals("翻译结果将显示在这里") || currentTranslation.equals("未找到该单词的翻译")) {
            Toast.makeText(this, "无有效翻译，无法收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            if (isCollected) {
                // 取消收藏（按单词删除，无需翻译）
                int rows = dbHelper.removeCollection(currentWord);
                mainHandler.post(() -> {
                    if (rows > 0) {
                        isCollected = false;
                        updateCollectIcon();
                        Toast.makeText(this, "取消收藏", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // 新增收藏（传入单词+翻译，调用修改后的addCollection）
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

    // 刷新历史列表 - 保持不变
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

    // 查看所有收藏单词 - 保持不变
    private void viewCollection() {
        new Thread(() -> {
            // 调用修改后的方法，获取含翻译的收藏列表
            List<WordDatabaseHelper.WordBean> collectList = dbHelper.queryAllCollections();
            mainHandler.post(() -> {
                if (collectList.isEmpty()) {
                    Toast.makeText(this, "暂无收藏单词", Toast.LENGTH_SHORT).show();
                    return;
                }
                StringBuilder sb = new StringBuilder("收藏单词：\n");
                for (WordDatabaseHelper.WordBean bean : collectList) {
                    sb.append(bean.getWord())
                            .append(" → ")
                            .append(bean.getTranslation())
                            .append("\n");
                }
                Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // 查看完整词典 - 保持不变
    private void viewDictionary() {
        Toast.makeText(this, "后续可跳转完整词典页面", Toast.LENGTH_SHORT).show();
    }

    // ---------------------- 辅助功能（均保持不变） ----------------------
    // 启动语音输入 - 修复空指针、优化异常处理
    private void startVoiceInput() {
        // 1. 检查SpeechRecognizer是否初始化成功（避免空指针）
        if (speechRecognizer == null) {
            Toast.makeText(this, "语音输入未初始化（请先授予麦克风权限）", Toast.LENGTH_SHORT).show();
            // 自动触发权限请求（提升体验）
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
            return;
        }

        // 2. 配置语音识别参数（优化识别准确率）
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // 语言模型：自由格式（适合单词识别）
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // 语言：中文（适配中文语音，若需识别英文可改为Locale.US）
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString());
        // 提示文字（引导用户）
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要翻译的单词（英文/中文均可）");
        // 最多返回1个结果（减少冗余）
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        // 禁止部分结果（提升识别稳定性）
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        // 3. 安全启动识别（捕获所有异常）
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "语音输入启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

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

    // ---------------------- 权限请求回调 - 保持不变 ----------------------
    // 权限请求回调 - 修复授权后未重新初始化的问题
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予成功：重新初始化语音识别（关键！之前可能漏了这步）
                initSpeechRecognizer();
                Toast.makeText(this, "麦克风权限已授予，可使用语音输入", Toast.LENGTH_SHORT).show();
            } else {
                // 权限拒绝：明确提示用户（避免用户不知道原因）
                Toast.makeText(this, "拒绝麦克风权限将无法使用语音输入，可在「设置-应用-权限」中开启", Toast.LENGTH_LONG).show();
                speechRecognizer = null; // 标记为null，避免后续调用崩溃
            }
        }
    }

    // ---------------------- 生命周期：释放资源 - 保持不变 ----------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    // ---------------------- 历史记录RecyclerView适配器 - 保持不变 ----------------------
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
    // 接收HistoryActivity返回的结果（选中的单词）
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_HISTORY && resultCode == RESULT_OK) {
            if (data != null) {
                // 获取选中的单词
                String selectedWord = data.getStringExtra("selected_word");
                if (selectedWord != null && !selectedWord.isEmpty()) {
                    // 自动填充到输入框，并触发翻译
                    etInputWord.setText(selectedWord);
                    translateWord(); // 调用现有翻译方法，自动查询
                }
            }
        }
    }

    // ---------------------- 按钮点击事件统一处理（核心修改4：删除btn_save_history分支） ----------------------
    @Override
    public void onClick(View v) {
        int id = v.getId();
         if (id == R.id.btn_clear) {
            clearInputAndResult();
        } else if (id == R.id.btn_copy_result) {
            copyResult();
        } else if (id == R.id.btn_voice_read) {
            readResult();
        } else if (id == R.id.btn_translate) {
            translateWord();
        } else if (id == R.id.btn_collect_word) {
            toggleCollect();
        } else if (id == R.id.btn_show_history) {
            // 启动历史记录页面，并用startActivityForResult接收返回结果
            Intent intent = new Intent(UserActivity.this, HistoryActivity.class);
            startActivityForResult(intent, REQUEST_HISTORY);
        } else if (id == R.id.btn_view_collection) {
            // 启动收藏夹页面，接收返回结果
            Intent intent = new Intent(UserActivity.this, CollectionActivity.class);
            startActivityForResult(intent, REQUEST_COLLECTION);
        }else if (id == R.id.btn_view_dict) {
            // 启动完整词典页面
            Intent intent = new Intent(UserActivity.this, DictionaryActivity.class);
            startActivity(intent);
        } else if (id == R.id.btn_back_to_main) {
            finish();
        }
    }
}
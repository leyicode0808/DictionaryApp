package com.example.dictionaryapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView; // 新增导入ScrollView
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DictionaryActivity extends AppCompatActivity {

    private static final String TAG = "DictionaryActivity";
    private static final int PAGE_SIZE = 20; // 每页显示20条单词（可调整）

    private RecyclerView rvWordList;
    private WordAdapter wordAdapter;
    private WordDatabaseHelper dbHelper;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 数据相关
    private List<WordDatabaseHelper.WordBean> allWordList = new ArrayList<>(); // 所有单词（原始数据）
    private List<WordDatabaseHelper.WordBean> currentWordList = new ArrayList<>(); // 当前显示的单词（分页/查询后）
    private Map<String, Integer> alphabetPositionMap = new HashMap<>(); // 字母->起始位置映射（用于快速跳转）

    // 分页相关
    private int currentPage = 1; // 当前页码（从1开始）
    private int totalPage = 0; // 总页数

    // 控件：llAlphabet类型从LinearLayout改为ScrollView（关键修改1）
    private EditText etSearch;
    private TextView tvPageInfo;
    private ScrollView llAlphabet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);
        // 关键代码：开启状态栏透明 + 布局延伸到状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // 初始化数据库和控件
        dbHelper = new WordDatabaseHelper(this);
        initViews();
        // 加载所有单词（子线程，避免卡顿）
        loadAllWords();
    }

    private void initViews() {
        // 返回按钮
        TextView tvBack = findViewById(R.id.tv_back);
        tvBack.setOnClickListener(v -> finish());

        // 搜索相关
        etSearch = findViewById(R.id.et_search);
        TextView btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> doSearch());

        // 添加单词按钮
        TextView btnAddWord = findViewById(R.id.btn_add_word);
        btnAddWord.setOnClickListener(v -> showAddWordDialog());

        // 单词列表
        rvWordList = findViewById(R.id.rv_word_list);
        rvWordList.setLayoutManager(new LinearLayoutManager(this));
        wordAdapter = new WordAdapter(currentWordList);
        rvWordList.setAdapter(wordAdapter);

        // 字母快速跳转栏：从ScrollView中获取内部LinearLayout，再遍历字母TextView（关键修改2）
        llAlphabet = findViewById(R.id.ll_alphabet);
        // 获取ScrollView内部的LinearLayout（布局中ScrollView的子View是LinearLayout）
        if (llAlphabet.getChildAt(0) instanceof LinearLayout) {
            LinearLayout alphabetContainer = (LinearLayout) llAlphabet.getChildAt(0);
            // 遍历字母TextView，绑定点击事件
            for (int i = 0; i < alphabetContainer.getChildCount(); i++) {
                View child = alphabetContainer.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tvAlphabet = (TextView) child;
                    tvAlphabet.setOnClickListener(v -> {
                        String letter = ((TextView) v).getText().toString().toLowerCase();
                        jumpToAlphabet(letter);
                    });
                }
            }
        }

        // 翻页控制
        TextView btnPrevPage = findViewById(R.id.btn_prev_page);
        TextView btnNextPage = findViewById(R.id.btn_next_page);
        tvPageInfo = findViewById(R.id.tv_page_info);

        btnPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePageData();
            } else {
                Toast.makeText(this, "已经是第一页了", Toast.LENGTH_SHORT).show();
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (currentPage < totalPage) {
                currentPage++;
                updatePageData();
            } else {
                Toast.makeText(this, "已经是最后一页了", Toast.LENGTH_SHORT).show();
            }
        });

        // 核心新增：点击页码文本，跳转到指定页面
        tvPageInfo.setOnClickListener(v -> {
            if (totalPage == 0) {
                Toast.makeText(this, "暂无数据，无法跳转", Toast.LENGTH_SHORT).show();
                return;
            }
            showJumpPageDialog();
        });

        // 搜索框文本变化监听（可选：输入完成后自动查询）
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (TextUtils.isEmpty(s.toString())) {
                    currentWordList.clear();
                    currentWordList.addAll(allWordList);
                    calculateTotalPage();
                    currentPage = 1;
                    updatePageData();
                }
            }
        });
    }

    // 新增：显示跳转页码弹窗
    private void showJumpPageDialog() {
        EditText etJumpPage = new EditText(this);
        etJumpPage.setHint("请输入页码（1-" + totalPage + "）");
        etJumpPage.setPadding(32, 16, 32, 16);

        new AlertDialog.Builder(this)
                .setTitle("跳转到指定页面")
                .setView(etJumpPage)
                .setPositiveButton("确认跳转", (dialog, which) -> {
                    String pageStr = etJumpPage.getText().toString().trim();
                    if (TextUtils.isEmpty(pageStr)) {
                        Toast.makeText(DictionaryActivity.this, "页码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int targetPage;
                    try {
                        targetPage = Integer.parseInt(pageStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(DictionaryActivity.this, "请输入合法数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (targetPage < 1 || targetPage > totalPage) {
                        Toast.makeText(DictionaryActivity.this, "页码必须在1-" + totalPage + "之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentPage = targetPage;
                    updatePageData();
                    rvWordList.scrollToPosition(0);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 1. 加载所有单词（子线程）
    private void loadAllWords() {
        new Thread(() -> {
            try {
                allWordList = dbHelper.queryAllWords();
                Log.d(TAG, "加载所有单词：" + allWordList.size() + "条");
                buildAlphabetPositionMap();
                mainHandler.post(() -> {
                    currentWordList.clear();
                    currentWordList.addAll(allWordList);
                    calculateTotalPage();
                    currentPage = 1;
                    updatePageData();
                });
            } catch (Exception e) {
                Log.e(TAG, "加载单词失败：" + e.getMessage());
                mainHandler.post(() -> Toast.makeText(DictionaryActivity.this, "加载词典失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 2. 构建字母->起始位置映射（A-Z对应单词列表的起始索引）
    private void buildAlphabetPositionMap() {
        alphabetPositionMap.clear();
        for (int i = 0; i < allWordList.size(); i++) {
            WordDatabaseHelper.WordBean wordBean = allWordList.get(i);
            String firstLetter = wordBean.getWord().substring(0, 1).toLowerCase();
            if (!alphabetPositionMap.containsKey(firstLetter)) {
                alphabetPositionMap.put(firstLetter, i);
                Log.d(TAG, "字母" + firstLetter.toUpperCase() + "起始位置：" + i);
            }
        }
    }

    // 3. 模糊查询单词
    private void doSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(this, "请输入查询关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                List<WordDatabaseHelper.WordBean> searchResult = dbHelper.queryWordsByKeyword(keyword);
                Log.d(TAG, "查询结果：" + searchResult.size() + "条");
                mainHandler.post(() -> {
                    currentWordList.clear();
                    currentWordList.addAll(searchResult);
                    calculateTotalPage();
                    currentPage = 1;
                    updatePageData();

                    if (searchResult.isEmpty()) {
                        Toast.makeText(DictionaryActivity.this, "未找到匹配单词", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "查询失败：" + e.getMessage());
                mainHandler.post(() -> Toast.makeText(DictionaryActivity.this, "查询失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 4. 字母快速跳转
    private void jumpToAlphabet(String letter) {
        if (alphabetPositionMap.containsKey(letter)) {
            int position = alphabetPositionMap.get(letter);
            currentPage = (position / PAGE_SIZE) + 1;
            updatePageData();
            rvWordList.scrollToPosition(position % PAGE_SIZE);
            Toast.makeText(this, "跳转到字母 " + letter.toUpperCase(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "无" + letter.toUpperCase() + "开头的单词", Toast.LENGTH_SHORT).show();
        }
    }

    // 5. 计算总页数
    private void calculateTotalPage() {
        if (currentWordList.isEmpty()) {
            totalPage = 0;
        } else {
            totalPage = (currentWordList.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        }
    }

    // 6. 更新当前页数据
    private void updatePageData() {
        if (currentWordList.isEmpty()) {
            wordAdapter.setData(new ArrayList<>());
            tvPageInfo.setText("第 0 页 / 共 0 页");
            return;
        }

        int startIndex = (currentPage - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, currentWordList.size());
        List<WordDatabaseHelper.WordBean> pageList = currentWordList.subList(startIndex, endIndex);

        wordAdapter.setData(pageList);
        tvPageInfo.setText("第 " + currentPage + " 页 / 共 " + totalPage + " 页");
    }

    // 7. 显示添加陌生单词弹窗
    private void showAddWordDialog() {
        EditText etNewWord = new EditText(this);
        EditText etNewTranslation = new EditText(this);

        etNewWord.setHint("请输入新单词（英文）");
        etNewTranslation.setHint("请输入翻译（中文）");

        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        editTextParams.bottomMargin = 16;
        etNewWord.setLayoutParams(editTextParams);
        etNewTranslation.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(32, 16, 32, 16);
        dialogLayout.addView(etNewWord);
        dialogLayout.addView(etNewTranslation);

        new AlertDialog.Builder(this)
                .setTitle("添加陌生单词")
                .setView(dialogLayout)
                .setPositiveButton("确认添加", (dialog, which) -> {
                    String newWord = etNewWord.getText().toString().trim();
                    String newTranslation = etNewTranslation.getText().toString().trim();
                    if (TextUtils.isEmpty(newWord) || TextUtils.isEmpty(newTranslation)) {
                        Toast.makeText(DictionaryActivity.this, "单词和翻译不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addNewWordToDb(newWord, newTranslation);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 8. 添加陌生单词到数据库
    private void addNewWordToDb(String word, String translation) {
        new Thread(() -> {
            try {
                long result = dbHelper.addWord(word, translation);
                mainHandler.post(() -> {
                    if (result != -1) {
                        Toast.makeText(DictionaryActivity.this, "添加成功！", Toast.LENGTH_SHORT).show();
                        loadAllWords();
                    } else {
                        Toast.makeText(DictionaryActivity.this, "添加失败（单词已存在或格式错误）", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "添加单词失败：" + e.getMessage());
                mainHandler.post(() -> Toast.makeText(DictionaryActivity.this, "添加失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 单词列表适配器（保持不变）
    static class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
        private List<WordDatabaseHelper.WordBean> dataList;

        public WordAdapter(List<WordDatabaseHelper.WordBean> dataList) {
            this.dataList = dataList;
        }

        public void setData(List<WordDatabaseHelper.WordBean> newData) {
            this.dataList = newData;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = View.inflate(parent.getContext(), android.R.layout.simple_list_item_2, null);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            WordDatabaseHelper.WordBean wordBean = dataList.get(position);
            holder.tvWord.setText(wordBean.getWord());
            holder.tvWord.setTextSize(18);
            holder.tvWord.setPadding(8, 8, 8, 4);
            holder.tvTranslation.setText(wordBean.getTranslation());
            holder.tvTranslation.setTextSize(16);
            holder.tvTranslation.setTextColor(0xFF666666);
            holder.tvTranslation.setPadding(8, 4, 8, 8);
        }

        @Override
        public int getItemCount() {
            return dataList == null ? 0 : dataList.size();
        }

        static class WordViewHolder extends RecyclerView.ViewHolder {
            TextView tvWord;
            TextView tvTranslation;

            public WordViewHolder(@NonNull View itemView) {
                super(itemView);
                tvWord = itemView.findViewById(android.R.id.text1);
                tvTranslation = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
    }
}
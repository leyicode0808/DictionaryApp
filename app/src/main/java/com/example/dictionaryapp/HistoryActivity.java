package com.example.dictionaryapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

// 导入数据库帮助类
import com.example.dictionaryapp.WordDatabaseHelper;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistoryList;
    private HistoryAdapter historyAdapter;
    private WordDatabaseHelper dbHelper;
    private List<String> historyList = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 绑定布局（下一步创建）
        setContentView(R.layout.activity_history);

        // 初始化数据库帮助类
        dbHelper = new WordDatabaseHelper(this);
        // 初始化控件（返回按钮、RecyclerView）
        initViews();
        // 加载历史记录数据
        loadHistoryData();
    }

    // 初始化控件
    private void initViews() {
        // 返回按钮（布局中定义）
        TextView tvBack = findViewById(R.id.tv_back);
        rvHistoryList = findViewById(R.id.rv_history_list);

        // 返回按钮点击事件：关闭当前页面，回到UserActivity
        tvBack.setOnClickListener(v -> finish());

        // 初始化RecyclerView（列表展示历史记录）
        rvHistoryList.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(historyList,
                position -> {
                    // 列表项点击事件：返回UserActivity并携带选中的单词
                    String historyItem = historyList.get(position);
                    String word = historyItem.split(" → ")[0]; // 提取单词（格式：单词→翻译）
                    Intent intent = new Intent();
                    intent.putExtra("selected_word", word); // 传递单词
                    setResult(RESULT_OK, intent); // 设置返回结果
                    finish(); // 关闭当前页面
                },
                position -> {
                    // 列表项长按事件：删除该条历史记录
                    String historyItem = historyList.get(position);
                    String[] parts = historyItem.split(" → ");
                    if (parts.length >= 2) {
                        String word = parts[0];
                        String translation = parts[1];
                        deleteHistory(word, translation, position);
                    }
                });
        rvHistoryList.setAdapter(historyAdapter);
    }

    // 加载历史记录（从数据库查询）
    // 加载历史记录（从数据库查询）- 新增日志打印
    private void loadHistoryData() {
        new Thread(() -> {
            try {
                // 调用数据库方法查询所有历史记录
                List<String> tempList = dbHelper.queryAllHistory();
                // 新增日志：打印查询到的历史记录数量和内容
                Log.d("HistoryData", "查询到历史记录数：" + tempList.size());
                for (String item : tempList) {
                    Log.d("HistoryData", "历史记录：" + item);
                }

                // 修正：用 clear()+addAll() 更新列表（避免重新赋值导致适配器引用失效）
                mainHandler.post(() -> {
                    historyList.clear(); // 清空原有空列表
                    historyList.addAll(tempList); // 添加查询到的数据
                    historyAdapter.notifyDataSetChanged(); // 刷新列表
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(HistoryActivity.this, "加载历史记录失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 删除单条历史记录
    private void deleteHistory(String word, String translation, int position) {
        new Thread(() -> {
            try {
                int rows = dbHelper.deleteHistory(word, translation);
                mainHandler.post(() -> {
                    if (rows > 0) {
                        historyList.remove(position); // 移除列表项
                        historyAdapter.notifyItemRemoved(position); // 刷新列表
                        Toast.makeText(HistoryActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(HistoryActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(HistoryActivity.this, "删除异常", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 内部类：历史记录列表适配器
    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        private List<String> historyList;
        private OnItemClickListener onClickListener;
        private OnItemLongClickListener onLongClickListener;

        // 点击/长按接口
        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public interface OnItemLongClickListener {
            void onItemLongClick(int position);
        }

        // 构造方法
        public HistoryAdapter(List<String> historyList, OnItemClickListener onClickListener, OnItemLongClickListener onLongClickListener) {
            this.historyList = historyList;
            this.onClickListener = onClickListener;
            this.onLongClickListener = onLongClickListener;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 使用系统默认文本布局（也可自定义）
            View view = View.inflate(parent.getContext(), android.R.layout.simple_list_item_1, null);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            // 绑定历史记录文本（格式：单词 → 翻译）
            holder.tvHistory.setText(historyList.get(position));
            // 绑定点击事件
            holder.itemView.setOnClickListener(v -> onClickListener.onItemClick(position));
            // 绑定长按事件
            holder.itemView.setOnLongClickListener(v -> {
                onLongClickListener.onItemLongClick(position);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        // ViewHolder：缓存列表项控件
        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            TextView tvHistory;
            public HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHistory = itemView.findViewById(android.R.id.text1);
            }
        }
    }

    // 生命周期：关闭数据库
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
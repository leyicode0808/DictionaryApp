package com.example.dictionaryapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CollectionActivity extends AppCompatActivity {

    private RecyclerView rvCollectionList;
    private CollectionAdapter collectionAdapter;
    private WordDatabaseHelper dbHelper;
    // 核心修复1：显式引用 WordDatabaseHelper 的 WordBean，不自定义局部子类
    private List<WordDatabaseHelper.WordBean> collectionList = new ArrayList<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private RadioGroup rgSort;
    private int currentSortType = 0; // 0=时间排序，1=字母排序

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);

        dbHelper = new WordDatabaseHelper(this);
        initViews();
        loadCollectionData(currentSortType);
    }

    private void initViews() {
        // 返回按钮
        TextView tvBack = findViewById(R.id.tv_back);
        tvBack.setOnClickListener(v -> finish());

        // 排序单选组
        rgSort = findViewById(R.id.rg_sort);
        rgSort.setOnCheckedChangeListener((group, checkedId) -> {
            currentSortType = (checkedId == R.id.rb_time) ? 0 : 1;
            loadCollectionData(currentSortType);
        });

        // 收藏列表（修复2：适配器接收 WordDatabaseHelper.WordBean 列表）
        rvCollectionList = findViewById(R.id.rv_collection_list);
        rvCollectionList.setLayoutManager(new LinearLayoutManager(this));
        collectionAdapter = new CollectionAdapter(
                collectionList,
                position -> {
                    // 点击返回单词+翻译（显式获取 WordDatabaseHelper.WordBean）
                    WordDatabaseHelper.WordBean bean = collectionList.get(position);
                    Intent intent = new Intent();
                    intent.putExtra("selected_word", bean.getWord());
                    intent.putExtra("selected_translation", bean.getTranslation());
                    setResult(RESULT_OK, intent);
                    finish();
                },
                position -> {
                    // 长按取消收藏
                    WordDatabaseHelper.WordBean bean = collectionList.get(position);
                    cancelCollection(bean.getWord(), position);
                }
        );
        rvCollectionList.setAdapter(collectionAdapter);
    }

    // 加载收藏数据（修复3：接收类型与数据库返回一致）
    private void loadCollectionData(int sortType) {
        new Thread(() -> {
            try {
                // 数据库返回 List<WordDatabaseHelper.WordBean>，直接接收，无类型转换
                List<WordDatabaseHelper.WordBean> tempList = dbHelper.queryAllCollections(sortType);
                mainHandler.post(() -> {
                    collectionList.clear();
                    collectionList.addAll(tempList);
                    collectionAdapter.notifyDataSetChanged();

                    // 空数据提示
                    TextView tvEmptyHint = findViewById(R.id.tv_empty_hint);
                    if (collectionList.isEmpty()) {
                        tvEmptyHint.setVisibility(View.VISIBLE);
                        rvCollectionList.setVisibility(View.GONE);
                    } else {
                        tvEmptyHint.setVisibility(View.GONE);
                        rvCollectionList.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(this, "加载收藏失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 取消收藏（逻辑不变）
    private void cancelCollection(String word, int position) {
        new Thread(() -> {
            try {
                int rows = dbHelper.removeCollection(word);
                mainHandler.post(() -> {
                    if (rows > 0) {
                        collectionList.remove(position);
                        collectionAdapter.notifyItemRemoved(position);
                        Toast.makeText(this, "取消收藏成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "取消收藏失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> Toast.makeText(this, "取消收藏异常", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 修复4：适配器全程显式使用 WordDatabaseHelper.WordBean
    static class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.CollectionViewHolder> {
        // 数据类型：WordDatabaseHelper.WordBean 列表
        private List<WordDatabaseHelper.WordBean> collectionList;
        private OnItemClickListener onClickListener;
        private OnItemLongClickListener onLongClickListener;

        // 接口定义（无变化）
        public interface OnItemClickListener {
            void onItemClick(int position);
        }

        public interface OnItemLongClickListener {
            void onLongClick(int position);
        }

        // 构造函数：接收 WordDatabaseHelper.WordBean 列表
        public CollectionAdapter(List<WordDatabaseHelper.WordBean> collectionList,
                                 OnItemClickListener onClickListener,
                                 OnItemLongClickListener onLongClickListener) {
            this.collectionList = collectionList;
            this.onClickListener = onClickListener;
            this.onLongClickListener = onLongClickListener;
        }

        @NonNull
        @Override
        public CollectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 双文本布局：适配单词+翻译
            View view = View.inflate(parent.getContext(), android.R.layout.simple_list_item_2, null);
            return new CollectionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CollectionViewHolder holder, int position) {
            // 直接获取 WordDatabaseHelper.WordBean 对象
            WordDatabaseHelper.WordBean bean = collectionList.get(position);
            // 单词：加粗+18sp
            holder.tvWord.setText(bean.getWord());
            holder.tvWord.setTextSize(18);
            holder.tvWord.setTypeface(Typeface.DEFAULT_BOLD);
            // 翻译：灰色+16sp
            holder.tvTranslation.setText(bean.getTranslation());
            holder.tvTranslation.setTextSize(16);
            holder.tvTranslation.setTextColor(0xFF666666);

            // 点击/长按事件（无变化）
            holder.itemView.setOnClickListener(v -> onClickListener.onItemClick(position));
            holder.itemView.setOnLongClickListener(v -> {
                onLongClickListener.onLongClick(position);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return collectionList.size();
        }

        // ViewHolder 绑定双文本控件（无变化）
        static class CollectionViewHolder extends RecyclerView.ViewHolder {
            TextView tvWord;
            TextView tvTranslation;

            public CollectionViewHolder(@NonNull View itemView) {
                super(itemView);
                tvWord = itemView.findViewById(android.R.id.text1);
                tvTranslation = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
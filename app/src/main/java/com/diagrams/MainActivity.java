package com.diagrams;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.diagrams.pullrefreshlayout.PullRefreshLayout;
import com.diagrams.pullrefreshlayout.R;

public class MainActivity extends AppCompatActivity {
    ListView listView;
    PullRefreshLayout pullRefreshLayout;

    Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        listView = (ListView) findViewById(R.id.list);
        pullRefreshLayout = (PullRefreshLayout) findViewById(R.id.rl);
        refreshData();

        pullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                handler.postDelayed(new RefreshTask(), 2000);//延迟两秒，模拟网络操作
            }

            @Override
            public void onRefreshComplete() {
            }
        });
    }

    private void refreshData() {
        String[] data = new String[20];
        for (int i = 0; i < data.length; i++) {
            data[i] = "TEST" + Math.random();
        }
        listView.setAdapter(new ArrayAdapter<>(this, R.layout.list_item, R.id.tv, data));
    }

    public class RefreshTask implements Runnable {

        @Override
        public void run() {
            refreshData();
            pullRefreshLayout.stopRefresh();
        }
    }
}

# PullRefreshLayout
>仿网易新闻5.6 android客户端 下拉刷新控件

####示例：
在布局文件中添加：
```xml
  <com.diagrams.pullrefreshlayout.AbsListPullRefreshLayout
        android:id="@+id/rl"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        
        <!-- 刷新头，下拉ListView后，会露出此View-->
        <com.diagrams.pullrefreshlayout.refreshview.TestRefreshView
            android:id="@+id/rl_header"
            android:layout_width="match_parent"
            android:layout_height="100dp"
            android:background="#ff2277" />
        
        <!-- 要进行下拉刷新的View-->
        <ListView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:divider="#eedd22"
            android:dividerHeight="1dp" />
  </com.diagrams.pullrefreshlayout.AbsListPullRefreshLayout>
```
<B>注意：</B>被`PullRefreshLayout`包裹的必须有两个View，第一个View是刷新头View，第二个View是要下拉刷新的View。

代码中直接使用即可：
```java
 PullRefreshLayout pullRefreshLayout;
 
 pullRefreshLayout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
               //做网络请求等获取数据的操作，
               ...
               pullRefreshLayout.stopRefresh();
            }

            @Override
            public void onRefreshComplete() {
                //此方法是 pullRefreshLayout.stopRefresh() 后，动画执行完毕后回调的，如果想要更流畅，可以在这里更新数据到View
            }
 });
```

# PullRefreshLayout
>仿网易新闻5.6 android客户端 下拉刷新控件

由于重写了Touch事件比较顶层的传递方法（`dispatchTouchEvent(@NonNull MotionEvent ev)`），所以极少出现不兼容的情况。即使出现不兼容情况重写`PullRefreshLayout`的`canChildScrollUp(View)`方法也可以很方便的解决，具体如何实现可以参考`AbsListPullRefreshLayout`这个类。

####简单使用示例：
在布局文件中添加：
```xml
  <com.diagrams.pullrefreshlayout.PullRefreshLayout
        android:id="@+id/rl"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        
        <!-- 刷新头，下拉ListView后，会露出此View-->
        <com.diagrams.pullrefreshlayout.refreshview.TestRefreshView
            android:id="@+id/rl_header"
            android:layout_width="match_parent"
            android:layout_height="100dp"
            android:background="#ff2277" />
        
        <!-- 要进行下拉刷新的View，此处可以是任何View-->
        <ListView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:divider="#eedd22"
            android:dividerHeight="1dp" />
  </com.diagrams.pullrefreshlayout.PullRefreshLayout>
```
<B>注意：</B>
 1. 被`PullRefreshLayout`包裹的必须有两个`View`，第一个`View`是刷新头`View`，第二个`View`是要下拉刷新的`View`。
 2. 如果发现无法刷新ContentView，那么可以自己重写`PullRefreshLayout`的`canChildScrollUp(View)`方法，来自己确定ContentView何时可以下拉刷新。

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

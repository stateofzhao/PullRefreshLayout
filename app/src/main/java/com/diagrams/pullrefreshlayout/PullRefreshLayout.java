package com.diagrams.pullrefreshlayout;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.Scroller;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.view.ViewHelper;

import java.util.HashSet;
import java.util.Set;


/**
 * 能够实现下拉刷新的控件<P>
 * <p/>
 * 注意：
 * 1.它的第一个子View 是下拉时露出提示刷新的View。
 * 2.它的第二个View是 默认的内容View（默认的能够下拉刷新的View）
 * 3.其它的子View是非默认的内容View（OtherContentView）,如果让其也能够随着 默认的内容View 滚动，需要调用 {@link #registerViewForScroll(View)}
 * 来注册，不想随着 默认的内容View滚动的话 调用 {@link #unRegisterViewForScroll(View)} 来取消。
 *
 * @version 0.2 重新实现了下顶部刷新HeaderView的布局，把顶部刷新HeaderView放到了布局的最上层，这样就不限制给ContentView设置背景等问题了
 */
@SuppressLint("ClickableViewAccessibility")
public class PullRefreshLayout extends ViewGroup {

    protected static final int REGISTERED_VIEW_TRANSLATION_Y = "PullRefreshLayout_registered_view_ty".hashCode();

    private final static String TAG = "PullRefreshLayout";

    private final static boolean DEBUG = false;

    /** 最小子View个数 */
    private final static int MIN_CHILD_VIEW_COUNT = 2;
    /** 在Y轴滑动的距离 *本值 > 在X轴上滑动的距离就 触发本View的滚动事件 */
    private final static float YDISTANCE_OFFSET_XDISTANCE = 1.0f;
    /** 手指拖动初始阻尼系数 ,越小 阻尼越强 */
    private final static float INIT_DRAG_OFFSET = 0.35f;
    /** 调用自动下拉刷新时，自动向下滚动的速度 */
    private final static int AUTO_REFRESH_SCROLL_SPEED = 0;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
    private static final int INVALID_POINTER = -1;// 无效的手指id
    /** Scroller 的插值器 */
    private final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };
    protected State mState = State.NORMAL;
    /** 下拉后顶部露出的 提示刷新的View */
    protected View mRefreshHeaderView;
    /** 显示的具体View，能够触发下拉刷新 */
    protected View mContentView;
    /** 滚动实现方式 */
    private TranslationMode mScrollMode;
    /** 执行动画滚动 */
    private Scroller mScroller;
    /** 注册的View，能够随着ContentView偏移顶部时，一块滚动 */
    private Set<View> mRegisteredView = new HashSet<>();
    private Animation.AnimationListener mScrollAnimationListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (DEBUG) {
                Log.e(TAG, "ScrollAnimation end le");
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };
    private Interpolator mDecelerateInterpolator;
    private boolean mUseScroller = false;
    private boolean mAnimationScroll_isRefreshToNormal = false;
    private float mAnimationScroll_yDirection = 0f;
    private int mAnimationScroll_yStart = 0;
    private int mAnimationScroll_xStart = 0;
    private int mAnimationScroll_yDistance = 0;
    private int mAnimationScroll_xDistance = 0;
    private int mActivePointerId = INVALID_POINTER;// 能够执行手势的 手指id
    /** 能够触发下拉刷新的 ContentView向下滚动的距离 */
    private int mRefreshingHeight = 0;
    /** 顶部刷新header的高度 */
    private int mRefreshHeaderHeight = -1;
    /** 顶部刷新header 的布局缩放 */
    private float mRefreshHeaderViewLayoutScale;
    /** 手指拖动距离的阻尼系数 */
    private float mDragOffset = INIT_DRAG_OFFSET;
    /** 用来判断滑动的距离是否能够触发 本View跟随手指滚动 */
    private float mTouchScrollSlop;
    private float mLastMotionX;
    private float mLastMotionY;
    private float mInitialMotionY;// 用来判断首次滑动是否 达到了能够触发滑动的距离
    /** 正在执行拖动本View */
    private boolean mIsBeingDragged = false;
    /** 能否下拉刷新 */
    private boolean mCanDoRefresh = true;
    /** 如果不能够下拉刷新---能否出现下拉 回弹效果 */
    private boolean mCanPulldownShowRefreshView = true;
    /** 在一次Touch事件中，是否检测过了 TouchSlop，true 是检测过了，false 没有检测过 */
    private boolean mHasCheckedTouchSlop = false;
    /** ACTION_DOWN时，是否停止了Scroller */
    private boolean mTouchAbortScroller = false;
    /** 是否开始执行一次刷新操作,true开始执行 */
    private boolean mStartRefreshOperation = false;
    /** 一次刷新操作是否执行完毕 ,true 完毕 */
    private boolean mEndRefreshOperation = false;
    /** 如果为true，那么向布局中添加View的话，需要掉用 addViewInLayout()方法 */
    private boolean mInLayout;
    /**
     * 子View是否已经获取到了 ACTION_DOWN事件,true 是已经获取到了 ；默认是true
     * 因为即使没有获取DOWN事件，也能够向其发送ACTION_CANCEL事件
     */
    private boolean mChildHashDown = true;
    /**
     * 是否使用让顶部刷新HeaderView悬浮在ContentView上
     */
    private boolean mUserSuperRefreshHeaderView = false;
    /** 添加的额外的需要显示的 具体View，也能够触发下拉刷新 */
    private Set<View> mOtherContentViews;
    private RefreshViewCallback mRefreshViewCallback;
    private OnRefreshListener mListener;
    private boolean mJustAutoRefreshState = false;
    /** ---------- 执行动画滚动到指定位置的 任务 */
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private AutoRefreshTask mAutoRefreshTask;
    private StopRefreshTask mStopRefreshTask;
    private UpdateScrollToPositionTask mScrollToPositionTask;
    /** 动画滚动的{@link Animation} */
    private Animation mScrollAnimation = new Animation() {
        //动画过程中会反复调用此方法，interpolatedTime 值每次都变化，从0渐变为1，当参数是1时表示动画结束；
        //通过参数Transformation 来获取变换的矩阵（matrix），通过改变矩阵就可以实现各种复杂的效果；
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float desX = mAnimationScroll_xStart + mAnimationScroll_xDistance * interpolatedTime;
            float dexY = mAnimationScroll_yStart + mAnimationScroll_yDistance * interpolatedTime;

            setScrollToPosition(mAnimationScroll_isRefreshToNormal, mAnimationScroll_yDirection, (int) desX, (int) dexY);
            scrollingCallback();
        }
    };


    /**
     * 简单构造函数
     */
    public PullRefreshLayout(Context context) {
        super(context);
        init();
    }

    public PullRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PullRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PullRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * 是否使用让顶部刷新HeaderView悬浮在ContentView上
     *
     * @param use true使用；false不使用
     */
    public void setUserSuperHeaderView(boolean use) {
        mUserSuperRefreshHeaderView = use;
    }

    private void init() {
        final Context context = getContext();

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchScrollSlop = configuration.getScaledTouchSlop();
        mScroller = new Scroller(getContext(), sInterpolator);
        mOtherContentViews = new HashSet<>();

        if (OSVersionUtils.hasHoneycomb()) {
            mScrollMode = TranslationMode.SET_TRANSLATION;
        } else {
            mScrollMode = TranslationMode.SCROLL_TO;
        }

        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        mRefreshHeaderViewLayoutScale = 0;//首先让刷新HeaderView不可见

        setUseScroller(false);
        setUserSuperHeaderView(false);

    }

    /**
     * 是否开启超级 刷新HeaderView 模式，开启后，刷新HeaderView会布局到本ViewGroup所有子View上面，
     */
    private boolean useSuperHeaderViewMode() {
        return mUserSuperRefreshHeaderView;
    }

    //先执行这个方法，在执行 onMeasure()方法
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final int childCount = getChildCount();
        if (checkChildCount(childCount)) {
            throw new RuntimeException("必须有大于" + MIN_CHILD_VIEW_COUNT
                    + "个的子View,第一个子View是下拉时顶部漏出的提示刷新的View，其它的子View是显示的View（能够下拉刷新的View）");
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            //这个设置成顶部漏出的提示刷新的View
            lp.isHeaderRefresh = i == 0;
            if (i > 1) {
                mOtherContentViews.add(child);//存储非默认的ContentView
            }
        }

        if (null == mRefreshHeaderView) {
            mRefreshHeaderView = getChildAt(0);
            mContentView = getChildAt(1);
        }

        if (null == mRefreshViewCallback) {
            if (mRefreshHeaderView instanceof RefreshViewCallback) {
                mRefreshViewCallback = (RefreshViewCallback) mRefreshHeaderView;

                mRefreshingHeight = mRefreshViewCallback.getRefreshHeight();
            }
        }

        if (useSuperHeaderViewMode()) {
            //把顶部刷新headerView放置到所有子View之前
            removeView(mRefreshHeaderView);
            addView(mRefreshHeaderView, getChildCount() - 1);
        }


        onInitContentView(mContentView);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

//        int WidthMode = MeasureSpec.getMode(widthMeasureSpec);
//        switch (WidthMode){
//            case MeasureSpec.AT_MOST:
//                System.out.println("AT_MOST");
//                break;
//            case MeasureSpec.EXACTLY:
//                System.out.println("EXACTLY");
//                break;
//            case MeasureSpec.UNSPECIFIED:
//                System.out.println("UNSPECIFIED");
//                break;
//        }

        final int selfPaddingLeft = getPaddingLeft();
        final int selfPaddingRight = getPaddingRight();
        final int selfPaddingTop = getPaddingTop();
        final int selfPaddingBom = getPaddingBottom();

        mInLayout = true;//在确定有多少个子View前，表示正处于布局中，当确定了子View个数后，就正式进入测量，这时候再添加View，就需要重新调用oMeasure()方法了
        final int childCount = getChildCount();
        mInLayout = false;
        if (checkChildCount(childCount)) {
            throw new RuntimeException("必须有大于" + MIN_CHILD_VIEW_COUNT
                    + "个的子View,第一个子View是下拉时顶部漏出的提示刷新的View，其它的子View是显示的View（能够下拉刷新的View）");
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            //这个设置成顶部漏出的提示刷新的View
            lp.isHeaderRefresh = i == 0;

            if (i > 1) {
                mOtherContentViews.add(child);//存储非默认的ContentView
            }

            final int visible = child.getVisibility();
            if (visible != GONE) {
                // 下面的实现是把 measureChildWithMargins()这个方法的源码拿出来，方便理解计算子View
                // 尺寸是需要减去父View的padding和子View的margin
                final int childWidthMeasureSpec = getChildMeasureSpec(
                        widthMeasureSpec, selfPaddingLeft + selfPaddingRight
                                + lp.leftMargin + lp.rightMargin, lp.width);
                final int childHeightMeasureSpec = getChildMeasureSpec(
                        heightMeasureSpec, selfPaddingTop + selfPaddingBom
                                + lp.topMargin + lp.bottomMargin, lp.height);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);

                // 测量子View尺寸<br>
                // 可以看看这个方法的源码，它在计算子View尺寸时，减去了子View的margin值
                // measureChildWithMargins(child, widthMeasureSpec, 0,
                // heightMeasureSpec, 0);
            }

        }

        if (null != mRefreshViewCallback) {
            mRefreshingHeight = mRefreshViewCallback.getRefreshHeight();
        }

        mRefreshHeaderHeight = mRefreshHeaderView.getMeasuredHeight();//获取顶部headerView的高度

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        final int selfWidth = r - l; // 这个与getMeasuredWidth()的区别是，这个是最终显示的宽度，如果它上层布局不做特殊处理(比如说它上层ViewGroup在onLayout()方法中不根据它的getMeasuredWidth()来布局)的话，两者相等
        final int selfHeight = b - t;
        final int selfPaddingLeft = getPaddingLeft();
        final int selfPaddingRight = getPaddingRight();
        final int selfPaddingTop = getPaddingTop();
        final int selfPaddingBom = getPaddingBottom();

        int scrollY = 0;
        if (mScrollMode == TranslationMode.SCROLL_TO) {
            scrollY = getContentViewOffsetFromTop();
        } else if (mScrollMode == TranslationMode.SET_TRANSLATION) {
            scrollY = 0;
        }

        final int childCounts = getChildCount();

        for (int i = 0; i < childCounts; i++) {

            final View childView = getChildAt(i);
            final int visible = childView.getVisibility();
            final LayoutParams layoutParams = (LayoutParams) childView
                    .getLayoutParams();

            if (visible != GONE) {
                if (layoutParams.needsMeasure) {//由于这个并没有在 onMeasure()方法中测量到,需要重新测量尺寸
                    layoutParams.needsMeasure = false;
                    final int widthSpec = MeasureSpec.makeMeasureSpec(
                            selfWidth - selfPaddingLeft - selfPaddingRight - layoutParams.leftMargin - layoutParams.rightMargin,
                            MeasureSpec.EXACTLY);
                    final int heightSpec = MeasureSpec.makeMeasureSpec(
                            selfHeight - selfPaddingTop - selfPaddingBom - layoutParams.topMargin - layoutParams.bottomMargin,
                            MeasureSpec.EXACTLY);
                    childView.measure(widthSpec, heightSpec);

                    if (i > 1) {//非ContentView
                        mOtherContentViews.add(childView);//存储非默认的ContentView
                    }
                }

                int childMaxWidth = selfWidth - selfPaddingLeft - selfPaddingRight
                        - layoutParams.leftMargin - layoutParams.rightMargin;
                int childMaxHeight = selfHeight - selfPaddingTop - selfPaddingBom
                        - layoutParams.topMargin - layoutParams.bottomMargin;
                int childWidth = childMaxWidth >= childView.getMeasuredWidth() ? childView
                        .getMeasuredWidth() : childMaxWidth;
                int childHeight = childMaxHeight >= childView.getMeasuredHeight() ? childView
                        .getMeasuredHeight() : childMaxHeight;
                int left = selfPaddingLeft + layoutParams.leftMargin;
                int top = selfPaddingTop + layoutParams.topMargin;
                if (layoutParams.isHeaderRefresh) {
                    top += scrollY;
                }
                int right = left + childWidth;
                int bom;

                if (useSuperHeaderViewMode() && childView == mRefreshHeaderView) {
                    bom = top + (int) (childHeight * mRefreshHeaderViewLayoutScale);
                } else {
                    bom = top + childHeight;
                }

                childView.layout(left, top, right, bom);
            }

        }// for end

    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    // 这个方法会一直执行，不管是否 拦截掉了事件,所以需要在这个方法中处理，这样能够实现 平滑的接管和不接管事件。
    // 返回 true，事件直接分发给本View的 onTouchEvent()，不再向下分发，返回false 表示本View不需要事件继续向下分发
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        //当正在执行自动刷新 或者 正在执行 刷新完成后滚动回正常状态 时，阻止touch事件
        if (mState == State.AUTO_REFRESH || mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL) {
            //从本View开始截断所有事件
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG)
                    Log.v(TAG, "ACTION_CANCEL");
                if (!mIsBeingDragged && !mTouchAbortScroller) {
                    break;
                }
                if (mState == State.NORMAL) {
                    if (getContentViewOffsetFromTop() == 0) {
                        // 此时需要回调 结束事件
                        callbackContentViewEndScroll();
                    } else {
                        setSmoothScrollTo(0, 0, 0);
                    }

                }
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;

            case MotionEvent.ACTION_UP:
                if (DEBUG)
                    Log.v(TAG, "ACTION_UP");

                final int scrollY = getContentViewOffsetFromTop();// 正数表示 向上滚动了，负数表示向下滚动了
                final int scrolledDistance_up = Math.abs(scrollY);// 滚动的距离
                mIsBeingDragged = false;

                if (scrollY < 0) {// 处于下拉的状态
                    if (checkEnableRefresh()) {
                        if (DEBUG) {
                            Log.e(TAG, "刷新高度：" + mRefreshingHeight);
                            Log.e(TAG, "已经滚动的高度：" + scrolledDistance_up);
                        }

                        if (scrolledDistance_up > mRefreshingHeight) {
                            setSmoothScrollTo(0, -mRefreshingHeight, 0);
                        } else if (scrolledDistance_up == mRefreshingHeight
                                && mState == State.NORMAL) {
                            doRefresh();
                        } else {// 不能触发刷新
                            if (mState == State.NORMAL) {
                                if (scrolledDistance_up == 0) {
                                    // 此时需要回调 结束事件
                                    callbackContentViewEndScroll();
                                } else {
                                    // 平滑滚动到指定位置
                                    setSmoothScrollTo(0, 0, 0);
                                }
                            } else if (mState == State.REFRESHING) {// 此时正在刷新状态，需要惯性
                                //TODO 暂时不知道怎么平滑的添加惯性

                            }
                        }
                    } else {
                        // 平滑滚动到指定位置
                        setSmoothScrollTo(0, 0, 0);
                    }
                } else if (scrollY == 0) {
                    if (mState != State.REFRESHING) {
                        callbackContentViewEndScroll();
                    }
                }
                mActivePointerId = INVALID_POINTER;

                break;

            // 这个事件只有首次手指down下才执行，如果第二根手指再DOWN的话不执行这个分支
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);// 只根据第一根手指来判断是否满足本View滑动事件
                mLastMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mChildHashDown = true;
                mHasCheckedTouchSlop = false;

                mTouchAbortScroller = !isScrollAnimationFinish();
                forceAbortScrollAnimation();
                if (!mTouchAbortScroller) {// 上次操作完整的执行完
                    // 上次操作完整的执行完
                    mStartRefreshOperation = false;
                    mEndRefreshOperation = false;
                }
                if(mTouchAbortScroller){//此时证明触摸终止了动画，那么此时应该让布局获取到事件
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:

                final int activePointerId = mActivePointerId;

                if (activePointerId == INVALID_POINTER) {
                    break;
                }
                final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
                        activePointerId);
                int pointerCount = MotionEventCompat.getPointerCount(ev);
                if (pointerIndex <= -1 || pointerIndex >= pointerCount) {
                    break;
                }

                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float xDistance = mLastMotionX - x;
                final float yDistance = mLastMotionY - y;
                final float canScrollDistance = Math.abs(mInitialMotionY - y);

                final boolean pullDown = yDistance < 0;
                final boolean childViewCanCatchEvent = canChildScrollUp(mContentView);
                boolean enoughSlopDistance = true;

                // 只有首次ACTION_DOWN后需要判断（防止ContentView难以触发点击事件【被拦截被当做滑动事件】），
                if (!mHasCheckedTouchSlop) {
                    enoughSlopDistance = canScrollDistance > mTouchScrollSlop;
                    mHasCheckedTouchSlop = true;
                }

                // 首先检测是否已经拦截事件，如果没有拦截根据条件来判断是否拦截
                if (!mIsBeingDragged && enoughSlopDistance
                        && !(customContentViewAchieveEvent(-yDistance, mState) || childViewCanCatchEvent)) {
                    if (isContentViewOffsetFromTop()) {// 已经发生了偏移，必须拦截事件
                        interceptEvent(ev);
                        mIsBeingDragged = true;
                    }

                    if ((Math.abs(yDistance) * YDISTANCE_OFFSET_XDISTANCE >= Math.abs(xDistance))
                            && (checkEnableRefresh() || checkEnablePull())
                            && pullDown) { //首次判断是否需要拦截
                        interceptEvent(ev);
                        mIsBeingDragged = true;
                        if (mState == State.NORMAL && getContentViewOffsetFromTop() == 0) {
                            callbackContentViewBeginScroll();
                        }
                    }
                }

                // 如果已经拦截了事件，但是在手指拖动过程中，手指向上滑动了并且 ContentView 没有向下发生偏移,
                //此时需要 把事件分发给 子View，自己放弃事件
                if ((mIsBeingDragged && !pullDown && !isContentViewOffsetFromTop())
                        || customContentViewAchieveEvent(-yDistance, mState)) {
                    dropInterceptEvent(ev);
                    mIsBeingDragged = false;
                }

                // 执行拦截后的事件
                if (mIsBeingDragged) {
                    requestParentDisallowInterceptTouchEvent(true);
                    // 执行跟随手指滚动
                    scrollToYByFinger(y);

                    return true;
                } else {
                    if (DEBUG)
                        Log.v(TAG, "子View执行滚动");
                    mLastMotionY = y;
                    mLastMotionX = x;
                }
                break;
            case MotionEventCompat.ACTION_POINTER_DOWN:// 最后一根手指按下
                if (mIsBeingDragged) {
                    // 转而根据最后一根手指来执行滑动事件
                    final int actionIndex = MotionEventCompat.getActionIndex(ev);// 获取触发本次事件的手指
                    // id

                    // 也可以这么写，其实 actionIndex就是 触发此次事件的pointerIndex
                    // final float y = MotionEventCompat.getX(event, actionIndex);
                    // mLastMotionY = y;

                    mActivePointerId = MotionEventCompat.getPointerId(ev,
                            actionIndex);// 根据手指id
                    // 返回关联的事件id

                    final int pointerIndex_ = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);

                    // 也可以像上面注释掉的那样写
                    mLastMotionY = MotionEventCompat.getY(ev, pointerIndex_);

                    return true;
                }
                break;
            case MotionEventCompat.ACTION_POINTER_UP:// 最后按下的手指 up
                if (mIsBeingDragged) {

                    onSecondaryPointerUp(ev);
                    mLastMotionY = MotionEventCompat.getY(ev, MotionEventCompat
                            .findPointerIndex(ev, mActivePointerId));

                    return true;
                }
                break;

            default:
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    /**
     * 是否不使用{@link Scroller}作为滚动动画
     *
     * @return true 使用Scroller，false 不使用
     */
    private boolean isUseScroller() {
        return mUseScroller;
    }

    /**
     * 设置是否启用{@link Animator}来执行滚动动画
     *
     * @param use true 使用Animator，false 不使用Animator
     */
    public void setUseScroller(boolean use) {
        mUseScroller = use;
    }

    /**
     * 开始执行滚动动画
     *
     * @param sx       开始X轴坐标
     * @param sy       开始Y轴坐标
     * @param dx       在X轴上移动的距离
     * @param dy       在Y轴上移动的距离
     * @param duration 动画持续时间 ,暂时不使用这个了
     */
    private void startScrollAnimation(int sx, int sy, int dx, int dy, int duration) {
        if (!isUseScroller()) {
            mAnimationScroll_isRefreshToNormal = mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL;
            mAnimationScroll_yDirection = dy;
            mAnimationScroll_yStart = sy;
            mAnimationScroll_xStart = sx;
            mAnimationScroll_yDistance = dy;
            mAnimationScroll_xDistance = dx;

            mScrollAnimation.reset();
            mScrollAnimation.setDuration(duration);
            mScrollAnimation.setInterpolator(mDecelerateInterpolator);
            mScrollAnimation.setAnimationListener(mScrollAnimationListener);
            mContentView.clearAnimation();
            mContentView.startAnimation(mScrollAnimation);
        } else {
            mScroller.startScroll(sx, sy, dx, dy, duration);
            ViewCompat.postInvalidateOnAnimation(this);// 开始刷新界面，会调用 computeScroll()
        }

    }

    /**
     * 终止滚动动画并且让滚动动画停止到结束位置
     */
    private void forceAbortScrollAnimation() {
        if (!isUseScroller()) {
            mScrollAnimation.cancel();
            mContentView.clearAnimation();
            mScrollAnimation.setAnimationListener(null);
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
        }
    }

    /**
     * 滚动动画是否执行完成了
     *
     * @return true 执行完了，false 没有执行完
     */
    private boolean isScrollAnimationFinish() {
        if (!isUseScroller()) {
            return mScrollAnimation.hasEnded() || !mScrollAnimation.hasStarted();
        } else {
            return mScroller.isFinished();
        }
    }

    // 拦截事件到本View
    private void interceptEvent(MotionEvent event) {
        mIsBeingDragged = true;
        // 一旦拦截成功，需要给子View发送 ACTION_CANCEL事件，
        if (mChildHashDown) {
            sendCancelEvent(event);
            mChildHashDown = false;
        }

        if (DEBUG) {
            Log.v(TAG, "拦截成功");
        }
    }

    // 停止拦截事件
    private void dropInterceptEvent(MotionEvent event) {
        mIsBeingDragged = false;
        if (!mChildHashDown) {
            sendDownEvent(event);
            mChildHashDown = true;

            if (DEBUG) {
                Log.v(TAG, "停止拦截，向子View发送 ACTION_DOWN");
            }
        }
    }

    @Override
    public void computeScroll() {
        if (!isUseScroller()) {
            super.computeScroll();
            return;
        }

        // 在这里需要注意，当调用 Scroller.computeScrollOffset()后会更新Scroller的位置.
        // 在这里可能， mScroller.isFinished() 返回false，但是接着调用了mScroller.computeScrollOffset()，
        // 那么mScroller.isFinished()就返回true了
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {

            int oldX = getContentViewOffsetFromLeft();
            int oldY = getContentViewOffsetFromTop();

            int currentX = mScroller.getCurrX();
            int currentY = mScroller.getCurrY();

            int yDirection = currentY - oldY;

            //使用消息的形式自动滚动，貌似没啥提升
            //            post(new ScrollFrameTask(oldX,oldY,currentX,currentY,yDirection));

            if (oldX != currentX || oldY != currentY) {
                setScrollToPosition(
                        mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL,
                        yDirection, currentX, currentY);
            }

            scrollingCallback();

        } else {//这个当子View中有滚动时，也会回调 父类的这个方法，因为子View滚动时会导致重绘从而导致本父类这个方法也会调用
            super.computeScroll();
        }

    }//computeScroll() end

    private void sendCancelEvent(MotionEvent event) {
        MotionEvent e = MotionEvent.obtain(event.getDownTime(),
                event.getEventTime(), MotionEvent.ACTION_CANCEL, event.getX(),
                event.getY(), event.getMetaState());
        super.dispatchTouchEvent(e);
        e.recycle();
    }

    private void sendDownEvent(MotionEvent event) {
        // last.getY() - mTouchScrollSlop*2 :是为了防止ContentView执行ACTION_DOWN触发的click
        MotionEvent e = MotionEvent.obtain(event.getDownTime(),
                event.getEventTime(), MotionEvent.ACTION_DOWN, event.getX(),
                event.getY() - mTouchScrollSlop * 2, event.getMetaState());
        super.dispatchTouchEvent(e);
        if (DEBUG)
            Log.v(TAG, "子View接收到ACTION_DOWN");
        e.recycle();
    }

    // 将抬起的手指 的下一个手指作为 当前活动手指
    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // 如果把最先触摸的手指抬起来了，就让第二个手指处于活动手指，否则取倒数第二个手指

            final int newPointerIndex = pointerIndex == 0 ? 1
                    : MotionEventCompat.getPointerCount(ev) - 1 - 1;// 索引从0开始的，所以需要总数-1，然后需要取倒数第二个所以再-1
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev,
                    newPointerIndex);
        }
    }

    /**
     * 用于设置是否允许父View的 onInterceptTouchEvent() 拦截,如果传递true，那么父view就不会执行
     * onInterceptTouchEvent()方法了，也就是父View不会拦截子View的touch事件了，子View全权处理touch事件
     */
    private void requestParentDisallowInterceptTouchEvent(
            boolean disallowIntercept) {
        final ViewParent parent = getParent();
        if (parent != null) {
            // 用于设置是否允许父View的 onInterceptTouchEvent() 拦截,此处传递true，那么即使父View
            // 在onInterceptTouchEvent() return了true，子View仍然可以接受到touch事件
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * 让本View沿Y轴滚动
     *
     * @param y 当前手指触摸的y坐标
     *
     * @return 总共已经滚动的距离
     */
    private int scrollToYByFinger(float y) {

        float yDistance = (mLastMotionY - y);
        boolean pullUp;

        if (yDistance > 0) {// pull up
            pullUp = true;
        } else {// pull down
            pullUp = false;
            yDistance = yDistance * mDragOffset;
        }

        if (DEBUG) {
            Log.d(TAG, "scrollToYByFinger - mLastMotionY：" + mLastMotionY);
            Log.d(TAG, "scrollToYByFinger - y：" + y);
        }

        mLastMotionY = y;

        final int oldScrollY = getContentViewOffsetFromTop();// 取得以前滚动的位置
        float scrollY = oldScrollY + yDistance;// 计算当前需要滚动目标Y坐标

        // 这样做能够保证,肯定会正好滚动到刷新位置
        if (!pullUp) {// pull down
            if (oldScrollY > -mRefreshingHeight && scrollY < -mRefreshingHeight) {
                scrollY = -mRefreshingHeight;
            }
        } else {// pull up
            if (oldScrollY < -mRefreshingHeight && scrollY > -mRefreshingHeight) {
                scrollY = -mRefreshingHeight;
            }
        }

        if (DEBUG) {
            Log.d(TAG, "scrollToYByFinger() - oldScrollY：" + oldScrollY);
            Log.d(TAG, "scrollToYByFinger() - yDistance：" + yDistance);
            Log.d(TAG, "scrollToYByFinger() - scrollY：" + scrollY);
        }

        if (scrollY >= 0) {
            scrollY = 0;
        }

        // 执行滚动操作
        setScrollToPosition(false, yDistance, getContentViewOffsetFromLeft(), (int) scrollY);

        // 把布局少滚动的小数位加上，这样能够防止 滚动的距离没有手指移动的距离大
        mLastMotionY += scrollY - (int) scrollY;

        // 计算拖动阻尼系数
        mDragOffset = 1 - Math.abs((float) getContentViewOffsetFromTop()
                / getHeight());
        mDragOffset = Math.min(INIT_DRAG_OFFSET, mDragOffset);

        return Math.abs((int) scrollY);
    }

    /**
     * 滚动到指定位置
     *
     * @param isRefreshToNormal 是否是刷新完成后滚动到正常位置
     * @param yDirection        负数ContentView向上滚动，正数ContentView向下滚动
     * @param x                 目标x
     * @param y                 目标y
     */
    final public void setScrollToPosition(boolean isRefreshToNormal,
                                          float yDirection, int x, int y) {
        //执行注册的View的滚动
        for (View view : mRegisteredView) {
            if (view.getVisibility() == View.VISIBLE) {
                ViewHelper.setTranslationY(view, Math.abs(y) + (float) view.getTag(REGISTERED_VIEW_TRANSLATION_Y));
            }
        }

        if (null == mScrollToPositionTask) {
            mScrollToPositionTask = new UpdateScrollToPositionTask();
        }
        mScrollToPositionTask.isRefreshToNormal = isRefreshToNormal;
        mScrollToPositionTask.x = x;
        mScrollToPositionTask.y = y;
        mScrollToPositionTask.yDirection = yDirection;
        mScrollToPositionTask.run();

    }

    /**
     * 平滑滚动到指定位置
     *
     * @param x        目标x轴坐标
     * @param y        目标y轴坐标
     * @param velocity 滚动速度,如果为0，就自己计算
     */
    private void setSmoothScrollTo(int x, int y, int velocity) {
        if (!isScrollAnimationFinish()) {
            return;
        }

        // 取得当前位置
        int sx = getContentViewOffsetFromLeft();
        int sy = getContentViewOffsetFromTop();
        if (x == sx && y == sy) {
            return;
        }

        int dx = x - sx;
        int dy = y - sy;

        // ====下计算动画时间的方法 是从ViewPager源码中扒过来的
        final int clientHeight = getClientHeight();
        final int halfHeight = clientHeight / 2;
        // 计算需要滚动的距离与本View高度的比例
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx)
                / clientHeight);
        final float distance = halfHeight + halfHeight
                * distanceInfluenceForSnapDuration(distanceRatio);

        int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float delta = (float) Math.abs(dx) / (clientHeight);
            duration = (int) ((delta + 1) * 400);
        }
        startScrollAnimation(sx, sy, dx, dy, duration);
    }

    /**
     * 在滚动动画执行时回调
     */
    private void scrollingCallback() {
        final int scrollY = getContentViewOffsetFromTop();
        final int scrolledDistance = Math.abs(scrollY);
        if (isScrollAnimationFinish()) {// 滚动完成
            if (scrolledDistance == mRefreshingHeight) {
                if (mState == State.NORMAL || mState == State.AUTO_REFRESH) {
                    doRefresh();
                }
            } else if (scrolledDistance == 0) {
                if (mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL) {
                    refreshedScrollToNormalFinish();
                } else if (mState == State.NORMAL) {
                    callbackContentViewEndScroll();
                }
            }
        } else {
            //当Scroller滚动到最终位置时，不知道为啥会 多次在最终位置上滚动
            if (mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL && scrolledDistance == 0) {
                forceAbortScrollAnimation();
                refreshedScrollToNormalFinish();
            }

            if (isUseScroller()) {
                // 持续滚动，直到动画结束
                ViewCompat.postInvalidateOnAnimation(PullRefreshLayout.this);
            }
        }
    }

    // 跟随本View的滚动来移动 刷新View 布局位置
    private void offsetRefreshAndLoadMoreView() {
        final int paddingTop = getPaddingTop();

        final View childView = mRefreshHeaderView;
        final int scrollY = getContentViewOffsetFromTop();

        // childView getTop()不会改变，因为 getTop()获取的值是相对于父View的值，是相对值
        LayoutParams lp = (LayoutParams) childView.getLayoutParams();
        int childViewTop;
        childViewTop = paddingTop + lp.topMargin;
        childViewTop = childViewTop + scrollY;
        int offset = childViewTop - childView.getTop();
        childView.offsetTopAndBottom(offset);
    }

    private int getClientHeight() {
        return getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    }

    // 从ViewPager源码中弄过来的
    // We want the duration of the page snap animation to be influenced by the
    // distance that
    // the screen has to travel, however, we don't want this duration to be
    // effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect
    // that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    /**
     * 检测是否 停用了 下拉刷新
     *
     * @return true 表示没有，false表示停用
     */
    private boolean checkEnableRefresh() {

        return mCanDoRefresh;

    }

    /**
     * 检测是否能够执行 下拉 露出 刷新View
     *
     * @return true 表示能，false 表示不能
     */
    private boolean checkEnablePull() {
        return mCanPulldownShowRefreshView;
    }

    /**
     * 检测子View个数是否符合规则
     *
     * @return true 符合规则，false不符合规则
     */
    private boolean checkChildCount(int childCount) {
        return childCount < MIN_CHILD_VIEW_COUNT;
    }

    // 执行刷新
    private void doRefresh() {

        final boolean just = mJustAutoRefreshState;
        mJustAutoRefreshState = false;

        if (mState == State.REFRESHING) {
            return;
        }
        mState = State.REFRESHING;

        if (null != mRefreshViewCallback) {
            mRefreshViewCallback.doRefresh();
        }

        if (!just)
            if (null != mListener) {
                mListener.onRefresh();
            }

    }

    // 刷新执行完后，自动滚动到了 正常位置
    private void refreshedScrollToNormalFinish() {
        callbackContentViewEndScroll();
        mState = State.NORMAL;
        if (null != mListener) {
            mListener.onRefreshComplete();
        }
    }

    // 回调 RefreshViewCallback 中的方法
    private void callbackContentViewBeginScroll() {
        if (null != mRefreshViewCallback && !mStartRefreshOperation) {
            if (DEBUG)
                Log.e(TAG, "调用了callbackContentViewBeginScroll()");
            mRefreshViewCallback.contentViewBeginScroll();
            mStartRefreshOperation = true;
        }
    }

    // 回调 RefreshViewCallback 中的方法
    private void callbackContentViewScrollDistance(int scrolledDistance) {
        if (null != mRefreshViewCallback) {
            if (DEBUG)
                Log.e(TAG, "调用了 callbackContentViewScrollDistance()");
            mRefreshViewCallback.contentViewScrollDistance(scrolledDistance, mState);
        }
    }

    // 回调 RefreshViewCallback 中的方法
    private void callbackContentViewEndScroll() {
        if (null != mRefreshViewCallback && !mEndRefreshOperation) {
            if (DEBUG)
                Log.e(TAG, "调用了callbackContentViewEndScroll()");
            mRefreshViewCallback.contentViewEndScroll();
            mEndRefreshOperation = true;
        }
    }

    /** 设置ContentView下拉监听接口 */
    public void setOnContentViewPullListener(RefreshViewCallback callback) {
        mRefreshViewCallback = callback;
        if (null != mRefreshViewCallback) {
            mRefreshingHeight = mRefreshViewCallback.getRefreshHeight();
        }
    }

    /** 设置刷新监听接口 */
    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public int getRefreshHeight() {
        return mRefreshingHeight;
    }

    /**
     * 启用下拉刷新功能
     */
    public void enableRefresh() {
        mCanDoRefresh = true;
        mCanPulldownShowRefreshView = true;
    }

    /**
     * 停用下拉刷新功能
     *
     * @param canPullDown 是否同时停用 下拉露出刷新View的功能
     */
    public void disEnableRefresh(boolean canPullDown) {
        mCanDoRefresh = false;
        mCanPulldownShowRefreshView = canPullDown;
    }

    /** 是否正在刷新 */
    public boolean isRefresh() {
        return mState == State.REFRESHING;
    }

    public boolean isRefreshToNormal() {
        return mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL;
    }

    /** 停止刷新状态 */
    public void stopRefresh() {
        //非主线程调用，直接抛异常
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Must call in Main thread！！！");
        }

        if (mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL) {
            return;
        }

        if (null == mStopRefreshTask) {
            mStopRefreshTask = new StopRefreshTask();
        } else {
            mMainHandler.removeCallbacks(mStopRefreshTask);
        }
        mMainHandler.post(mStopRefreshTask);
    }

    /** 取消所有状态以及动画，此时应该是销毁 */
    public void cancel() {
        mState = State.REFRESH_FINISH_SCROLL_TO_NORMAL;
        forceAbortScrollAnimation();
        //        setScrollToPosition(false, -1f, 0, 0);//模拟成手指滚动，这样能够少执行一部分代码
        mMainHandler.removeCallbacksAndMessages(null);
    }

    /** 自动刷新 */
    public void autoRefresh() {
        autoRefresh(false, true);
    }

    /** 自动刷新 */
    public void autoRefresh(boolean justState, boolean useMsg) {
        mJustAutoRefreshState = justState;
        if (mState == State.REFRESH_FINISH_SCROLL_TO_NORMAL
                || mState == State.REFRESHING || mState == State.AUTO_REFRESH) {
            return;
        }
        if (!checkEnablePull()) {
            return;
        }

        mState = State.AUTO_REFRESH;
        forceAbortScrollAnimation();

        if (null == mAutoRefreshTask) {
            mAutoRefreshTask = new AutoRefreshTask();
        } else {
            if (useMsg) {
                mMainHandler.removeCallbacks(mAutoRefreshTask);
            }
        }

        if (useMsg) {
            mMainHandler.removeCallbacks(mAutoRefreshTask);
            mMainHandler.post(mAutoRefreshTask);
        } else {
            mAutoRefreshTask.run();
        }
    }

    public void addView(@NonNull View view, ViewGroup.LayoutParams layoutParams) {
        if (!checkLayoutParams(layoutParams)) {
            layoutParams = generateLayoutParams(layoutParams);
        }
        final LayoutParams lp = (LayoutParams) layoutParams;
        if (mInLayout) {
            lp.needsMeasure = true;
            addViewInLayout(view, MIN_CHILD_VIEW_COUNT + mOtherContentViews.size(), lp);
        } else
            super.addView(view, layoutParams);
        mOtherContentViews.add(view);
    }

    /**
     * 如果需要在代码中动态的改变 可能会调用{@link #registerViewForScroll(View)} 进行注册的View MarginTop ，
     * 需要调用这个方法设置，或者在外部直接调用 {@link View#setTranslationY(float)}来设置，不要使用改变View的Marin属性的 方法
     */
    public void setWillRegisteredViewMarginTop(View willRegistView, float marginTop) {
        ViewHelper.setTranslationY(willRegistView, marginTop);
    }

    /** 把View注册到本布局，当本布局的ContentView 偏移顶部时，跟随ContentView一块滚动 */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void registerViewForScroll(View view) {
        if (mRegisteredView.contains(view) || view == mContentView) {
            return;
        }
        view.setTag(REGISTERED_VIEW_TRANSLATION_Y, view.getTranslationY());
        mRegisteredView.add(view);
    }

    /** 取消 通过 {@link #registerViewForScroll(View)}注册的View */
    public void unRegisterViewForScroll(View view) {
        if (!mRegisteredView.contains(view) || view == mContentView) {
            return;
        }
        mRegisteredView.remove(view);
        ViewHelper.setTranslationY(view, (Float) view.getTag(REGISTERED_VIEW_TRANSLATION_Y));
    }

    /** ----------------以下两个方法可以重写,来判断contentView是否可以上拉和下拉 */

    /** 获取能够 下拉偏移顶部的 ContentView */
    public View getContentView() {
        return mContentView;
    }

    /** 判断View 能否向下滚动 (注意： 手指向上拖动，View跟随手指滚动，是向下滚动！) ,这个方法暂时没有用到 */
    // public boolean canChildScrollDown(View view) {
    //
    // if (android.os.Build.VERSION.SDK_INT < 14) {
    //
    // if (mContentView instanceof AbsListView) {
    //
    // final AbsListView absListView = (AbsListView) view;
    //
    // int itemCount = absListView.getCount();
    // int lastVisiblePosition = absListView.getLastVisiblePosition();
    //
    // return itemCount > 0
    // && (lastVisiblePosition <= itemCount - 1 || absListView
    // .getChildAt(absListView.getChildCount() - 1)
    // .getBottom() < absListView.getPaddingBottom());
    // } else {
    //
    // return view.getScrollY() < 0;
    //
    // }
    //
    // } else {
    // // 第二个参数的意思：负数 判断是否可以向上滚动，整数 判断是否可以向下滚动
    // return ViewCompat.canScrollVertically(mContentView, 1);
    // }
    //
    // }

    /**
     * 判断view 能否向上滚动(注意： 手指向下拖动，View跟随手指滚动，是向上滚动！)
     *
     * @return true 可以向上滚动，false不可以向上滚动
     */
    public boolean canChildScrollUp(View view) {
        if (Build.VERSION.SDK_INT < 14) {
            if (view instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) view;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(view, -1) || view.getScrollY() > 0;
            }
        } else {
            // 第二个参数的意思：负数 判断是否 可以 向上滚动了，正数 判断是否 可以 向下滚动了
            return ViewCompat.canScrollVertically(view, -1);//这个有bug，就是当AbsListView有PaddingTop值时,并且设置了setClipToPadding(false),返回不正确
        }
    }

    /** -----------------可以重写下面三个方法，来更改ContentView的偏移方式 */

    /**
     * 获取ContentView偏移本View 左边的距离
     *
     * @ return 偏移左边的距离， 负数证明向右偏移了，正数证明向左偏移了
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected int getContentViewOffsetFromLeft() {
        if (mScrollMode == TranslationMode.SCROLL_TO) {
            return getScrollX();
        } else if (mScrollMode == TranslationMode.SET_TRANSLATION && null != mContentView) {
            return -(int) getTranslationX();
        }

        return 0;
    }

    /**
     * 获取ContentView偏移本View 顶部的距离
     *
     * @ return 偏移顶部的距离， 负数证明向下偏移，正数证明向上偏移
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected int getContentViewOffsetFromTop() {

        if (mScrollMode == TranslationMode.SCROLL_TO) {
            return getScrollY();
        } else if (mScrollMode == TranslationMode.SET_TRANSLATION && null != mContentView) {
            return -(int) mContentView.getTranslationY();
        }

        return 0;
    }

    /**
     * ContentView是否 由于本View的 TouchEvent事件 发生了偏移
     *
     * @return true 发生了偏移，false 没有发生偏移
     */
    protected boolean isContentViewOffsetFromTop() {
        return getContentViewOffsetFromTop() < 0;
    }
    /** -----------------可以重写上面三个方法，来更改ContentView的偏移方式 */

    /**
     * 滚动到指定X,Y轴的位置
     *
     * @param isRefreshToNormal 是否是刷新完成后滚动到正常位置
     * @param yDirection        负数ContentView向上滚动，正数ContentView向下滚动
     * @param x                 目标x
     * @param y                 目标y
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void contentViewScrollToPosition(boolean isRefreshToNormal, float yDirection, int x, int y) {
        if (DEBUG) {
            Log.d(TAG, "scrollToYByFinger() - x：" + x);
            Log.d(TAG, "scrollToYByFinger() -  y：" + y);
        }

        if (mScrollMode == TranslationMode.SCROLL_TO) {//这种方案会造成额外开销，抛弃
            // 处理下拉刷新和上拉加载更多View的动态位置
            offsetRefreshAndLoadMoreView();
            scrollTo(x, y);
        } else if (mScrollMode == TranslationMode.SET_TRANSLATION) {
            //----------这种方案比较好
            if (null != mContentView) {
                mContentView.setTranslationY(-y);
                mContentView.setTranslationX(-x);
            }
        }
    }

    /**
     * 自定义来让 ContentView获取到ToucheEvent
     *
     * @param yDirection 正数ContentView向上滚动，负数ContentView向下滚动
     *
     * @return true ,ContentView获取到ToucheEvent；false ，反之
     */
    protected boolean customContentViewAchieveEvent(float yDirection,
                                                    State state) {

        return false;
    }

    /** 在这里初始化ContentView，可以重写 来自己实现初始化 ,比如启用ContentView的默写特殊属性等 */
    protected void onInitContentView(View contentView) {

    }

    /** -----------------下面方法是自定义 本ViewGroup的子View的 LayoutParams */
    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(
            ViewGroup.LayoutParams p) {
        return generateDefaultLayoutParams();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }


    /** 滚动模式 */
    public enum TranslationMode {
        /** 调用 {@link View#scrollTo(int, int)} 来实现滚动 */
        SCROLL_TO,
        /** 调用{@link View#setTranslationY(float)} 来实现滚动 */
        SET_TRANSLATION
    }

    /** 本View所处状态 */
    public enum State {
        /** 正常状态 */
        NORMAL,
        /** 正在执行刷新操作 */
        REFRESHING,
        /** 刷新操作完成，自动滚动到正常状态 ,在这个过程中 是不会触发手指滑动操作的 */
        REFRESH_FINISH_SCROLL_TO_NORMAL,
        /** 正在执行自动刷新 */
        AUTO_REFRESH
    }

    /**
     * 事件回调接口
     */
    public interface OnRefreshListener {

        /** 执行下拉刷新事件 */
        void onRefresh();

        /** {@link PullRefreshLayout#stopRefresh()} 调用后，动画执行完毕后回调 */
        void onRefreshComplete();

    }

    public static class LayoutParams extends MarginLayoutParams {
        /** 是否需要重新测量 */
        private boolean needsMeasure;
        /** 是否是下拉后漏出的顶部提示刷新的View */
        private boolean isHeaderRefresh;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

    }

    //执行自动刷新的task
    private class AutoRefreshTask implements Runnable {
        public void run() {
            mState = State.AUTO_REFRESH;
            mStartRefreshOperation = false;
            //添加这个变量，解决自动下拉刷新时，不会回调 contentViewEndScroll() bug
            mEndRefreshOperation = false;
            callbackContentViewBeginScroll();
            if (DEBUG)
                Log.e(TAG, "开始自动刷新操作：" + -mRefreshingHeight);
            setSmoothScrollTo(0, -mRefreshingHeight, AUTO_REFRESH_SCROLL_SPEED);
        }
    }

    //执行停止刷新的task
    private class StopRefreshTask implements Runnable {
        @Override
        public void run() {
            mState = State.REFRESH_FINISH_SCROLL_TO_NORMAL;
            if (null != mRefreshViewCallback) {
                mRefreshViewCallback.refreshStop();
            }
            final int sy = getContentViewOffsetFromTop();
            if (sy == 0) {// 不需要滚动
                refreshedScrollToNormalFinish();
            } else {
                forceAbortScrollAnimation();// 停止Scroller的滚动
                setSmoothScrollTo(0, 0, 0);
            }
        }
    }


    private class UpdateScrollToPositionTask implements Runnable {

        boolean isRefreshToNormal;//是否是刷新完成后滚动到正常位置
        float yDirection;//负数ContentView向上滚动，正数ContentView向下滚动
        int x;//目标x
        int y;//目标y

        @Override
        public void run() {
            //执行让ContentView向下偏移的方法
            contentViewScrollToPosition(isRefreshToNormal, yDirection, x, y);

            //更新顶部刷新HeaderView的布局，使之露出来
            if (useSuperHeaderViewMode()) {
                mRefreshHeaderViewLayoutScale = -y / (float) mRefreshHeaderHeight;
                if (mRefreshHeaderViewLayoutScale <= 1) {
                    mRefreshHeaderView.requestLayout();
                }
            }

            // 执行回调
            callbackContentViewScrollDistance(Math.abs(y));
        }
    }

}

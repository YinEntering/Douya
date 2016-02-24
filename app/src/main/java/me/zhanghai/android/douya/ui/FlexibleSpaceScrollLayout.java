/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.douya.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.support.v4.view.InputDeviceCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.support.v4.widget.FriendlyScrollerCompat;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

public class FlexibleSpaceScrollLayout extends FrameLayout {

    public static final String TAG_FLEXIBLE_SPACE_SCROLLABLE_CHILD = "flexibleSpaceScrollableChild";

    private static final int INVALID_POINTER_ID = -1;

    private int mTouchSlop;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;

    private FriendlyScrollerCompat mScroller;
    private EdgeEffectCompat mEdgeEffectBottom;

    private int mScroll;
    private List<View> mScrollableChildren = new ArrayList<>();
    private int mScrollingChildIndex;

    private boolean mIsBeingDragged;
    private int mActivePointerId;
    private float mLastMotionY;
    private VelocityTracker mVelocityTracker;

    private float mView_verticalScrollFactor = Float.MIN_VALUE;

    public FlexibleSpaceScrollLayout(Context context) {
        super(context);

        init(context, null, 0, 0);
    }

    public FlexibleSpaceScrollLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0, 0);
    }

    public FlexibleSpaceScrollLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public FlexibleSpaceScrollLayout(Context context, AttributeSet attrs, int defStyleAttr,
                                     int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);

        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mTouchSlop = viewConfiguration.getScaledTouchSlop();
        mMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();

        mScroller = FriendlyScrollerCompat.create(context);
        mEdgeEffectBottom = new EdgeEffectCompat(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        addScrollableChildren(this);
    }

    private void addScrollableChildren(ViewGroup viewGroup) {
        for (int i = 0, childCount = viewGroup.getChildCount(); i < childCount; ++i) {
            View child = viewGroup.getChildAt(i);
            Object childTag = child.getTag();
            if (childTag != null && childTag.equals(TAG_FLEXIBLE_SPACE_SCROLLABLE_CHILD)) {
                if (!(child instanceof FlexibleSpaceView || child instanceof ScrollView
                        || child instanceof NestedScrollView)) {
                    throw new IllegalStateException("Child at index " + i
                            + " must be an instance of " + FlexibleSpaceView.class.getSimpleName()
                            + ", " + ScrollView.class.getSimpleName() + " or "
                            + NestedScrollView.class.getSimpleName() + ".");
                }
                mScrollableChildren.add(child);
            }
            if (child instanceof ViewGroup) {
                addScrollableChildren((ViewGroup) child);
            }
        }
    }

    public void scrollBy(int delta) {
        scrollTo(mScroll + delta);
    }

    public void scrollTo(int scroll) {
        if (mScroll == scroll) {
            return;
        }
        // FIXME: mScrollingChildIndex == -1
        for (int indexMax = mScrollableChildren.size() - 1, step = scroll - mScroll > 0 ? 1 : -1; ;
             mScrollingChildIndex += step) {
            mScroll += scrollChildBy(mScrollableChildren.get(mScrollingChildIndex),
                    scroll - mScroll);
            if (mScroll == scroll) {
                break;
            } else if (mScrollingChildIndex == 0 || mScrollingChildIndex == indexMax) {
                break;
            }
        }
    }

    private int scrollChildBy(View child, int delta) {
        if (child instanceof FlexibleSpaceView) {
            FlexibleSpaceView flexibleSpaceView = ((FlexibleSpaceView) child);
            int oldScroll = flexibleSpaceView.getScroll();
            flexibleSpaceView.scrollBy(delta);
            return flexibleSpaceView.getScroll() - oldScroll;
        } else if (child instanceof ScrollView || child instanceof NestedScrollView) {
            int oldScrollY = child.getScrollY();
            child.scrollBy(0, delta);
            return child.getScrollY() - oldScrollY;
        } else {
            throw new RuntimeException("Should not reach here.");
        }
    }

    private void fling(float velocity) {
        // From AOSP MultiShrinkScroller
        // TODO: Is this true?
        // For reasons I do not understand, scrolling is less janky when maxY=Integer.MAX_VALUE
        // then when maxY is set to an actual value.
        mScroller.fling(0, mScroll, 0, (int) velocity, 0, 0, -Integer.MAX_VALUE, Integer.MAX_VALUE);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {

        switch (MotionEventCompat.getActionMasked(event)) {

            case MotionEvent.ACTION_DOWN:
                if (!mScroller.isFinished()) {
                    return true;
                    // updateActivePointerId(event) and clearVelocityTrackerIfHas() should be called
                    // in onTouchEvent().
                } else {
                    updateActivePointerId(event);
                    clearVelocityTrackerIfHas();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mIsBeingDragged) {
                    return true;
                } else if (Math.abs(getMotionEventY(event) - mLastMotionY) > mTouchSlop) {
                    return true;
                }
                break;

            case MotionEventCompat.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onPointerUp(event);
                break;
        }

        // updateLastMotion() is called here if the touch event is not to be intercepted, so
        // otherwise it should always be called in onTouchEvent().
        updateLastMotion(event);

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (MotionEventCompat.getActionMasked(event)) {

            case MotionEvent.ACTION_DOWN:
                updateActivePointerId(event);
                clearVelocityTrackerIfHas();
                if (!mIsBeingDragged) {
                    startDrag();
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                float deltaY = getMotionEventY(event) - mLastMotionY;
                if (!mIsBeingDragged && Math.abs(deltaY) > mTouchSlop) {
                    startDrag();
                    if (deltaY > 0) {
                        deltaY -= mTouchSlop;
                    } else {
                        deltaY += mTouchSlop;
                    }
                }
                if (mIsBeingDragged) {
                    int oldScroll = mScroll;
                    scrollBy((int) -deltaY);
                    deltaY += mScroll - oldScroll;
                    if (deltaY < 0) {
                        mEdgeEffectBottom.onPull(-deltaY / getHeight(),
                                1f - getMotionEventX(event) / getWidth());
                        if (!mEdgeEffectBottom.isFinished()) {
                            ViewCompat.postInvalidateOnAnimation(this);
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
                endDrag(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                endDrag(true);
                break;

            case MotionEventCompat.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onPointerUp(event);
                break;
        }

        updateLastMotion(event);

        return true;
    }

    private void onPointerDown(MotionEvent event) {
        int pointerIndex = MotionEventCompat.getActionIndex(event);
        mActivePointerId = MotionEventCompat.getPointerId(event, pointerIndex);
        mLastMotionY = MotionEventCompat.getY(event, pointerIndex);
    }

    private void onPointerUp(MotionEvent event) {
        int pointerIndex = MotionEventCompat.getActionIndex(event);
        int pointerId = MotionEventCompat.getPointerId(event, pointerIndex);
        if (pointerId == mActivePointerId) {
            int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = MotionEventCompat.getPointerId(event, newPointerIndex);
            mLastMotionY = MotionEventCompat.getY(event, newPointerIndex);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (MotionEventCompat_isFromSource(event, InputDeviceCompat.SOURCE_CLASS_POINTER)) {
            if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                if (!mIsBeingDragged) {
                    float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (vscroll != 0) {
                        float deltaY = vscroll * View_getScrollFactor();
                        int oldScrollY = getScrollY();
                        scrollBy(0, (int) -deltaY);
                        return getScrollY() != oldScrollY;
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void startDrag() {
        mScroller.abortAnimation();
        requestParentDisallowInterceptTouchEventIfHas(true);
        mIsBeingDragged = true;
    }

    private void endDrag(boolean cancelled) {

        if (!mIsBeingDragged) {
            return;
        }
        mIsBeingDragged = false;

        mEdgeEffectBottom.onRelease();

        int flingDelta = 0;
        if (!cancelled) {
            float velocity = getCurrentVelocity();
            if (Math.abs(velocity) > mMinimumFlingVelocity) {
                fling(-velocity);
                flingDelta = mScroller.getFinalY() - mScroller.getStartY();
            }
        }
        onDragEnd(flingDelta);

        mActivePointerId = INVALID_POINTER_ID;
        recycleVelocityTrackerIfHas();
    }

    private void onDragEnd(int flingDelta) {
        // TODO
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int oldScroll = mScroll;
            int scrollerCurrY = mScroller.getCurrY();
            scrollTo(scrollerCurrY);
            if (mScroll > oldScroll && scrollerCurrY > mScroll) {
                mEdgeEffectBottom.onAbsorb((int) mScroller.getCurrVelocity());
                mScroller.abortAnimation();
            }
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (!mEdgeEffectBottom.isFinished()) {
            int count = canvas.save();
            int width = getWidth();
            int height = getHeight();
            canvas.translate(-width, height);
            canvas.rotate(180, getWidth(), 0);
            mEdgeEffectBottom.setSize(width, height);
            if (mEdgeEffectBottom.draw(canvas)) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
            canvas.restoreToCount(count);
        }
    }

    private void updateActivePointerId(MotionEvent event) {
        // ACTION_DOWN always refers to pointer index 0.
        mActivePointerId = MotionEventCompat.getPointerId(event, 0);
    }

    private void updateLastMotion(MotionEvent event) {
        mLastMotionY = getMotionEventY(event);
        ensureVelocityTracker().addMovement(event);
    }

    private float getMotionEventX(MotionEvent event) {
        if (mActivePointerId != INVALID_POINTER_ID) {
            int pointerIndex = MotionEventCompat.findPointerIndex(event,
                    mActivePointerId);
            if (pointerIndex != -1) {
                return MotionEventCompat.getX(event, pointerIndex);
            } else {
                // Error!
            }
        }
        return event.getX();
    }

    private float getMotionEventY(MotionEvent event) {
        if (mActivePointerId != INVALID_POINTER_ID) {
            int pointerIndex = MotionEventCompat.findPointerIndex(event,
                    mActivePointerId);
            if (pointerIndex != -1) {
                return MotionEventCompat.getY(event, pointerIndex);
            } else {
                // Error!
            }
        }
        return event.getY();
    }

    private VelocityTracker ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        return mVelocityTracker;
    }

    private void clearVelocityTrackerIfHas() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTrackerIfHas() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private float getCurrentVelocity() {
        if (mVelocityTracker == null) {
            return 0;
        }
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
        return mVelocityTracker.getYVelocity(mActivePointerId);
    }

    private void requestParentDisallowInterceptTouchEventIfHas(boolean disallowIntercept) {
        ViewParent viewParent = getParent();
        if (viewParent != null) {
            viewParent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private float View_getScrollFactor() {
        if (mView_verticalScrollFactor == Float.MIN_VALUE) {
            Context context = getContext();
            TypedValue outValue = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight,
                    outValue, true)) {
                mView_verticalScrollFactor = outValue.getDimension(
                        context.getResources().getDisplayMetrics());
            } else {
                throw new IllegalStateException(
                        "Expected theme to define listPreferredItemHeight.");
            }

        }
        return mView_verticalScrollFactor;
    }

    private boolean MotionEventCompat_isFromSource(MotionEvent event, int source) {
        return (MotionEventCompat.getSource(event) & source) == source;
    }
}

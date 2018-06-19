package org.snapgrub.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class CellView extends View {

    private static final String TAG = "CellView";

    private static final int TRANSPARENT = 0x00000000;

    private Paint mMarginPaint = new Paint();
    private Paint mBlankPaint = new Paint();
    private Paint mTextPaint = new Paint();

    private Rect mRect = new Rect();

    private CellData mData;

    public interface Listener {
        void onCellTouchDown(CellView cellView);
        void onCellViewportUpdate();
    }

    private Listener mListener;

    private float mDownX0;
    private float mDownY0;
    private float mDownX1;
    private float mDownY1;
    private Point mDragOffset = new Point();
    private float mPinchScale = 1;
    private boolean mDragInProgress;
    private boolean mPinchInProgress;

    public CellView(Context context) {
        super(context);
    }

    public CellView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CellView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CellView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mListener.onCellTouchDown(this);
        }

        if (!mData.hasImage()) {
            return super.dispatchTouchEvent(ev);
        }

        int pointerCount = ev.getPointerCount();
        float x0 = (int) ev.getX(0);
        float y0 = (int) ev.getY(0);

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                mDownX0 = x0;
                mDownY0 = y0;
                mDragInProgress = true;
                mDragOffset.set(0, 0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN: {
                final float dragDist = distance(0, 0, mDragOffset.x, mDragOffset.y);
                // Do not start the pinch if the drag is already too significant.
                if (dragDist < 5) {
                    if (mDragInProgress) {
                        mDragInProgress = false;
                        mDragOffset.set(0, 0);
                        invalidate();
                    }
                    if (pointerCount == 2) {
                        mDownX1 = ev.getX(1);
                        mDownY1 = ev.getX(1);
                        mPinchInProgress = true;
                        mPinchScale = 1;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE:
                if (mDragInProgress && pointerCount == 1) {
                    mData.computeOffset(mDragOffset, mDownX0 - x0, mDownY0 - y0);
                    invalidate();
                } else if (mPinchInProgress && pointerCount == 2) {
                    float x1 = ev.getX(1);
                    float y1 = ev.getX(1);

                    float distOnDown = distance(mDownX0, mDownY0, mDownX1, mDownY1);
                    float distCurrent = distance(x0, y0, x1, y1);
                    if (distOnDown > 1) {
                        mPinchScale = distCurrent / distOnDown;
                    } else {
                        mPinchScale = 1;
                    }
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mPinchInProgress && pointerCount == 2) {
                    mPinchInProgress = false;
                    mData.applyScale(mPinchScale);
                    mListener.onCellViewportUpdate();
                    mPinchScale = 1;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mDragInProgress) {
                    mDragInProgress = false;
                    mData.applyOffset(mDragOffset);
                    mListener.onCellViewportUpdate();
                    mDragOffset.set(0, 0);
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if (mPinchInProgress) {
                    mPinchInProgress = false;
                    mPinchScale = 1;
                    invalidate();
                }
                if (mDragInProgress) {
                    mDragInProgress = false;
                    mDragOffset.set(0, 0);
                    invalidate();
                }
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mMarginPaint.setStyle(Paint.Style.FILL);
        mMarginPaint.setColor(getResources().getColor(R.color.colorCollageBackground, null));

        mBlankPaint.setStyle(Paint.Style.FILL);
        mBlankPaint.setColor(getResources().getColor(R.color.colorBlankCell, null));

        mTextPaint.setStyle(Paint.Style.STROKE);
        mTextPaint.setColor(getResources().getColor(R.color.colorTimestamp, null));
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.timestampSize));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mRect.set(0, 0, getWidth(), getHeight());
        if (mData == null || !mData.hasImage()) {
            canvas.drawRect(mRect, mBlankPaint);
            return;
        }

        canvas.drawRect(mRect, mMarginPaint);

        canvas.save();
        drawBitmap(canvas);
        canvas.restore();

        String timestamp = mData.getTimestamp();
        if (timestamp == null) {
            timestamp = "n/a";
        } else {
            timestamp = timestamp.split(" ")[1].substring(0, 5);
        }
        mTextPaint.getTextBounds(timestamp, 0, timestamp.length(), mRect);
        float margin = getResources().getDimensionPixelSize(R.dimen.timestampMargin);
        canvas.drawText(timestamp, getWidth() - margin - mRect.width(), margin + mRect.height(), mTextPaint);
    }

    private void drawBitmap(Canvas canvas) {
        final int viewPivotX = getWidth() / 2;
        final int viewPivotY = getHeight() / 2;
        canvas.rotate(mData.getRotation() * 90, viewPivotX, viewPivotY);

        float scale = mData.getScale() * mPinchScale;
        canvas.scale(scale, scale, viewPivotX, viewPivotY);

        Log.e(TAG, "onDraw canvas:" + mRect + ", bitmap:" + mData.getBitmap().getWidth() + "x" + mData.getBitmap().getHeight());

        canvas.drawBitmap(
                mData.getBitmap(),
                viewPivotX - (mData.getPivotX() + (mDragOffset == null ? 0 : mDragOffset.x)),
                viewPivotY - (mData.getPivotY() + (mDragOffset == null ? 0 : mDragOffset.y)),
                null);
    }

    public void bind(CellData data, Listener listener) {
        mData = data;
        mListener = listener;
        invalidate();
    }

    public void highlight(boolean on) {
        View parent = (View) getParent();
        if (on) {
            parent.setBackgroundColor(getResources().getColor(R.color.colorCellBorder, null));
        } else {
            parent.setBackgroundColor(TRANSPARENT);
        }
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
    }
}

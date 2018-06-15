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

    private float mDownX;
    private float mDownY;
    private float mDownX2;
    private float mDownY2;
    private Point mDragOffset;

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
        Log.e("VLAD", ev.toString());
        final int action = ev.getActionMasked();
        float x = (int) ev.getX();
        float y = (int) ev.getY();

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                getMainActivity().activateCell(this);
                if (mData.hasImage()) {
                    mDownX = x;
                    mDownY = y;
                    mDragOffset = new Point(0, 0);
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (mDragOffset != null) {
                    mDragOffset = null;
                    invalidate();
                }
                if (ev.getPointerCount() == 2) {
                    mDownX2 = ev.getX(1);
                    mDownY2 = ev.getX(1);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (ev.getPointerCount() == 1) {
                    if (mDragOffset != null) {
                        mData.computeOffset(mDragOffset, mDownX - x, mDownY - y);
                        invalidate();
                    }
                } else if (ev.getPointerCount() == 2) {
                    float x2 = ev.getX(1);
                    float y2 = ev.getX(1);
                    float xDiffBase = Math.abs(mDownX2 - mDownX);
                    float yDiffBase = Math.abs(mDownY2 - mDownY);
                    float xDiff = Math.abs(x2 - x);
                    float yDiff = Math.abs(y2 - y);
                    Log.e("VLAD", "Pinch: " + ((xDiff + yDiff) / (xDiffBase + yDiffBase)));
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mDragOffset != null) {
                    mData.applyOffset(mDragOffset);
                    mDragOffset = null;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if (mDragOffset != null) {
                    mDragOffset = null;
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

        float scale = mData.getScale();
        canvas.scale(scale, scale, viewPivotX, viewPivotY);

        Log.e(TAG, "onDraw canvas:" + mRect + ", bitmap:" + mData.getBitmap().getWidth() + "x" + mData.getBitmap().getHeight());

        canvas.drawBitmap(
                mData.getBitmap(),
                viewPivotX - (mData.getPivotX() + (mDragOffset == null ? 0 : mDragOffset.x)),
                viewPivotY - (mData.getPivotY() + (mDragOffset == null ? 0 : mDragOffset.y)),
                null);
    }

    public void bind(CellData data) {
        mData = data;
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

    private MainActivity getMainActivity() {
        return (MainActivity) getContext();
    }
}

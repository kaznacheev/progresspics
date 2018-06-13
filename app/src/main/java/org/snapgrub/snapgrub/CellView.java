package org.snapgrub.snapgrub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class CellView extends View {

    private static final String TAG = "CellView";

    private static final int BORDER_COLOR = 0xFF338833;
    private static final int TRANSPARENT = 0x00000000;

    private int mIndex;

    private int mRotation = 0;
    private int mOffsetX = 0;
    private int mOffsetY = 0;
    private float mScale = 1;

    Bitmap mBitmap;

    private Paint mMarginPaint = new Paint();
    private Paint mBlankPaint = new Paint();
    private Rect mRect = new Rect();

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
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                getMainActivity().activateCell(mIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mMarginPaint.setStyle(Paint.Style.FILL);
        mMarginPaint.setColor(getResources().getColor(R.color.colorCollageBackground , null));

        mBlankPaint.setStyle(Paint.Style.FILL);
        mBlankPaint.setColor(getResources().getColor(R.color.colorBlankCell , null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int canvasWidth = getWidth();
        int canvasHeight = getHeight();
        mRect.set(0, 0, canvasWidth, canvasHeight);
        if (mBitmap == null) {
            canvas.drawRect(mRect, mBlankPaint);
            return;
        }

        canvas.drawRect(mRect, mMarginPaint);

        int bitmapWidth = mBitmap.getWidth();
        int bitmapHeight = mBitmap.getHeight();
        canvas.rotate(mRotation * 90, canvasWidth / 2, canvasHeight / 2);

        float fitScaleX = canvasWidth * 1f / bitmapWidth;
        float fitScaleY = canvasHeight * 1f / bitmapHeight;
        float fitScale = Math.max(fitScaleX, fitScaleY);
        canvas.scale(fitScale, fitScale, canvasWidth / 2, canvasHeight / 2);

        Log.e(TAG, "onDraw canvas:" + mRect + ", bitmap:" + ((mBitmap != null)?(bitmapWidth + "x" + bitmapHeight): null));

        canvas.drawBitmap(mBitmap, (canvasWidth - bitmapWidth) / 2, (canvasHeight - bitmapHeight) / 2, null);
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void highlight(boolean on) {
        View parent = (View) getParent();
        if (on) {
            parent.setBackgroundColor(BORDER_COLOR);
        } else {
            parent.setBackgroundColor(TRANSPARENT);
        }
    }

    public void setImage(Bitmap bitmap) {
        mBitmap = bitmap;
        mRotation = 0;
        invalidate();
    }


    private MainActivity getMainActivity() {
        return (MainActivity) getContext();
    }

    public void rotateImage() {
        mRotation++;
        mRotation %= 4;
        invalidate();
    }
}

package org.snapgrub.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class CellView extends View {

    private static final String TAG = "CellView";

    private static final int TRANSPARENT = 0x00000000;

    private int mRotation = 0;

    private Paint mMarginPaint = new Paint();
    private Paint mBlankPaint = new Paint();
    private Paint mTextPaint = new Paint();

    private Rect mRect = new Rect();
    private CellData mData;

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
                getMainActivity().activateCell(this);
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
        int canvasWidth = getWidth();
        int canvasHeight = getHeight();
        mRect.set(0, 0, canvasWidth, canvasHeight);
        if (mData == null || mData.getBitmap() == null) {
            canvas.drawRect(mRect, mBlankPaint);
            return;
        }

        Bitmap bitmap = mData.getBitmap();
        canvas.drawRect(mRect, mMarginPaint);

        canvas.save();
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        canvas.rotate(mRotation * 90, canvasWidth / 2, canvasHeight / 2);

        float fitScaleX = canvasWidth * 1f / bitmapWidth;
        float fitScaleY = canvasHeight * 1f / bitmapHeight;
        float fitScale = Math.max(fitScaleX, fitScaleY);
        canvas.scale(fitScale, fitScale, canvasWidth / 2, canvasHeight / 2);

        Log.e(TAG, "onDraw canvas:" + mRect + ", bitmap:" + (bitmapWidth + "x" + bitmapHeight));

        canvas.drawBitmap(bitmap, (canvasWidth - bitmapWidth) / 2, (canvasHeight - bitmapHeight) / 2, null);
        canvas.restore();

        String timestamp = mData.getTimestamp();
        mTextPaint.getTextBounds(timestamp, 0, timestamp.length(), mRect);
        float margin = getResources().getDimensionPixelSize(R.dimen.timestampMargin);
        canvas.drawText(timestamp, canvasWidth - margin - mRect.width(), margin + mRect.height(), mTextPaint);
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

    public boolean setImage(Uri uri) {
        mData.loadFromUri(uri, getContext().getContentResolver());
        mRotation = 0;
        invalidate();
        return mData.getBitmap() != null;
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

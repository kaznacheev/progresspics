package org.snapgrub.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CellView extends View {

    private static final String TAG = "CellView";

    private static final int TRANSPARENT = 0x00000000;

    private int mIndex;

    private int mRotation = 0;
    private int mOffsetX = 0;
    private int mOffsetY = 0;
    private float mScale = 1;

    Bitmap mBitmap;

    private Paint mMarginPaint = new Paint();
    private Paint mBlankPaint = new Paint();
    private Paint mTextPaint = new Paint();

    private Rect mRect = new Rect();
    private String mTime;

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
        if (mBitmap == null) {
            canvas.drawRect(mRect, mBlankPaint);
            return;
        }

        canvas.drawRect(mRect, mMarginPaint);

        canvas.save();
        int bitmapWidth = mBitmap.getWidth();
        int bitmapHeight = mBitmap.getHeight();
        canvas.rotate(mRotation * 90, canvasWidth / 2, canvasHeight / 2);

        float fitScaleX = canvasWidth * 1f / bitmapWidth;
        float fitScaleY = canvasHeight * 1f / bitmapHeight;
        float fitScale = Math.max(fitScaleX, fitScaleY);
        canvas.scale(fitScale, fitScale, canvasWidth / 2, canvasHeight / 2);

        Log.e(TAG, "onDraw canvas:" + mRect + ", bitmap:" + ((mBitmap != null)?(bitmapWidth + "x" + bitmapHeight): null));

        canvas.drawBitmap(mBitmap, (canvasWidth - bitmapWidth) / 2, (canvasHeight - bitmapHeight) / 2, null);
        canvas.restore();

        mTextPaint.getTextBounds(mTime, 0, mTime.length(), mRect);
        float margin = getResources().getDimensionPixelSize(R.dimen.timestampMargin);
        canvas.drawText(mTime, canvasWidth - margin - mRect.width(), margin + mRect.height(), mTextPaint);
    }

    public void setIndex(int index) {
        mIndex = index;
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
        mBitmap = readScaledBitmap(uri);
        mRotation = 0;
        invalidate();
        return mBitmap != null;
    }

    private Bitmap readScaledBitmap(Uri uri) {
        try {
            ExifInterface exif = new ExifInterface(getContext().getContentResolver().openInputStream(uri));
            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp != null) {
                mTime = timestamp.split(" ")[1].substring(0, 5);
            } else {
                mTime = new SimpleDateFormat("HH:mm").format(new Date());
            }

            int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
            int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            if (width <= 0 || height <= 0) {
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(uri), null, bmOptions);
                width = bmOptions.outWidth;
                height = bmOptions.outHeight;
            }

            final int targetSize = 512;
            bmOptions.inSampleSize = Math.min(width, height) / targetSize;
            bmOptions.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(uri), null, bmOptions);
        } catch (Exception ignore) {
            return null;
        }
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

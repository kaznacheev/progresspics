package org.progresspics.android;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.BaseBundle;

public class CellData {
    private static final String KEY_URI = "uri";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_ROTATION = "rotation";
    private static final String KEY_PIVOT_X = "pivot_x";
    private static final String KEY_PIVOT_Y = "pivot_y";
    private static final String KEY_SCALE = "scale";

    private Uri mUri;
    private Bitmap mBitmap;
    private String mTimestamp;
    private String mDate;
    private String mTime;
    private int mRotation;
    private int mPivotX;
    private int mPivotY;
    private float mScale;

    public CellData() {}

    public void load(Uri uri, ContentResolver resolver) {
        mUri = uri;
        mBitmap = null;
        if (mUri == null) {
            return;
        }
        try {
            mBitmap = BitmapFactory.decodeStream(resolver.openInputStream(mUri));
        } catch (Exception ignore) {
        }
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public boolean hasImage() {
        return mBitmap != null;
    }

    public String getTimestamp() {
        return mTimestamp;
    }

    public String getDate() {
        return mDate;
    }

    public String getTime() {
        return mTime;
    }

    public void setTimestamp(String timestamp) {
        mTimestamp = timestamp;
        if (mTimestamp != null) {
            final String[] tokens = timestamp.split(" " );
            mDate = tokens[0].replace(':', '/');
            mTime = tokens[1].substring(0, 5);
        }
    }

    public int getRotation() {
        return mRotation;
    }

    public void rotate(int direction) {
        mRotation += (direction > 0 ? 1 : 3);
        mRotation %= 4;
    }


    public void scaleToFit(int width, int height) {
        mPivotX = mBitmap.getWidth() / 2;
        mPivotY = mBitmap.getHeight() / 2;
        if (mRotation % 2 == 0) {
            mScale = Math.min(width * 1f / mBitmap.getWidth(), height * 1f / mBitmap.getHeight());
        } else {
            mScale = Math.min(height * 1f / mBitmap.getWidth(), width * 1f / mBitmap.getHeight());
        }
    }

    public void scaleToFill(int width, int height) {
        mPivotX = mBitmap.getWidth() / 2;
        mPivotY = mBitmap.getHeight() / 2;
        if (mRotation % 2 == 0) {
            mScale = Math.max(width * 1f / mBitmap.getWidth(), height * 1f / mBitmap.getHeight());
        } else {
            mScale = Math.max(height * 1f / mBitmap.getWidth(), width * 1f / mBitmap.getHeight());
        }
    }

    public int getPivotX() {
        return mPivotX;
    }

    public int getPivotY() {
        return mPivotY;
    }

    public float getScale() {
        return mScale;
    }

    public void applyScale(float scale) {
        mScale *= scale;
    }

    public void computeOffset(Point outImageOffset, float screenOffsetX, float screenOffsetY) {
        final int imageX = (int) (screenOffsetX / mScale);
        final int imageY = (int) (screenOffsetY / mScale);

        switch (mRotation) {
            case 0:
                outImageOffset.set(imageX, imageY);
                break;

            case 1:
                //noinspection SuspiciousNameCombination
                outImageOffset.set(imageY, -imageX);
                break;

            case 2:
                outImageOffset.set(-imageX, -imageY);
                break;

            case 3:
                //noinspection SuspiciousNameCombination
                outImageOffset.set(-imageY, imageX);
                break;
        }
    }

    public void applyOffset(Point offset) {
        mPivotX += offset.x;
        mPivotY += offset.y;
    }

    public void restoreState(BaseBundle b, ContentResolver contentResolver) {
        final String uri = b.getString(KEY_URI);
        load(uri != null ? Uri.parse(uri) : null, contentResolver);
        setTimestamp(b.getString(KEY_TIMESTAMP));
        mRotation = b.getInt(KEY_ROTATION);
        mPivotX = b.getInt(KEY_PIVOT_X);
        mPivotY = b.getInt(KEY_PIVOT_Y);
        mScale = (float) b.getDouble(KEY_SCALE);
    }

    public void saveState(BaseBundle b) {
        if (mUri != null) {
            b.putString(KEY_URI, mUri.toString());
        }
        b.putString(KEY_TIMESTAMP, mTimestamp);
        b.putInt(KEY_ROTATION, mRotation);
        b.putInt(KEY_PIVOT_X, mPivotX);
        b.putInt(KEY_PIVOT_Y, mPivotY);
        b.putDouble(KEY_SCALE, mScale);
    }
}

package org.progresspics.android;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.BaseBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;
import android.util.Log;

import java.io.File;

import static android.support.v4.content.FileProvider.getUriForFile;

class CellData {
    private static final String KEY_URI = "uri";
    private static final String KEY_DATE = "date";
    private static final String KEY_TIME = "time";
    private static final String KEY_ROTATION = "rotation";
    private static final String KEY_PIVOT_X = "pivot_x";
    private static final String KEY_PIVOT_Y = "pivot_y";
    private static final String KEY_SCALE = "scale";

    @NonNull
    private final Uri mUri;

    @NonNull
    private final Bitmap mBitmap;

    @NonNull
    private final String mDate;

    @NonNull
    private final String mTime;

    private int mRotation;  // In 90 degree increments.
    private int mPivotX;
    private int mPivotY;
    private float mScale = 1;

    private CellData(@NonNull Uri uri, @NonNull Bitmap bitmap, @NonNull String date,
                     @NonNull String time) {
        mUri = uri;
        mBitmap = bitmap;
        mDate = date;
        mTime = time;
        resetPivot();
    }

    @NonNull Bitmap getBitmap() {
        return mBitmap;
    }

    @NonNull String getDate() {
        return mDate;
    }

    @NonNull String getTime() {
        return mTime;
    }

    @NonNull String getDateTime() {
        return mDate + " " + mTime;
    }

    int getRotationInDegrees() {
        return mRotation * 90;
    }

    void rotate(int direction) {
        mRotation += (direction > 0 ? 1 : 3);
        mRotation %= 4;
    }

    void scaleToFit(int width, int height) {
        resetPivot();
        if (mRotation % 2 == 0) {
            mScale = Math.min(width * 1f / mBitmap.getWidth(), height * 1f / mBitmap.getHeight());
        } else {
            mScale = Math.min(height * 1f / mBitmap.getWidth(), width * 1f / mBitmap.getHeight());
        }
    }

    void scaleToFill(int width, int height) {
        resetPivot();
        if (mRotation % 2 == 0) {
            mScale = Math.max(width * 1f / mBitmap.getWidth(), height * 1f / mBitmap.getHeight());
        } else {
            mScale = Math.max(height * 1f / mBitmap.getWidth(), width * 1f / mBitmap.getHeight());
        }
    }

    private void resetPivot() {
        mPivotX = mBitmap.getWidth() / 2;
        mPivotY = mBitmap.getHeight() / 2;
    }

    int getPivotX() {
        return mPivotX;
    }

    int getPivotY() {
        return mPivotY;
    }

    float getScale() {
        return mScale;
    }

    void adjustScale(float scale) {
        mScale *= scale;
    }

    void computeOffset(Point outImageOffset, float screenOffsetX, float screenOffsetY) {
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

    void adjustPivot(Point offset) {
        mPivotX += offset.x;
        mPivotY += offset.y;
    }

    private void saveViewport(BaseBundle b) {
        b.putInt(KEY_ROTATION, mRotation);
        b.putInt(KEY_PIVOT_X, mPivotX);
        b.putInt(KEY_PIVOT_Y, mPivotY);
        b.putDouble(KEY_SCALE, mScale);
    }

    private void restoreViewport(BaseBundle b) {
        mRotation = b.getInt(KEY_ROTATION);
        mPivotX = b.getInt(KEY_PIVOT_X);
        mPivotY = b.getInt(KEY_PIVOT_Y);
        mScale = (float) b.getDouble(KEY_SCALE);
    }

    void save(BaseBundle b) {
        b.putString(KEY_URI, mUri.toString());
        b.putString(KEY_DATE, mDate);
        b.putString(KEY_TIME, mTime);
        saveViewport(b);
    }

    static CellData fromBundle(BaseBundle b, ContentResolver resolver) {
        final String uriString = b.getString(KEY_URI);
        if (uriString == null) {
            return null;
        }
        final String date = b.getString(KEY_DATE);
        if (date == null) {
            return null;
        }
        final String time = b.getString(KEY_TIME);
        if (time == null) {
            return null;
        }
        try {
            Uri uri = Uri.parse(uriString);
            final Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri));
            if (bitmap == null) {
                return null;
            }
            CellData cellData = new CellData( uri, bitmap, date, time);
            cellData.restoreViewport(b);
            return cellData;
        } catch (Exception ignore) {
            return null;
        }
    }

    @Nullable
    static CellData fromUri(Uri source, File cache, Context context) {
        try {
            final ContentResolver resolver = context.getContentResolver();

            final ExifInterface exif = new ExifInterface(resolver.openInputStream(source));

            int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
            int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            if (width <= 0 || height <= 0) {
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions);
                width = bmOptions.outWidth;
                height = bmOptions.outHeight;
            }

            bmOptions.inSampleSize = Math.min(width, height) / MainActivity.CACHED_IMAGE_SIZE_LIMIT;
            bmOptions.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream(
                    resolver.openInputStream(source), null, bmOptions);
            if (bitmap == null) {
                return null;
            }

            if (!Util.saveBitmap(cache, bitmap, MainActivity.JPEG_QUALITY)) {
                return null;
            }

            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp == null || timestamp.isEmpty()) {
                Log.w(MainActivity.LOG_TAG, "No timestamp found in " + source);
                timestamp = Util.getExifTimestamp();
            }
            final String date;
            final String time;
            final String[] tokens = timestamp.split(" " );
            if (tokens.length == 2) {
                date = tokens[0].replace(':', '/');
                time = tokens[1].substring(0, 5);
            } else {
                date = "";
                time = "";
            }

            return new CellData(getUriForFile(context, MainActivity.AUTHORITY, cache), bitmap, date, time);
        } catch (Exception e) {
            Util.reportException(e);
            return null;
        }
    }
}

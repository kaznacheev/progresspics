package org.snapgrub.android;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CellData {
    private final static int SIZE_LIMIT = 512;

    private Uri mUri;
    private Bitmap mBitmap;
    private String mTimestamp;


    public CellData() {

    }

    public void loadFromUri(Uri source, ContentResolver contentResolver) {
        mUri = source;
        mBitmap = null;
        mTimestamp = null;
        if (mUri == null) {
            return;
        }

        try {
            ExifInterface exif = new ExifInterface(contentResolver.openInputStream(source));
            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp != null) {
                mTimestamp = timestamp.split(" ")[1].substring(0, 5);
            } else {
                mTimestamp = new SimpleDateFormat("HH:mm").format(new Date());
            }

            int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
            int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            if (width <= 0 || height <= 0) {
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(contentResolver.openInputStream(source), null, bmOptions);
                width = bmOptions.outWidth;
                height = bmOptions.outHeight;
            }

            bmOptions.inSampleSize = Math.min(width, height) / SIZE_LIMIT;
            bmOptions.inJustDecodeBounds = false;
            mBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(source), null, bmOptions);
        } catch (Exception ignore) {
        }
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public String getTimestamp() {
        return mTimestamp;
    }

    public void restoreState(Bundle b, ContentResolver contentResolver) {
        loadFromUri(b.getParcelable("uri"), contentResolver);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putParcelable("uri", mUri);
        return b;
    }
}

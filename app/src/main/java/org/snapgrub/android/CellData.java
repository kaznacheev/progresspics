package org.snapgrub.android;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

public class CellData {
    private Uri mUri;
    private Bitmap mBitmap;
    private String mTimestamp;
    private int mRotation;

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

    public String getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(String timestamp) {
        mTimestamp = timestamp;
    }

    public int getRotation() {
        return mRotation;
    }

    public void rotate() {
        mRotation++;
        mRotation %= 4;
    }

    public void restoreState(Bundle b, ContentResolver contentResolver) {
        load(b.getParcelable("uri"), contentResolver);
        mRotation = b.getInt("rotation");
        mTimestamp = b.getString("timestamp");
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putParcelable("uri", mUri);
        b.putString("timestamp", mTimestamp);
        b.putInt("rotation", mRotation);
        return b;
    }
}

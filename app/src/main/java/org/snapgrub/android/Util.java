package org.snapgrub.android;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

class Util {

    @Nullable
    static File getFile(File root, String dirName, String fileName) {
        File dir = new File(root, dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            reportError("Failed to create " + dir);
            return null;
        }
        return new File(dir, fileName);
    }

    @Nullable
    static File getUniqueImageFile(File root, String dirName, String filePrefix) {
        File file = getFile(root, dirName, filePrefix + "_" + getTimestamp() + ".jpg");
        if (file == null) {
            return null;
        }
        if (file.exists() && !file.delete()) {
            reportError("Failed to delete " + file);
            return null;
        }
        return file;
    }

    @NonNull
    private static String getTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    static void writeBundle(File file, PersistableBundle bundle) {
        final Parcel parcel = Parcel.obtain();
        bundle.writeToParcel(parcel, 0);
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            fOut.write(parcel.marshall());
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            reportException(e);
        }
        parcel.recycle();
    }

    @SuppressLint("ParcelClassLoader")
    static PersistableBundle readBundle(File file) {
        Parcel parcel = Parcel.obtain();
        try {
            FileInputStream fIn = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int readBytes = fIn.read(data);
            fIn.close();
            if (readBytes != data.length) {
                reportError("State file too short");
                return null;
            }
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            return parcel.readPersistableBundle();
        } catch (IOException e) {
            reportException(e);
            return null;
        } finally {
            parcel.recycle();
        }
    }

    static void saveBitmap(File file, Bitmap bitmap, int quality) {
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            reportException(e);
        }
    }

    static void reportError(String message) {
        Log.e(MainActivity.LOG_TAG, message);
    }

    static void reportException(Exception e) {
        reportError(e.getMessage());
        e.printStackTrace();
    }
}

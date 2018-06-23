package org.progresspics.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Parcel
import android.os.PersistableBundle
import android.support.media.ExifInterface
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

internal object Util {

    private val timestamp: String
        @SuppressLint("SimpleDateFormat")
        get() = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

    val exifTimestamp: String
        @SuppressLint("SimpleDateFormat")
        get() = SimpleDateFormat("YYYY:MM:dd HH:mm:ss").format(Date())

    fun getFile(root: File, dirName: String, fileName: String): File? {
        val dir = File(root, dirName)
        if (!dir.exists() && !dir.mkdirs()) {
            reportError("Failed to create $dir")
            return null
        }
        return File(dir, fileName)
    }

    @JvmOverloads
    fun getTimestampedImageFile(root: File, dirName: String, filePrefix: String, uid: String = ""): File? {
        val file = getFile(
                root, dirName, filePrefix + "_" + timestamp + uid + ".jpg") ?: return null
        if (file.exists() && !file.delete()) {
            reportError("Failed to delete $file")
            return null
        }
        return file
    }

    fun writeBundle(file: File, bundle: PersistableBundle) {
        val parcel = Parcel.obtain()
        bundle.writeToParcel(parcel, 0)
        try {
            val fOut = FileOutputStream(file)
            fOut.write(parcel.marshall())
            fOut.flush()
            fOut.close()
        } catch (e: IOException) {
            reportException(e)
        }

        parcel.recycle()
    }

    @SuppressLint("ParcelClassLoader")
    fun readBundle(file: File): PersistableBundle? {
        val parcel = Parcel.obtain()
        try {
            val fIn = FileInputStream(file)
            val data = ByteArray(file.length().toInt())
            val readBytes = fIn.read(data)
            fIn.close()
            if (readBytes != data.size) {
                reportError("State file too short")
                return null
            }
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            return parcel.readPersistableBundle()
        } catch (e: IOException) {
            reportException(e)
            return null
        } finally {
            parcel.recycle()
        }
    }

    fun saveBitmap(file: File, bitmap: Bitmap, quality: Int): Boolean {
        return try {
            val fOut = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fOut)
            fOut.flush()
            fOut.close()
            true
        } catch (e: IOException) {
            reportException(e)
            false
        }

    }

    fun addExif(file: File) {
        try {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifTimestamp)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "ProgressPics v" + BuildConfig.VERSION_NAME)
            exif.saveAttributes()
        } catch (e: IOException) {
            reportException(e)
        }

    }

    fun reportError(message: String?) {
        Log.e(MainActivity.LOG_TAG, message)
    }

    fun reportException(e: Exception) {
        reportError(e.message)
        e.printStackTrace()
    }
}

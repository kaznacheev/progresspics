package org.progresspics.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.BaseBundle
import android.support.media.ExifInterface
import android.util.Log

import java.io.File

import android.support.v4.content.FileProvider.getUriForFile

class CellData private constructor(private val mUri: Uri, val bitmap: Bitmap, val date: String,
                                            val time: String) {

    var pivotX: Int = 0
        private set
    var pivotY: Int = 0
        private set
    var scale = 1f
        private set

    private var rotation: Int = 0  // In 90 degree increments.

    val rotationInDegrees: Int
        get() = rotation * 90

    val dateTime: String
        get() = "$date $time"

    init {
        resetPivot()
    }

    fun scaleToFit(width: Int, height: Int) {
        resetPivot()
        scale = if (rotation % 2 == 0) {
            Math.min(width * 1f / bitmap.width, height * 1f / bitmap.height)
        } else {
            Math.min(height * 1f / bitmap.width, width * 1f / bitmap.height)
        }
    }

    fun scaleToFill(width: Int, height: Int) {
        resetPivot()
        scale = if (rotation % 2 == 0) {
            Math.max(width * 1f / bitmap.width, height * 1f / bitmap.height)
        } else {
            Math.max(height * 1f / bitmap.width, width * 1f / bitmap.height)
        }
    }

    private fun resetPivot() {
        pivotX = bitmap.width / 2
        pivotY = bitmap.height / 2
    }

    fun computeOffset(outImageOffset: Point, screenOffsetX: Float, screenOffsetY: Float) {
        val imageX = (screenOffsetX / scale).toInt()
        val imageY = (screenOffsetY / scale).toInt()

        when (rotation) {
            0 -> outImageOffset.set(imageX, imageY)
            1 -> outImageOffset.set(imageY, -imageX)
            2 -> outImageOffset.set(-imageX, -imageY)
            3 -> outImageOffset.set(-imageY, imageX)
        }
    }

    fun adjustPivot(offset: Point) {
        pivotX += offset.x
        pivotY += offset.y
    }

    fun adjustScale(scale: Float) {
        this.scale *= scale
    }

    fun adjustRotation(direction: Int) {
        rotation += if (direction > 0) 1 else 3
        rotation %= 4
    }

    private fun saveViewport(b: BaseBundle) {
        b.putInt(KEY_ROTATION, rotation)
        b.putInt(KEY_PIVOT_X, pivotX)
        b.putInt(KEY_PIVOT_Y, pivotY)
        b.putDouble(KEY_SCALE, scale.toDouble())
    }

    private fun restoreViewport(b: BaseBundle) {
        rotation = b.getInt(KEY_ROTATION)
        pivotX = b.getInt(KEY_PIVOT_X)
        pivotY = b.getInt(KEY_PIVOT_Y)
        scale = b.getDouble(KEY_SCALE).toFloat()
    }

    fun save(b: BaseBundle) {
        b.putString(KEY_URI, mUri.toString())
        b.putString(KEY_DATE, date)
        b.putString(KEY_TIME, time)
        saveViewport(b)
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_DATE = "date"
        private const val KEY_TIME = "time"
        private const val KEY_PIVOT_X = "pivot_x"
        private const val KEY_PIVOT_Y = "pivot_y"
        private const val KEY_SCALE = "scale"
        private const val KEY_ROTATION = "rotation"

        fun fromBundle(b: BaseBundle, resolver: ContentResolver): CellData? {
            val uriString = b.getString(KEY_URI) ?: return null
            val date = b.getString(KEY_DATE) ?: return null
            val time = b.getString(KEY_TIME) ?: return null
            try {
                val uri = Uri.parse(uriString)
                val bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri))
                        ?: return null
                val cellData = CellData(uri, bitmap, date, time)
                cellData.restoreViewport(b)
                return cellData
            } catch (ignore: Exception) {
                return null
            }

        }

        fun fromUri(source: Uri, cache: File, context: Context): CellData? {
            try {
                val resolver = context.contentResolver

                val exif = ExifInterface(resolver.openInputStream(source)!!)

                var width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)
                var height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)

                val bmOptions = BitmapFactory.Options()
                if (width <= 0 || height <= 0) {
                    bmOptions.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions)
                    width = bmOptions.outWidth
                    height = bmOptions.outHeight
                }

                bmOptions.inSampleSize = Math.min(width, height) / MainActivity.CACHED_IMAGE_SIZE_LIMIT
                bmOptions.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeStream(
                        resolver.openInputStream(source), null, bmOptions) ?: return null

                if (!Util.saveBitmap(cache, bitmap, MainActivity.JPEG_QUALITY)) {
                    return null
                }

                var timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (timestamp == null || timestamp.isEmpty()) {
                    Log.w(MainActivity.LOG_TAG, "No timestamp found in $source")
                    timestamp = Util.exifTimestamp
                }
                val date: String
                val time: String
                val tokens = timestamp.split(" ".toRegex()).toTypedArray()
                if (tokens.size == 2) {
                    date = tokens[0].replace(':', '/')
                    time = tokens[1].substring(0, 5)
                } else {
                    date = ""
                    time = ""
                }

                return CellData(getUriForFile(context, MainActivity.AUTHORITY, cache), bitmap, date, time)
            } catch (e: Exception) {
                Util.reportException(e)
                return null
            }
        }
    }
}

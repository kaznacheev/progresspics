package org.progresspics.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.BaseBundle
import android.support.media.ExifInterface

import java.io.File

import android.support.v4.content.FileProvider.getUriForFile

class CellData private constructor(
        private val mUri: Uri, val bitmap: Bitmap, val timestamp: String) {

    var date: String
    var time: String

    var pivotX: Int = 0
        private set
    var pivotY: Int = 0
        private set
    var scale = 1f
        private set

    private var rotation: Int = 0  // In 90 degree increments.

    val rotationInDegrees: Int
        get() = rotation * 90

    init {
        val tokens = timestamp.split(" ".toRegex()).toTypedArray()
        if (tokens.size == 2) {
            date = tokens[0].replace(':', '/')
            time = tokens[1].substring(0, 5)
        } else {
            date = ""
            time = ""
        }
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

    fun adjustScale(factor: Float) {
        scale *= factor
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
        b.putString(KEY_TIMESTAMP, timestamp)
        saveViewport(b)
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_PIVOT_X = "pivot_x"
        private const val KEY_PIVOT_Y = "pivot_y"
        private const val KEY_SCALE = "scale"
        private const val KEY_ROTATION = "rotation"

        private const val CACHED_IMAGE_SIZE_LIMIT = 1024

        fun fromBundle(b: BaseBundle, resolver: ContentResolver): CellData? {
            val uriString = b.getString(KEY_URI) ?: return null
            val timestamp = b.getString(KEY_TIMESTAMP) ?: return null
            try {
                val uri = Uri.parse(uriString)
                val bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri))
                        ?: return null
                val cellData = CellData(uri, bitmap, timestamp)
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
                    BitmapFactory.decodeStream(
                            resolver.openInputStream(source), null, bmOptions)
                    width = bmOptions.outWidth
                    height = bmOptions.outHeight
                }

                bmOptions.inSampleSize = Math.min(width, height) / CACHED_IMAGE_SIZE_LIMIT
                bmOptions.inJustDecodeBounds = false
                val bitmap = BitmapFactory.decodeStream(
                        resolver.openInputStream(source), null, bmOptions) ?: return null

                if (!Util.saveBitmap(cache, bitmap, MainActivity.JPEG_QUALITY)) {
                    return null
                }

                var timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (timestamp == null || timestamp.isEmpty()) {
                    Util.reportWarning("No timestamp found in $source")
                    timestamp = Util.exifTimestamp
                }

                return CellData(
                        getUriForFile(context, MainActivity.AUTHORITY, cache), bitmap, timestamp)
            } catch (e: Exception) {
                Util.reportException(e)
                return null
            }
        }
    }
}

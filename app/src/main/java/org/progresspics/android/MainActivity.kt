package org.progresspics.android

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.BaseBundle
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import java.io.File
import java.util.ArrayList
import java.util.Arrays
import java.util.Comparator
import java.util.HashSet

import android.support.v4.content.FileProvider.getUriForFile

class MainActivity : AppCompatActivity(), CellView.Listener {

    private var mStateFile: File? = null

    private var mGridView: ViewGroup? = null
    private var mDateView: TextView? = null

    private val mCellData = ArrayList<CellData?>()
    private val mCellText = ArrayList<String?>()
    private val mCellView = ArrayList<CellView>()

    private var mCellsPerRow: IntArray? = null
    private var mActiveCellIndex: Int = 0

    private var mCaptureUri: Uri? = null
    private var mTextEditingOn: Boolean = false  // Not persistable on purpose.

    private val activeCellView: CellView
        get() = mCellView[mActiveCellIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.button_clear).setOnClickListener { clear() }
        findViewById<View>(R.id.button_snap).setOnClickListener { snap() }
        findViewById<View>(R.id.button_pick).setOnClickListener { pick() }
        findViewById<View>(R.id.button_save).setOnClickListener { save() }
        findViewById<View>(R.id.button_share).setOnClickListener { share() }

        findViewById<View>(R.id.button_erase).setOnClickListener { erase() }
        findViewById<View>(R.id.button_rotate_left).setOnClickListener { rotate(1) }
        findViewById<View>(R.id.button_rotate_right).setOnClickListener { rotate(-1) }
        findViewById<View>(R.id.button_text).setOnClickListener { text(it) }
        findViewById<View>(R.id.button_layout).setOnClickListener { pickLayout() }

        findViewById<View>(R.id.button_fit).setOnClickListener { fitCell() }
        findViewById<View>(R.id.button_fill).setOnClickListener { fillCell() }

        mGridView = findViewById(R.id.grid)
        mDateView = findViewById(R.id.date)

        mCellsPerRow = intArrayOf(3, 3, 3)
        mActiveCellIndex = 0

        mStateFile = Util.getFile(filesDir, STATE_DIRECTORY, STATE_FILE)

        val intent = intent
        val action = intent.action

        if (Intent.ACTION_MAIN == action) {
            if (savedInstanceState != null) {
                restoreInstanceState(savedInstanceState)
            } else if (mStateFile!!.exists()) {
                val persistentState = Util.readBundle(mStateFile!!)
                if (persistentState != null) {
                    restoreInstanceState(persistentState)
                }
            }
        } else if (Intent.ACTION_SEND == action) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                mGridView!!.post { importAndLayoutImages(listOf(uri)) }
                return
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (uris != null) {
                mGridView!!.post { importAndLayoutImages(uris) }
                return
            }
        }

        setupCellLayout()
    }

    private fun pickLayout() {
        val dialog = Dialog(this)
        val content = LayoutPicker.inflate(this,
                object: LayoutPicker.Listener {
                    override fun onItemClicked(cellsPerRow: IntArray) {
                        changeLayout(cellsPerRow)
                        dialog.dismiss();
                    }
                })
        dialog.addContentView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.show()
    }

    fun changeLayout(cellsPerRow: IntArray) {
        if (Arrays.equals(mCellsPerRow, cellsPerRow)) {
            return
        }
        mCellsPerRow = cellsPerRow
        setupCellLayout()
        // Delay until after new layout is done.
        mGridView!!.post {
            for (cellView in mCellView) {
                cellView.scaleToFill()
            }
            saveStateToFile()
        }
    }

    override fun onCellActivate(index: Int) {
        activateCell(index)
    }

    override fun onCellViewportUpdate(index: Int) {
        saveStateToFile()
    }

    override fun onCellTextUpdate(index: Int, text: String) {
        mCellText[index] = text
        saveStateToFile()
    }

    private fun setupCellLayout() {
        if (mTextEditingOn) {
            // Emulate the toggle.
            text(findViewById(R.id.button_text))
        }

        if (mActiveCellIndex < mCellView.size) {
            mCellView[mActiveCellIndex].highlight(false)
        }

        mCellView.clear()

        for (r in 0 until mGridView!!.childCount) {
            val activeRow = r < mCellsPerRow!!.size
            val row = mGridView!!.getChildAt(r) as ViewGroup
            if (activeRow) {
                row.visibility = View.VISIBLE
            } else {
                row.visibility = View.GONE
            }

            for (c in 0 until row.childCount) {
                val activeColumn = activeRow && c < mCellsPerRow!![r]
                val cellWrapper = row.getChildAt(c) as ViewGroup

                val cellView = cellWrapper.getChildAt(0) as CellView
                if (activeColumn) {
                    val index = mCellView.size
                    if (index == mCellData.size) {
                        mCellData.add(null)
                    }
                    if (index == mCellText.size) {
                        mCellText.add(null)
                    }
                    cellView.bind(index, mCellData[index], mCellText[index], this)
                    mCellView.add(cellView)
                    cellWrapper.visibility = View.VISIBLE
                    cellView.isClickable = true
                } else {
                    cellView.bind(-1, null, null, null)
                    cellWrapper.visibility = View.GONE
                }
                cellView.enableTextEditing(false)
            }
            mGridView!!.requestLayout()
        }

        if (mActiveCellIndex >= mCellView.size) {
            mActiveCellIndex = mCellView.size - 1
        }

        mCellView[mActiveCellIndex].highlight(true)

        updateTimestampDisplay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveInstanceState(outState)
    }

    private fun saveInstanceState(outState: BaseBundle) {
        outState.putIntArray(KEY_LAYOUT, mCellsPerRow)
        outState.putInt(KEY_ACTIVE, mActiveCellIndex)
        outState.putInt(KEY_CELLS, mCellData.size)

        for ((c, cellData) in mCellData.withIndex()) {
            val cellKey = getCellKey(c)
            val cellBundle: BaseBundle = if (outState is Bundle) Bundle() else PersistableBundle()
            cellData?.save(cellBundle)
            cellBundle.putString(KEY_TEXT, mCellText[c])
            if (outState is Bundle) {
                outState.putBundle(cellKey, cellBundle as Bundle)
            } else {
                (outState as PersistableBundle).putPersistableBundle(cellKey, cellBundle as PersistableBundle)
            }
        }
    }

    private fun restoreInstanceState(inState: BaseBundle) {
        val cellsPerRow = inState.getIntArray(KEY_LAYOUT)
        mCellsPerRow = cellsPerRow ?: intArrayOf(3, 3, 3)
        mActiveCellIndex = inState.getInt(KEY_ACTIVE, mActiveCellIndex)
        val cellCount = inState.getInt(KEY_CELLS, 0)
        mCellData.clear()

        for (c in 0 until cellCount) {
            val cellKey = getCellKey(c)
            val cellBundle: BaseBundle?
            cellBundle = if (inState is Bundle) {
                inState.getBundle(cellKey)
            } else {
                (inState as PersistableBundle).getPersistableBundle(cellKey)
            }
            var cellData: CellData? = null
            var cellText: String? = ""
            if (cellBundle != null) {
                cellData = CellData.fromBundle(cellBundle, contentResolver)
                cellText = cellBundle.getString(KEY_TEXT)
            }
            mCellData.add(cellData)
            mCellText.add(cellText)
        }
    }

    private fun saveStateToFile() {
        val state = PersistableBundle()
        saveInstanceState(state)
        Util.writeBundle(mStateFile!!, state)
    }

    private fun activateCell(index: Int) {
        if (mActiveCellIndex == index) {
            return
        }
        activeCellView.highlight(false)
        mActiveCellIndex = index
        activeCellView.highlight(true)
        saveStateToFile()
    }

    private fun clear() {
        mCellData.clear()
        mCellText.clear()
        for (cellView in mCellView) {
            cellView.bind(mCellData.size, null, null, this)
            mCellData.add(null)
            mCellText.add(null)
        }
        clearImportedImages()
        activateCell(0)
        saveStateToFile()
        updateTimestampDisplay()
    }

    private fun clearImportedImages() {
        val importDir = File(cacheDir, IMPORT_DIRECTORY)
        val files = importDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (!file.delete()) {
                    Util.reportError("Failed to delete $file")
                }
            }
        }
    }

    private fun erase() {
        mCellData[mActiveCellIndex] = null
        mCellText[mActiveCellIndex] = null
        activeCellView.bind(mActiveCellIndex, null, null, this)
        saveStateToFile()
        updateTimestampDisplay()
    }

    private fun rotate(direction: Int) {
        activeCellView.rotate(direction)
        saveStateToFile()
    }

    private fun snap() {
        if (mustRequestStorageAccess(SNAP_REQUEST_CODE)) {
            return
        }

        val file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), CAPTURE_DIRECTORY, CAPTURE_PREFIX) ?: return

        mCaptureUri = getUriForFile(this, AUTHORITY, file)

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureUri)
        captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Util.reportException(e)
        }

    }

    private fun pick() {
        val pickIntent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(pickIntent, PICK_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Util.reportException(e)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }

        when (requestCode) {
            CAPTURE_REQUEST_CODE -> {
                importImages(listOf(mCaptureUri?: return))
                publishImage(mCaptureUri)
            }

            PICK_REQUEST_CODE -> {
                val clipData = data?.clipData
                if (clipData != null) {
                    val uris = ArrayList<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        if (uri != null) {
                            uris.add(uri)
                        }
                    }
                    importImages(uris)
                } else if (data?.data != null) {
                    importImages(listOf(data.data))
                }
            }
        }
    }

    private fun publishImage(uri: Uri?) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = uri
        sendBroadcast(mediaScanIntent)
    }

    private fun loadCells(uris: List<Uri>): List<CellData> {
        val cells = ArrayList<CellData>()
        for ((uid, uri) in uris.withIndex()) {
            val cacheFile = Util.getTimestampedImageFile(cacheDir, IMPORT_DIRECTORY, IMPORT_PREFIX, "_$uid")
                    ?: continue
            val cell = CellData.fromUri(uri, cacheFile, this) ?: continue
            cells.add(cell)
        }
        cells.sortWith(Comparator { p0, p1 -> p0.dateTime.compareTo(p1.dateTime) })
        return cells
    }

    private fun importCells(cells: List<CellData>) {
        for (cellData in cells) {
            mCellData[mActiveCellIndex] = cellData
            mCellText[mActiveCellIndex] = null
            val cellView = activeCellView
            cellView.bind(mActiveCellIndex, cellData, null, this)
            cellView.scaleToFill()
            if (cells.size > 1) {
                if (mActiveCellIndex == mCellView.size - 1) {
                    break
                }
                activateCell(mActiveCellIndex + 1)
            }
        }

        saveStateToFile()
        updateTimestampDisplay()
    }

    private fun importImages(uris: List<Uri>) {
        importCells(loadCells(uris))
    }

    private fun importAndLayoutImages(uris: List<Uri>?) {
        val cells = loadCells(uris!!)
        mCellsPerRow = LayoutPicker.findBestLayout(resources, cells.size)
        setupCellLayout()
        mGridView!!.post { importCells(cells) }
    }

    private fun updateTimestampDisplay() {
        val validDates = ArrayList<String>()
        for (cellView in mCellView) {
            val cellData = cellView.data
            if (cellData != null) {
                validDates.add(cellData.date)
            }
        }

        val uniqueDates = HashSet(validDates)

        val validDateCount = validDates.size
        val uniqueDateCount = uniqueDates.size

        for (cellView in mCellView) {
            val cellData = cellView.data
            val timestampView = cellView.timestampView
            val displayTimestamp: String
            displayTimestamp = when {
                cellData == null -> ""
                // Don't show date in cells if it is all the same.
                uniqueDateCount == 1 -> cellData.time
                // Don't show time if all dates are different
                uniqueDateCount == validDateCount -> cellData.date
                else -> cellData.dateTime
            }
            timestampView.text = displayTimestamp
            timestampView.visibility = if (displayTimestamp.isEmpty()) View.GONE else View.VISIBLE
        }

        if (uniqueDateCount == 1) {
            // No date shown in cells, show it in the dedicated view.
            mDateView!!.visibility = View.VISIBLE
            mDateView!!.text = uniqueDates.iterator().next()
        } else {
            mDateView!!.visibility = View.GONE
        }
    }

    fun text(v: View) {
        mTextEditingOn = !mTextEditingOn
        // TODO: find a better way to highlight the button
        val scale = if (mTextEditingOn) 1.25f else 1f
        v.scaleX = scale
        v.scaleY = scale
        // Update focusability on inactive cells first, then the active one.
        // This avoids weird cascading focus transitions.
        for (cellView in mCellView) {
            if (cellView != activeCellView) {
                cellView.enableTextEditing(mTextEditingOn)
            }
        }
        activeCellView.enableTextEditing(mTextEditingOn)
        if (mTextEditingOn) {
            activeCellView.startEditing()
        }
    }

    private fun save() {
        if (mustRequestStorageAccess(SAVE_REQUEST_CODE)) {
            return
        }
        val file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY, EXPORT_PREFIX) ?: return
        Util.saveBitmap(file, createSnapshot(), JPEG_QUALITY)
        Util.addExif(file)
        publishImage(getUriForFile(this, AUTHORITY, file))
    }

    private fun share() {
        if (mustRequestStorageAccess(SHARE_REQUEST_CODE)) {
            return
        }

        val file = Util.getTimestampedImageFile(cacheDir, SHARE_DIRECTORY, SHARE_PREFIX) ?: return

        Util.saveBitmap(file, createSnapshot(), JPEG_QUALITY)
        Util.addExif(file)

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, getUriForFile(this, AUTHORITY, file))
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, "Share to"))
    }

    private fun fitCell() {
        activeCellView.scaleToFit()
    }

    private fun fillCell() {
        activeCellView.scaleToFill()
    }

    private fun mustRequestStorageAccess(requestCode: Int): Boolean {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return false
        }

        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                SAVE_REQUEST_CODE -> save()
                SHARE_REQUEST_CODE -> share()
                SNAP_REQUEST_CODE -> snap()
            }
        }
    }

    private fun createSnapshot(): Bitmap {
        activeCellView.highlight(false)
        val view = findViewById<ViewGroup>(R.id.collage)
        val bitmap = Bitmap.createBitmap(
                view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        activeCellView.highlight(true)
        return bitmap
    }

    companion object {

        internal const val LOG_TAG = "ProgressPics"

        private const val EXPORT_DIRECTORY = "ProgressPics"
        private const val EXPORT_PREFIX = "collage"

        private const val CAPTURE_DIRECTORY = "ProgressPicsCapture"
        private const val CAPTURE_PREFIX = "capture"

        private const val SHARE_DIRECTORY = "share"
        private const val SHARE_PREFIX = "share"

        private const val IMPORT_DIRECTORY = "import"
        private const val IMPORT_PREFIX = "import"

        private const val STATE_DIRECTORY = "state"
        private const val STATE_FILE = "state.bin"

        internal const val AUTHORITY = "org.progresspics.android.fileprovider"

        private const val KEY_LAYOUT = "layout"
        private const val KEY_ACTIVE = "active"
        private const val KEY_CELL = "cell"
        private const val KEY_TEXT = "text"
        private const val KEY_CELLS = "cellCount"

        private const val CAPTURE_REQUEST_CODE = 1
        private const val PICK_REQUEST_CODE = 2
        private const val SAVE_REQUEST_CODE = 3
        private const val SHARE_REQUEST_CODE = 4
        private const val SNAP_REQUEST_CODE = 5

        const val JPEG_QUALITY = 85

        const val CACHED_IMAGE_SIZE_LIMIT = 1024

        private fun getCellKey(index: Int): String {
            return KEY_CELL + index
        }
    }
}

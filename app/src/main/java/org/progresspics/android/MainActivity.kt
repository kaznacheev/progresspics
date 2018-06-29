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
import android.support.v7.app.AlertDialog
import android.support.v7.widget.TooltipCompat
import android.widget.Toast

class MainActivity : AppCompatActivity(), CellView.Listener {

    private var stateFile: File? = null

    private var gridView: ViewGroup? = null
    private var dateView: TextView? = null

    private val cellData = ArrayList<CellData?>()
    private val cellText = ArrayList<String?>()
    private val cellView = ArrayList<CellView>()

    private var cellsPerRow: IntArray? = null
    private var activeCellIndex: Int = 0

    private var captureUri: Uri? = null
    private var textEditingOn: Boolean = false  // Not persistable on purpose.

    private val activeCellView: CellView
        get() = cellView[activeCellIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addTooltips(findViewById(R.id.tools))

        gridView = findViewById(R.id.grid)
        dateView = findViewById(R.id.date)

        cellsPerRow = intArrayOf(3, 3, 3)
        activeCellIndex = 0

        stateFile = Util.getFile(filesDir, STATE_DIRECTORY, STATE_FILE)

        when (intent.action) {
            Intent.ACTION_MAIN -> {
                if (savedInstanceState != null) {
                    restoreInstanceState(savedInstanceState)
                } else if (stateFile!!.exists()) {
                    val persistentState = Util.readBundle(stateFile!!)
                    if (persistentState != null) {
                        restoreInstanceState(persistentState)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    gridView!!.post { importAndLayoutImages(listOf(uri)) }
                    return
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    gridView!!.post { importAndLayoutImages(uris) }
                    return
                }
            }
        }

        setupCellLayout()
    }

    public fun pickLayout(view: View) {
        val dialog = Dialog(this)
        val content = LayoutPicker.inflate(this,
                object: LayoutPicker.Listener {
                    override fun onLayoutPicked(cellsPerRow: IntArray) {
                        changeLayout(cellsPerRow)
                        dialog.dismiss()
                    }

                    override fun onMagicButtonClicked() {
                        changeLayout(findBestLayout())
                        dialog.dismiss()
                    }
                })
        dialog.addContentView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.show()
    }

    private fun findBestLayout(): IntArray {
        var count = cellData.size
        // Ignore completely blank cells except for the very first one
        while (count > 1 && cellData[count - 1] == null && cellText[count - 1] == null) {
            count--
        }
        return LayoutPicker.findBestLayout(resources, count)
    }

    fun changeLayout(cellsPerRow: IntArray) {
        if (Arrays.equals(this.cellsPerRow, cellsPerRow)) {
            return
        }
        this.cellsPerRow = cellsPerRow
        setupCellLayout()
        // Delay until after new layout is done.
        gridView!!.post {
            cellView.forEach { it.scaleToFill() }
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
        cellText[index] = text
        saveStateToFile()
    }

    private fun setupCellLayout() {
        exitTextEditingMode()

        if (activeCellIndex < cellView.size) {
            activeCellView.highlight(false)
        }

        cellView.clear()

        val grid = gridView!!
        for (r in 0 until grid.childCount) {
            val activeRow = r < cellsPerRow!!.size
            val row = grid.getChildAt(r) as ViewGroup
            row.visibility = if (activeRow) View.VISIBLE else View.GONE

            for (c in 0 until row.childCount) {
                val activeColumn = activeRow && c < cellsPerRow!![r]
                val cellWrapper = row.getChildAt(c) as ViewGroup
                cellWrapper.visibility = if (activeColumn) View.VISIBLE else View.GONE

                val view = cellWrapper.getChildAt(0) as CellView
                if (activeColumn) {
                    val index = cellView.size
                    view.bind(index, this)
                    cellView.add(view)
                } else {
                    view.bind(-1, null)
                    view.update(null, null)
                }
                view.enableTextEditing(false)
            }
        }

        updateCells(0)

        grid.requestLayout()

        if (activeCellIndex >= cellView.size) {
            activeCellIndex = cellView.size - 1
        }
        activeCellView.highlight(true)

        updateTimestampDisplay()
    }

    private fun updateCells(firstIndex: Int) {
        for (i in firstIndex until cellView.size) {
            if (i == cellData.size) {
                cellData.add(null)
                cellText.add(null)
            }
            cellView[i].update(cellData[i], cellText[i])
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveInstanceState(outState)
    }

    private fun saveInstanceState(outState: BaseBundle) {
        outState.putIntArray(KEY_LAYOUT, cellsPerRow)
        outState.putInt(KEY_ACTIVE, activeCellIndex)
        outState.putInt(KEY_CELLS, cellData.size)

        for ((c, cellData) in cellData.withIndex()) {
            val cellKey = getCellKey(c)
            val cellBundle: BaseBundle = if (outState is Bundle) Bundle() else PersistableBundle()
            cellData?.save(cellBundle)
            if (cellText[c] != null) cellBundle.putString(KEY_TEXT, cellText[c])
            if (outState is Bundle) {
                outState.putBundle(cellKey, cellBundle as Bundle)
            } else {
                (outState as PersistableBundle).putPersistableBundle(
                        cellKey, cellBundle as PersistableBundle)
            }
        }
    }

    private fun restoreInstanceState(inState: BaseBundle) {
        if (!cellData.isEmpty() || !cellText.isEmpty()) {
            Util.reportError("restoreInstanceState called with non-empty state")
            return
        }

        cellsPerRow = inState.getIntArray(KEY_LAYOUT) ?: intArrayOf(3, 3, 3)
        activeCellIndex = inState.getInt(KEY_ACTIVE, activeCellIndex)
        val cellCount = inState.getInt(KEY_CELLS, 0)

        for (c in 0 until cellCount) {
            val cellKey = getCellKey(c)
            val cellBundle: BaseBundle? = if (inState is Bundle) {
                inState.getBundle(cellKey)
            } else {
                (inState as PersistableBundle).getPersistableBundle(cellKey)
            }
            cellData.add(if (cellBundle != null)
                CellData.fromBundle(cellBundle, contentResolver) else null)
            cellText.add(cellBundle?.getString(KEY_TEXT))
        }
    }

    private fun saveStateToFile() {
        val state = PersistableBundle()
        saveInstanceState(state)
        Util.writeBundle(stateFile!!, state)
    }

    private fun activateCell(index: Int) {
        if (activeCellIndex == index) {
            return
        }
        activeCellView.highlight(false)
        activeCellIndex = index
        activeCellView.highlight(true)
        saveStateToFile()
    }

    public fun clearAll(view: View) {
        cellData.clear()
        cellText.clear()
        cellView.forEach {
            it.update(null, null)
            cellData.add(null)
            cellText.add(null)
        }
        clearImportedImages()
        activateCell(0)
        saveStateToFile()
        updateTimestampDisplay()
    }

    private fun clearImportedImages() {
        File(cacheDir, IMPORT_DIRECTORY).listFiles()?.forEach {
            if (!it.delete()) Util.reportError("Failed to delete $it")
        }
    }

    public fun clearActive(view: View) {
        cellData[activeCellIndex] = null
        cellText[activeCellIndex] = null
        activeCellView.update(null, null)
        saveStateToFile()
        updateTimestampDisplay()
    }

    public fun rotateLeft(view: View) {
        rotate(-1)
    }

    public fun rotateRight(view: View) {
        rotate(1)
    }

    private fun rotate(direction: Int) {
        activeCellView.rotate(direction)
        saveStateToFile()
    }

    public fun snap(view: View) {
        if (mustRequestStorageAccess(SNAP_REQUEST_CODE)) {
            return
        }

        val file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), CAPTURE_DIRECTORY, CAPTURE_PREFIX) ?: return

        captureUri = getUriForFile(this, AUTHORITY, file)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivityForResult(intent, CAPTURE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Util.reportException(e)
        }
    }

    public fun pick(view: View) {
        val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(intent, PICK_REQUEST_CODE)
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
                importImages(listOf(captureUri?: return))
                publishImage(captureUri)
            }

            PICK_REQUEST_CODE -> {
                if (data?.clipData != null) {
                    val uris = ArrayList<Uri>()
                    for (i in 0 until data.clipData.itemCount) {
                        val uri = data.clipData.getItemAt(i).uri ?: continue
                        uris.add(uri)
                    }
                    importImages(uris)
                } else if (data?.data != null) {
                    importImages(listOf(data.data))
                }
            }
        }
    }

    private fun publishImage(uri: Uri?) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = uri
        sendBroadcast(intent)
    }

    private fun loadCells(uris: List<Uri>): List<CellData> {
        val cells = ArrayList<CellData>()
        for ((uid, uri) in uris.withIndex()) {
            val cacheFile = Util.getTimestampedImageFile(
                    cacheDir, IMPORT_DIRECTORY, IMPORT_PREFIX, "_$uid")
                    ?: continue
            val cell = CellData.fromUri(uri, cacheFile, this) ?: continue
            cells.add(cell)
        }
        cells.sortWith(Comparator { p0, p1 -> p0.timestamp.compareTo(p1.timestamp) })
        return cells
    }

    private fun importCells(cells: List<CellData>) {
        cells.forEach {
            val text = null
            cellData[activeCellIndex] = it
            cellText[activeCellIndex] = text
            activeCellView.update(it, text)
            activeCellView.scaleToFill()
            if (cells.size != 1 && activeCellIndex < cellView.size - 1) {
                activateCell(activeCellIndex + 1)
            }
        }
        saveStateToFile()
        updateTimestampDisplay()
    }

    private fun importImages(uris: List<Uri>) {
        importCells(loadCells(uris))
    }

    private fun importAndLayoutImages(uris: List<Uri>) {
        val cells = loadCells(uris)
        cellsPerRow = LayoutPicker.findBestLayout(resources, cells.size)
        setupCellLayout()
        gridView!!.post { importCells(cells) }
    }

    private fun updateTimestampDisplay() {
        val validDates = cellView.filter { it.data != null }.map { it.data!!.date }
        val uniqueDates = HashSet(validDates)

        val allDatesSame = uniqueDates.size == 1
        val allDatesDifferent = uniqueDates.size == validDates.size

        cellView.forEach {
            val cellData = it.data
            val cellTimestamp = when {
                cellData == null -> ""
                allDatesSame -> cellData.time
                allDatesDifferent -> cellData.date
                else -> "${cellData.date} ${cellData.time}"
            }
            it.timestampView.text = cellTimestamp
            it.timestampView.visibility = if (cellTimestamp.isEmpty()) View.GONE else View.VISIBLE
        }

        val dateView = dateView!!
        if (allDatesSame) {
            dateView.visibility = View.VISIBLE
            dateView.text = uniqueDates.iterator().next()
        } else {
            dateView.visibility = View.GONE
        }
    }

    fun text(v: View) {
        textEditingOn = !textEditingOn
        // TODO: find a better way to highlight the button
        val scale = if (textEditingOn) 1.25f else 1f
        v.scaleX = scale
        v.scaleY = scale
        // Update focusability on inactive cells first, then the active one.
        // This avoids weird cascading focus transitions.
        cellView.forEach {
            if (it != activeCellView) {
                it.enableTextEditing(textEditingOn)
            }
        }
        activeCellView.enableTextEditing(textEditingOn)
        if (textEditingOn) {
            activeCellView.startEditing()
        }
    }

    private fun exitTextEditingMode() {
        if (textEditingOn) {
            // Emulate the toggle.
            text(findViewById(R.id.button_text))
        }
    }

    public fun save(view: View) {
        if (mustRequestStorageAccess(SAVE_REQUEST_CODE)) {
            return
        }
        val file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY, EXPORT_PREFIX) ?: return
        Util.saveBitmap(file, createSnapshot(), JPEG_QUALITY)
        Util.addExif(file)
        publishImage(getUriForFile(this, AUTHORITY, file))
        val text = "Saved ${file.name}\nunder ${Environment.DIRECTORY_PICTURES}/$EXPORT_DIRECTORY"
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    public fun share(view: View) {
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

    public fun fitCell(view: View) {
        activeCellView.scaleToFit()
    }

    public fun fillCell(view: View) {
        activeCellView.scaleToFill()
    }

    private fun mustRequestStorageAccess(requestCode: Int): Boolean {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
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
                SAVE_REQUEST_CODE -> save(findViewById(R.id.button_save))
                SHARE_REQUEST_CODE -> share(findViewById(R.id.button_share))
                SNAP_REQUEST_CODE -> snap(findViewById(R.id.button_snap))
            }
        }
    }

    private fun createSnapshot(): Bitmap {
        exitTextEditingMode()
        activeCellView.highlight(false)
        val collage = findViewById<ViewGroup>(R.id.collage)
        if (collage.width != collage.height) {
            Util.reportError("Collage view is not square: ${collage.width}x${collage.height}")
        }
        val bitmap = Bitmap.createBitmap(collage.width, collage.height, Bitmap.Config.ARGB_8888)
        collage.draw(Canvas(bitmap))
        activeCellView.highlight(true)
        return bitmap
    }

    public fun help(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.help_dialog_title))
        builder.setMessage(getString(R.string.help_dialog_message))
        builder.setPositiveButton(android.R.string.ok, null)
        builder.create().show()
    }

    public fun removeActive(view: View) {
        cellData.removeAt(activeCellIndex)
        cellText.removeAt(activeCellIndex)
        updateCells(activeCellIndex)
    }

    public fun insertBeforeActive(view: View) {
        cellData.add(activeCellIndex, null)
        cellText.add(activeCellIndex, null)
        // cellData might be longer than cellView, keep the diff reasonable.
        if (cellData.size >= MAX_CELL_DATA) {
            val lastIndex = cellData.size - 1
            cellData.removeAt(lastIndex)
            cellText.removeAt(lastIndex)
        }
        updateCells(activeCellIndex)
    }

    private fun addTooltips(view: View) {
        val contentDescription = view.contentDescription
        if (contentDescription != null && !contentDescription.isEmpty()) {
            TooltipCompat.setTooltipText(view, contentDescription)
        }
        if (view is ViewGroup) {
            for (c in 0 until view.childCount) {
                addTooltips(view.getChildAt(c))
            }
       }
    }

    companion object {

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

        internal const val AUTHORITY = "org.progresspics.android.fileprovider"
        internal const val JPEG_QUALITY = 85

        internal const val MAX_CELL_DATA = 16

        private fun getCellKey(index: Int): String {
            return KEY_CELL + index
        }
    }
}

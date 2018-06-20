package org.snapgrub.android;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Environment;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import androidx.exifinterface.media.ExifInterface;

import static android.support.v4.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity
        implements LayoutDialogFragment.Listener, CellView.Listener {

    static final String LOG_TAG = "SnapGrub";

    private static final String EXPORT_DIRECTORY = "SnapGrub";
    private static final String EXPORT_PREFIX = "collage";

    private static final String CAPTURE_DIRECTORY = "SnapGrubCapture";
    private static final String CAPTURE_PREFIX = "capture";

    private static final String SHARE_DIRECTORY = "share";
    private static final String SHARE_PREFIX = "share";

    public static final String IMPORT_DIRECTORY = "import";
    public static final String IMPORT_PREFIX = "import";

    private static final String STATE_DIRECTORY = "state";
    private static final String STATE_FILE = "state.bin";

    public static final String AUTHORITY = "org.snapgrub.android.fileprovider";

    public static final String KEY_ROWS = "rows";
    public static final String KEY_COLUMNS = "columns";
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_CELL = "cell";

    private static final int CAPTURE_REQUEST_CODE = 1;
    private static final int PICK_REQUEST_CODE = 2;
    private static final int SAVE_REQUEST_CODE = 3;
    private static final int SHARE_REQUEST_CODE = 4;

    public static final int MAX_ROWS = 3;
    public static final int MAX_COLUMNS = 3;
    public static final int MAX_CELLS = MAX_ROWS * MAX_COLUMNS;

    public static final int JPEG_QUALITY = 85;

    private File mStateFile;

    private ViewGroup mGridView;
    private TextView mDateView;

    private CellData[] mCellData;
    private CellView[] mCellView;

    private int mActiveRows;
    private int mActiveColumns;
    private int mActiveCellIndex;

    private Uri mCaptureUri;
    boolean mTextEditingOn;  // Not persistable on purpose.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_clear).setOnClickListener(v -> clear());
        findViewById(R.id.button_snap).setOnClickListener(v -> snap());
        findViewById(R.id.button_pick).setOnClickListener(v -> pick());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
        findViewById(R.id.button_share).setOnClickListener(v -> share());

        findViewById(R.id.button_erase).setOnClickListener(v -> erase());
        findViewById(R.id.button_rotate_left).setOnClickListener(v -> rotate(1));
        findViewById(R.id.button_rotate_right).setOnClickListener(v -> rotate(-1));
        findViewById(R.id.button_text).setOnClickListener(this::text);
        findViewById(R.id.button_layout).setOnClickListener(v -> pickLayout());

        findViewById(R.id.button_fit).setOnClickListener(v -> fitCell());
        findViewById(R.id.button_fill).setOnClickListener(v -> fillCell());

        mGridView = findViewById(R.id.grid);
        mDateView = findViewById(R.id.date);

        mCellData = new CellData[MAX_CELLS];
        for (int c = 0; c != MAX_CELLS; c++) {
            mCellData[c] = new CellData();
        }

        mActiveRows = MAX_ROWS;
        mActiveColumns = MAX_COLUMNS;
        mActiveCellIndex = 0;

        mStateFile = Util.getFile(getFilesDir(), STATE_DIRECTORY, STATE_FILE);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_MAIN.equals(action)) {
            if (savedInstanceState != null) {
                restoreInstanceState(savedInstanceState);
            } else if (mStateFile.exists()) {
                PersistableBundle persistentState = Util.readBundle(mStateFile);
                if (persistentState != null) {
                    restoreInstanceState(persistentState);
                }
            }
        }

        setupCellLayout();

        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                mGridView.post(() -> importImages(Collections.singletonList(uri)));
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                mGridView.post(() -> importImages(uris));
            }
        }
    }

    private void pickLayout() {
        new LayoutDialogFragment().show(getSupportFragmentManager(), "LayoutDialog");
    }

    @Override
    public void changeLayout(int rows, int columns) {
        if (mActiveRows == rows && mActiveColumns == columns) {
            return;
        }
        mActiveRows = rows;
        mActiveColumns = columns;
        setupCellLayout();
        // Delay until after new layout is done.
        mGridView.post(() -> {
            for (CellView cellView : mCellView) {
                cellView.scaleToFill();
            }
            saveStateToFile();
        });
        updateDate();
    }

    @Override
    public void onCellActivate(CellView cellView) {
        for (int c = 0; c != mCellView.length; c++) {
            if (mCellView[c] == cellView) {
                activateCell(c);
                return;
            }
        }
        Util.reportError("Cannot activate cell");
    }

    @Override
    public void onCellUpdate() {
        saveStateToFile();
    }

    private void setupCellLayout() {
        if (mCellView != null) {
            mCellView[mActiveCellIndex].highlight(false);
        }

        mCellView = new CellView[mActiveRows * mActiveColumns];

        int nextCellIndex = 0;

        if (mTextEditingOn) {
            // Emulate the toggle.
            text(findViewById(R.id.text));
        };

        for (int r = 0; r != MAX_ROWS; r++) {
            final boolean activeRow = r < mActiveRows;
            final View row = mGridView.getChildAt(r);
            if (activeRow) {
                row.setVisibility(View.VISIBLE);
            } else {
                row.setVisibility(View.GONE);
            }

            for (int c = 0; c != MAX_COLUMNS; c++) {
                final boolean activeColumn = c < mActiveColumns;
                View cellWrapper = ((ViewGroup) row).getChildAt(c);

                final CellView cellView = (CellView)((ViewGroup) cellWrapper).getChildAt(0);
                if (activeRow && activeColumn) {
                    cellView.bind(mCellData[nextCellIndex], this);
                    mCellView[nextCellIndex++] = cellView;
                    cellWrapper.setVisibility(View.VISIBLE);
                    cellView.setClickable(true);
                } else {
                    cellView.bind(null, this);
                    cellWrapper.setVisibility(View.GONE);
                }
                cellView.enableTextEditing(false);
            }
            mGridView.requestLayout();
        }

        if (mActiveCellIndex >= mCellView.length) {
            mActiveCellIndex = mCellView.length - 1;
        }

        mCellView[mActiveCellIndex].highlight(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveInstanceState(outState);
    }

    private void saveInstanceState(BaseBundle outState) {
        outState.putInt(KEY_ROWS, mActiveRows);
        outState.putInt(KEY_COLUMNS, mActiveColumns);
        outState.putInt(KEY_ACTIVE, mActiveCellIndex);

        for (int c = 0; c != MAX_CELLS; c++) {
            final String cellKey = getCellKey(c);
            final BaseBundle cellBundle;
            if (outState instanceof Bundle) {
                cellBundle = new Bundle();
            } else {
                cellBundle = new PersistableBundle();
            }
            mCellData[c].saveState(cellBundle);
            if (outState instanceof Bundle) {
                ((Bundle) outState).putBundle(cellKey, (Bundle)cellBundle);
            } else {
                ((PersistableBundle) outState).putPersistableBundle(cellKey, (PersistableBundle)cellBundle);
            }
        }
    }

    private void restoreInstanceState(BaseBundle inState) {
        mActiveRows = inState.getInt(KEY_ROWS, mActiveRows);
        mActiveColumns = inState.getInt(KEY_COLUMNS, mActiveColumns);
        mActiveCellIndex = inState.getInt(KEY_ACTIVE, mActiveCellIndex);

        for (int c = 0; c != MAX_CELLS; c++) {
            final String cellKey = getCellKey(c);
            BaseBundle cellBundle;
            if (inState instanceof Bundle) {
                cellBundle = ((Bundle)inState).getBundle(cellKey);
            } else {
                cellBundle = ((PersistableBundle)inState).getPersistableBundle(cellKey);
            }
            if (cellBundle != null) {
                mCellData[c].restoreState(cellBundle, getContentResolver());
            }
        }

        updateDate();
    }

    @NonNull
    private static String getCellKey(int index) {
        return KEY_CELL + index;
    }

    private void saveStateToFile() {
        PersistableBundle state = new PersistableBundle();
        saveInstanceState(state);
        Util.writeBundle(mStateFile, state);
    }

    private void setActiveCellData(CellData cellData) {
        mCellData[mActiveCellIndex] = cellData;
    }

    private CellData getActiveCellData() {
        return mCellData[mActiveCellIndex];
    }

    private CellView getActiveCellView() {
        return mCellView[mActiveCellIndex];
    }

    public void activateCell(int index) {
        if (mActiveCellIndex == index) {
            return;
        }
        getActiveCellView().highlight(false);
        mActiveCellIndex = index;
        getActiveCellView().highlight(true);
        saveStateToFile();
    }

    private void clear() {
        for (int d = 0; d != mCellData.length; d++) {
            mCellData[d] = new CellData();
        }
        for (int c = 0; c != mCellView.length; c++) {
            mCellView[c].bind(mCellData[c], this);
        }
        clearImportedImages();
        activateCell(0);
        updateDate();
    }

    private void clearImportedImages() {
        File importDir = new File(getCacheDir(), IMPORT_DIRECTORY);
        for (File file : importDir.listFiles()) {
            if (!file.delete()) {
                Util.reportError("Failed to delete " + file);
            }
        }
    }

    private void erase() {
        CellData newCell = new CellData();
        setActiveCellData(newCell);
        getActiveCellView().bind(newCell, this);
        getActiveCellView().invalidate();
        saveStateToFile();
        updateDate();
    }

    private void rotate(int direction) {
        getActiveCellData().rotate(direction);
        getActiveCellView().invalidate();
        saveStateToFile();
    }

    public void snap() {
        File file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), CAPTURE_DIRECTORY, CAPTURE_PREFIX);
        if (file == null) {
            return;
        }

        mCaptureUri = getUriForFile(this, AUTHORITY, file);

        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureUri);
        captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Util.reportException(e);
        }
    }

    private void pick() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(pickIntent, PICK_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Util.reportException(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case CAPTURE_REQUEST_CODE: {
                importImages(Collections.singletonList(mCaptureUri));
                publishImage(mCaptureUri);
                break;
            }

            case PICK_REQUEST_CODE: {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    List<Uri> uris = new ArrayList<>();
                    for (int i = 0; i != clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) {
                            uris.add(uri);
                        }
                    }
                    importImages(uris);
                } else if (data.getData() != null) {
                    importImages(Collections.singletonList(data.getData()));
                }
                break;
            }
        }
    }

    private void publishImage(Uri uri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        sendBroadcast(mediaScanIntent);
    }

    @Nullable
    private CellData loadCell(Uri source, String uid) {
        Log.v(LOG_TAG, "loading " + source);
        try {
            final ContentResolver resolver = getContentResolver();

            final ExifInterface exif = new ExifInterface(resolver.openInputStream(source));

            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp == null) {
                Log.w(LOG_TAG, "No timestamp found in " + source);
                timestamp = new SimpleDateFormat("YYYY:MM:dd HH:mm:ss").format(new Date());
            }

            int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1);
            int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1);

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            if (width <= 0 || height <= 0) {
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions);
                width = bmOptions.outWidth;
                height = bmOptions.outHeight;
            }

            int SIZE_LIMIT = 1024;
            bmOptions.inSampleSize = Math.min(width, height) / SIZE_LIMIT;
            bmOptions.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(source), null, bmOptions);
            if (bitmap == null) {
                return null;
            }

            File file = Util.getTimestampedImageFile(getCacheDir(), IMPORT_DIRECTORY, IMPORT_PREFIX, uid);
            if (file == null) {
                return null;
            }

            Util.saveBitmap(file, bitmap, JPEG_QUALITY);

            CellData cellData = new CellData();
            cellData.load(getUriForFile(this, AUTHORITY, file), getContentResolver());
            if (!cellData.hasImage()) {
                return null;
            }
            cellData.setTimestamp(timestamp);
            return cellData;
        } catch (Exception e) {
            Util.reportException(e);
            return null;
        }
    }

    private void importImages(List<Uri> uris) {
        List<CellData> cells = new ArrayList<>();
        int uid = 0;
        for (Uri uri : uris) {
            CellData cell = loadCell(uri, " " + uid++);
            if (cell != null) {
                cells.add(cell);
            }
        }

        cells.sort(Comparator.comparing(CellData::getTimestamp));

        for (CellData cellData : cells) {
            setActiveCellData(cellData);
            CellView cellView = getActiveCellView();
            cellView.bind(cellData, this);
            cellView.scaleToFill();
            if (cells.size() > 1) {
                if (mActiveCellIndex == mCellView.length - 1) {
                    break;
                }
                activateCell(mActiveCellIndex + 1);
            }
        }

        saveStateToFile();
        updateDate();
    }

    private void updateDate() {
        for (CellData cellData : mCellData) {
            final String timestamp = cellData.getTimestamp();
            if (timestamp != null) {
                mDateView.setVisibility(View.VISIBLE);
                mDateView.setText(timestamp.split(" ")[0].replace(':', '/'));
                return;
            }
        }
        mDateView.setVisibility(View.GONE);
    }

    public void text(View v) {
        mTextEditingOn = !mTextEditingOn;
        // TODO: find a better way to highlight the button
        float scale = mTextEditingOn ? 1.25f : 1f;
        v.setScaleX(scale);
        v.setScaleY(scale);
        // Update focusability on inactive cells first, then the active one.
        // This avoids weird cascading focus transitions.
        for (CellView cellView : mCellView) {
            if (cellView != getActiveCellView()) {
                cellView.enableTextEditing(mTextEditingOn);
            }
        }
        getActiveCellView().enableTextEditing(mTextEditingOn);
        if (mTextEditingOn) {
            getActiveCellView().startEditing();
        }
    }

    private void save() {
        if (mustRequestStorageAccess(SAVE_REQUEST_CODE)) {
            return;
        }
        File file = Util.getTimestampedImageFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY, EXPORT_PREFIX);
        if (file == null) {
            return;
        }
        Util.saveBitmap(file, createSnapshot(), JPEG_QUALITY);
        publishImage(getUriForFile(this, AUTHORITY, file));
    }

    private void share() {
        if (mustRequestStorageAccess(SHARE_REQUEST_CODE)) {
            return;
        }

        File file = Util.getTimestampedImageFile(getCacheDir(), SHARE_DIRECTORY, SHARE_PREFIX);
        if (file == null) {
            return;
        }

        Util.saveBitmap(file, createSnapshot(), JPEG_QUALITY);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, getUriForFile(this, AUTHORITY, file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share to"));
    }

    private void fitCell() {
        getActiveCellView().scaleToFit();
    }

    private void fillCell() {
        getActiveCellView().scaleToFill();
    }

    public boolean mustRequestStorageAccess(int requestCode) {
        if (Build.VERSION.SDK_INT < 23) { //
            // permission is automatically granted on sdk<23 upon installation
            return false;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case SAVE_REQUEST_CODE:
                    save();
                    break;

                case SHARE_REQUEST_CODE:
                    share();
                    break;
            }
        }
    }

    @NonNull
    private Bitmap createSnapshot() {
        getActiveCellView().highlight(false);
        ViewGroup view = findViewById(R.id.collage);
        Bitmap bitmap = Bitmap.createBitmap(
                view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        getActiveCellView().highlight(true);
        return bitmap;
    }
}

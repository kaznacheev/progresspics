package org.snapgrub.snapgrub;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.support.v4.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SnapGrub";

    private static final String EXPORT_DIRECTORY = "SnapGrub";
    private static final String EXPORT_PREFIX = "collage";

    private static final String IMAGES_DIRECTORY = "images";
    private static final String CAPTURE_PREFIX = "capture";

    public static final String AUTHORITY = "org.snapgrub.snapgrub.fileprovider";

    private static final int CAPTURE_REQUEST_CODE = 1;
    private static final int PICK_REQUEST_CODE = 2;
    private static final int SAVE_REQUEST_CODE = 3;
    private static final int SHARE_REQUEST_CODE = 4;

    public static final int NUM_ROWS = 3;
    public static final int NUM_COLUMNS = 3;
    public static final int NUM_CELLS = NUM_ROWS * NUM_COLUMNS;
    public static final int JPEG_QUALITY = 85;

    private ViewGroup mGridView;
    private int mActiveCellIndex;

    private Uri mCaptureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_clear).setOnClickListener(v -> clear());
        findViewById(R.id.button_flip).setOnClickListener(v -> rotate());
        findViewById(R.id.button_snap).setOnClickListener(v -> snap());
        findViewById(R.id.button_pick).setOnClickListener(v -> pick());
        findViewById(R.id.button_text).setOnClickListener(v -> text());
        findViewById(R.id.button_save).setOnClickListener(v -> save());
        findViewById(R.id.button_share).setOnClickListener(v -> share());

        mGridView = findViewById(R.id.grid);

        mActiveCellIndex = 0;

        if (savedInstanceState != null) {
            mActiveCellIndex = savedInstanceState.getInt("active", mActiveCellIndex);
        }
        for (int i = 0; i != NUM_CELLS; i++) {
            final CellView cellView = findCellView(i);
            cellView.setIndex(i);
            if (i == mActiveCellIndex) {
                cellView.highlight(true);
            }
            registerForContextMenu(cellView);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("active", mActiveCellIndex);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_snap:
                snap();
                break;
            case R.id.action_text:
                text();
                break;
            case R.id.action_clear:
                getActiveCellView().setImage(null);
                break;
            default:
                return false;
        }
        return super.onContextItemSelected(item);
    }

    private CellView findCellView(int index) {
        ViewGroup rowContainer = (ViewGroup) mGridView.getChildAt(index / NUM_COLUMNS);
        ViewGroup cellContainer = (ViewGroup) rowContainer.getChildAt(index % NUM_COLUMNS);
        return (CellView) cellContainer.getChildAt(0);
    }

    private CellView getActiveCellView() {
        return findCellView(mActiveCellIndex);
    }

    public void activateCell(int index) {
        if (mActiveCellIndex == index) {
            return;
        }
        getActiveCellView().highlight(false);
        mActiveCellIndex = index;
        getActiveCellView().highlight(true);
    }

    private void clear() {
        for (int i = 0; i != NUM_CELLS; i++) {
            findCellView(i).setImage(null);
        }
        activateCell(0);
    }

    private void rotate() {
        getActiveCellView().rotateImage();
    }

    public void snap() {
        File file = getNewFile(getCacheDir(), IMAGES_DIRECTORY, CAPTURE_PREFIX);
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
            reportError("Failed to resolve intent");
        }
    }

    private void pick() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            startActivityForResult(pickIntent, PICK_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            reportError("Failed to resolve intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            reportError("Intent failed: " + resultCode);
            return;
        }

        switch (requestCode) {
            case CAPTURE_REQUEST_CODE: {
                try {
                    getActiveCellView().setImage(readScaledBitmap(mCaptureUri));
                    nextCell();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                break;
            }

            case PICK_REQUEST_CODE: {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    try {
                        getActiveCellView().setImage(readScaledBitmap(imageUri));
                        nextCell();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }
    }

    private Bitmap readScaledBitmap(Uri uri) throws FileNotFoundException {
        InputStream input = getContentResolver().openInputStream(uri);
        return BitmapFactory.decodeStream(input);
    }

    private void nextCell() {
        if (mActiveCellIndex < NUM_CELLS - 1) {
            activateCell(mActiveCellIndex + 1);
        }
    }

    public void text() {

    }

    private void save() {
        if (mustRequestStorageAccess(SAVE_REQUEST_CODE)) {
            return;
        }
        File file = getNewFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY, EXPORT_PREFIX);
        if (file == null) {
            return;
        }
        saveBitmap(createSnapshot(), file);
    }

    private void share() {
        if (mustRequestStorageAccess(SHARE_REQUEST_CODE)) {
            return;
        }

        File file = getNewFile(getCacheDir(), IMAGES_DIRECTORY, EXPORT_PREFIX);
        if (file == null) {
            return;
        }

        saveBitmap(createSnapshot(), file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, getUriForFile(this, AUTHORITY, file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share to"));
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
            Log.v(LOG_TAG, "Permission: " + permissions[0] + " was " + grantResults[0]);
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
        Bitmap bitmap = Bitmap.createBitmap(
                mGridView.getWidth(), mGridView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        getActiveCellView().highlight(false);
        mGridView.draw(canvas);
        getActiveCellView().highlight(true);
        return bitmap;
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            reportError(e.getMessage());
        }
    }

    @Nullable
    private File getNewFile(File root, String dirName, String filePrefix) {
        File dir = new File(root, dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            reportError("Failed to create " + dir);
            return null;
        }
        File file = new File(dir, getTimestampedFileName(filePrefix));
        if (file.exists() && !file.delete()) {
            reportError("Failed to delete " + file);
            return null;
        }
        return file;
    }

    @NonNull
    private String getTimestampedFileName(String prefix) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return prefix + "_" + timestamp + ".jpg";
    }

    private void reportError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.e(LOG_TAG, message);
    }
}

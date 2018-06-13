package org.snapgrub.snapgrub;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SnapGrub";

    private static final String EXPORT_DIRECTORY = "SnapGrub";
    private static final String EXPORT_FILE_PREFIX = "snapgrub";

    private static final int CAMERA_INTENT_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 2;

    public static final int NUM_ROWS = 3;
    public static final int NUM_COLUMNS = 3;
    public static final int NUM_CELLS = NUM_ROWS * NUM_COLUMNS;

    private ViewGroup mCollageView;
    private int mActiveCellIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_clear).setOnClickListener(v -> clear());
        findViewById(R.id.button_flip).setOnClickListener(v -> rotate());
        findViewById(R.id.button_snap).setOnClickListener(v -> snap());
        findViewById(R.id.button_text).setOnClickListener(v -> text());
        findViewById(R.id.button_save).setOnClickListener(v -> save());

        mCollageView = findViewById(R.id.collage);

        mActiveCellIndex = 0;

        if (savedInstanceState != null) {
            mActiveCellIndex = savedInstanceState.getInt("active", mActiveCellIndex);
        }
        for (int i = 0; i != NUM_CELLS; i++) {
            final CellView cellView = findCellView(i);
            cellView.setIndex(i);
            cellView.setImage(null);
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
                getActiveCell().setImage(null);
                break;
            default:
                return false;
        }
        return super.onContextItemSelected(item);
    }

    private CellView findCellView(int index) {
        ViewGroup rowContainer = (ViewGroup) mCollageView.getChildAt(index / NUM_COLUMNS);
        ViewGroup cellContainer = (ViewGroup) rowContainer.getChildAt(index % NUM_COLUMNS);
        return (CellView) cellContainer.getChildAt(0);
    }

    private CellView getActiveCell() {
        return findCellView(mActiveCellIndex);
    }

    public void activateCell(int index) {
        if (mActiveCellIndex == index) {
            return;
        }
        getActiveCell().highlight(false);
        mActiveCellIndex = index;
        getActiveCell().highlight(true);
    }

    private void clear() {
        for (int i = 0; i != NUM_CELLS; i++) {
            findCellView(i).setImage(null);
        }
        activateCell(0);
    }

    private void rotate() {
        getActiveCell().rotateImage();
    }

    public void snap() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, CAMERA_INTENT_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_INTENT_REQUEST_CODE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            getActiveCell().setImage(imageBitmap);
            nextCell();
        }
    }

    private void nextCell() {
        if (mActiveCellIndex < NUM_CELLS - 1) {
            activateCell(mActiveCellIndex + 1);
        }
    }

    public void text() {

    }

    private void save() {
        if (!isExternalStorageWriteable()) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(mCollageView.getWidth(), mCollageView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        getActiveCell().highlight(false);
        mCollageView.draw(canvas);
        getActiveCell().highlight(true);
        writeBitmap(bitmap);
    }

    private void writeBitmap(Bitmap bitmap) {
        // Get the directory for the user's public pictures directory.
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), EXPORT_DIRECTORY);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(LOG_TAG, "Directory not created: " + dir);
                Toast.makeText(this, "Cannot create directory", Toast.LENGTH_LONG).show();
                return;
            }
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File file = new File(dir, EXPORT_FILE_PREFIX + "_" + timestamp + ".png");
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
            Log.v(LOG_TAG, "Written " + file.toString());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    public  boolean isExternalStorageWriteable() {
        if (Build.VERSION.SDK_INT < 23) { //
            // permission is automatically granted on sdk<23 upon installation
            Log.v(LOG_TAG,"Permission is already granted from manifest");
            return true;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v(LOG_TAG,"Permission is already granted");
            return true;
        }

        Log.v(LOG_TAG,"Requesting permission");
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                Log.d(LOG_TAG, "External storage");
                if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
                    Log.v(LOG_TAG,"Permission: " + permissions[0]+ " was " + grantResults[0]);
                    //resume tasks needing this permission
                    save();
                }
                break;
        }
    }
}

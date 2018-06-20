package org.progresspics.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

public class LayoutDialogFragment extends DialogFragment {

    public interface Listener {
        void changeLayout(int[] cellsPerRow);
    }

    private Listener mListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mListener = (MainActivity)context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Pick the layout")
                .setItems(R.array.layouts_array, (dialog, which) -> parseSelectedLayoutItem(which));
        return builder.create();
    }

    private void parseSelectedLayoutItem(int which) {
        String layout = getContext().getResources().getStringArray(R.array.layouts_array)[which];
        String[] parts = layout.split("x");
        if (parts.length == 2) {
            int rows = Integer.parseInt(parts[1]);
            int columns = Integer.parseInt(parts[0]);
            int [] cellsPerRow = new int[rows];
            for (int r = 0; r != rows; ++r) {
                cellsPerRow[r] = columns;
            }
            mListener.changeLayout(cellsPerRow);
        } else {
            parts = layout.split("\\+");
            if (parts.length > 1) {
                int [] cellsPerRow = new int[parts.length];
                for (int r = 0; r != parts.length; ++r) {
                    cellsPerRow[r] = Integer.parseInt(parts[r]);
                }
                mListener.changeLayout(cellsPerRow);
            }
        }
    }
}
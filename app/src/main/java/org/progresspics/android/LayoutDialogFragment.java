package org.progresspics.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

public class LayoutDialogFragment extends DialogFragment {

    public interface Listener {
        void changeLayout(int rows, int columns);
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
        int rows = Integer.parseInt(parts[1]);
        int columns = Integer.parseInt(parts[0]);
        mListener.changeLayout(rows, columns);
    }
}
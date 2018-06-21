package org.progresspics.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LayoutDialogFragment extends DialogFragment {

    public interface Listener {
        void changeLayout(int[] cellsPerRow);
    }

    private Listener mListener;

    private String[] mDescriptors;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mListener = (MainActivity)context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View content = getActivity().getLayoutInflater().inflate(R.layout.layout_picker, null);

        mRecyclerView = content.findViewById(R.id.layout_list);
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new GridLayoutManager(getContext(), 3);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mDescriptors = getContext().getResources().getStringArray(R.array.layouts_array);

        // specify an adapter (see also next example)
        mAdapter = new LayoutListAdapter(mDescriptors);
        mRecyclerView.setAdapter(mAdapter);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.layout_picker_title)
                .setView(content)
//                .setPositiveButton(android.R.string.ok,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int whichButton) {
//                                mListener.changeLayout(new int[]{3,2});
//                            }
//                        }
//                )
//                .setNegativeButton(android.R.string.cancel,
//                        (dialog, whichButton) -> {
////                                mListener.changeLayout(new int[]{2,3});
//                        }
//                )
                .create();
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

    int[] parseLayoutDescriptor(String descriptor) {
        String[] parts = descriptor.split("x");
        if (parts.length == 2) {
            int rows = Integer.parseInt(parts[1]);
            int columns = Integer.parseInt(parts[0]);
            int [] cellsPerRow = new int[rows];
            for (int r = 0; r != rows; ++r) {
                cellsPerRow[r] = columns;
            }
            return cellsPerRow;
        }
        parts = descriptor.split("\\+");
        if (parts.length > 1) {
            int [] cellsPerRow = new int[parts.length];
            for (int r = 0; r != parts.length; ++r) {
                cellsPerRow[r] = Integer.parseInt(parts[r]);
            }
            return cellsPerRow;
        }
        return null;
    }


    private class LayoutListAdapter extends RecyclerView.Adapter {
        private final String[] mDescriptors;

        LayoutListAdapter(String[] descriptors) {
            mDescriptors = descriptors;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            ViewGroup gridSample = (ViewGroup) inflater.inflate(R.layout.layout_sample_grid, null);
            return new LayoutViewHolder(gridSample);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int [] cellsPerRow = parseLayoutDescriptor(mDescriptors[position]);

            ViewGroup grid = ((LayoutViewHolder) holder).mGrid;
            grid.setOnClickListener((v) -> {
                mListener.changeLayout(cellsPerRow);
                dismiss();
            });

            grid = (ViewGroup) grid.getChildAt(0);

            for (int r = 0; r != grid.getChildCount(); r++) {
                final boolean activeRow = r < cellsPerRow.length;
                final ViewGroup row = (ViewGroup) grid.getChildAt(r);
                if (activeRow) {
                    row.setVisibility(View.VISIBLE);
                } else {
                    row.setVisibility(View.GONE);
                }
                for (int c = 0; c != row.getChildCount(); c++) {
                    final boolean activeColumn = activeRow && c < cellsPerRow[r];
                    ViewGroup cellView = (ViewGroup) row.getChildAt(c);
                    if (activeColumn) {
                        cellView.setVisibility(View.VISIBLE);
                        cellView.setClickable(true);
                    } else {
                        cellView.setVisibility(View.GONE);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return mDescriptors.length;
        }

        private class LayoutViewHolder extends RecyclerView.ViewHolder {
            ViewGroup mGrid;
            LayoutViewHolder(ViewGroup grid) {
                super(grid);
                mGrid = grid;
            }
        }
    }
}
package org.progresspics.android;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LayoutPicker {

    public interface Listener {
        void onItemClicked(int[] cellsPerRow);
    }

    @NonNull
    public static View inflate(Context context, Listener listener) {
        View content = LayoutInflater.from(context).inflate(R.layout.layout_picker, null);

        RecyclerView recyclerView = content.findViewById(R.id.layout_list);
        recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(context, 4);
        recyclerView.setLayoutManager(layoutManager);

        RecyclerView.Adapter adapter = new Adapter(
                listener,
                context.getResources().getStringArray(R.array.layouts_array));
        recyclerView.setAdapter(adapter);

        return content;
    }

    private static class Adapter extends RecyclerView.Adapter {
        private Listener mListener;
        private final String[] mDescriptors;

        Adapter(Listener listener, String[] descriptors) {
            mListener = listener;
            mDescriptors = descriptors;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View itemView = inflater.inflate(R.layout.layout_sample_grid, null);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int [] cellsPerRow = parseLayoutDescriptor(mDescriptors[position]);

            View itemView = ((ViewHolder) holder).mItemView;
            itemView.setOnClickListener((v) -> mListener.onItemClicked(cellsPerRow));

            ViewGroup grid = (ViewGroup) ((ViewGroup) itemView).getChildAt(0);

            for (int r = 0; r != grid.getChildCount(); r++) {
                final boolean activeRow = r < cellsPerRow.length;
                final ViewGroup row = (ViewGroup) grid.getChildAt(r);
                row.setVisibility(activeRow ? View.VISIBLE : View.GONE);
                for (int c = 0; c != row.getChildCount(); c++) {
                    final boolean activeColumn = activeRow && c < cellsPerRow[r];
                    final View cell = row.getChildAt(c);
                    cell.setVisibility(activeColumn ? View.VISIBLE : View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return mDescriptors.length;
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        View mItemView;
        ViewHolder(View grid) {
            super(grid);
            mItemView = grid;
        }
    }

    private static int[] parseLayoutDescriptor(String descriptor) {
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
}
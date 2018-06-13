package org.snapgrub.snapgrub;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class CellView extends LinearLayout {

    private static final int BORDER_COLOR = 0xFF338833;
    private static final int TRANSPARENT = 0x00000000;

    private int mIndex;
    private int mRotation = 0;

    public CellView(Context context) {
        super(context);
    }

    public CellView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CellView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CellView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                getMainActivity().activateCell(mIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void highlight(boolean on) {
        View parent = (View) getParent();
        if (on) {
            parent.setBackgroundColor(BORDER_COLOR);
        } else {
            parent.setBackgroundColor(TRANSPARENT);
        }
    }

    public void setImage(Bitmap bitmap) {
        getImageView().setImageBitmap(bitmap);
        int colorId = bitmap != null ? R.color.colorCollageBackground : R.color.colorBlankCell;
        setBackgroundColor(getResources().getColor(colorId, null));
        getImageView().setRotation(0);
    }


    private MainActivity getMainActivity() {
        return (MainActivity) getContext();
    }

    private ImageView getImageView() {
        return (ImageView) getChildAt(0);
    }

    public void rotateImage() {
        mRotation += 90;
        if (mRotation == 360) {
            mRotation = 0;
        }
        getImageView().setRotation(mRotation);
    }
}

package org.progresspics.android;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class TouchTransparentLinearLayout extends LinearLayout {
    public TouchTransparentLinearLayout(Context context) {
        super(context);
    }

    public TouchTransparentLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchTransparentLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TouchTransparentLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }
}

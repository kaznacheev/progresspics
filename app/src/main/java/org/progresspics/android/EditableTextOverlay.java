package org.progresspics.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class EditableTextOverlay extends android.support.v7.widget.AppCompatEditText {

    public EditableTextOverlay(Context context) {
        super(context);
    }

    public EditableTextOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditableTextOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return isEnabled() && super.dispatchTouchEvent(event);
    }

    public void activate(boolean on) {
        setEnabled(on);
        setFocusable(on);
        setFocusableInTouchMode(on);
        setClickable(on);
    }
}

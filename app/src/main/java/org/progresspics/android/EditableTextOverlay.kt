package org.progresspics.android

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class EditableTextOverlay : android.support.v7.widget.AppCompatEditText {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return isEnabled && super.dispatchTouchEvent(event)
    }

    fun activate(on: Boolean) {
        isEnabled = on
        isFocusable = on
        isFocusableInTouchMode = on
        isClickable = on
    }
}

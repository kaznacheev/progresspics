package org.progresspics.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class CellView : View {

    private val mMarginPaint = Paint()
    private val mBlankPaint = Paint()

    private val mRect = Rect()

    private var mIndex: Int = 0
    var data: CellData? = null
    private var mListener: Listener? = null

    private var mDownX0: Float = 0f
    private var mDownY0: Float = 0f
    private var mDownX1: Float = 0f
    private var mDownY1: Float = 0f
    private val mDragOffset = Point()
    private var mPinchScale = 1f
    private var mDragInProgress: Boolean = false
    private var mPinchInProgress: Boolean = false

    val timestampView: TextView
        get() = (parent as ViewGroup).getChildAt(1) as TextView

    private val editableTextOverlay: EditableTextOverlay
        get() = (parent as ViewGroup).getChildAt(2) as EditableTextOverlay

    interface Listener {
        fun onCellActivate(index: Int)
        fun onCellViewportUpdate(index: Int)
        fun onCellTextUpdate(index: Int, text: String)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            mListener?.onCellActivate(mIndex)
        }

        val data = this.data ?: return super.dispatchTouchEvent(ev)

        val pointerCount = ev.pointerCount
        val x0 = ev.getX(0).toInt().toFloat()
        val y0 = ev.getY(0).toInt().toFloat()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mDownX0 = x0
                mDownY0 = y0
                mDragInProgress = true
                mDragOffset.set(0, 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val dragDist = distance(0f, 0f, mDragOffset.x.toFloat(), mDragOffset.y.toFloat())
                // Do not start the pinch if the drag is already too significant.
                if (dragDist < 5) {
                    if (mDragInProgress) {
                        mDragInProgress = false
                        mDragOffset.set(0, 0)
                        invalidate()
                    }
                    if (pointerCount == 2) {
                        mDownX1 = ev.getX(1)
                        mDownY1 = ev.getX(1)
                        mPinchInProgress = true
                        mPinchScale = 1f
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> if (mDragInProgress && pointerCount == 1) {
                data.computeOffset(mDragOffset, mDownX0 - x0, mDownY0 - y0)
                invalidate()
            } else if (mPinchInProgress && pointerCount == 2) {
                val x1 = ev.getX(1)
                val y1 = ev.getX(1)

                val distOnDown = distance(mDownX0, mDownY0, mDownX1, mDownY1)
                val distCurrent = distance(x0, y0, x1, y1)
                mPinchScale = if (distOnDown > 1) {
                    distCurrent / distOnDown
                } else {
                    1f
                }
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> if (mPinchInProgress && pointerCount == 2) {
                mPinchInProgress = false
                data.adjustScale(mPinchScale)
                mListener?.onCellViewportUpdate(mIndex)
                mPinchScale = 1f
                invalidate()
            }

            MotionEvent.ACTION_UP -> if (mDragInProgress) {
                mDragInProgress = false
                data.adjustPivot(mDragOffset)
                mListener?.onCellViewportUpdate(mIndex)
                mDragOffset.set(0, 0)
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mPinchInProgress) {
                    mPinchInProgress = false
                    mPinchScale = 1f
                    invalidate()
                }
                if (mDragInProgress) {
                    mDragInProgress = false
                    mDragOffset.set(0, 0)
                    invalidate()
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mMarginPaint.style = Paint.Style.FILL
        mMarginPaint.color = resources.getColor(R.color.colorCollageBackground, null)

        mBlankPaint.style = Paint.Style.FILL
        mBlankPaint.color = resources.getColor(R.color.colorBlankCell, null)
    }

    override fun onDraw(canvas: Canvas) {
        mRect.set(0, 0, width, height)
        if (data == null) {
            canvas.drawRect(mRect, mBlankPaint)
            return
        }

        canvas.drawRect(mRect, mMarginPaint)

        canvas.save()
        drawBitmap(canvas)
        canvas.restore()
    }

    private fun drawBitmap(canvas: Canvas) {
        val data = this.data ?: return
        val viewPivotX = width / 2
        val viewPivotY = height / 2
        canvas.rotate(data.rotationInDegrees.toFloat(), viewPivotX.toFloat(), viewPivotY.toFloat())

        val scale = data.scale * mPinchScale
        canvas.scale(scale, scale, viewPivotX.toFloat(), viewPivotY.toFloat())

        canvas.drawBitmap(
                data.bitmap,
                (viewPivotX - (data.pivotX + mDragOffset.x)).toFloat(),
                (viewPivotY - (data.pivotY + mDragOffset.y)).toFloat(), null)
    }

    fun bind(index: Int, data: CellData?, text: String?, listener: Listener?) {
        mIndex = index
        this.data = data
        mListener = listener

        val textEditorView = editableTextOverlay
        if (listener != null) {
            textEditorView.setText(text)
            textEditorView.setTextColor(resources.getColor(
                    if (this.data != null) R.color.colorOverlayLight else R.color.colorOverlayDark, null))
            textEditorView.setOnFocusChangeListener { _, focus ->
                if (focus) {
                    listener.onCellActivate(mIndex)
                } else {
                    listener.onCellTextUpdate(mIndex, textEditorView.text.toString())
                }
            }
        } else {
            textEditorView.text = null;
            textEditorView.onFocusChangeListener = null;
        }

        invalidate()
    }

    fun scaleToFit() {
        data?.scaleToFit(width, height)
        invalidate()
    }

    fun scaleToFill() {
        data?.scaleToFill(width, height)
        invalidate()
    }

    fun rotate(direction: Int) {
        data?.adjustRotation(direction)
        invalidate()
    }

    fun enableTextEditing(on: Boolean) {
        editableTextOverlay.activate(on)
    }

    fun startEditing() {
        editableTextOverlay.requestFocus()
    }

    fun highlight(on: Boolean) {
        val parent = parent as View
        if (on) {
            parent.setBackgroundColor(resources.getColor(R.color.colorCellBorder, null))
        } else {
            parent.setBackgroundColor(TRANSPARENT)
        }
    }

    companion object {
        private const val TRANSPARENT = 0x00000000

        private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            return Math.sqrt(((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()).toFloat()
        }
    }
}

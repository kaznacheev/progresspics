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

    private val marginPaint = Paint()
    private val blankPaint = Paint()
    private val guidePaint = Paint()

    private val tempRect = Rect()

    private var index: Int = 0
    private var listener: Listener? = null

    var data: CellData? = null
        private set

    private var downX0: Float = 0f
    private var downY0: Float = 0f
    private var downX1: Float = 0f
    private var downY1: Float = 0f
    private val dragOffset = Point()
    private var pinchScale = 1f
    private var dragInProgress: Boolean = false
    private var pinchInProgress: Boolean = false

    val timestampView: TextView
        get() = (parent as ViewGroup).getChildAt(1) as TextView

    private val editableTextOverlay: EditableTextOverlay
        get() = (parent as ViewGroup).getChildAt(2) as EditableTextOverlay

    interface Listener {
        fun onCellActivate(index: Int)
        fun onCellViewportUpdate(index: Int)
        fun onCellTextUpdate(index: Int, text: String)
        fun areGuidelinesOn(): Boolean
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            listener?.onCellActivate(index)
        }

        val data = data ?: return super.dispatchTouchEvent(ev)

        val x0 = ev.getX(0).toInt().toFloat()
        val y0 = ev.getY(0).toInt().toFloat()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX0 = x0
                downY0 = y0
                dragInProgress = true
                dragOffset.set(0, 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val dragDist = distance(0f, 0f, dragOffset.x.toFloat(), dragOffset.y.toFloat())
                // Do not start the pinch if the drag is already too significant.
                if (dragDist < 5) {
                    if (dragInProgress) {
                        dragInProgress = false
                        dragOffset.set(0, 0)
                        invalidate()
                    }
                    if (ev.pointerCount == 2) {
                        downX1 = ev.getX(1)
                        downY1 = ev.getY(1)
                        pinchInProgress = true
                        pinchScale = 1f
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> if (dragInProgress && ev.pointerCount == 1) {
                data.computeOffset(dragOffset, downX0 - x0, downY0 - y0)
                invalidate()
            } else if (pinchInProgress && ev.pointerCount == 2) {
                val x1 = ev.getX(1)
                val y1 = ev.getY(1)
                val distOnDown = distance(downX0, downY0, downX1, downY1)
                val distCurrent = distance(x0, y0, x1, y1)
                pinchScale = if (distOnDown > 1) distCurrent / distOnDown else 1f
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> if (pinchInProgress && ev.pointerCount == 2) {
                pinchInProgress = false
                data.adjustScale(pinchScale)
                listener?.onCellViewportUpdate(index)
                pinchScale = 1f
                invalidate()
            }

            MotionEvent.ACTION_UP -> if (dragInProgress) {
                dragInProgress = false
                data.adjustPivot(dragOffset)
                listener?.onCellViewportUpdate(index)
                dragOffset.set(0, 0)
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (pinchInProgress) {
                    pinchInProgress = false
                    pinchScale = 1f
                    invalidate()
                }
                if (dragInProgress) {
                    dragInProgress = false
                    dragOffset.set(0, 0)
                    invalidate()
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        marginPaint.style = Paint.Style.FILL
        marginPaint.color = resources.getColor(R.color.colorCollageBackground, null)

        blankPaint.style = Paint.Style.FILL
        blankPaint.color = resources.getColor(R.color.colorBlankCell, null)

        guidePaint.style = Paint.Style.STROKE
        guidePaint.strokeWidth = 1f
        guidePaint.color = resources.getColor(R.color.colorGuideline, null)
    }

    override fun onDraw(canvas: Canvas) {
        tempRect.set(0, 0, width, height)
        val data = data
        if (data == null) {
            canvas.drawRect(tempRect, blankPaint)
            return
        }

        canvas.drawRect(tempRect, marginPaint)

        canvas.save()
        val viewPivotX = width / 2
        val viewPivotY = height / 2
        canvas.rotate(data.rotationInDegrees.toFloat(), viewPivotX.toFloat(), viewPivotY.toFloat())
        val scale = data.scale * pinchScale
        canvas.scale(scale, scale, viewPivotX.toFloat(), viewPivotY.toFloat())
        canvas.drawBitmap(
                data.bitmap,
                (viewPivotX - (data.pivotX + dragOffset.x)).toFloat(),
                (viewPivotY - (data.pivotY + dragOffset.y)).toFloat(), null)
        canvas.restore()

        if (listener != null && listener!!.areGuidelinesOn()) {
            val guidelinesStep = resources.getDimensionPixelSize(R.dimen.guideline_step)
            for (x in guidelinesStep until width step guidelinesStep) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), guidePaint);
            }
            for (y in guidelinesStep until height step guidelinesStep) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), guidePaint);
            }
        }
    }

    fun bind(index: Int, listener: Listener?) {
        this.index = index
        this.listener = listener
    }

    fun update(data: CellData?, text: String?) {
        this.data = data

        val textEditorView = editableTextOverlay
        val listener = this.listener
        if (listener != null) {
            textEditorView.setText(text)
            textEditorView.setTextColor(resources.getColor(
                    if (data != null) R.color.colorOverlayLight else R.color.colorOverlayDark, null))
            textEditorView.setOnFocusChangeListener { _, focus ->
                if (focus) {
                    listener.onCellActivate(index)
                } else {
                    listener.onCellTextUpdate(index, textEditorView.text.toString())
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

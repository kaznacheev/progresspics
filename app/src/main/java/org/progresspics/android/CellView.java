package org.progresspics.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class CellView extends View {

    private static final String TAG = "CellView";

    private static final int TRANSPARENT = 0x00000000;

    private Paint mMarginPaint = new Paint();
    private Paint mBlankPaint = new Paint();

    private Rect mRect = new Rect();

    private int mIndex;
    private CellData mData;

    public interface Listener {
        void onCellActivate(int index);
        void onCellViewportUpdate(int index);
        void onCellTextUpdate(int index, String text);
    }

    private Listener mListener;

    private float mDownX0;
    private float mDownY0;
    private float mDownX1;
    private float mDownY1;
    private Point mDragOffset = new Point();
    private float mPinchScale = 1;
    private boolean mDragInProgress;
    private boolean mPinchInProgress;

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

    public CellData getData() {
        return mData;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mListener.onCellActivate(mIndex);
        }

        if (mData == null) {
            return super.dispatchTouchEvent(ev);
        }

        int pointerCount = ev.getPointerCount();
        float x0 = (int) ev.getX(0);
        float y0 = (int) ev.getY(0);

        switch(action) {
            case MotionEvent.ACTION_DOWN:
                mDownX0 = x0;
                mDownY0 = y0;
                mDragInProgress = true;
                mDragOffset.set(0, 0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN: {
                final float dragDist = distance(0, 0, mDragOffset.x, mDragOffset.y);
                // Do not start the pinch if the drag is already too significant.
                if (dragDist < 5) {
                    if (mDragInProgress) {
                        mDragInProgress = false;
                        mDragOffset.set(0, 0);
                        invalidate();
                    }
                    if (pointerCount == 2) {
                        mDownX1 = ev.getX(1);
                        mDownY1 = ev.getX(1);
                        mPinchInProgress = true;
                        mPinchScale = 1;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE:
                if (mDragInProgress && pointerCount == 1) {
                    mData.computeOffset(mDragOffset, mDownX0 - x0, mDownY0 - y0);
                    invalidate();
                } else if (mPinchInProgress && pointerCount == 2) {
                    float x1 = ev.getX(1);
                    float y1 = ev.getX(1);

                    float distOnDown = distance(mDownX0, mDownY0, mDownX1, mDownY1);
                    float distCurrent = distance(x0, y0, x1, y1);
                    if (distOnDown > 1) {
                        mPinchScale = distCurrent / distOnDown;
                    } else {
                        mPinchScale = 1;
                    }
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mPinchInProgress && pointerCount == 2) {
                    mPinchInProgress = false;
                    mData.adjustScale(mPinchScale);
                    mListener.onCellViewportUpdate(mIndex);
                    mPinchScale = 1;
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mDragInProgress) {
                    mDragInProgress = false;
                    mData.adjustPivot(mDragOffset);
                    mListener.onCellViewportUpdate(mIndex);
                    mDragOffset.set(0, 0);
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if (mPinchInProgress) {
                    mPinchInProgress = false;
                    mPinchScale = 1;
                    invalidate();
                }
                if (mDragInProgress) {
                    mDragInProgress = false;
                    mDragOffset.set(0, 0);
                    invalidate();
                }
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mMarginPaint.setStyle(Paint.Style.FILL);
        mMarginPaint.setColor(getResources().getColor(R.color.colorCollageBackground, null));

        mBlankPaint.setStyle(Paint.Style.FILL);
        mBlankPaint.setColor(getResources().getColor(R.color.colorBlankCell, null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mRect.set(0, 0, getWidth(), getHeight());
        if (mData == null) {
            canvas.drawRect(mRect, mBlankPaint);
            return;
        }

        canvas.drawRect(mRect, mMarginPaint);

        canvas.save();
        drawBitmap(canvas);
        canvas.restore();
    }

    private void drawBitmap(Canvas canvas) {
        final int viewPivotX = getWidth() / 2;
        final int viewPivotY = getHeight() / 2;
        canvas.rotate(mData.getRotationInDegrees(), viewPivotX, viewPivotY);

        float scale = mData.getScale() * mPinchScale;
        canvas.scale(scale, scale, viewPivotX, viewPivotY);

        canvas.drawBitmap(
                mData.getBitmap(),
                viewPivotX - (mData.getPivotX() + mDragOffset.x),
                viewPivotY - (mData.getPivotY() + mDragOffset.y),
                null);
    }

    public void bind(int index, CellData data, String text, Listener listener) {
        mIndex = index;
        mData = data;
        mListener = listener;

        TextView textEditorView = getEditableTextOverlay();
        if (mListener != null) {
            textEditorView.setText(text);
            textEditorView.setOnFocusChangeListener((view, focus) -> {
                if (focus) {
                    mListener.onCellActivate(mIndex);
                } else {
                    mListener.onCellTextUpdate(mIndex, textEditorView.getText().toString());
                }
            });
            textEditorView.setTextColor(getResources().getColor(
                    mData != null ? R.color.colorOverlayLight : R.color.colorOverlayDark, null));
        } else {
            textEditorView.setText(null);
            textEditorView.setOnFocusChangeListener(null);
        }

        invalidate();
    }

    public void scaleToFit() {
        if (mData != null) {
            mData.scaleToFit(getWidth(), getHeight());
            invalidate();
        }
    }

    public void scaleToFill() {
        if (mData != null) {
            mData.scaleToFill(getWidth(), getHeight());
            invalidate();
        }
    }

    public void rotate(int direction) {
        if (mData != null) {
            mData.adjustRotation(direction);
            invalidate();
        }
    }

    public TextView getTimestampView() {
        return (TextView)((ViewGroup) getParent()).getChildAt(1);
    }

    private EditableTextOverlay getEditableTextOverlay() {
        return (EditableTextOverlay)((ViewGroup) getParent()).getChildAt(2);
    }

    public void enableTextEditing(boolean on) {
        getEditableTextOverlay().activate(on);
    }

    public void startEditing() {
        getEditableTextOverlay().requestFocus();
    }

    public void highlight(boolean on) {
        View parent = (View) getParent();
        if (on) {
            parent.setBackgroundColor(getResources().getColor(R.color.colorCellBorder, null));
        } else {
            parent.setBackgroundColor(TRANSPARENT);
        }
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
    }
}

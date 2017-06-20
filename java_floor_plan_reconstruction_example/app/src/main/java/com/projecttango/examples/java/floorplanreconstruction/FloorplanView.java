/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.floorplanreconstruction;

import com.google.atap.tango.reconstruction.TangoPolygon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view to represent a floorplan.
 *
 * It is implemented as a regular SurfaceView with its own render thread running at a fixed 10Hz
 * rate.
 * The floorplan is drawn using standard canvas draw methods.
 */
public class FloorplanView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = FloorplanView.class.getSimpleName();

    // Scale between meters and pixels. Hardcoded to a reasonable default.
    private static final float SCALE = 100f;
    private static final int INVALID_POINTER_ID = -1;

    private volatile List<TangoPolygon> mPolygons = new ArrayList<>();

    private Paint mBackgroundPaint;
    private Paint mWallPaint;
    private Paint mSpacePaint;
    private Paint mFurniturePaint;
    private Paint mUserMarkerPaint;
    private ScaleGestureDetector mScaleDetector;

    private Path mUserMarkerPath;

    // Pan and Zoom matrix.
    private Matrix mPanAndZoomMatrix;
    // The fixed camera matrix does not include the translation that comes from the user dragging
    // the plan around.
    private Matrix mFixedCameraMatrix;
    private Matrix mFixedCameraInverse;

    // The ‘active pointer’ is the one currently moving our object.
    private int mActivePointerId = INVALID_POINTER_ID;

    // Position of the last touch event.
    private float mLastTouchX;
    private float mLastTouchY;

    // Position of the user marker relative to its start position, in screen coordinates.
    private float mDragX = 0;
    private float mDragY = 0;

    private float mScaleFactor = 1.f;

    private boolean mIsDrawing = false;
    private SurfaceHolder mSurfaceHolder;

    private float mMinAreaSpace = 0f;
    private float mMinAreaWall = 0f;

    /**
     * Custom render thread, running at a fixed 10Hz rate.
     */
    private class RenderThread extends Thread {
        @Override
        public void run() {
            while (mIsDrawing) {
                Canvas canvas = mSurfaceHolder.lockCanvas();
                if (canvas != null) {
                    doDraw(canvas);
                    mSurfaceHolder.unlockCanvasAndPost(canvas);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }
    };
    private RenderThread mDrawThread;

    /**
     * Pre-drawing callback.
     */
    public interface DrawingCallback {
        /**
         * Called during onDraw, before any element is drawn to the view canvas.
         */
        void onPreDrawing();
    }

    private DrawingCallback mCallback;

    public FloorplanView(Context context) {
        super(context);
        init(context);
    }

    public FloorplanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FloorplanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        // Get parameters.
        TypedValue typedValue = new TypedValue();
        getResources().getValue(R.dimen.min_area_space, typedValue, true);
        mMinAreaSpace = typedValue.getFloat();
        getResources().getValue(R.dimen.min_area_wall, typedValue, true);
        mMinAreaWall = typedValue.getFloat();

        // Pre-create graphics objects.
        mWallPaint = new Paint();
        mWallPaint.setColor(getResources().getColor(android.R.color.black));
        mWallPaint.setStyle(Paint.Style.STROKE);
        mWallPaint.setStrokeWidth(3);
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(getResources().getColor(android.R.color.white));
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mSpacePaint = new Paint();
        mSpacePaint.setColor(getResources().getColor(R.color.explored_space));
        mSpacePaint.setStyle(Paint.Style.FILL);
        mFurniturePaint = new Paint();
        mFurniturePaint.setColor(getResources().getColor(R.color.furniture));
        mFurniturePaint.setStyle(Paint.Style.FILL);
        mUserMarkerPaint = new Paint();
        mUserMarkerPaint.setColor(getResources().getColor(R.color.user_marker));
        mUserMarkerPaint.setStyle(Paint.Style.FILL);
        mUserMarkerPath = new Path();
        mUserMarkerPath.lineTo(-0.2f * SCALE, 0);
        mUserMarkerPath.lineTo(-0.2f * SCALE, -0.05f * SCALE);
        mUserMarkerPath.lineTo(0.2f * SCALE, -0.05f * SCALE);
        mUserMarkerPath.lineTo(0.2f * SCALE, 0);
        mUserMarkerPath.lineTo(0, 0);
        mUserMarkerPath.lineTo(0, -0.05f * SCALE);
        mUserMarkerPath.lineTo(-0.4f * SCALE, -0.5f  * SCALE);
        mUserMarkerPath.lineTo(0.4f  * SCALE, -0.5f * SCALE);
        mUserMarkerPath.lineTo(0, 0);
        mPanAndZoomMatrix = new Matrix();
        mFixedCameraMatrix = new Matrix();
        mFixedCameraInverse = new Matrix();

        // Register for surface callback events.
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mSurfaceHolder = surfaceHolder;
        mIsDrawing = true;
        mDrawThread = new RenderThread();
        mDrawThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        mSurfaceHolder = surfaceHolder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mIsDrawing = false;
    }

    private void doDraw(Canvas canvas) {
        // Notify the activity so that it can use Tango to query the current device pose.
        if (mCallback != null) {
            mCallback.onPreDrawing();
        }

        // Erase the previous canvas image.
        canvas.drawColor(getResources().getColor(android.R.color.white));

        // Start drawing from the center of the canvas.
        float translationX = canvas.getWidth() / 2f;
        float translationY = canvas.getHeight() / 2f;
        canvas.translate(translationX, translationY);

        updatePanAndZoomMatrix();
        canvas.concat(mPanAndZoomMatrix);
        // Update position and orientation based on the device position and orientation.
        canvas.concat(mFixedCameraMatrix);

        // Draw all the polygons. Make a shallow copy in case mPolygons is reset while rendering.
        List<TangoPolygon> drawPolygons = mPolygons;
        boolean largestSpaceDrawn = false;
        for (TangoPolygon polygon : drawPolygons) {
            Paint paint;
            switch(polygon.layer) {
                case TangoPolygon.TANGO_3DR_LAYER_FURNITURE:
                    paint = mFurniturePaint;
                    break;
                case TangoPolygon.TANGO_3DR_LAYER_SPACE:
                    // Only draw free space polygons larger than 2 square meter.
                    // The goal of this is to suppress free space polygons in front of windows.
                    // Always draw holes (=negative area) independent of surface area.
                    if (polygon.area > 0) {
                        if (largestSpaceDrawn && polygon.area < mMinAreaSpace) {
                            continue;
                        }
                        largestSpaceDrawn = true;
                    } 
                    paint = mSpacePaint;
                    break;
                case TangoPolygon.TANGO_3DR_LAYER_WALLS:
                    // Only draw wall polygons larger than 20cm x 20cm to suppress noise.
                    if (Math.abs(polygon.area) < mMinAreaWall) {
                        continue;
                    }
                    paint = mWallPaint;
                    break;
                default:
                    Log.w(TAG, "Ignoring polygon with unknown layer value: " + polygon.layer);
                    continue;
            }
            if (polygon.area < 0.0) {
                paint = mBackgroundPaint;
            }
            Path path = new Path();
            float[] p = polygon.vertices2d.get(0);
            // NOTE: We need to flip the Y axis since the polygon data is in Tango start of
            // service frame (Y+ forward) and we want to draw image coordinates (Y+ 2D down).
            path.moveTo(p[0] * SCALE, -1 * p[1] * SCALE);
            for (int i = 1; i < polygon.vertices2d.size(); i++) {
                float[] point = polygon.vertices2d.get(i);
                path.lineTo(point[0] * SCALE, -1 * point[1] * SCALE);
            }
            if (polygon.isClosed) {
                path.close();
            }
            canvas.drawPath(path, paint);
        }

        // Draw a user / device marker.
        canvas.concat(mFixedCameraInverse);
        canvas.drawPath(mUserMarkerPath, mUserMarkerPaint);
    }

    /**
     * Sets the new floorplan polygons model and levels.
     */
    public void setFloorplan(List<TangoPolygon> polygons) {
        mPolygons = polygons;
    }

    public void registerCallback(DrawingCallback callback) {
        mCallback = callback;
    }

    /**
     * Updates the current rotation and translation to be used for the map. This is called with the
     * current device position and orientation.
     */
    public void updateCameraMatrix(float translationX, float translationY, float yawRadians) {
        mFixedCameraMatrix.setTranslate(-translationX * SCALE, -translationY * SCALE);
        mFixedCameraMatrix.preRotate((float) Math.toDegrees(yawRadians), translationX * SCALE,
                translationY * SCALE);
        mFixedCameraMatrix.invert(mFixedCameraInverse);
    }

    /**
     * Updates the pan and zoom matrix with the most recent drag and scale information.
     */
    private void updatePanAndZoomMatrix() {
        float dragX;
        float dragY;
        float scaleFactor;
        // Synchronize before reading fields that can be changed in a different thread.
        synchronized (this) {
            dragX = mDragX;
            dragY = mDragY;
            scaleFactor = mScaleFactor;
        }
        mPanAndZoomMatrix.setTranslate(dragX, dragY);
        mPanAndZoomMatrix.postScale(scaleFactor, scaleFactor);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        mScaleDetector.onTouchEvent(ev);

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;
                // Save the ID of this pointer (for dragging)
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex =
                        MotionEventCompat.findPointerIndex(ev, mActivePointerId);

                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);

                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                // Synchronize while changing the drag variables, since they are read in the
                // renderer thread.
                synchronized (this) {
                    mDragX += dx;
                    mDragY += dy;
                }

                invalidate();

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;

                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mLastTouchX = MotionEventCompat.getX(ev, newPointerIndex);
                    mLastTouchY = MotionEventCompat.getY(ev, newPointerIndex);
                    mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    /**
     * A class to handle the scale gestures.
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private final float mMinScaleFactor = 0.1f;
        private final float mMaxScaleFactor = 5.f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Synchronize while changing the scale factor, since it is read in the renderer thread.
            synchronized (FloorplanView.this) {
                mScaleFactor *= detector.getScaleFactor();
                // Don't let the object get too small or too large.
                mScaleFactor = Math.max(mMinScaleFactor, Math.min(mScaleFactor, mMaxScaleFactor));
            }
            invalidate();
            return true;
        }
    }
}

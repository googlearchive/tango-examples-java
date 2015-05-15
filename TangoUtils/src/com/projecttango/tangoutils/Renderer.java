/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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
 * limitations under the License.*/

package com.projecttango.tangoutils;

import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

public class Renderer {

    protected static final int FIRST_PERSON = 0;
    protected static final int TOP_DOWN = 1;
    protected static final int THIRD_PERSON = 2;
    protected static final int THIRD_PERSON_FOV = 65;
    protected static final int TOPDOWN_FOV = 65;
    protected static final int MATRIX_4X4 = 16;

    protected static final float CAMERA_FOV = 37.8f;
    protected static final float CAMERA_NEAR = 0.01f;
    protected static final float CAMERA_FAR = 200f;
    protected float mCameraAspect;
    protected float[] mProjectionMatrix = new float[MATRIX_4X4];
    private ModelMatCalculator mModelMatCalculator;
    private int viewId = 2;
    protected float[] mViewMatrix = new float[MATRIX_4X4];
    protected float[] mCameraPosition;
    protected float[] mLookAtPosition;
    protected float[] mCameraUpVector;
    private float[] mDevicePosition;
    private float mCameraOrbitRadius;
    private float mRotationX;
    private float mRotationY;
    private float mPreviousRotationX;
    private float mPreviousRotationY;
    private float mPreviousTouchX;
    private float mPreviousTouchY;
    private float mTouch1X, mTouch2X, mTouch1Y, mTouch2Y, mTouchStartDistance,
            mTouchMoveDistance, mStartCameraRadius;

    public Renderer() {
        mModelMatCalculator = new ModelMatCalculator();
        mRotationX = (float) Math.PI / 4;
        mRotationY = 0;
        mCameraOrbitRadius = 5.0f;
        mCameraPosition = new float[3];
        mCameraPosition[0] = 5f;
        mCameraPosition[1] = 5f;
        mCameraPosition[2] = 5f;
        mDevicePosition = new float[3];
        mDevicePosition[0] = 0;
        mDevicePosition[1] = 0;
        mDevicePosition[2] = 0;
    }

    /**
     * Update the view matrix of the Renderer to follow the position of the
     * device in the current perspective.
     */
    public void updateViewMatrix() {
        mDevicePosition = mModelMatCalculator.getTranslation();

        switch (viewId) {
        case FIRST_PERSON:
            float[] invertModelMat = new float[MATRIX_4X4];
            Matrix.setIdentityM(invertModelMat, 0);

            float[] temporaryMatrix = new float[MATRIX_4X4];
            Matrix.setIdentityM(temporaryMatrix, 0);

            Matrix.setIdentityM(mViewMatrix, 0);
            Matrix.invertM(invertModelMat, 0,
                    mModelMatCalculator.getModelMatrix(), 0);
            Matrix.multiplyMM(temporaryMatrix, 0, mViewMatrix, 0,
                    invertModelMat, 0);
            System.arraycopy(temporaryMatrix, 0, mViewMatrix, 0, 16);
            break;
        case THIRD_PERSON:

            Matrix.setLookAtM(mViewMatrix, 0, mDevicePosition[0]
                    + mCameraPosition[0], mCameraPosition[1]
                    + mDevicePosition[1], mCameraPosition[2]
                    + mDevicePosition[2], mDevicePosition[0],
                    mDevicePosition[1], mDevicePosition[2], 0f, 1f, 0f);
            break;
        case TOP_DOWN:
            // Matrix.setIdentityM(mViewMatrix, 0);
            Matrix.setLookAtM(mViewMatrix, 0, mDevicePosition[0]
                    + mCameraPosition[0], mCameraPosition[1],
                    mCameraPosition[2] + mDevicePosition[2], mDevicePosition[0]
                            + mCameraPosition[0], mCameraPosition[1] - 5,
                    mCameraPosition[2] + mDevicePosition[2], 0f, 0f, -1f);
            break;
        default:
            viewId = THIRD_PERSON;
            return;
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (viewId == THIRD_PERSON) {
            int pointCount = event.getPointerCount();
            if (pointCount == 1) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    final float x = event.getX();
                    final float y = event.getY();
                    // Remember where we started
                    mPreviousTouchX = x;
                    mPreviousTouchY = y;
                    mPreviousRotationX = mRotationX;
                    mPreviousRotationY = mRotationY;
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    final float x = event.getX();
                    final float y = event.getY();
                    // Calculate the distance moved
                    final float dx = mPreviousTouchX - x;
                    final float dy = mPreviousTouchY - y;
                    mRotationX = mPreviousRotationX
                            + (float) (Math.PI * dx / 1900); // ScreenWidth
                    mRotationY = mPreviousRotationY
                            + (float) (Math.PI * dy / 1200); // Screen height
                    if (mRotationY > (float) Math.PI)
                        mRotationY = (float) Math.PI;
                    if (mRotationY < 0)
                        mRotationY = 0.0f;
                    mCameraPosition[0] = (float) (mCameraOrbitRadius * Math
                            .sin(mRotationX));
                    mCameraPosition[1] = (float) (mCameraOrbitRadius * Math
                            .cos(mRotationY));
                    mCameraPosition[2] = (float) (mCameraOrbitRadius * Math
                            .cos(mRotationX));
                    break;
                }
                }
            }
            if (pointCount == 2) {
                switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    mTouch1X = event.getX(0);
                    mTouch1Y = event.getY(0);
                    mTouch2X = event.getX(1);
                    mTouch2Y = event.getY(1);
                    mTouchStartDistance = (float) Math.sqrt(Math.pow(mTouch1X
                            - mTouch2X, 2)
                            + Math.pow(mTouch1Y - mTouch2Y, 2));
                    mStartCameraRadius = mCameraOrbitRadius;
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    mTouch1X = event.getX(0);
                    mTouch1Y = event.getY(0);
                    mTouch2X = event.getX(1);
                    mTouch2Y = event.getY(1);
                    mTouchMoveDistance = (float) Math.sqrt(Math.pow(mTouch1X
                            - mTouch2X, 2)
                            + Math.pow(mTouch1Y - mTouch2Y, 2));
                    float tmp = 0.05f * (mTouchMoveDistance - mTouchStartDistance);
                    mCameraOrbitRadius = mStartCameraRadius - tmp;
                    if (mCameraOrbitRadius < 1)
                        mCameraOrbitRadius = 1;
                    mCameraPosition[0] = (float) (mCameraOrbitRadius * Math
                            .sin(mRotationX));
                    mCameraPosition[1] = (float) (mCameraOrbitRadius * Math
                            .cos(mRotationY));
                    mCameraPosition[2] = (float) (mCameraOrbitRadius * Math
                            .cos(mRotationX));
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    int index = event.getActionIndex() == 0 ? 1 : 0;
                    final float x = event.getX(index);
                    final float y = event.getY(index);
                    // Remember where we started
                    mPreviousTouchX = x;
                    mPreviousTouchY = y;
                    mPreviousRotationX = mRotationX;
                    mPreviousRotationY = mRotationY;
                }
                }
            }
        } else if (viewId == TOP_DOWN) {
            int pointCount = event.getPointerCount();
            if (pointCount == 1) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    final float x = event.getX();
                    final float y = event.getY();
                    // Remember where we started
                    mPreviousTouchX = x;
                    mPreviousTouchY = y;
                    mPreviousRotationX = mCameraPosition[0];
                    mPreviousRotationY = mCameraPosition[2];
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    final float x = event.getX();
                    final float y = event.getY();
                    // Calculate the distance moved
                    final float dx = mPreviousTouchX - x;
                    final float dy = mPreviousTouchY - y;
                    mCameraPosition[0] = mPreviousRotationX + dx / 190;
                    mCameraPosition[2] = mPreviousRotationY + dy / 120;
                    break;
                }
                }
            }
            if (pointCount == 2) {
                switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    mTouch1X = event.getX(0);
                    mTouch1Y = event.getY(0);
                    mTouch2X = event.getX(1);
                    mTouch2Y = event.getY(1);
                    mTouchStartDistance = (float) Math.sqrt(Math.pow(mTouch1X
                            - mTouch2X, 2)
                            + Math.pow(mTouch1Y - mTouch2Y, 2));
                    mStartCameraRadius = mCameraPosition[1];
                    Log.i("Start Radius is :", "" + mStartCameraRadius);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    mTouch1X = event.getX(0);
                    mTouch1Y = event.getY(0);
                    mTouch2X = event.getX(1);
                    mTouch2Y = event.getY(1);
                    mTouchMoveDistance = (float) Math.sqrt(Math.pow(mTouch1X
                            - mTouch2X, 2)
                            + Math.pow(mTouch1Y - mTouch2Y, 2));
                    float tmp = 0.05f * (mTouchMoveDistance - mTouchStartDistance);
                    mCameraPosition[1] = mStartCameraRadius - tmp;
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    int index = event.getActionIndex() == 0 ? 1 : 0;
                    final float x = event.getX(index);
                    final float y = event.getY(index);
                    // Remember where we started
                    mPreviousTouchX = x;
                    mPreviousTouchY = y;
                    mPreviousRotationX = mCameraPosition[0];
                    mPreviousRotationY = mCameraPosition[2];
                }
                }
            }
        }
        return true;
    }

    public void setFirstPersonView() {
        viewId = FIRST_PERSON;
        Matrix.perspectiveM(mProjectionMatrix, 0, CAMERA_FOV, mCameraAspect,
                CAMERA_NEAR, CAMERA_FAR);
    }

    public void setThirdPersonView() {
        viewId = THIRD_PERSON;
        mCameraPosition[0] = 5;
        mCameraPosition[1] = 5;
        mCameraPosition[2] = 5;
        mRotationX = mRotationY = (float) (Math.PI / 4);
        mCameraOrbitRadius = 5.0f;
        Matrix.perspectiveM(mProjectionMatrix, 0, THIRD_PERSON_FOV,
                mCameraAspect, CAMERA_NEAR, CAMERA_FAR);
    }

    public void setTopDownView() {
        viewId = TOP_DOWN;
        mCameraPosition[0] = 0;
        mCameraPosition[1] = 5;
        mCameraPosition[2] = 0;
        Matrix.perspectiveM(mProjectionMatrix, 0, TOPDOWN_FOV, mCameraAspect,
                CAMERA_NEAR, CAMERA_FAR);
    }

    public void resetModelMatCalculator() {
        mModelMatCalculator = new ModelMatCalculator();
    }

    public ModelMatCalculator getModelMatCalculator() {
        return mModelMatCalculator;
    }

    public float[] getViewMatrix() {
        return mViewMatrix;
    }

    public float[] getProjectionMatrix() {
        return mProjectionMatrix;
    }

}

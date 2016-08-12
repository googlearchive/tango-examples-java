/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
package com.projecttango.rajawali;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import org.rajawali3d.cameras.Camera;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

/**
 * This is a helper class that adds top-down and third-person views in a VR setting, including
 * handling of standard pan and zoom touch interactions.
 */
public class TouchViewHandler {
    // Touch interaction tuning constants.
    private static final int TOUCH_THIRD_PITCH_LIMIT = 60;
    private static final int TOUCH_THIRD_PITCH_DEFAULT = 45;
    private static final int TOUCH_THIRD_YAW_DEFAULT = -45;
    private static final int TOUCH_FOV_MAX = 120;
    private static final int TOUCH_THIRD_DISTANCE = 10;
    private static final int TOUCH_TOP_DISTANCE = 10;

    // Virtual reality view parameters.
    private static final float FIRST_PERSON_FOV = 37.8f;
    private static final int THIRD_PERSON_FOV = 65;
    private static final int TOP_DOWN_FOV = 65;

    private enum ViewMode {
        FIRST_PERSON, TOP_DOWN, THIRD_PERSON
    }

    private ViewMode viewMode = ViewMode.THIRD_PERSON;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private Vector3 topDownCameraDelta = new Vector3();
    private float thirdPersonPitch = TOUCH_THIRD_PITCH_DEFAULT;
    private float thirdPersonYaw = TOUCH_THIRD_YAW_DEFAULT;

    private Camera camera;

    public TouchViewHandler(Context context, Camera camera) {
        gestureDetector = new GestureDetector(context, new DragListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        this.camera = camera;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public void updateCamera(Vector3 position, Quaternion orientation) {
        if (viewMode == ViewMode.FIRST_PERSON) {
            camera.setPosition(position);
            camera.setOrientation(orientation);
        } else if (viewMode == ViewMode.TOP_DOWN) {
            camera.setPosition(position.x + topDownCameraDelta.x, TOUCH_TOP_DISTANCE,
                    position.z + topDownCameraDelta.z);
            camera.setRotation(Vector3.Axis.X, 90);
        } else if (viewMode == ViewMode.THIRD_PERSON) {
            camera.setPosition(position.x, position.y, position.z);
            camera.setRotZ(thirdPersonPitch);
            camera.rotate(Vector3.Axis.Y, thirdPersonYaw);
            camera.moveForward(TOUCH_THIRD_DISTANCE);
        }
    }

    public void onTouchEvent(MotionEvent motionEvent) {
        gestureDetector.onTouchEvent(motionEvent);
        scaleGestureDetector.onTouchEvent(motionEvent);
    }

    public void setFirstPersonView() {
        viewMode = ViewMode.FIRST_PERSON;
        camera.setFieldOfView(FIRST_PERSON_FOV);
    }

    public void setTopDownView() {
        viewMode = ViewMode.TOP_DOWN;
        topDownCameraDelta = new Vector3();
        camera.setFieldOfView(TOP_DOWN_FOV);
    }

    public void setThirdPersonView() {
        viewMode = ViewMode.THIRD_PERSON;
        thirdPersonYaw = TOUCH_THIRD_YAW_DEFAULT;
        thirdPersonPitch = TOUCH_THIRD_PITCH_DEFAULT;
        camera.setFieldOfView(THIRD_PERSON_FOV);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        float scale = 1f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scale = detector.getScaleFactor();
            scale = Math.max(0.1f, Math.min(scale, 5f));

            camera.setFieldOfView(
                    Math.min(camera.getFieldOfView() / scale, TOUCH_FOV_MAX));

            return true;
        }
    }

    private class DragListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

            if (viewMode == ViewMode.TOP_DOWN) {
                double factor = camera.getFieldOfView() / 45;
                topDownCameraDelta.add(
                        new Vector3(distanceX / 100 * factor, 0, distanceY / 100 * factor));
            } else if (viewMode == ViewMode.THIRD_PERSON) {
                thirdPersonPitch -= distanceY / 10;
                thirdPersonPitch =
                        Math.min(thirdPersonPitch, TOUCH_THIRD_PITCH_LIMIT);
                thirdPersonPitch =
                        Math.max(thirdPersonPitch, -TOUCH_THIRD_PITCH_LIMIT);
                thirdPersonYaw -= distanceX / 10;
                thirdPersonYaw %= 360;
            }

            return true;
        }
    }
}

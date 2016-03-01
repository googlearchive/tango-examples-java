/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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
package com.projecttango.experiments.pointtopointsample;

import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Line3D;

import java.util.Stack;
import java.util.Vector;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliRenderer;

/**
 * Very simple example point to point renderer which displays a line fixed in place.
 * Whenever the user clicks on the screen, the line is re-rendered with an endpoint 
 * placed at the point corresponding to the depth at the point of the click.
 * <p/>
 * This follows the same development model than any regular Rajawali application
 * with the following peculiarities:
 * - It extends {@code TangoRajawaliArRenderer}.
 * - It calls {@code super.initScene()} in the initialization.
 * - When a new location is obtained for a line endpoint, the line is updated in the render loop.
 * - The associated PointToPointActivity is taking care of updating the camera pose to match
 * the displayed RGB camera texture and produce the AR effect through a Scene Frame Callback
 * (@see PointToPointActivity)
 */
public class PointToPointRenderer extends TangoRajawaliRenderer {

    private Object3D mLine;
    private Vector3[] mLinePoints = new Vector3[2];
    private boolean mLineIsVisible = false;
    private boolean mPointsUpdated = false;
    private boolean mPointSwitch = true;

    public PointToPointRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Remember to call super.initScene() to allow TangoRajawaliArRenderer
        // to be set-up.
        super.initScene();

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        // Update the AR object if necessary
        // Synchronize against concurrent access with the setter below.
        synchronized (this) {
            if (mPointsUpdated) {
                if (mLineIsVisible) {
                    // Remove current line
                    if (mLine != null) {
                        getCurrentScene().removeChild(mLine);
                    }
                    // Place the line based on the two points.
                    Stack<Vector3> points = new Stack<Vector3>();
                    points.push(mLinePoints[0]);
                    points.push(mLinePoints[1]);
                    mLine = new Line3D(points, 50, Color.RED);
                    Material m = new Material();
                    m.setColor(Color.RED);
                    mLine.setMaterial(m);
                    getCurrentScene().addChild(mLine);
                } else {
                    // Remove line
                    if (mLine != null) {
                        getCurrentScene().removeChild(mLine);
                    }
                }
                mPointsUpdated = false;
            }
            
        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Update the oldest line endpoint to the value passed into this function.
     * This will also flag the line for update on the next render pass.
     */
    public synchronized void updateLine(Vector3 worldPoint) {
        mPointsUpdated = true;
        if (mPointSwitch) {
            mPointSwitch = !mPointSwitch;
            mLinePoints[0] = worldPoint;
            return;
        }
        mPointSwitch = !mPointSwitch;
        mLinePoints[1] = worldPoint;
        mLineIsVisible = true;
    }
    
    /*
     * Remove all the points from the Scene.
     */
    public synchronized void clearLine() {
        if (mLine != null) {
            getCurrentScene().removeChild(mLine);
        }
        mLineIsVisible = false;
        mPointSwitch = true;
        mPointsUpdated = true;
    }

    /**
     * Produces the String for the line length base on
     * endpoint locations.
     */
    public synchronized String getPointSeparation() {
        if (!mLineIsVisible) {
            return "Null";
        }
        Vector3 p1 = mLinePoints[0];
        Vector3 p2 = mLinePoints[1];
        double separation = Math.sqrt(
                                Math.pow(p1.x - p2.x, 2) + 
                                Math.pow(p1.y - p2.y, 2) + 
                                Math.pow(p1.z - p2.z, 2));
        return String.format("%.2f", separation) + " meters";
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}

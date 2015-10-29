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
 * limitations under the License.
 */
package com.projecttango.experiments.augmentedrealitysample;

import android.content.Context;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalcuator;
import com.projecttango.rajawali.ar.TangoRajawaliRenderer;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;

/**
 * Very simple example augmented reality renderer which displays two objects in a fixed position
 * in the world and the uses the Tango position tracking to keep them in place.
 * <p/>
 * This follows the same development model than any regular Rajawali application with the following
 * peculiarities:
 * - It extends <code>TangoRajawaliArRenderer</code>
 * - It calls <code>super.initScene()</code> in the initialization
 * - It doesn't do anything with the camera, since that is handled automatically by Tango
 */
public class AugmentedRealityRenderer extends TangoRajawaliRenderer {

    private static final float CUBE_SIDE_LENGTH = 0.5f;

    private Pose mPlanePose;
    private boolean mPlanePoseUpdated = false;

    private Object3D mObject;

    public AugmentedRealityRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Remember to call super.initScene() to allow TangoRajawaliArRenderer to set-up
        super.initScene();

        // Add a directional light in an arbitrary direction
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        // Set-up a material: green with application of the light and instructions
        Material material = new Material();
        material.setColor(0xff009900);
        try {
            Texture t = new Texture("instructions", R.drawable.instructions);
            material.addTexture(t);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        material.setColorInfluence(0.1f);
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());

        // Build a Cube and place it initially in the origin
        mObject = new Cube(CUBE_SIDE_LENGTH);
        mObject.setMaterial(material);
        mObject.setPosition(0, 0, -3);
        mObject.setRotation(Vector3.Axis.Z, 180);
        getCurrentScene().addChild(mObject);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);

        synchronized (this) {
            if (mPlanePoseUpdated == true) {
                mPlanePoseUpdated = false;
                // Place the 3D object in the location of the detected plane
                mObject.setPosition(mPlanePose.getPosition());
                mObject.setOrientation(mPlanePose.getOrientation());
                // Move it forward by half of the size of the cube to make it flush with the plane
                // surface
                mObject.moveForward(CUBE_SIDE_LENGTH / 2.0f);
            }
        }
    }

    /**
     * Update the 3D object based on the provided measurement point, normal (in depth frame) and
     * device pose at the time of measurement.
     */
    public synchronized void updateObjectPose(double[] point, double[] normal,
                                              TangoPoseData devicePose) {
        mPlanePose = mScenePoseCalcuator.planeFitToOpenGLPose(point, normal, devicePose);
        mPlanePoseUpdated = true;
    }

    /**
     * Provide access to scene calculator helper class to perform necessary transformations.
     * NOTE: This won't be necessary once transformation functions are available through the
     * support library
     */
    public ScenePoseCalcuator getPoseCalculator() {
        return mScenePoseCalcuator;
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}

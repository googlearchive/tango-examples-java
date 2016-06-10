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

package com.projecttango.examples.java.motiontracking;

import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;

import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.renderer.RajawaliRenderer;

import com.projecttango.rajawali.Pose;

import com.projecttango.tangosupport.TangoSupport;

/**
 * This class implements the rendering logic for the Motion Tracking application using Rajawali.
 */
public class MotionTrackingRajawaliRenderer extends RajawaliRenderer {
    private static final String TAG = MotionTrackingRajawaliRenderer.class.getSimpleName();

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;

    // Latest available device pose;
    private Pose mDevicePose = new Pose(Vector3.ZERO, Quaternion.getIdentity());
    private boolean mPoseUpdated = false;
  
    // The current screen rotation index. The index value follow the Android surface rotation enum:
    // http://developer.android.com/reference/android/view/Surface.html#ROTATION_0
    private int mCurrentScreenRotation = 0;

    public MotionTrackingRajawaliRenderer(Context context) {
        super(context);
    }

    public void setCurrentScreenRotation(int currentRotation) {
        mCurrentScreenRotation = currentRotation;
    }
  
    @Override
    protected void initScene() {

        getCurrentScene().setBackgroundColor(0x7EC0EE); // Sky color
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);

        // We add a grass floor to the scene for a more comfortable walk
        Material floorMaterial = new Material();
        floorMaterial.setColorInfluence(0);

        try {
            Texture t = new Texture("grass", R.drawable.grass);
            floorMaterial.addTexture(t);

        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception generating grass texture", e);
        }

        Plane floor = new Plane(100f, 100f, 1, 1, Vector3.Axis.Y, true, true, 100);
        floor.setMaterial(floorMaterial);
        floor.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(floor);

        // A floating Project Tango logo as a world reference.
        Material logoMaterial = new Material();
        logoMaterial.setColorInfluence(0);

        try {
            Texture t = new Texture("logo", R.drawable.tango_logo);
            logoMaterial.addTexture(t);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception generating logo texture", e);
        }

        Cube logo = new Cube(0.5f);
        // Change the texture coordinates to be in the right position for the viewer
        logo.getGeometry().setTextureCoords(new float[]
                {
                        1, 0, 0, 0, 0, 1, 1, 1, // THIRD
                        0, 0, 0, 1, 1, 1, 1, 0, // SECOND
                        0, 1, 1, 1, 1, 0, 0, 0, // FIRST
                        1, 0, 0, 0, 0, 1, 1, 1, // FOURTH
                        0, 1, 1, 1, 1, 0, 0, 0, // TOP
                        0, 1, 1, 1, 1, 0, 0, 0, // BOTTOM

                });
        // Update the buffers after changing the geometry
        logo.getGeometry().changeBufferData(logo.getGeometry().getTexCoordBufferInfo(),
                logo.getGeometry().getTextureCoords(), 0);
        logo.rotate(Vector3.Axis.Y, 180);
        logo.setPosition(0, 0, -2);
        logo.setMaterial(logoMaterial);
        getCurrentScene().addChild(logo);

        // Rotate around its Y axis
        Animation3D animLogo = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
        animLogo.setInterpolator(new LinearInterpolator());
        animLogo.setDurationMilliseconds(6000);
        animLogo.setRepeatMode(Animation.RepeatMode.INFINITE);
        animLogo.setTransformable3D(logo);
        getCurrentScene().registerAnimation(animLogo);
        animLogo.play();

    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        // Update the scene objects with the latest device position and orientation information.
        // Synchronize to avoid concurrent access from the Tango callback thread below.
        try {
            TangoPoseData pose =
                TangoSupport.getPoseAtTime(0.0, TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                     TangoPoseData.COORDINATE_FRAME_DEVICE,
                                     TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                     mCurrentScreenRotation);
            if (pose.statusCode == TangoPoseData.POSE_VALID) {
                getCurrentCamera().setPosition((float) pose.translation[0],
                                               (float) pose.translation[1],
                                               (float) pose.translation[2]);
            
        
                Quaternion invOrientation = new Quaternion((float) pose.rotation[3],
                                                            (float) pose.rotation[0],
                                                            (float) pose.rotation[1],
                                                            (float) pose.rotation[2]);

                // For some reason, rajawalli's orientation is inversed.
                Quaternion orientation = invOrientation.inverse();
                getCurrentCamera().setOrientation(orientation);   
            }
        } catch (TangoErrorException e) {
            Log.e(TAG, "TangoSupport.getPoseAtTime error", e);
        }

        // Perform the actual OpenGL rendering of the updated objects
        super.onRender(ellapsedRealtime, deltaTime);
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }

    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }
}

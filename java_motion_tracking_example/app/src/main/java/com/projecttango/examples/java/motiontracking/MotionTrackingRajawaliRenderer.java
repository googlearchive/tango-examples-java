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

/**
 * This class implements the rendering logic for the Motion Tracking application using Rajawali.
 */
public class MotionTrackingRajawaliRenderer extends RajawaliRenderer {
    private static final String TAG = MotionTrackingRajawaliRenderer.class.getSimpleName();

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;

    public MotionTrackingRajawaliRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {

        getCurrentScene().setBackgroundColor(0x7EC0EE); // Sky color.
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);

        // We add a grass floor to the scene for a more comfortable walk.
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
        // Change the texture coordinates to be in the right position for the viewer.
        logo.getGeometry().setTextureCoords(new float[]
                {
                        1, 0, 0, 0, 0, 1, 1, 1, // THIRD
                        0, 0, 0, 1, 1, 1, 1, 0, // SECOND
                        0, 1, 1, 1, 1, 0, 0, 0, // FIRST
                        1, 0, 0, 0, 0, 1, 1, 1, // FOURTH
                        0, 1, 1, 1, 1, 0, 0, 0, // TOP
                        0, 1, 1, 1, 1, 0, 0, 0, // BOTTOM

                });
        // Update the buffers after changing the geometry.
        logo.getGeometry().changeBufferData(logo.getGeometry().getTexCoordBufferInfo(),
                logo.getGeometry().getTextureCoords(), 0);
        logo.rotate(Vector3.Axis.Y, 180);
        logo.setPosition(0, 0, -2);
        logo.setMaterial(logoMaterial);
        getCurrentScene().addChild(logo);

        // Rotate around its Y axis.
        Animation3D animLogo = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
        animLogo.setInterpolator(new LinearInterpolator());
        animLogo.setDurationMilliseconds(6000);
        animLogo.setRepeatMode(Animation.RepeatMode.INFINITE);
        animLogo.setTransformable3D(logo);
        getCurrentScene().registerAnimation(animLogo);
        animLogo.play();
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The camera pose should match the pose of the camera color at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is need because Rajawali uses left handed convention for
        // quaternions.
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
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

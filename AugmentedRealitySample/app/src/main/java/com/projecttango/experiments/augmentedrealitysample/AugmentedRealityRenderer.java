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

import com.projecttango.rajawali.ar.TangoRajawaliRenderer;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.primitives.NPrism;
import org.rajawali3d.primitives.Sphere;

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

        // Set-up a material: green with application of the light
        Material material = new Material();
        material.setColor(0xff009900);
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());

        // Build a pyramid and place it roughly in front and a bit to the right
        Object3D object1 = new NPrism(4, 0f, 0.2f, 0.2f);
        object1.setMaterial(material);
        object1.setPosition(-0.25, 0, -1);
        getCurrentScene().addChild(object1);

        // Build a sphere and place it roughly in front and a bit to the left
        object1 = new Sphere(0.1f, 24, 24);
        object1.setMaterial(material);
        object1.setPosition(0.25, 0, -1);
        getCurrentScene().addChild(object1);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}

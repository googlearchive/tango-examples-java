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
package com.projecttango.examples.java.modelcorrespondence;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.ALoader;
import org.rajawali3d.loader.LoaderSTL;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.loader.async.IAsyncLoaderCallback;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.tangosupport.TangoSupport;

/**
 * Simple example augmented reality renderer which displays spheres fixed in place for every
 * point measurement and a 3D model of a house in the position given by the found correspondence.
 * Whenever the user clicks on '+' button, a sphere is placed in the aimed position with the
 * crosshair.
 */
public class ModelCorrespondenceRenderer extends RajawaliRenderer {
    private static final float SPHERE_RADIUS = 0.02f;
    private static final String TAG = ModelCorrespondenceRenderer.class.getSimpleName();

    // Augmented reality related fields
    private ATexture mTangoCameraTexture;
    private boolean mSceneCameraConfigured;

    private Object3D mHouseObject3D;
    private Object3D mNextPointObject3D;
    private List<Object3D> mDestPointsObjectList = new ArrayList<Object3D>();
    private Material mSphereMaterial;
    private Material mHouseMaterial;

    public ModelCorrespondenceRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);

        // Add two directional lights in arbitrary directions.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        DirectionalLight light2 = new DirectionalLight(-1, 0.2, -1);
        light.setColor(1, 4, 4);
        light.setPower(0.8f);
        light.setPosition(3, 3, 3);
        getCurrentScene().addLight(light2);

        // Set-up a materials.
        mSphereMaterial = new Material();
        mSphereMaterial.enableLighting(true);
        mSphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mSphereMaterial.setSpecularMethod(new SpecularMethod.Phong());

        mHouseMaterial = new Material();
        mHouseMaterial.enableLighting(true);
        mHouseMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mHouseMaterial.setSpecularMethod(new SpecularMethod.Phong());
        mHouseMaterial.setColor(0xffcc6644);
        mHouseMaterial.setColorInfluence(0.5f);

        // Load STL model.
        LoaderSTL parser = new LoaderSTL(getContext().getResources(), mTextureManager,
                R.raw.farmhouse);
        try {
            parser.parse();
            mHouseObject3D = parser.getParsedObject();
            mHouseObject3D.setMaterial(mHouseMaterial);
            getCurrentScene().addChild(mHouseObject3D);
        } catch (ParsingException e) {
            Log.d(TAG, "Model load failed");
        }
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scen camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
        getCurrentCamera().setProjectionMatrix(projectionMatrix);
    }

    /**
     * Render the data model.
     * Render the next correspondence source point to be added as a green sphere. Render the
     * correspondence destination points as red spheres. Render the 3d model in the position and
     * orientation given by the found correspondence transform.
     */
    public void updateModelRendering(HouseModel houseModel, float[] openGlTHouse,
                                     List<float[]> destPoints) {
        if (destPoints.size() > mDestPointsObjectList.size()) {
            // If new destination points were measured, then add them as points as red spheres.
            for (int i = mDestPointsObjectList.size(); i < destPoints.size(); i++) {
                Object3D destPointObject3D = makePoint(destPoints.get(i), Color.RED);
                getCurrentScene().addChild(destPointObject3D);
                mDestPointsObjectList.add(destPointObject3D);
            }
        } else if (destPoints.size() < mDestPointsObjectList.size()) {
            // If destination points were deleted, then add them as points as red spheres.
            for (int i = destPoints.size(); i < mDestPointsObjectList.size(); i++) {
                Object3D destPointObject3D = mDestPointsObjectList.get(i);
                getCurrentScene().removeChild(destPointObject3D);
                mDestPointsObjectList.remove(i);
            }
        }

        // Move the position of the next source point to be added.
        int nextPointNumber = destPoints.size();
        List<float[]> houseModelPoints = houseModel.getOpenGlModelPpoints(openGlTHouse);
        if (nextPointNumber < houseModelPoints.size()) {
            if (mNextPointObject3D == null) {
                mNextPointObject3D = makePoint(new float[]{0, 0, 0}, Color.GREEN);
                getCurrentScene().addChild(mNextPointObject3D);
            }
            float[] position = houseModelPoints.get(nextPointNumber);
            mNextPointObject3D.setPosition(position[0], position[1], position[2]);
        } else {
            getCurrentScene().removeChild(mNextPointObject3D);
            mNextPointObject3D = null;
        }

        // Place the house object in the position and orientation given by the correspondence
        // transform.
        if (mHouseObject3D != null) {
            Matrix4 transform = new Matrix4(openGlTHouse);
            double scale = transform.getScaling().x;
            mHouseObject3D.setScale(scale);
            // Multiply by the inverse of the scale so the transform is only rotation and
            // translation.
            Vector3 translation = transform.getTranslation();
            Matrix4 invScale = Matrix4.createScaleMatrix(1 / scale, 1 / scale, 1 / scale);
            transform.multiply(invScale);
            // Conjugation is needed because Rajawali uses a left handed convention for quaternions.
            Quaternion orientation = new Quaternion().fromMatrix(transform).conjugate();
            orientation.normalize();
            mHouseObject3D.setPosition(translation);
            mHouseObject3D.setOrientation(orientation);
        }
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
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    /**
     * Render the new correspondence destination point measurements as red spheres.
     */
    private Object3D makePoint(float[] openGLPpoint, int color) {
        Object3D object3D = new Sphere(SPHERE_RADIUS, 10, 10);
        object3D.setMaterial(mSphereMaterial);
        object3D.setColor(color);
        object3D.setPosition(openGLPpoint[0], openGLPpoint[1], openGLPpoint[2]);
        return object3D;
    }

}

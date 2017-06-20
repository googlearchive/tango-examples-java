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
package com.projecttango.examples.java.planefitting;

import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.support.TangoSupport;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.Renderer;

import java.util.Stack;
import javax.microedition.khronos.opengles.GL10;

/**
 * Very simple example augmented reality renderer which displays a cube fixed in place.
 * The position of the cube in the OpenGL world is updated using the {@code updateObjectPose}
 * method.
 */
public class PlaneFittingRenderer extends Renderer {
    private static final String TAG = PlaneFittingRenderer.class.getSimpleName();
    private static final float CUBE_SIDE_LENGTH = 0.5f;
    private static final float AXIS_THICKNESS = 20.0f;

    private static final Matrix4 DEPTH_T_OPENGL = new Matrix4(new float[] {
            1.0f,  0.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 1.0f, 0.0f,
            0.0f, -1.0f, 0.0f, 0.0f,
            0.0f,  0.0f, 0.0f, 1.0f
    });

    private float[] textureCoords0 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    // Augmented Reality related fields.
    private ATexture mTangoCameraTexture;
    private boolean mSceneCameraConfigured;

    private Object3D mObject;
    private Matrix4 mObjectTransform;
    private boolean mObjectPoseUpdated = false;

    private ScreenQuad mBackgroundQuad;

    public PlaneFittingRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
            mBackgroundQuad.getGeometry().setTextureCoords(textureCoords0);
        }
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering.
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            mBackgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(mBackgroundQuad, 0);

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        // Build a Axis and a cube to represent plane's transformation.

        Cube cube = new Cube(CUBE_SIDE_LENGTH);
        Material cubeMaterial = new Material();
        cubeMaterial.setColor(0xff009900);
        cubeMaterial.setColorInfluence(0.2f);
        cubeMaterial.enableLighting(true);
        cubeMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        try {
            Texture t = new Texture("instructions", R.drawable.instructions);
            cubeMaterial.addTexture(t);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();;
        }
        cube.setMaterial(cubeMaterial);

        Line3D xLine3d, yLine3d, zLine3d;
        Material lineMaterial = new Material();

        Stack<Vector3> xpoints = new Stack<Vector3>();
        xpoints.push(new Vector3(CUBE_SIDE_LENGTH, 0.0f, 0.0f));
        xpoints.push(new Vector3(0.0f, 0.0f, 0.0f));

        Stack<Vector3> ypoints = new Stack<Vector3>();
        ypoints.push(new Vector3(0.0f, 0.0f, 0.0f));
        ypoints.push(new Vector3(0.0f, CUBE_SIDE_LENGTH, 0.0f));

        Stack<Vector3> zpoints = new Stack<Vector3>();
        zpoints.push(new Vector3(0.0f, 0.0f, 0.0f));
        zpoints.push(new Vector3(0.0f, 0.0f, CUBE_SIDE_LENGTH));

        xLine3d = new Line3D(xpoints, AXIS_THICKNESS, Color.RED);
        xLine3d.setMaterial(lineMaterial);

        yLine3d = new Line3D(ypoints, AXIS_THICKNESS, Color.GREEN);
        yLine3d.setMaterial(lineMaterial);

        zLine3d = new Line3D(zpoints, AXIS_THICKNESS, Color.BLUE);
        zLine3d.setMaterial(lineMaterial);

        mObject = new Object3D();
        mObject.addChild(xLine3d);
        mObject.addChild(yLine3d);
        mObject.addChild(zLine3d);
        mObject.addChild(cube);
        cube.moveUp(CUBE_SIDE_LENGTH / 2.0);
        cube.moveForward(CUBE_SIDE_LENGTH / 2.0);
        cube.moveRight(CUBE_SIDE_LENGTH / 2.0);
        mObject.setPosition(0, 0, -3);
        getCurrentScene().addChild(mObject);
    }

    /**
     * Update background texture's UV coordinates when device orientation is changed (i.e., change
     * between landscape and portrait mode.
     * This must be run in the OpenGL thread.
     */
    public void updateColorCameraTextureUvGlThread(int rotation) {
        if (mBackgroundQuad == null) {
            mBackgroundQuad = new ScreenQuad();
        }

        float[] textureCoords =
                TangoSupport.getVideoOverlayUVBasedOnDisplayRotation(textureCoords0, rotation);
        mBackgroundQuad.getGeometry().setTextureCoords(textureCoords, true);
        mBackgroundQuad.getGeometry().reload();
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        // Update the AR object if necessary.
        // Synchronize against concurrent access with the setter below.
        synchronized (this) {
            if (mObjectPoseUpdated) {
                // Place the 3D object in the location of the detected plane.
                mObject.setPosition(mObjectTransform.getTranslation());
                // Note that Rajawali uses left-hand convention for Quaternions so we need to
                // specify a quaternion with rotation in the opposite direction.
                mObject.setOrientation(new Quaternion().fromMatrix(mObjectTransform));
                // Move it forward by half of the size of the cube to make it
                // flush with the plane surface.
                mObjectPoseUpdated = false;
            }
        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Save the updated plane fit pose to update the AR object on the next render pass.
     * This is synchronized against concurrent access in the render loop above.
     */
    public synchronized void updateObjectPose(
            float[] openglTDepthArr,
            float[] mDepthTPlaneArr) {
        Matrix4 openglTDepth = new Matrix4(openglTDepthArr);
        Matrix4 openglTPlane =
                openglTDepth.multiply(new Matrix4(mDepthTPlaneArr));

        mObjectTransform = openglTPlane.multiply(DEPTH_T_OPENGL);;
        mObjectPoseUpdated = true;
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The camera pose should match the pose of the camera color at the time of the last rendered
     * RGB frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention for
        // quaternions.
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread; it is not thread safe.
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
     * Sets the projection matrix for the scene camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(float[] matrix) {
        getCurrentCamera().setProjectionMatrix(new Matrix4(matrix));
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

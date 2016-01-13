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
package com.projecttango.rajawali.ar;

import android.content.Context;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.RajawaliRenderer;

import javax.microedition.khronos.opengles.GL10;

/**
 * This is a specialization of <code>RajawaliRenderer</code> that makes it easy to build
 * augmented reality applications.
 * It sets up a simple scene with the camera contents rendered in full screen in the background and
 * a default Rajawali <code>Camera</code> automatically adjusted to replicate the real world
 * movement of the Tango device in the virtual world.
 *
 * It is used the same as any <code>RajawaliRenderer</code> with the following additional
 * considerations:
 *
 *  - It is optional (although recommended) to overwrite the <code>initScene</code> method. If this
 *    method is overwritten, it is important to call <code>super.initScene()</code> at the beginning
 *    of the overwriting method.
 *  - It is important to be careful to not clear the scene since this will remove the Tango camera
 *    from the background.
 *  - In most cases the Rajawali camera will not be handled by the user since it is automatically
 *    handled by this mRenderer.
 */
public abstract class TangoRajawaliRenderer extends RajawaliRenderer {
    private static final String TAG = TangoRajawaliRenderer.class.getSimpleName();

    private static TangoCoordinateFramePair TANGO_WORLD_T_DEVICE = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE
    );

    // Rajawali scene objects to render the color camera
    private StreamingTexture mTangoCameraTexture;
    private ScreenQuad mBackgroundQuad;

    private Tango mTango;
    private int mCameraId;
    private boolean mUpdatePending = false;
    private int mConnectedTextureId = -1;
    private double mLastRGBFrameTimestamp = -1;
    private boolean mIsCameraConfigured = false;
    private Matrix4 mProjectionMatrix;

    public TangoRajawaliRenderer(Context context) {
        super(context);
    }

    /**
     * Sets up the initial scene with a default Rajawali camera and a background quad rendering
     * the Tango camera contents.
     */
    @Override
    protected void initScene() {
        mBackgroundQuad = new ScreenQuad();

        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);

        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            mBackgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
        getCurrentScene().addChildAt(mBackgroundQuad, 0);
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        synchronized (this) {
            // mTango != null is used to indicate that a Tango device is connected to this
            // renderer, via a corresponding TangoRajawaliView
            if (mTango != null) {
                try {
                    if (mUpdatePending) {
                        mLastRGBFrameTimestamp = updateTexture();
                        mUpdatePending = false;
                    }
                    if (!mIsCameraConfigured) {
                        getCurrentCamera().setProjectionMatrix(mProjectionMatrix);
                        mIsCameraConfigured = true;
                    }
                } catch (TangoInvalidException ex) {
                    Log.e(TAG, "Error while updating texture!", ex);
                } catch (TangoErrorException ex) {
                    Log.e(TAG, "Error while updating texture!", ex);
                }
            }
        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Updates the TangoCameraMaterial with the latest camera data.
     *
     * @return the timestamp of the RGB image rendered into the texture.
     */
    private double updateTexture() {
        // Try this again here because it is possible that when the user called
        // connectToTangoCamera the texture wasn't assigned yet and the connection couldn't
        // be done
        if (mConnectedTextureId != getTextureId()) {
            mConnectedTextureId = connectTangoTexture();
        }
        if (mConnectedTextureId != -1) {
            // Copy the camera frame from the camera to the OpenGL texture
            return mTango.updateTexture(this.mCameraId);
        }
        return -1.0;
    }

    private int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    private int connectTangoTexture() {
        int textureId = -1;
        if (mTangoCameraTexture != null) {
            textureId = mTangoCameraTexture.getTextureId();
        }
        mTango.connectTextureId(mCameraId, textureId);
        return textureId;
    }

    /**
     * Intended to be called from <code>TangoRajawaliView</code>.
     */
    synchronized void connectCamera(Tango tango, int cameraId) {
        mTango = tango;
        mCameraId = cameraId;
        mConnectedTextureId = connectTangoTexture();
        TangoCameraIntrinsics intrinsics = tango.getCameraIntrinsics(mCameraId);
        mProjectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
    }

    /**
     * Intended to be called from <code>TangoRajawaliView</code>.
     */
    synchronized void disconnectCamera() {
        Tango oldTango = mTango;
        mTango = null;
        if (oldTango != null) {
            oldTango.disconnectCamera(mCameraId);
        }
        mConnectedTextureId = -1;
        mIsCameraConfigured = false;
    }

    /**
     * Intended to be called from <code>TangoRajawaliView</code>.
     */
    synchronized void onTangoFrameAvailable() {
        mUpdatePending = true;
    }

    /**
     * Get the latest camera frame timestamp. This value will be updated when
     * the updateTexture() is called.
     *
     * @return The timestamp. This can be used to associate camera data with a
     * pose or other sensor data using other pieces of the Tango API.
     */
    synchronized public double getTimestamp() {
        return mLastRGBFrameTimestamp;
    }

    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        // The camera projection matrix gets reset whenever the render surface is changed
        mIsCameraConfigured = false;
    }
}

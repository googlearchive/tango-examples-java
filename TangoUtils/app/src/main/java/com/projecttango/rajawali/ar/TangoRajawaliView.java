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
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.google.atap.tangoservice.Tango;

import org.rajawali3d.surface.RajawaliSurfaceView;

/**
 * This is a specialized <code>RajawaliSurfaceView</code> that allows rendering of a Rajawali scene
 * together with the Tango Camera Preview and optionally using the Tango Pose Estimation to drive
 * the Rajawali Camera and build Augmented Reality applications.
 *
 */
public class TangoRajawaliView extends RajawaliSurfaceView {
    private static final String TAG = "TangoRajawaliView";
    TangoRajawaliRenderer mRenderer;

    public TangoRajawaliView(Context context) {
        super(context);
        // It is important to set render mode to manual to force rendering only when there is a
        // Tango Camera image available and get correct synchronization between the camera and the
        // rest of the scene.
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public TangoRajawaliView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // It is important to set render mode to manual to force rendering only when there is a
        // Tango Camera image available and get correct synchronization between the camera and the
        // rest of the scene.
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    /**
     * Sets up the <code>RajawaliRenderer</code> that will be used to render the scene.
     * In order to use Tango components, a subclass of either <code>TangoRajawaliRenderer</code>
     * or <code>TangoRajawaliArRenderer</code> should be used.
     */
    public void setSurfaceRenderer(TangoRajawaliRenderer renderer) throws IllegalStateException {
        super.setSurfaceRenderer(renderer);
        this.mRenderer = renderer;
    }

    /**
     * Get the latest camera data's timestamp. This value will be updated when
     * the updateTexture() is called.
     *
     * @return The timestamp. This can be used to associate camera data with a
     * pose or other sensor data using other pieces of the Tango API.
     *
     */
    public double getTimestamp() {
        return mRenderer.getTimestamp();
    }

    /**
     * Updates the TangoRajawaliView with the latest camera data. This method
     * synchronizes the data in the OpenGL context.
     *
     * Call this method from the onFrameAvailable() method of
     * Tango.OnTangoUpdateListener, which provides a set of callbacks for
     * getting updates from the Project Tango sensors.
     */
    public synchronized void onFrameAvailable() {
        mRenderer.onTangoFrameAvailable();
        requestRender();
    }

    /**
     * Gets a textureId from a valid OpenGL Context through Rajawali and connects it to the
     * TangoRajawaliView.
     *
     * Use OnFrameAvailable events or updateTexture calls to update the view with
     * the latest camera data. Only the RGB and fisheye cameras are currently
     * supported.
     *
     * @param tango A reference to the Tango service.
     * @param cameraId The id of the camera to connect to. Ids listed in
     * TangoCameraIntrinsics.
     */
    public void connectToTangoCamera(Tango tango, int cameraId) {
        mRenderer.connectCamera(tango, cameraId);
    }

    /**
     * Disables rendering of the Tango camera in the TangoCameraMaterial.
     * Should be called before disconnecting from the Tango service.
     */
    public void disconnectCamera() {
        mRenderer.disconnectCamera();
    }
}

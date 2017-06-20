/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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
package com.projecttango.examples.java.markerdetection;

import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.markers.TangoMarkers;
import com.google.tango.support.TangoSupport;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.scene.Scene;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer that implements a basic augmented reality scene using Rajawali.
 * It creates a scene with a background using color camera image, and renders the bounding box and
 * three axes of any marker that has been detected in the image.
 */
public class MarkerDetectionRenderer extends Renderer {
    private static final String TAG = MarkerDetectionRenderer.class.getSimpleName();
    private static final int MAX_MARKER_ID = 255;

    private float[] textureCoords0 = new float[] {0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};

    // Rajawali texture used to render the Tango color camera.
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured.
    private boolean mSceneCameraConfigured;

    private ScreenQuad mBackgroundQuad;

    // All markers
    private Map<String, MarkerObject> mMarkerObjects;

    public MarkerDetectionRenderer(Context context) {
        super(context);
    }

  @Override
  protected void initScene() {
      if (mMarkerObjects == null) {
          mMarkerObjects = new HashMap();
      }

      // Create a quad covering the whole background and assign a texture to it where the
      // Tango color camera contents will be rendered.
      Material tangoCameraMaterial = new Material();
      tangoCameraMaterial.setColorInfluence(0);

      if (mBackgroundQuad == null) {
          mBackgroundQuad = new ScreenQuad();
          mBackgroundQuad.getGeometry().setTextureCoords(textureCoords0);
      }
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

      Scene scene = getCurrentScene();
      scene.addChildAt(mBackgroundQuad, 0);

      // Create marker objects and add them to the scene.
      for (int i = 0; i <= MAX_MARKER_ID; i++) {
        MarkerObject newObject = new MarkerObject();
        mMarkerObjects.put(Integer.toString(i), newObject);
        newObject.addToScene(scene);
      }
  }

  /**
   * Update marker objects and scene.
   */
  public void updateMarkers(List<TangoMarkers.Marker> markerList) {
      if (markerList != null && markerList.size() > 0) {
          // Update objects with new marker poses.
          for (int i = 0; i < markerList.size(); i++) {
              TangoMarkers.Marker marker = markerList.get(i);
              Log.w(TAG, "Marker detected[" + i + "] = " + marker.content);

              // Update the marker coordinates if it has been created.
              MarkerObject existingObject = mMarkerObjects.get(marker.content);
              if (existingObject != null) {
                  existingObject.updateGeometry(marker);
              } else {
                Log.e(TAG, "Marker id is out of range!");
              }
          }
      }
  }

  /**
   * Update background texture's UV coordinates when device orientation is changed (i.e., change
   * between landscape and portrait mode).
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

  /**
   * Update the scene camera based on the provided pose in Tango start of service frame.
   * The camera pose should match the pose of the camera color at the time of the last rendered
   * RGB frame, which can be retrieved with this.getTimestamp();
   * <p/>
   * NOTE: This must be called from the OpenGL render thread; it is not thread-safe.
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
   * NOTE: This must be called from the OpenGL render thread; it is not thread-safe.
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
  public void setProjectionMatrix(float[] matrixFloats) {
      getCurrentCamera().setProjectionMatrix(new Matrix4(matrixFloats));
  }

  @Override
  public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
      int xPixelOffset, int yPixelOffset) {}

  @Override
  public void onTouchEvent(MotionEvent event) {}
}

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

package com.projecttango.examples.java.occlusion;

import com.google.atap.tango.mesh.TangoMesh;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.examples.java.occlusion.meshing.TangoMesher;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build an application that implements occlusion of virtual objects.
 * It uses {@code TangoMesher} to do a mesh reconstruction of the scene. The meshes are then
 * rendered to a frame buffer to make a depth texture.
 * The occlusion of the virtual objects is decided per fragment in the fragment shader using
 * depth information.
 */
public class OcclusionActivity extends Activity implements View.OnTouchListener {
    private static final String TAG = OcclusionActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    private GLSurfaceView mSurfaceView;
    private OcclusionRenderer mRenderer;
    private TangoMesher mTangoMesher;
    private volatile TangoMesh[] mMeshVector;
    private Tango mTango;
    private TangoConfig mConfig;
    private TangoPointCloudManager mPointCloudManager;
    private boolean mIsConnected = false;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private int mDisplayRotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        // Set ZOrderOnTop to false so the other views don't get hidden by the SurfaceView.
        mSurfaceView.setZOrderOnTop(false);
        mSurfaceView.setOnTouchListener(this);
        mPointCloudManager = new TangoPointCloudManager();
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }
        connectRenderer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mSurfaceView.onResume();

        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until
        // the Tango service is properly set up and we start getting onFrameAvailable callbacks.
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSurfaceView.onPause();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            try {
                mTangoMesher.stopSceneReconstruction();
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
                mTangoMesher.resetSceneReconstruction();
                mTangoMesher.release();
                mIsConnected = false;
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Synchronize against disconnecting while the service is being used in the OpenGL
        // thread or in the UI thread.
        if (!mIsConnected) {
            // Initialize Tango Service as a normal Android Service. Since we call
            // mTango.disconnect() in onPause, this will unbind Tango Service, so
            // every time onResume gets called we should create a new Tango object.
            mTango = new Tango(OcclusionActivity.this, new Runnable() {
                // Pass in a Runnable to be called from UI thread when Tango is ready;
                // this Runnable will be running on a new thread.
                // When Tango is ready, we can call Tango functions safely here only
                // when there are no UI thread changes involved.
                @Override
                public void run() {
                    try {
                        synchronized (OcclusionActivity.this) {
                            TangoSupport.initialize();
                            mConfig = setupTangoConfig(mTango);
                            mTango.connect(mConfig);
                            startupTango();
                            mIsConnected = true;
                            setDisplayRotation();
                        }
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            });
        }
    }

    /**
     * Configure how we connect to Tango service.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Start from default configuration for Tango Service.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of virtual
        // objects with the RGB image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        // Depth information is needed for the mesh reconstruction.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        // Color camera is needed for AR.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        return config;
    }

    /**
     * Connect tango to callbacks and start TangoMesher.
     */
    private void startupTango() {
        // Connect listeners to Tango Service and forward point cloud information to TangoMesher.
        List<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData tangoPoseData) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Now that we are receiving onFrameAvailable callbacks, we can switch
                    // to RENDERMODE_WHEN_DIRTY to drive the render loop from this callback.
                    // This will result on a frame rate of approximately 30FPS, in synchrony with
                    // the RGB camera driver.
                    // If you need to render at a higher rate (i.e., if you want to render complex
                    // animations smoothly) you  can use RENDERMODE_CONTINUOUSLY throughout the
                    // application lifecycle.
                    if (mSurfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }

                    // Mark a camera frame as available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an OpenGL render to update the OpenGL scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onTangoEvent(TangoEvent tangoEvent) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {
                if (mTangoMesher != null) {
                    mTangoMesher.onPointCloudAvailable(tangoPointCloudData);
                }
                if (mPointCloudManager != null) {
                    mPointCloudManager.updatePointCloud(tangoPointCloudData);
                }
            }
        });

        // Create a TangoMesher to do a 3D reconstruction of the scene to implement occlusion.
        mTangoMesher = new TangoMesher(new TangoMesher.OnTangoMeshesAvailableListener() {
            @Override
            public void onMeshesAvailable(TangoMesh[] tangoMeshes) {
                mMeshVector = tangoMeshes;
            }
        });

        // Set camera intrinsics to TangoMesher.
        mTangoMesher.setColorCameraCalibration(mTango.getCameraIntrinsics(TangoCameraIntrinsics
                .TANGO_CAMERA_COLOR));
        mTangoMesher.setDepthCameraCalibration(mTango.getCameraIntrinsics(TangoCameraIntrinsics
                .TANGO_CAMERA_DEPTH));
        // Start the scene reconstruction. We will start getting new meshes from TangoMesher. These
        // meshes will be rendered to a depth texture to do the occlusion.
        mTangoMesher.startSceneReconstruction();
    }

    /**
     * Connects the view and renderer to the color camera and callbacks.
     */
    private void connectRenderer() {
        mSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new OcclusionRenderer(OcclusionActivity.this, new OcclusionRenderer
                .RenderCallback() {
            @Override
            public void preRender() {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks have a chance to run and before scene objects are rendered
                // into the scene.

                try {
                    // Synchronize against disconnecting while using the service.
                    synchronized (OcclusionActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the
                        // service.
                        if (!mIsConnected) {
                            return;
                        }

                        // Set up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isProjectionMatrixConfigured()) {
                            // Set up scene camera projection to match RGB camera intrinsics.
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            mDisplayRotation);
                            mRenderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(intrinsics), 0.1f, 100f);
                        }
                        // Connect the Tango SDK to the OpenGL texture ID where we are
                        // going to render the camera.
                        // NOTE: This must be done after both the texture is generated
                        // and the Tango Service is connected.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }
                        // If there is a new RGB camera frame available, update the texture and
                        // scene camera pose.
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.
                                            TANGO_CAMERA_COLOR);
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            TangoSupport.TangoMatrixTransformData ssTrgb =
                                    TangoSupport.getMatrixTransformAtTime(
                                            mRgbTimestampGlThread,
                                            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                            TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                            mDisplayRotation);

                            if (ssTrgb.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer.
                                mRenderer.updateViewMatrix(ssTrgb.matrix);
                            } else {
                                Log.w(TAG, "Can't get last camera pose");
                            }
                        }
                    }
                    // Update mesh.
                    updateMeshMap();

                    // Avoid crashing the application due to unhandled exceptions.
                } catch (TangoErrorException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                } catch (TangoInvalidException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                }
            }
        });
        mSurfaceView.setRenderer(mRenderer);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the OpenGL scene.
     *
     * @param intrinsics camera instrinsics for computing the project matrix.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;
        float width = (float) intrinsics.width;
        float height = (float) intrinsics.height;
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;

        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        float xScale = near / fx;
        float yScale = near / fy;
        float xOffset = (cx - (width / 2.0f)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = -(cy - (height / 2.0f)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -width / 2.0f - xOffset,
                xScale * (float) width / 2.0f - xOffset,
                yScale * (float) -height / 2.0f - yOffset,
                yScale * (float) height / 2.0f - yOffset,
                near, far);
        return m;
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mIsConnected) {
                    mRenderer.updateColorCameraTextureUv(mDisplayRotation);
                }
            }
        });
    }

    /**
     * Replace the earth 30 cm above the clicked point.
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Fit a plane on the clicked point using the latest point cloud data.
                // Synchronize against concurrent access to the RGB timestamp in the OpenGL thread
                // and a possible service disconnection due to an onPause event.
                float[] planeFitTransform;
                synchronized (this) {
                    planeFitTransform = doFitPlane(u, v, mRgbTimestampGlThread);
                }

                if (planeFitTransform != null) {
                    // Place the earth 30 cm above the plane.
                    Matrix.translateM(planeFitTransform, 0, 0, 0, 0.3f);
                    mRenderer.updateEarthTransform(planeFitTransform);
                }

            } catch (TangoException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_measurement,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_measurement), t);
            } catch (SecurityException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_permissions,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_permissions), t);
            }
        }
        return true;
    }

    /**
     * Use the Tango Support Library with point cloud data to calculate the plane
     * of the world feature pointed at the location the camera is looking.
     * It returns the transform of the fitted plane in a double array.
     */
    private float[] doFitPlane(float u, float v, double rgbTimestamp) {
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();

        if (pointCloud == null) {
            return null;
        }

        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData depthTcolorPose = TangoSupport.calculateRelativePose(
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR);

        // Perform plane fitting with the latest available point cloud data.
        double[] identityTranslation = {0.0, 0.0, 0.0};
        double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
        TangoSupport.IntersectionPointPlaneModelPair intersectionPointPlaneModelPair =
                TangoSupport.fitPlaneModelNearPoint(pointCloud,
                        identityTranslation, identityRotation, u, v, mDisplayRotation,
                        depthTcolorPose.translation, depthTcolorPose.rotation);

        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
        TangoSupport.TangoMatrixTransformData transform =
                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);
        if (transform.statusCode == TangoPoseData.POSE_VALID) {
            float[] openGlTPlane = calculatePlaneTransform(
                    intersectionPointPlaneModelPair.intersectionPoint,
                    intersectionPointPlaneModelPair.planeModel, transform.matrix);

            return openGlTPlane;
        } else {
            Log.w(TAG, "Can't get depth camera transform at time " + pointCloud.timestamp);
            return null;
        }
    }

    /**
     * Calculate the pose of the plane based on the position and normal orientation of the plane
     * and align it with gravity.
     */
    private float[] calculatePlaneTransform(double[] point, double normal[],
                                            float[] openGlTdepth) {
        // Vector aligned to gravity.
        float[] openGlUp = new float[]{0, 1, 0, 0};
        float[] depthTOpenGl = new float[16];
        Matrix.invertM(depthTOpenGl, 0, openGlTdepth, 0);
        float[] depthUp = new float[4];
        Matrix.multiplyMV(depthUp, 0, depthTOpenGl, 0, openGlUp, 0);
        // Create the plane matrix transform in depth frame from a point, the plane normal and the
        // up vector.
        float[] depthTplane = matrixFromPointNormalUp(point, normal, depthUp);
        float[] openGlTplane = new float[16];
        Matrix.multiplyMM(openGlTplane, 0, openGlTdepth, 0, depthTplane, 0);
        return openGlTplane;
    }

    /**
     * Calculates a transformation matrix based on a point, a normal and the up gravity vector.
     * The coordinate frame of the target transformation will be a right handed system with Z+ in
     * the direction of the normal and Y+ up.
     */
    private float[] matrixFromPointNormalUp(double[] point, double[] normal, float[] up) {
        float[] zAxis = new float[]{(float) normal[0], (float) normal[1], (float) normal[2]};
        normalize(zAxis);
        float[] xAxis = crossProduct(up, zAxis);
        normalize(xAxis);
        float[] yAxis = crossProduct(zAxis, xAxis);
        normalize(yAxis);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        m[0] = xAxis[0];
        m[1] = xAxis[1];
        m[2] = xAxis[2];
        m[4] = yAxis[0];
        m[5] = yAxis[1];
        m[6] = yAxis[2];
        m[8] = zAxis[0];
        m[9] = zAxis[1];
        m[10] = zAxis[2];
        m[12] = (float) point[0];
        m[13] = (float) point[1];
        m[14] = (float) point[2];
        return m;
    }

    /**
     * Normalize a vector.
     */
    private void normalize(float[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= norm;
        v[1] /= norm;
        v[2] /= norm;
    }

    /**
     * Cross product between two vectors following the right hand rule.
     */
    private float[] crossProduct(float[] v1, float[] v2) {
        float[] result = new float[3];
        result[0] = v1[1] * v2[2] - v2[1] * v1[2];
        result[1] = v1[2] * v2[0] - v2[2] * v1[0];
        result[2] = v1[0] * v2[1] - v2[0] * v1[1];
        return result;
    }

    /**
     * Updates the rendered mesh map if a new mesh vector is available.
     * This is run in the OpenGL thread.
     */
    private void updateMeshMap() {
        if (mMeshVector != null) {
            Log.d(TAG, "Got mesh");
            for (TangoMesh tangoMesh : mMeshVector) {
                if (tangoMesh != null && tangoMesh.numFaces > 0) {
                    mRenderer.updateMesh(tangoMesh);
                }
            }
            mMeshVector = null;
        }
    }

    /**
     * Check to see if we have the necessary permissions for this app; ask for them if we don't.
     *
     * @return True if we have the necessary permissions, false if we don't.
     */
    private boolean checkAndRequestPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    /**
     * Check to see if we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Occlusion Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(OcclusionActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OcclusionActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (hasCameraPermission()) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java Occlusion Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}

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

package com.projecttango.examples.java.meshbuilder;

import com.google.atap.tango.mesh.TangoMesh;
import com.google.atap.tango.reconstruction.TangoMesher;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build a very simple application that allows the user to create a mesh
 * in Java. It uses {@code TangoMesher} to do a mesh reconstruction of the scene.
 */
public class MeshBuilderActivity extends Activity {
    private static final String TAG = MeshBuilderActivity.class.getSimpleName();

    private GLSurfaceView mSurfaceView;
    private MeshBuilderRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private TangoMesher mTangoMesher;
    private volatile TangoMesh[] mMeshVector;
    private Tango mTango;
    private boolean mIsConnected = false;
    private Button mPauseButton;
    private boolean mIsPaused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPauseButton = (Button) findViewById(R.id.pause_button);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        // Set ZOrderOnTop to false so the other views don't get hidden by the SurfaceView.
        mSurfaceView.setZOrderOnTop(false);
        connectRenderer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSurfaceView.onPause();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            if (mIsConnected) {
                mTangoMesher.stopSceneReconstruction();
                mTangoMesher.resetSceneReconstruction();
                mTango.disconnect();
                mIsConnected = false;
                mIsPaused = true;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        // Check if it has permissions.
        // Dataset permissions are needed in order to build the mesh.
        if (Tango.hasPermission(this, Tango.PERMISSIONTYPE_DATASET)) {
            connectAndStart();
        } else {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_DATASET),
                    Tango.TANGO_INTENT_ACTIVITYCODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Dataset Permissions Required!",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * Connect to Tango service and connect the camera to the renderer.
     */
    private void connectAndStart() {
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        if (!mIsConnected) {
            // Initialize Tango Service as a normal Android Service, since we call
            // mTango.disconnect() in onPause, this will unbind Tango Service, so
            // everytime when onResume get called, we should create a new Tango object.
            mTango = new Tango(MeshBuilderActivity.this, new Runnable() {
                // Pass in a Runnable to be called from UI thread when Tango is ready,
                // this Runnable will be running on a new thread.
                // When Tango is ready, we can call Tango functions safely here only
                // when there is no UI thread changes involved.
                @Override
                public void run() {
                    try {
                        synchronized (MeshBuilderActivity.this) {
                            TangoSupport.initialize();
                            connectTango();
                            mIsConnected = true;
                        }
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    }
                }
            });
        }
    }


    /**
     * Configure the Tango service and connect it to callbacks.
     */
    private void connectTango() {
        // Start from default configuration for Tango Service.
        TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of virtual
        // objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_SMOOTH_POSE, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        // Enable scene reconstruction to allow TangoMesher to build a mesh reconstruction.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_EXPERIMENTAL_ENABLE_SCENE_RECONSTRUCTION,
                true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DATASETRECORDING, true);
        config.putInt(TangoConfig.KEY_INT_DATASETRECORDING_MODE,
                TangoConfig.TANGO_DATASETRECORDING_MODE_SCENE_RECONSTRUCTION);
        mTango.connect(config);

        mTangoMesher = new TangoMesher(new TangoMesher.OnTangoMeshesAvailableListener() {
            @Override
            public void onMeshesAvailable(TangoMesh[] tangoMeshes) {
                mMeshVector = tangoMeshes;
            }
        });

        // Connect listeners to tnago service and forward point cloud and camera information to
        // TangoMesher.
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
            public void onFrameAvailable(int i) {
                // We are not using onFrameAvailable for this app.
            }

            @Override
            public void onTangoEvent(TangoEvent tangoEvent) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData tangoPointCloudData) {
                mTangoMesher.onPointCloudAvailable(tangoPointCloudData);
            }
        });
        mTango.experimentalConnectOnFrameListener(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, new
                Tango.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int i) {
                mTangoMesher.onFrameAvailable(tangoImageBuffer, i);
            }
        });

        // Set camera intrinsics to TangoMesher.
        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
        mTangoMesher.setColorCameraCalibration(mIntrinsics);

        mTangoMesher.startSceneReconstruction();
        mIsPaused = false;
    }

    /**
     * Connects the view and renderer to the color camera and callbacks.
     */
    private void connectRenderer() {
        mSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new MeshBuilderRenderer(new MeshBuilderRenderer.RenderCallback() {
            @Override
            public void preRender() {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Synchronize against disconnecting while using the service.
                synchronized (MeshBuilderActivity.this) {
                    // Don't execute any tango API actions if we're not connected to the service
                    if (!mIsConnected) {
                        return;
                    }

                    // Set-up scene camera projection to match RGB camera intrinsics
                    if (!mRenderer.isSceneCameraConfigured()) {
                        mRenderer.setProjectionMatrix(
                                projectionMatrixFromCameraIntrinsics(mIntrinsics));
                    }

                    // Calculate the camera color pose at the camera frame update time in
                    // OpenGL engine.
                    TangoSupport.TangoMatrixTransformData ssTdev =
                            TangoSupport.getMatrixTransformAtTime(
                                    0.0, TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                    TangoPoseData.COORDINATE_FRAME_DEVICE,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL);

                    if (ssTdev.statusCode == TangoPoseData.POSE_VALID) {
                        // Update the camera pose from the renderer
                        mRenderer.updateViewMatrix(ssTdev.matrix);
                    } else {
                        Log.w(TAG, "Can't get last camera pose");
                    }
                }
                // Update mesh.
                updateMeshMap();
            }
        });
        mSurfaceView.setRenderer(mRenderer);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        float xScale = near / (float) intrinsics.fx;
        float yScale = near / (float) intrinsics.fy;
        float xOffset = (float) (intrinsics.cx - (intrinsics.width / 2.0)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        float yOffset = (float) -(intrinsics.cy - (intrinsics.height / 2.0)) * yScale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                xScale * (float) -intrinsics.width / 2.0f - xOffset,
                xScale * (float) intrinsics.width / 2.0f - xOffset,
                yScale * (float) -intrinsics.height / 2.0f - yOffset,
                yScale * (float) intrinsics.height / 2.0f - yOffset,
                near, far);
        return m;
    }


    public void onPauseButtonClick(View v) {
        if (mIsPaused) {
            mTangoMesher.startSceneReconstruction();
            mPauseButton.setText("Pause");
        } else {
            mTangoMesher.stopSceneReconstruction();
            mPauseButton.setText("Resume");
        }
        mIsPaused = !mIsPaused;
    }

    public void onClearButtonClicked(View v) {
        mTangoMesher.resetSceneReconstruction();
        mRenderer.clearMeshes();
    }

    /**
     * Updates the rendered mesh map is a new mesh vector is available.
     * This is run in the OpenGL thread.
     */
    private void updateMeshMap() {
        if (mMeshVector != null) {
            for (TangoMesh tangoMesh : mMeshVector) {
                if (tangoMesh != null && tangoMesh.numFaces > 0) {
                    mRenderer.updateMesh(tangoMesh);
                }
            }
            mMeshVector = null;
        }
    }
}

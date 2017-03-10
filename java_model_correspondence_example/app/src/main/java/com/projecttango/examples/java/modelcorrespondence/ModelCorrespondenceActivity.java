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

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
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
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build a very simple application that allows the user to make a
 * correspondence between a model and world coordinates. It will allow you to place a given model
 * in world coordinates and render it in augmented reality.
 * It uses the TangoSupportLibrary to find the correspondence similarity transform and to
 * measure depth points.
 * The model will be fixed at the upper left corner of the screen and will show which point must be
 * added next to make the correspondence. The correspondence points can be added with the '+'
 * button. Once all the correspondence points are added, the similarity transform is
 * calculated and the model object is placed in the desired location.
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 * <p/>
 * For more details on the augmented reality effects, including color camera texture rendering,
 * see java_augmented_reality_example or java_hello_video_example.
 */
public class ModelCorrespondenceActivity extends Activity {
    private static final String TAG = ModelCorrespondenceActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    private ImageButton mAddButton;
    private Button mUndoButton;
    private Button mResetButton;
    private SurfaceView mSurfaceView;
    private ModelCorrespondenceRenderer mRenderer;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private ImageView mCrosshair;
    // The destination points to make the correspondence.
    private List<float[]> mDestPointList;
    // The given data model.
    private HouseModel mHouseModel;
    // Transform of the house in OpenGl frame.
    private float[] mOpenGlTHouse;
    // A flag indicating whether the model was updated and must be re-rendered in the next loop.
    private boolean mModelUpdated;
    // If the correspondence is not done yet, the model object is fixed to the camera in the top
    // left corner.
    private boolean mCorrespondenceDone;
    // Rotation of the model around the Z axis when it is fixed to the camera. Used to show the next
    // correspondence point to be added.
    private float mModelZRotation;
    private ValueAnimator mZRotationAnimator;

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

        mAddButton = (ImageButton) findViewById(R.id.add_point_button);
        mUndoButton = (Button) findViewById(R.id.undo_button);
        mResetButton = (Button) findViewById(R.id.reset_button);
        mSurfaceView = (SurfaceView) findViewById(R.id.ar_view);
        mRenderer = new ModelCorrespondenceRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        // Set ZOrderOnTop to false so the other views don't get hidden by the SurfaceView.
        mSurfaceView.setZOrderOnTop(false);
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        mCrosshair = (ImageView) findViewById(R.id.crosshair);
        mCrosshair.setColorFilter(getResources().getColor(R.color.crosshair_ready));

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
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            try {
                mRenderer.getCurrentScene().clearFrameCallbacks();
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume.
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
                mIsConnected = false;
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(ModelCorrespondenceActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (ModelCorrespondenceActivity.this) {
                    try {
                        TangoSupport.initialize();
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        connectRenderer();
                        mIsConnected = true;
                        setDisplayRotation();
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
            }
        });

        // Reset status and correspondence every time we connect again to the service.
        // If we didn't do it, the old points wouldn't make sense.
        reset(null);
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service (motion tracking), plus low latency
        // IMU integration, color camera, and depth.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a
        // precise alignment of virtual objects with the RGB image and
        // produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera and Point Cloud.
     */
    private void startupTango() {
        // No need to add any coordinate frame pairs since we are not
        // using pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Mark a camera frame as available for rendering in the OpenGL thread
                    mIsFrameAvailableTangoThread.set(true);
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                // Save the cloud and point data for later use.
                mPointCloudManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }
        });
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void connectRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                try {
                    synchronized (ModelCorrespondenceActivity.this) {
                        // Don't execute any Tango API actions if we're not connected to the
                        // service.
                        if (!mIsConnected) {
                            return;
                        }

                        // Set up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isSceneCameraConfigured()) {
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            mDisplayRotation);
                            mRenderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(intrinsics));
                        }

                        // Connect the camera texture to the OpenGL Texture if necessary.
                        // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                        // texture with a different ID.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with
                        // it.
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match.
                        if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    mRgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    mDisplayRotation);

                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer.
                                mRenderer.updateRenderCameraPose(lastFramePose);
                                mCameraPoseTimestamp = lastFramePose.timestamp;
                                // While the correspondence is not done, fix the model to the upper
                                // right corner of the screen by following the camera.
                                if (!mCorrespondenceDone) {
                                    TangoSupport.TangoMatrixTransformData transform =
                                            TangoSupport.getMatrixTransformAtTime(
                                                    mCameraPoseTimestamp,
                                                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                                    mDisplayRotation);
                                    if (transform.statusCode == TangoPoseData.POSE_VALID) {
                                        // Place it in the top left corner, and rotate and scale it
                                        // accordingly.
                                        float[] rgbTHouse = calculateModelTransformFixedToCam
                                                (mDisplayRotation);
                                        // Combine the two transforms.
                                        float[] openGlTHouse = new float[16];
                                        Matrix.multiplyMM(openGlTHouse, 0, transform.matrix,
                                                0, rgbTHouse, 0);
                                        mOpenGlTHouse = openGlTHouse;
                                        mModelUpdated = true;
                                    } else {
                                        Log.w(TAG, "Can't get camera transform at time: " +
                                                mCameraPoseTimestamp);
                                    }
                                }
                            } else {
                                Log.w(TAG, "Can't get device pose at time: " +
                                        mRgbTimestampGlThread);
                            }
                        }

                        // If the model was updated then it must be re-rendered.
                        if (mModelUpdated) {
                            mRenderer.updateModelRendering(mHouseModel, mOpenGlTHouse,
                                    mDestPointList);
                            mModelUpdated = false;
                        }
                    }
                    // Avoid crashing the application due to unhandled exceptions.
                } catch (TangoErrorException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
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

        // Adjust camera intrinsics according to rotation from color camera to display.
        double cx = intrinsics.cx;
        double cy = intrinsics.cy;
        double width = intrinsics.width;
        double height = intrinsics.height;
        double fx = intrinsics.fx;
        double fy = intrinsics.fy;

        double xscale = near / fx;
        double yscale = near / fy;

        double xoffset = (cx - (width / 2.0)) * xscale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yoffset = -(cy - (height / 2.0)) * yscale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                (float) (xscale * -width / 2.0 - xoffset),
                (float) (xscale * width / 2.0 - xoffset),
                (float) (yscale * -height / 2.0 - yoffset),
                (float) (yscale * height / 2.0 - yoffset), near, far);
        return m;
    }

    /**
     * This method handles when the user clicks the add point button. It will try to find a point
     * using the point cloud and the TangoSupportLibrary in the aimed location with the crosshair.
     * The resulting point will be shown in AR as a red sphere.
     */
    public void addPoint(View view) {
        // Set the point position at the center of the screen.
        float u = .5f;
        float v = .5f;
        try {
            synchronized (this) {
                // Take a point measurement by using the TangoSupportLibrary and the point cloud
                // data.
                float[] point = doPointMeasurement(u, v, mRgbTimestampGlThread);
                // If the measurement was successful add it to the list and render a sphere.
                if (point != null) {
                    mDestPointList.add(point);
                    mModelUpdated = true;
                    // Rotate the model to show the next correspondence point to be added.
                    startRotationAnimation(mModelZRotation, mDestPointList.size() * 90);
                } else {
                    Toast.makeText(this, "Could not measure depth point", Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Could not measure point");
                }
                // If it's the last point, find the correspondence.
                if (mDestPointList.size() == mHouseModel.getNumberOfPoints()) {
                    findCorrespondence();
                    mAddButton.setVisibility(View.GONE);
                    mUndoButton.setVisibility(View.GONE);
                    mResetButton.setVisibility(View.VISIBLE);
                }
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

    /**
     * Discard the last recorded point.
     */
    public synchronized void undoPoint(View view) {
        if (!mDestPointList.isEmpty()) {
            mDestPointList.remove(mDestPointList.size() - 1);
            mModelUpdated = true;
            // Rotate the model in reverse to show the next correspondence point to be added.
            startRotationAnimation(mModelZRotation, mDestPointList.size() * 90);
        }
    }

    /**
     * Reset the example to the beginning.
     */
    public synchronized void reset(View view) {
        if (mZRotationAnimator != null) {
            mZRotationAnimator.cancel();
        }
        mHouseModel = new HouseModel();
        mOpenGlTHouse = new float[16];
        mDestPointList = new ArrayList<float[]>();
        mCorrespondenceDone = false;
        mModelUpdated = true;
        mModelZRotation = 0;
        mResetButton.setVisibility(View.GONE);
        mAddButton.setVisibility(View.VISIBLE);
        mUndoButton.setVisibility(View.VISIBLE);
    }

    /**
     * Use the TangoSupport library with point cloud data to calculate the point in OpenGL frame
     * pointed at the location the crosshair is aiming.
     */
    private float[] doPointMeasurement(float u, float v, double rgbTimestamp) {
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();

        if (pointCloud == null) {
            return null;
        }

        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData colorTdepthPose = TangoSupport.calculateRelativePose(
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

        // Get depth point with the latest available point cloud data.
        double[] identityTranslation = {0.0, 0.0, 0.0};
        double[] identityRotation = {0.0, 0.0, 0.0, 1.0};
        float[] point = TangoSupport.getDepthAtPointNearestNeighbor(pointCloud,
                colorTdepthPose.translation, colorTdepthPose.rotation, u, v, mDisplayRotation,
                identityTranslation, identityRotation);

        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
        TangoSupport.TangoMatrixTransformData transform =
                TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);
        if (transform.statusCode == TangoPoseData.POSE_VALID) {
            if (point == null) {
                return null;
            }

            float[] depthPoint = new float[]{point[0], point[1], point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, transform.matrix, 0, depthPoint, 0);

            return openGlPoint;
        } else {
            Log.w(TAG, "Can't get depth camera transform at time: " + pointCloud.timestamp);
            return null;
        }
    }


    /**
     * Calculate the correspondence transform between the given points in the data model and the
     * chosen points in world frame. Update the pose of the data model and render it in AR.
     * Uses the TangoSupport library to find the correspondence similarity transform.
     */
    public void findCorrespondence() {
        // Get the correspondence source 3D points.
        List<float[]> srcVectors = mHouseModel.getOpenGlModelPpoints(mOpenGlTHouse);
        double[][] src = new double[4][3];
        for (int i = 0; i < mHouseModel.getNumberOfPoints(); i++) {
            float[] v = srcVectors.get(i);
            src[i][0] = v[0];
            src[i][1] = v[1];
            src[i][2] = v[2];
        }
        // Get the correspondence destination 3D points.
        double[][] dest = new double[4][3];
        for (int i = 0; i < mHouseModel.getNumberOfPoints(); i++) {
            float[] v = mDestPointList.get(i);
            dest[i][0] = v[0];
            dest[i][1] = v[1];
            dest[i][2] = v[2];
        }

        // Find the correspondence similarity transform.
        double[] output = TangoSupport.findCorrespondenceSimilarityTransform(src, dest);
        // Place the model in the desired location.
        transformModel(toFloatArray(output));
    }

    /**
     * Update the pose of the model.
     */
    public void transformModel(float[] newTransform) {
        float[] newOpenGlTModel = new float[16];
        Matrix.multiplyMM(newOpenGlTModel, 0, newTransform, 0, mOpenGlTHouse, 0);
        mOpenGlTHouse = newOpenGlTModel;
        mModelUpdated = true;
        mCorrespondenceDone = true;
    }

    /**
     * Calculate the transform needed to place the model in the upper left corner of the camera,
     * and rotate it to show the next point to make the correspondence.
     */
    private float[] calculateModelTransformFixedToCam(int mDisplayRotation) {
        // Translate to the upper left corner and ahead of the cam if the device is in landscape
        // mode or to the upper center if it is in portrait mode.
        float[] rgbTHouse = new float[16];
        Matrix.setIdentityM(rgbTHouse, 0);
        if (mDisplayRotation == Surface.ROTATION_0 || mDisplayRotation == Surface.ROTATION_180) {
            Matrix.translateM(rgbTHouse, 0, 0f, 1.2f, -4);
        } else {
            Matrix.translateM(rgbTHouse, 0, -1.5f, 0.3f, -4);
        }

        // Rotate it 180 degrees around the Z axis to show the front of the house as default
        // orientation.
        Matrix.rotateM(rgbTHouse, 0, 180, 0, 0, 1);
        // Rotate it around the X axis so it looks better as seen from above.
        Matrix.rotateM(rgbTHouse, 0, 70, 1, 0, 0);
        // Rotate it around the Z axis to show the next correspondence point to be added.
        Matrix.rotateM(rgbTHouse, 0, -mModelZRotation, 0, 0, 1);
        // Scale it to a proper size.
        Matrix.scaleM(rgbTHouse, 0, 0.03f, 0.03f, 0.03f);
        return rgbTHouse;
    }

    /**
     * Animate rotation value to show the next point of the model to make the correspondence.
     */
    private void startRotationAnimation(float start, float end) {
        if (mZRotationAnimator != null) {
            mZRotationAnimator.cancel();
        }
        mZRotationAnimator = ValueAnimator.ofFloat(start, end);
        mZRotationAnimator.setDuration(1500);
        mZRotationAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Float value = (Float) animation.getAnimatedValue();
                mModelZRotation = value;
            }
        });
        mZRotationAnimator.start();
    }

    float[] toFloatArray(double[] source) {
        float[] dest = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            dest[i] = (float) source[i];
        }
        return dest;
    }

    /**
     * Set the color camera background texture rotation and save the display rotation.
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
                    mRenderer.updateColorCameraTextureUvGlThread(mDisplayRotation);
                }
            }
        });
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
                .setMessage("Java Model Correspondence Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(ModelCorrespondenceActivity.this,
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
                Toast.makeText(ModelCorrespondenceActivity.this,
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
            Toast.makeText(this, "Java Model Correspondence Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}

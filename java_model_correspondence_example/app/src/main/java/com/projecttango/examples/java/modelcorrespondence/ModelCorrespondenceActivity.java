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
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

/**
 * An example showing how to build a very simple application that allows the user to make a
 * correspondence between a model and world coordinates. It will allow to place a given model in
 * world coordinates and render it in augmented reality.
 * It uses the TangoSupportLibrary to find the correspondence similarity transform and to
 * measure depth points.
 * The model will be fixed at the upper left corner of the screen and will show which point must be
 * added next to make the correspondence. The correspondence points can be added with the '+'
 * button. Once all the correspondence points were added, the similiraty transform will be
 * calculated, and the model object will be placed in the desired location.
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 * <p/>
 * For more details on the augmented reality effects, including color camera texture rendering,
 * see java_augmented_reality_example or java_hello_video_example.
 */
public class ModelCorrespondenceActivity extends Activity {
    private static final String TAG = ModelCorrespondenceActivity.class.getSimpleName();
    // Record Device to Start of Service as the main frame pair to be used for device pose
    // queries.
    private static final TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE);
    private static final int INVALID_TEXTURE_ID = 0;

    private ImageButton mAddButton;
    private Button mUndoButton;
    private Button mResetButton;
    private RajawaliSurfaceView mSurfaceView;
    private ModelCorrespondenceRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private ImageView mCrosshair;
    // The destination points to make the correspondence.
    private List<float[]> mDestPointList;
    // The given data model.
    private HouseModel mHouseModel;
    // Transform of the house in OpenGl frame.
    private float[] mOpenGlTHouse;
    // A flag indicating whether the model was updated and must be re rendered in the next loop.
    private boolean mModelUpdated;
    // If the correspondence was not done yet, the model object is fixed to the camera in the top
    // left corner.
    private boolean mCorrespondenceDone;
    // Rotation of the model around the Z axis when it is fixed to the camera. Used to show the next
    // correspondence point to be added.
    private float mModelZRotation;
    private ValueAnimator mZRotationAnimator;

    // Texture rendering related fields
    // NOTE: Naming indicates which thread is in charge of updating this variable
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAddButton = (ImageButton) findViewById(R.id.add_point_button);
        mUndoButton = (Button) findViewById(R.id.undo_button);
        mResetButton = (Button) findViewById(R.id.reset_button);
        mSurfaceView = (RajawaliSurfaceView) findViewById(R.id.ar_view);
        mRenderer = new ModelCorrespondenceRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        // Set ZOrderOnTop to false so the other views don't get hidden by the SurfaceView.
        mSurfaceView.setZOrderOnTop(false);
        mTango = new Tango(this);
        mPointCloudManager = new TangoPointCloudManager();
        mCrosshair = (ImageView) findViewById(R.id.crosshair);
        mCrosshair.setColorFilter(getResources().getColor(R.color.crosshair_ready));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        synchronized (this) {
            if (mIsConnected) {
                mRenderer.getCurrentScene().clearFrameCallbacks();
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a re-connection
                // in the OpenGL thread after resume
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
                mIsConnected = false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectAndStart();
        // Reset status and correspondence every time we connect again to the service.
        // If we didn't do it, then the old points wouldn't make sense.
        mHouseModel = new HouseModel();
        mModelUpdated = true;
        mCorrespondenceDone = false;
        mOpenGlTHouse = new float[16];
        mDestPointList = new ArrayList<float[]>();
    }

    /**
     * Connect to Tango service and connect the camera to the renderer.
     */
    private void connectAndStart() {
        if (!mIsConnected) {
            // Initialize Tango Service as a normal Android Service, since we call
            // mTango.disconnect() in onPause, this will unbind Tango Service, so
            // everytime when onResume get called, we should create a new Tango object.
            mTango = new Tango(ModelCorrespondenceActivity.this, new Runnable() {
                // Pass in a Runnable to be called from UI thread when Tango is ready,
                // this Runnable will be running on a new thread.
                // When Tango is ready, we can call Tango functions safely here only
                // when there is no UI thread changes involved.
                @Override
                public void run() {
                    try {
                        TangoSupport.initialize();
                        connectTango();
                        connectRenderer();
                        mIsConnected = true;
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.TangoOutOfDateException), e);
                    }
                }
            });
        }
    }

    /**
     * Configures the Tango service and connect it to callbacks.
     */
    private void connectTango() {
        // Use default configuration for Tango Service, plus low latency
        // IMU integration, depth and color camera.
        TangoConfig config = mTango.getConfig(
                TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a
        // precise alignment of virtual objects with the RBG image and
        // produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        mTango.connect(config);

        // No frame pairs needed.
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
                    // Mark a camera frame is available for rendering in the OpenGL thread
                    mIsFrameAvailableTangoThread.set(true);
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // Save the cloud and point data for later use.
                mPointCloudManager.updateXyzIj(xyzIj);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }
        });

        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
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
                synchronized (ModelCorrespondenceActivity.this) {
                    // Don't execute any tango API actions if we're not connected to the service
                    if (!mIsConnected) {
                        return;
                    }

                    // Set-up scene camera projection to match RGB camera intrinsics
                    if (!mRenderer.isSceneCameraConfigured()) {
                        mRenderer.setProjectionMatrix(mIntrinsics);
                    }

                    // Connect the camera texture to the OpenGL Texture if necessary
                    // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                    // texture with a different ID.
                    if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                        mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                mRenderer.getTextureId());
                        mConnectedTextureIdGlThread = mRenderer.getTextureId();
                        Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                    }

                    // If there is a new RGB camera frame available, update the texture with it
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
                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL, 0);

                        if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                            // Update the camera pose from the renderer
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
                                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL);
                                if (transform.statusCode == TangoPoseData.POSE_VALID) {
                                    // Place it in the top left corner, and rotate and scale it
                                    // accordingly.
                                    float[] rgbTHouse = calculateModelTransformFixedToCam();
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

                    // If the model was updated then it must be re rendered.
                    if (mModelUpdated) {
                        mRenderer.updateModelRendering(mHouseModel, mOpenGlTHouse, mDestPointList);
                        mModelUpdated = false;
                    }
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
                // If it's the last point, then find the correspondence.
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
        mCorrespondenceDone = false;
        mDestPointList.clear();
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
        TangoXyzIjData xyzIj = mPointCloudManager.getLatestXyzIj();

        if (xyzIj == null) {
            return null;
        }

        // We need to calculate the transform between the color camera at the
        // time the user clicked and the depth camera at the time the depth
        // cloud was acquired.
        TangoPoseData colorTdepthPose = TangoSupport.calculateRelativePose(
                rgbTimestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                xyzIj.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH);

        // Get depth point with the latest available point cloud data.
        float[] point = TangoSupport.getDepthAtPointNearestNeighbor(xyzIj, mIntrinsics,
                colorTdepthPose, u, v);

        // Get the transform from depth camera to OpenGL world at the timestamp of the cloud.
        TangoSupport.TangoMatrixTransformData transform =
                TangoSupport.getMatrixTransformAtTime(xyzIj.timestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                        TangoSupport.TANGO_SUPPORT_ENGINE_TANGO);
        if (transform.statusCode == TangoPoseData.POSE_VALID) {
            if (point == null) {
                return null;
            }

            float[] dephtPoint = new float[]{point[0], point[1], point[2], 1};
            float[] openGlPoint = new float[4];
            Matrix.multiplyMV(openGlPoint, 0, transform.matrix, 0, dephtPoint, 0);

            return openGlPoint;
        } else {
            Log.w(TAG, "Can't get depth camera transform at time: " + xyzIj.timestamp);
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
    private float[] calculateModelTransformFixedToCam() {
        // Translate to the upper left corner and ahead of the cam.
        float[] rgbTHouse = new float[16];
        Matrix.setIdentityM(rgbTHouse, 0);
        Matrix.translateM(rgbTHouse, 0, -1.5f, 0.3f, -4);
        // Rotate it 180 degrees around the Z axis to show the front of the house as default
        // orientation.
        Matrix.rotateM(rgbTHouse, 0, 180, 0, 0, 1);
        // Rotate it around the X axis so it looks better as seen from above.
        Matrix.rotateM(rgbTHouse, 0, 70, 1, 0, 0);
        // Rotate it around the Z axis to show the next correspondence point to be added.
        Matrix.rotateM(rgbTHouse, 0, -mModelZRotation, 0, 0, 1);
        // Scale it to a proper size.
        Matrix.scaleM(rgbTHouse, 0, 0.03f, 0.03f, 0.03f);
        Matrix4 m = new Matrix4(rgbTHouse);
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
}

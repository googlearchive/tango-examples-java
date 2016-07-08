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

package com.projecttango.examples.java.hellodepthperception;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Main Activity class for the Depth Perception Sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango XyzIj data to Layout view.
 */
public class HelloDepthPerceptionActivity extends Activity {

    private static final String TAG = HelloDepthPerceptionActivity.class.getSimpleName();

    private Tango mTango;
    private TangoConfig mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depth_perception);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(HelloDepthPerceptionActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                synchronized (HelloDepthPerceptionActivity.this) {
                    mConfig = setupTangoConfig(mTango);

                    try {
                        setTangoListeners();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (SecurityException e) {
                        Log.e(TAG, getString(R.string.permission_motion_tracking), e);
                    }
                    try {
                        mTango.connect(mConfig);
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Create a new Tango Configuration and enable the Depth Sensing API.
        TangoConfig config = new TangoConfig();
        config = tango.getConfig(config.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // We are not using TangoPoseData for this application.
            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIjData) {
                logXyzIj(xyzIjData);
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                // Ignoring TangoEvents.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    /**
     * Log the point count and the average depth of the given XyzIj data
     * in the Logcat as information.
     */
    private void logXyzIj(TangoXyzIjData xyzIjData) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Point count: " + xyzIjData.xyzCount);
        stringBuilder.append(". Average depth (m): " + calculateAveragedDepth(xyzIjData.xyz));
        Log.i(TAG, stringBuilder.toString());
    }

    /**
     * Calculates the average depth from a point cloud buffer.
     */
    private float calculateAveragedDepth(FloatBuffer pointCloudBuffer) {
        int pointCount = pointCloudBuffer.capacity() / 3;
        float totalZ = 0;
        float averageZ = 0;
        for (int i = 0; i < pointCloudBuffer.capacity() - 3; i = i + 3) {
            totalZ = totalZ + pointCloudBuffer.get(i + 2);
        }
        if (pointCount != 0) {
            averageZ = totalZ / pointCount;
        }
        return averageZ;
    }
}

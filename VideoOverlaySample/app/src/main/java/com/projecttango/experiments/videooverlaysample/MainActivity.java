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
package com.projecttango.experiments.videooverlaysample;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCameraPreview;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

/**
 * An example showing the usage of TangoCameraPreview class
 * Usage of TangoCameraPreviewClass:
 * To use this class, we first need initialize the TangoCameraPreview class with the activity's 
 * context and connect to the camera we want by using connectToTangoCamera class.Once the connection 
 * is established we need to manually update the TangoCameraPreview's texture by using the
 * onFrameAvailable callbacks.
 * NOTE: It is important to declare the CAMERA permission in the Android Manifest to access
 * the device camera.
 */
public class MainActivity extends Activity {
	private TangoCameraPreview tangoCameraPreview;
	private Tango mTango;
	private boolean mIsConnected;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		tangoCameraPreview = new TangoCameraPreview(this);
		mTango = new Tango(this);
		setContentView(tangoCameraPreview);
	}

	// Camera Preview
	private void startCameraPreview() {
	    // Connect to color camera
		tangoCameraPreview.connectToTangoCamera(mTango,
				TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
		// Use default configuration for Tango Service.
		TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
		mTango.connect(config);
		mIsConnected = true;

		// No need to add any coordinate frame pairs since we are not using
		// pose data. So just initialize.
		ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
		mTango.connectListener(framePairs, new OnTangoUpdateListener() {
			@Override
			public void onPoseAvailable(TangoPoseData pose) {
				// We are not using OnPoseAvailable for this app
			}

			@Override
			public void onFrameAvailable(int cameraId) {

			    // Check if the frame available is for the camera we want and
			    // update its frame on the camera preview.
				if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
					tangoCameraPreview.onFrameAvailable();
				}
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
				// We are not using OnPoseAvailable for this app
			}

			@Override
			public void onTangoEvent(TangoEvent event) {
				// We are not using OnPoseAvailable for this app
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(mIsConnected) {
			mTango.disconnect();
			tangoCameraPreview.disconnectFromTangoCamera();
			mIsConnected = false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		try {
			if (!mIsConnected) {
				startCameraPreview();
			}
		} catch (TangoOutOfDateException e) {
			Toast.makeText(getApplicationContext(), R.string.TangoOutOfDateException,
					Toast.LENGTH_SHORT).show();
		} catch (TangoErrorException e) {
			Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
		}
	}
}

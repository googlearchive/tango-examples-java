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

package com.projecttango.jmotiontrackingsample;

import java.util.ArrayList;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCoordinateFramePair;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Main Activity class for the Motion Tracking API Sample.  Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views.  OpenGL rendering logic
 * is delegated to the {@link MTGLRenderer} class.
 */
public class MotionTracking extends Activity implements View.OnClickListener {

	private Tango mTango;
	private TangoConfig mConfig;
	
	private TextView mPoseX;
	private TextView mPoseY;
	private TextView mPoseZ;
	private TextView mPoseQuaternion0;
	private TextView mPoseQuaternion1;
	private TextView mPoseQuaternion2;
	private TextView mPoseQuaternion3;
	private TextView mPoseStatus;
	private TextView mVersion;
	private Button mFirstPersonButton;
	private Button mThirdPersonButton;
	private Button mTopDownButton;
	private MTGLRenderer mRenderer;
	private GLSurfaceView mGLView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_motion_tracking);

		mPoseX = (TextView) findViewById(R.id.poseX);
		mPoseY = (TextView) findViewById(R.id.poseY);
		mPoseZ = (TextView) findViewById(R.id.poseZ);
		mPoseQuaternion0 = (TextView) findViewById(R.id.Quaternion1);
		mPoseQuaternion1 = (TextView) findViewById(R.id.Quaternion2);
		mPoseQuaternion2 = (TextView) findViewById(R.id.Quaternion3);
		mPoseQuaternion3 = (TextView) findViewById(R.id.Quaternion4);
		
		mFirstPersonButton = (Button) findViewById(R.id.firstPerson);
		mThirdPersonButton = (Button) findViewById(R.id.thirdPerson);
		mTopDownButton = (Button) findViewById(R.id.topDown);
		
		mPoseStatus = (TextView) findViewById(R.id.Status);
		mVersion = (TextView) findViewById(R.id.version);
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
		
		// Set up button click listeners
		mFirstPersonButton.setOnClickListener(this);
		mThirdPersonButton.setOnClickListener(this);
		mTopDownButton.setOnClickListener(this);

		// Configure OpenGL renderer
		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		
		// Instantiate the Tango service
		mTango = new Tango(this);
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		mVersion.setText(mConfig.getString("tango_service_library_version"));
		
		// Select coordinate frame pairs
		ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
        		TangoPoseData.COORDINATE_FRAME_DEVICE));
        
		// Listen for new Tango data
		int statusCode = mTango.connectListener(framePairs,new OnTangoUpdateListener() {

			@Override
			public void onPoseAvailable(final TangoPoseData pose) {
				mRenderer.getTrajectory().updateTrajectory(pose.translation);
				mRenderer.getModelMatCalculator().updateModelMatrix(pose.translation, pose.rotation);
				mGLView.requestRender();
				
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// Display pose data on screen in TextViews
						mPoseX.setText(Float.toString(pose.translation[0]));
						mPoseY.setText(Float.toString(pose.translation[1]));
						mPoseZ.setText(Float.toString(pose.translation[2]));
						mPoseQuaternion0.setText(Float.toString(pose.rotation[0]));
						mPoseQuaternion1.setText(Float.toString(pose.rotation[1]));
						mPoseQuaternion2.setText(Float.toString(pose.rotation[2]));
						mPoseQuaternion3.setText(Float.toString(pose.rotation[3]));
						
						// Display status of this TangoPose object
						if (pose.statusCode == TangoPoseData.POSE_VALID) {
							mPoseStatus.setText("Valid");
						} else if (pose.statusCode == TangoPoseData.POSE_INVALID) {
							mPoseStatus.setText("Invalid");
						} else if (pose.statusCode == TangoPoseData.POSE_INITIALIZING) {
							mPoseStatus.setText("Initializing");
						} else if (pose.statusCode == TangoPoseData.POSE_UNKNOWN) {
							mPoseStatus.setText("Unknown");
						}
					}
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// We are not using TangoXyzIjData for this application
			}
		});
	}
	
	
	@Override
	protected void onPause() {
		super.onPause();
		mTango.unlockConfig();
		mTango.disconnect();
	}
	
	@Override
	protected void onResume() {	
		super.onResume();
		mTango.lockConfig(mConfig);
		mTango.connect();
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mTango.unlockConfig();
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.firstPerson:
			mRenderer.setFirstPersonView();
			break;
		case R.id.topDown:
			mRenderer.setTopDownView();
			break;
		case R.id.thirdPerson:
			mRenderer.setThirdPersonView();
			break;
		default:
			Log.w("MotionTracking", "Unknown button click");
			return;
		}
	}
	
}

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

package com.projecttango.jarealearning;

import java.util.ArrayList;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoEvent;
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
 * is delegated to the {@link ALRenderer} class.
 */
public class AreaLearning extends Activity implements View.OnClickListener {

	protected static final String TAG = null;
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
	private ALRenderer mRenderer;
	private GLSurfaceView mGLView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_area_learning);

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
		mRenderer = new ALRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		
		// Instantiate the Tango service
		mTango = new Tango(this);
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
		mVersion.setText(mConfig.getString("tango_service_library_version"));
		
		// Select coordinate frame pairs
		ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
//        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
//        		TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION));
//        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
//        		TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
        		TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION));

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
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onTangoEvent(TangoEvent event) {
				// TODO Auto-generated method stub
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
		 ArrayList<String> fullUuidList = new ArrayList<String>();
         mTango.listAreaDescriptions(fullUuidList);
         if (fullUuidList.size() == 0) {
             Log.e(TAG, "No UUIDs on device");
         } else {
             for (String s : fullUuidList) {
                 Log.e(TAG, "UUID: " + s);
                 TangoAreaDescriptionMetaData metadata =
                     new TangoAreaDescriptionMetaData();
                 int resultLoad = mTango.loadAreaDescriptionMetaData(s, metadata);
                 Log.e(TAG,
                       "Metadata load result is " + resultLoad);
                 byte[] id = metadata.get("id");
                 String idString = new String(id);
                 Log.e(TAG, "Id is " + idString);
                 byte[] name = metadata.get("name");
                 String nameString = new String(name);
                 Log.e(TAG, "Name is " + nameString);
                 nameString = idString + "_tester";
                 metadata.set("name", nameString.getBytes());
                 int resultSave = mTango.saveAreaDescriptionMetadata(s, metadata);
                 Log.e(TAG,
                       "Metadata save result is " + resultSave);
             }
         }
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

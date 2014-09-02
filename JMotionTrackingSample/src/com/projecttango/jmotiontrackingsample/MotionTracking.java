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

import java.text.DecimalFormat;
import java.util.ArrayList;

import com.google.atap.tangoservice.Tango;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

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
	private Button mStart;
	private Button mMotionReset;
	private ToggleButton mAutoResetButton;
	private float mPreviousX,mPreviousY;
	private boolean mIsAutoReset;

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
		mStart =(Button) findViewById(R.id.start);
		mMotionReset = (Button) findViewById(R.id.manualReset);
		
		mAutoResetButton = (ToggleButton) findViewById(R.id.autoReset);
		mPoseStatus = (TextView) findViewById(R.id.Status);
		mVersion = (TextView) findViewById(R.id.version);
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
		
		// Set up button click listeners
		mMotionReset.setOnClickListener(this);
		mStart.setOnClickListener(this);
		mAutoResetButton.setOnClickListener(this);
		mFirstPersonButton.setOnClickListener(this);
		mThirdPersonButton.setOnClickListener(this);
		mTopDownButton.setOnClickListener(this);

		// Configure OpenGL renderer
		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		mIsAutoReset = mAutoResetButton.isChecked();
		// Instantiate the Tango service
		mTango = new Tango(this);
		
	}
	
	private void SetTangoConfig(){
		
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);	
		if(mIsAutoReset){
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORESET, true);
			mMotionReset.setVisibility(View.GONE);
		}
		else{
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORESET, false);
			mMotionReset.setVisibility(View.VISIBLE);
		}
		mVersion.setText(mConfig.getString("tango_service_library_version"));
		
		// Select coordinate frame pairs
		final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
        		TangoPoseData.COORDINATE_FRAME_DEVICE));
        mTango.lockConfig(mConfig);
		mTango.connect();
		// Listen for new Tango data
		int statusCode = mTango.connectListener(framePairs,new OnTangoUpdateListener() {

			@Override
			public void onPoseAvailable(final TangoPoseData pose) {
				if(!mIsAutoReset){
					if(pose.statusCode == TangoPoseData.POSE_INVALID){
						Log.e("Motion Tracking Invalid", "Invalid State");
						
					}
				}
				mRenderer.getTrajectory().updateTrajectory(pose.translation);
				mRenderer.getModelMatCalculator().updateModelMatrix(pose.translation, pose.rotation);
				mRenderer.updateViewMatrix();
				mGLView.requestRender();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						DecimalFormat twoDec = new DecimalFormat("0.00");
						// Display pose data on screen in TextViews
						mPoseX.setText(Float.toString(pose.translation[0]));
						mPoseY.setText(Float.toString(pose.translation[1]));
						mPoseZ.setText(Float.toString(pose.translation[2]));
						mPoseQuaternion0.setText(Float.toString(pose.rotation[0]));
						mPoseQuaternion1.setText(Float.toString(pose.rotation[1]));
						mPoseQuaternion2.setText(Float.toString(pose.rotation[2]));
						mPoseQuaternion3.setText(Float.toString(pose.rotation[3]));
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

			@Override
			public void onTangoEvent(TangoEvent arg0) {
				// TODO Auto-generated method stub
				
			}
		});
		mAutoResetButton.setVisibility(View.GONE);
		mStart.setVisibility(View.GONE);
	}
	
	private void ShowManualReset(){
	
	}
	
	private void MotionReset(){
		mTango.resetMotionTracking();
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
		case R.id.autoReset:
			mIsAutoReset = mAutoResetButton.isChecked();
			break;
		case R.id.start:
			SetTangoConfig();
			break;
		case R.id.manualReset:
			MotionReset();
			break;
		default:
			Log.w("MotionTracking", "Unknown button click");
			return;
		}
	}
	

	@Override
	public boolean onTouchEvent(MotionEvent event) {
	        //Log.e("TOuched","Touched");
	        final int action = event.getAction();
	        switch (action) {
	        case MotionEvent.ACTION_DOWN: {
	            final float x = event.getX();
	            final float y = event.getY();
	            
	            // Remember where we started
	            mPreviousX = x;
	            mPreviousY = y;
	            break;
	        }
	            
	        case MotionEvent.ACTION_MOVE: {
	            final float x = event.getX();
	            final float y = event.getY();
	            
	            // Calculate the distance moved
	            final float dx = x - mPreviousX;
	            final float dy = y - mPreviousY;
	            
	            float distance =(float) Math.sqrt(Math.pow(dx, 2)+Math.pow(dy, 2));
	          
	            mPreviousX = x;
	            mPreviousY = y;
	            
	            break;
	        }
	        }
	        
	        return true;
	    }
	
}

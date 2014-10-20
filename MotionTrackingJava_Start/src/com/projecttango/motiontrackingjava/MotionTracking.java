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

package com.projecttango.motiontrackingjava;

import java.text.DecimalFormat;
import java.util.ArrayList;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCoordinateFramePair;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the
 * connection to the Tango service and propagation of Tango pose data to OpenGL
 * and Layout views. OpenGL rendering logic is delegated to the
 * {@link MTGLRenderer} class.
 */
public class MotionTracking extends Activity implements View.OnClickListener {

	private static String TAG = MotionTracking.class.getSimpleName();
	private static int SECS_TO_MILLISECS = 1000;
	private Tango mTango;
	private TangoConfig mConfig;
	private TextView mDeltaTextView;
	private TextView mPoseCountTextView;
	private TextView mPoseTextView;
	private TextView mQuatTextView;
	private TextView mPoseStatusTextView;
	private TextView mTangoServiceVersionTextView;
	private TextView mApplicationVersionTextView;
	private TextView mTangoEventTextView;
	private Button mMotionResetButton;
	private float mPreviousTimeStamp;
	private int count;
	private float mDeltaTime;
	private boolean mIsAutoRecovery;
	private MTGLRenderer mRenderer;
	private GLSurfaceView mGLView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_motion_tracking);
		Intent intent = getIntent();
		mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER, false);
		// Text views for displaying translation and rotation data
		mPoseTextView = (TextView) findViewById(R.id.pose);
		mQuatTextView = (TextView) findViewById(R.id.quat);
		mPoseCountTextView =(TextView) findViewById(R.id.posecount);
		mDeltaTextView =(TextView) findViewById(R.id.deltatime);
		mTangoEventTextView =(TextView) findViewById(R.id.tangoevent);
		// Buttons for selecting camera view and Set up button click listeners
		findViewById(R.id.first_person_button).setOnClickListener(this);
		findViewById(R.id.third_person_button).setOnClickListener(this);
		findViewById(R.id.top_down_button).setOnClickListener(this);

		// Button to reset motion tracking
		mMotionResetButton = (Button) findViewById(R.id.resetmotion);

		// Text views for the status of the pose data and Tango library versions
		mPoseStatusTextView = (TextView) findViewById(R.id.status);
		mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
		mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

		// OpenGL view where all of the graphics are drawn
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

		// Set up button click listeners
		mMotionResetButton.setOnClickListener(this);

		// Configure OpenGL renderer
		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.first_person_button:
			mRenderer.setFirstPersonView();
			break;
		case R.id.top_down_button:
			mRenderer.setTopDownView();
			break;
		case R.id.third_person_button:
			mRenderer.setThirdPersonView();
			break;
		default:
			Log.w(TAG, "Unknown button click");
			return;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return mRenderer.onTouchEvent(event);
	}
}

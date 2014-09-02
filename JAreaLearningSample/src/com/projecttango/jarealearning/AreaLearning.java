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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import android.widget.ToggleButton;

/**
 * Main Activity class for the Motion Tracking API Sample.  Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views.  OpenGL rendering logic
 * is delegated to the {@link ALRenderer} class.
 */
public class AreaLearning extends Activity implements View.OnClickListener {

	protected static final String TAG = null;
	private Tango mTango;
	private TangoConfig mConfig;
	private TextView mStart2Device;
	private TextView mAdf2Device;
	private TextView mAdf2Start;
	private int mNoOfLocalizationEvents;
	private TextView mVersion;
	private boolean mRelocalized;
	private boolean mIsLearningMode;
	private boolean mIsConstantSpaceRelocalize;
	private TextView mUUID;
	private ToggleButton mLearningMode;
	private ToggleButton mConstantSpaceRelocaliztion;
	private Button mStartButton;
	private Button mSaveAdf;
	private Button mFirstPersonButton;
	private Button mThirdPersonButton;
	private Button mTopDownButton;
	
	private ALRenderer mRenderer;
	private GLSurfaceView mGLView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_area_learning);
		
		
		mAdf2Device = (TextView) findViewById(R.id.Adf2Device);
		mStart2Device = (TextView) findViewById(R.id.Start2Device);
		mAdf2Start = (TextView) findViewById(R.id.Adf2Start);

		
		mFirstPersonButton = (Button) findViewById(R.id.firstPerson);
		mThirdPersonButton = (Button) findViewById(R.id.thirdPerson);
		mTopDownButton = (Button) findViewById(R.id.topDown);
		
		mVersion = (TextView) findViewById(R.id.version);
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
		
		mLearningMode = (ToggleButton) findViewById(R.id.learningMode);
		mConstantSpaceRelocaliztion = (ToggleButton) findViewById(R.id.constantSpaceRelocalization);
		mStartButton = (Button) findViewById(R.id.start);
		mSaveAdf = (Button) findViewById(R.id.saveAdf);
		mUUID = (TextView) findViewById(R.id.uuid);

		
		mStartButton.setOnClickListener(this);
		mSaveAdf.setOnClickListener(this);
		mLearningMode.setOnClickListener(this);
		mConstantSpaceRelocaliztion.setOnClickListener(this);
		
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
		mRelocalized = false;
	}
	
	private void SetTangoConfig()
	{
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		if(mIsLearningMode){
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);
			mSaveAdf.setVisibility(View.VISIBLE);
			
		}
		if(mIsConstantSpaceRelocalize){
			ArrayList<String> fullUUIDList = new ArrayList<String>();
			mTango.listAreaDescriptions(fullUUIDList);
			if(fullUUIDList.size()==0)
			{
				mUUID.setText("No UUIDs");
			}
			if(fullUUIDList.size()>0)
			{
                mConfig.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, fullUUIDList.get(fullUUIDList.size()-1));
				mUUID.setText("No of UUIDs : " + fullUUIDList.size() + ", Latest is :"+fullUUIDList.get(fullUUIDList.size()-1));
			}
		}
		mNoOfLocalizationEvents = 0;
		mStartButton.setVisibility(View.GONE);
		mLearningMode.setVisibility(View.GONE);
		mConstantSpaceRelocaliztion.setVisibility(View.GONE);
		mTango.lockConfig(mConfig);
		mVersion.setText(mConfig.getString("tango_service_library_version"));
		SetUpTangoListeners();
		mTango.connect();
	}
	
	private void SetUpTangoListeners(){
		
		ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
        		TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
        		TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
        		TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));	
		mTango.connectListener(framePairs, new OnTangoUpdateListener() {
			
			@Override
			public void onXyzIjAvailable(TangoXyzIjData xyzij) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onTangoEvent(TangoEvent event) {
				
				if(event.description.matches("ADFEvent:Relocalized")){
					mRelocalized = true;
				}
				if(event.description.matches("ADFEvent:NotRelocalized")){
					mRelocalized = false;
				}
			}
			
			@Override
			public void onPoseAvailable(TangoPoseData pose) {
				UpdateTextViewWith(pose);
				if(mRelocalized){
					if(pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE){
						mRenderer.getTrajectory().updateTrajectory(pose.translation);
						mRenderer.getModelMatCalculator().updateModelMatrix(pose.translation, pose.rotation);
						mRenderer.UpdateViewMatrix();
						mGLView.requestRender();
					}
				}
				else
				{	
					if(pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE){
						mRenderer.getTrajectory().updateTrajectory(pose.translation);
						mRenderer.getModelMatCalculator().updateModelMatrix(pose.translation, pose.rotation);
						mRenderer.UpdateViewMatrix();
						mGLView.requestRender();
					}
				}
			}
		});
	}
	
	private void SaveAdf(){
		ArrayList<String> uuids = new ArrayList<String>();
		mTango.saveAreaDescription(uuids);
	}
	
	private void UpdateTextViewWith(final TangoPoseData pose){
		final DecimalFormat twoDec = new DecimalFormat("0.00");
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE){
					mAdf2Device.setText("(" + twoDec.format(pose.translation[0])+","+twoDec.format(pose.translation[1])+","+twoDec.format(pose.translation[2])+") " + GetPoseStatus(pose));
				}
				
				if(pose.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE){
					mStart2Device.setText("(" + twoDec.format(pose.translation[0])+","+twoDec.format(pose.translation[1])+","+twoDec.format(pose.translation[2])+") " + GetPoseStatus(pose));
				}
				if(pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE){
					mAdf2Start.setText("(" + twoDec.format(pose.translation[0])+","+twoDec.format(pose.translation[1])+","+twoDec.format(pose.translation[2])+") " + GetPoseStatus(pose));
					mNoOfLocalizationEvents++;
				}
			}
			
		});
			
	}
	
	private String GetPoseStatus(TangoPoseData pose){
		String returnString;
		switch(pose.statusCode)
		{
			case TangoPoseData.POSE_INITIALIZING:
				returnString = "Initializing";
				break;
			case TangoPoseData.POSE_INVALID:
				returnString = "Invalid";
				break;
			case TangoPoseData.POSE_VALID:
				returnString = "Valid";
				break;
			default:
				returnString = "UnKnown";
				break;
		}
		return returnString;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onResume() {	
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
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
		case R.id.start:
			SetTangoConfig();
			break;
		case R.id.learningMode:
			mIsLearningMode = mLearningMode.isChecked();
			break;
		case R.id.constantSpaceRelocalization:
			mIsConstantSpaceRelocalize = mConstantSpaceRelocaliztion.isChecked();
			break;
		case R.id.saveAdf:
			SaveAdf();
			break;
		default:
			Log.w("MotionTracking", "Unknown button click");
			return;
		}
	}
	
}

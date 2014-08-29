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

package com.projecttango.quickstart;

import java.util.ArrayList;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final String sTranslationFormat = "Translation: %f, %f, %f";
	private static final String sRotationFormat = "Rotation: %f, %f, %f, %f";
	
	private TextView mTranslationTextView;
	private TextView mRotationTextView;
	
	private Tango mTango;
	private TangoConfig mConfig;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mTranslationTextView = (TextView) findViewById(R.id.translation_text_view);
		mRotationTextView = (TextView) findViewById(R.id.rotation_text_view);
	
		// Instantiate Tango client
		mTango = new Tango(this);
		
		// Set up Tango configuration for motion tracking
		// 	If you want to use other APIs, add more appropriate to the config like:
		// 	mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		
	    // Select coordinate frame pairs
	    ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
	    framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
	                TangoPoseData.COORDINATE_FRAME_DEVICE));
		
		// Add a listener for Tango pose data
		int statusCode = mTango.connectListener(framePairs, new OnTangoUpdateListener() {

			@SuppressLint("DefaultLocale") 
			@Override
			public void onPoseAvailable(TangoPoseData pose) {
				// Format Translation and Rotation data
				final String translationMsg = String.format(sTranslationFormat, pose.translation[0],
						pose.translation[1], pose.translation[2]);
				final String rotationMsg = String.format(sRotationFormat, pose.rotation[0],
						pose.rotation[1], pose.rotation[2], pose.rotation[3]);
				
				// Output to LogCat
				String logMsg = translationMsg + " | " + rotationMsg;
				Log.i(TAG, logMsg);
				
				// Display data in TextViews.  This must be done inside a runOnUiThread call because
				// 	it affects the UI, which will cause an error if performed from the Tango
				//	service thread
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mTranslationTextView.setText(translationMsg);
						mRotationTextView.setText(rotationMsg);
					}	
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// Ignoring XyzIj data
			}
			
		});
		
		Log.i(TAG, "Status Code: " + statusCode);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// Lock the Tango configuration and reconnect to the service each time the app
		//	is brought to the foreground.
		mTango.lockConfig(mConfig);
		mTango.connect();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		// When the app is pushed to the background, unlock the Tango configuration and disconnect
		//	from the service so that other apps will behave properly.
		mTango.unlockConfig();
		mTango.disconnect();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// When the app is pushed to the background, unlock the Tango configuration and disconnect
		//	from the service so that other apps will behave properly.
		mTango.unlockConfig();
		mTango.disconnect();
	}
	
}

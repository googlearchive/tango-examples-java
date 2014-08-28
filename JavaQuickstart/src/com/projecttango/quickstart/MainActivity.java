package com.projecttango.quickstart;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();
	
	private TextView mTextView;
	private Tango mTango;
	private TangoConfig mConfig;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mTextView = (TextView) findViewById(R.id.main_text_view);
		mTextView.setText("Welcome to Tango");
	
		// Instantiate Tango client
		mTango = new Tango(this);
		
		// Set up Tango configuration for motion tracking
		// 	If you want to use other APIs, add more appropriate to the config like:
		// 	mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		
		// Add a listener for Tango pose data
		mTango.connectListener(new OnTangoUpdateListener() {

			@Override
			public void onPoseAvailable(TangoPoseData pose) {
				// Log translation and rotation data to LogCat
				String translationFormat = "Translation: %f, %f, %f";
				String rotationFormat = "Rotation: %f, %f, %f, %f";
				final String logMessage = String.format(translationFormat, pose.translation) +
						" | " + String.format(rotationFormat, pose.rotation);
				
				// Output to LogCat
				Log.i(TAG, logMessage);
				
				// Display in TextView.  This must be done inside a runOnUiThread call because
				// 	it affects the UI, which will cause an error if performed from the Tango
				//	service thread
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mTextView.setText(logMessage);
					}	
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// Ignoring XyzIj data
			}
			
		});
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mTango.lockConfig(mConfig);
		mTango.connect();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		// mTango.unlockConfig();
		mTango.disconnect();
	}
	
}

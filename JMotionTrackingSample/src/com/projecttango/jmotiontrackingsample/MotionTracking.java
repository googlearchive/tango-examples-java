package com.projecttango.jmotiontrackingsample;

import java.text.DecimalFormat;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.TextView;

public class MotionTracking extends Activity {

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
		mPoseStatus = (TextView) findViewById(R.id.Status);
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		
		// Instantiate the Tango service
		mTango = new Tango(this);
		mConfig = new TangoConfig();
		mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		
		// Listen for new Tango data
		mTango.connectListener(TangoPoseData.COORDINATE_FRAME_DEVICE,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,new OnTangoUpdateListener() {
			final DecimalFormat fourDec = new DecimalFormat("0.0000");

			@Override
			public void onPoseAvailable(final TangoPoseData pose) {
				// Update the Axis and CameraFrustum with new pose data, then render
				mRenderer.getCameraFrustum().updateModelMatrix(pose.translation, pose.rotation);
				mRenderer.getAxis().updateModelMatrix(pose.translation, pose.rotation);
				mGLView.requestRender();
				
				// Run UI updates on the UI thread, doing this in the service's main thread
				//	will result in an error.
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// Display pose data on screen in TextViews
						mPoseX.setText(fourDec.format(pose.translation[0]));
						mPoseY.setText(fourDec.format(pose.translation[1]));
						mPoseZ.setText(fourDec.format(pose.translation[2]));
						mPoseQuaternion0.setText(fourDec.format(pose.rotation[0]));
						mPoseQuaternion1.setText(fourDec.format(pose.rotation[1]));
						mPoseQuaternion2.setText(fourDec.format(pose.rotation[2]));
						mPoseQuaternion3.setText(fourDec.format(pose.rotation[3]));
						if(pose.statusCode == TangoPoseData.POSE_VALID){
							mPoseStatus.setText("Valid");
						}
						else if(pose.statusCode == TangoPoseData.POSE_INVALID){
							mPoseStatus.setText("Invalid");
						}
						else if(pose.statusCode == TangoPoseData.POSE_INITIALIZING){
							mPoseStatus.setText("Initializing");
						}
						else if(pose.statusCode == TangoPoseData.POSE_UNKNOWN){
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
		// mTango.unlockConfig();
	}
	
}

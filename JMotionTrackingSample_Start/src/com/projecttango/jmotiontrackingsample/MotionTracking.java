package com.projecttango.jmotiontrackingsample;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.TextView;

public class MotionTracking extends Activity {

	private TextView mPoseX;
	private TextView mPoseY;
	private TextView mPoseZ;
	private TextView mPoseQuaternion0;
	private TextView mPoseQuaternion1;
	private TextView mPoseQuaternion2;
	private TextView mPoseQuaternion3;
	public MTGLRenderer mRenderer;
	public GLSurfaceView mGLView;

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
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
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
	
}
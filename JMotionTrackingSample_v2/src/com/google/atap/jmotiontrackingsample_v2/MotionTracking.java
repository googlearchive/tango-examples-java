package com.google.atap.jmotiontrackingsample_v2;

import java.text.DecimalFormat;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.TextView;

public class MotionTracking extends Activity {

	private Tango mTango;
	private TextView poseX;
	private TextView poseY;
	private TextView poseZ;
	private TextView poseQuaternion0;
	private TextView poseQuaternion1;
	private TextView poseQuaternion2;
	private TextView poseQuaternion3;
	public MTGLRenderer mRenderer;
	public GLSurfaceView mGLView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_motion_tracking);

		poseX = (TextView) findViewById(R.id.poseX);
		poseY = (TextView) findViewById(R.id.poseY);
		poseZ = (TextView) findViewById(R.id.poseZ);
		poseQuaternion0 = (TextView) findViewById(R.id.Quaternion1);
		poseQuaternion1 = (TextView) findViewById(R.id.Quaternion2);
		poseQuaternion2 = (TextView) findViewById(R.id.Quaternion3);
		poseQuaternion3 = (TextView) findViewById(R.id.Quaternion4);
		mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

		mRenderer = new MTGLRenderer();
		mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		mTango = new Tango(this);
		mTango.connectListener(new OnTangoUpdateListener() {
			final DecimalFormat fourDec = new DecimalFormat("0.0000");

			@Override
			public void onPoseAvailable(final TangoPoseData pose) {
				mRenderer.cameraFrustrum.updateModelMatrix(pose.translation,
						pose.rotation);
				mGLView.requestRender();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						poseX.setText(fourDec.format(pose.translation[0]));
						poseY.setText(fourDec.format(pose.translation[1]));
						poseZ.setText(fourDec.format(pose.translation[2]));
						poseQuaternion0.setText(fourDec
								.format(pose.rotation[0]));
						poseQuaternion1.setText(fourDec
								.format(pose.rotation[1]));
						poseQuaternion2.setText(fourDec
								.format(pose.rotation[2]));
						poseQuaternion3.setText(fourDec
								.format(pose.rotation[3]));
					}
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// TODO Auto-generated method stub
			}
		});
		mTango.connect();
	}
}

package com.google.atap.jmotiontrackingsample_v2;

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
        
       poseX = (TextView) findViewById(R.id.PosX);        
       poseY = (TextView) findViewById(R.id.PosY);        
       poseZ = (TextView) findViewById(R.id.PosZ);
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
       
       poseUpdater();
       }

	public void poseUpdater(){
		mTango.connectListener(new OnTangoUpdateListener() {     
			@Override 
			public void onPoseAvailable(final TangoPoseData pose) {
				mRenderer.cameraFrustrum.updateModelMatrix(pose.translation, pose.rotation);  
				mGLView.requestRender();
				runOnUiThread(new Runnable(){  
					@Override      
					public void run() {        
						poseX.setText("PosX: " +pose.translation[0] + "");      
						poseY.setText("PosY: " +pose.translation[1] + "");       
						poseZ.setText("PosZ: " +pose.translation[2] + "");       
						poseQuaternion0.setText("Q1: " +pose.rotation[0] + "");      
						poseQuaternion1.setText("Q2: " +pose.rotation[1] + "");       
						poseQuaternion2.setText("Q3: " +pose.rotation[2] + "");       
						poseQuaternion3.setText("Q4: " +pose.rotation[3] + "");	                           
	                    //timestamp.setText(pose.timestamp + "");   
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

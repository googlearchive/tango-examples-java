package com.google.atap.jpointcloudsample;


import java.io.FileInputStream;
import java.io.IOException;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class JPointCloud extends Activity {
	private Tango mTango;
	public PCRenderer mRenderer;
	public GLSurfaceView mGLView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpoint_cloud);
        mRenderer = new PCRenderer();
        mGLView = (GLSurfaceView)findViewById(R.id.gl_surface_view);
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);		
        mTango = new Tango(this);
        TangoConfig config = new TangoConfig();
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mTango.lockConfig(config);
        mTango.connectListener(new OnTangoUpdateListener() {
            
        	@Override
        	public void onPoseAvailable(final TangoPoseData pose) {
        		// mRenderer.cameraFrustrum.updateModelMatrix(pose.translation, pose.rotation);
                 //mGLView.requestRender();
        	}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
				
				byte[] buffer = new byte[xyzIj.xyzParcelFileDescriptorSize];
            	FileInputStream fileStream = new FileInputStream(
                xyzIj.xyzParcelFileDescriptor.getFileDescriptor());
            	try {
            		fileStream.read(buffer, xyzIj.xyzParcelFileDescriptorOffset,
                    xyzIj.xyzParcelFileDescriptorSize);
            		fileStream.close();
            		mRenderer.mPointCloud.UpdatePoints(buffer);
            		mGLView.requestRender();
            		} catch (IOException e) {
            		e.printStackTrace();
            		}
            	 
  //          	for (int i = 0; i < buffer.length; i++) {
   //         		Log.e("XyzIj data", "[i]= "+i+" "+ buffer[i]);
    //        		}
				}
        });
        Log.e("TangoNativeServiceTester", "Attempting to connect...");
        mTango.connect();
   }
}

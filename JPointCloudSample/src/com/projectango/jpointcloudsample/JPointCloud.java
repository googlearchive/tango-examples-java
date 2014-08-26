
package com.projectango.jpointcloudsample;


import java.io.FileInputStream;
import java.io.IOException;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.projecttango.jpointcloudsample.R;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class JPointCloud extends Activity {
	private Tango mTango;
	private TangoConfig mConfig;
	private PCRenderer mRenderer;
	private GLSurfaceView mGLView;
	
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
        mConfig = new TangoConfig();
        mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, false);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        
        mTango.connectListener(TangoPoseData.COORDINATE_FRAME_DEVICE, 
        		TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, new OnTangoUpdateListener() {
        	
        	@Override
        	public void onPoseAvailable(final TangoPoseData pose) {
        		// mRenderer.getCameraFrustum().updateModelMatrix(pose.translation, pose.rotation);
                // mGLView.requestRender();
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
            		mRenderer.getPointCloud().UpdatePoints(buffer);
            		mGLView.requestRender();
            	} catch (IOException e) {
            		e.printStackTrace();
            	}
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

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
import android.widget.TextView;

public class JPointCloud extends Activity {
	private Tango mTango;
	private TangoConfig mConfig;
	private PCRenderer mRenderer;
	private GLSurfaceView mGLView;
	private TextView mPointCount;
	private TextView mVersion;
	private String mServiceVersion;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpoint_cloud);
        
        mRenderer = new PCRenderer();
        mGLView = (GLSurfaceView)findViewById(R.id.gl_surface_view);
        mPointCount = (TextView) findViewById(R.id.vertexCount);
        mVersion=  (TextView) findViewById(R.id.version);
        
        mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);	
        
        mTango = new Tango(this);
        mConfig = new TangoConfig();
        mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT, mConfig);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, false);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mServiceVersion=mConfig.getString("tango_service_library_version");
    	// Display the version of Tango Service
		mVersion.setText(mServiceVersion);
        mTango.connectListener( new OnTangoUpdateListener() {
        	
        	@Override
        	public void onPoseAvailable(final TangoPoseData pose) {
        		// mRenderer.getCameraFrustum().updateModelMatrix(pose.translation, pose.rotation);
                // mGLView.requestRender();
        	}

			@Override
			public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {
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
            	
            	runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// Display Number of points in the Point Cloud in TextViews
						mPointCount.setText(Integer.toString(xyzIj.xyzCount));
					}
				});
            	
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

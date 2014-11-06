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
package com.projecttango.videooverlaysample;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;


public class MainActivity extends Activity  implements SurfaceHolder.Callback {
	
	private SurfaceView surfaceView;
    	private SurfaceHolder surfaceHolder;
	private Tango mTango;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = (SurfaceView) findViewById(R.id.cameraView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        mTango = new Tango(this);
    }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	Surface surface = holder.getSurface();
         if (surface.isValid()) {
        	 TangoConfig config = new TangoConfig();
        	 config =  mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        	 mTango.connectSurface(0, surface);
        	 mTango.connect(config);
         }
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
         mTango.disconnectSurface(0);
	}

}

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

package com.projecttango.areadescriptionjava;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ToggleButton;
import android.view.View;

public class ADStartActivity extends Activity implements View.OnClickListener{
	
	public static String USE_AREA_LEARNING = "com.projecttango.areadescriptionjava.usearealearning";
	public static String LOAD_ADF = "com.projecttango.areadescriptionjava.loadadf";
	private ToggleButton mLearningModeToggleButton;
	private ToggleButton mLoadADFToggleButton;
	private Button mStartButton;
	private boolean mIsUseAreaLearning;
	private Button mAdfPathPreviewButton;
	private boolean mIsLoadADF;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.start_activity);
		setTitle(R.string.app_name);
		mLearningModeToggleButton = (ToggleButton)findViewById(R.id.learningmode);
		mLoadADFToggleButton= (ToggleButton)findViewById(R.id.loadadf);
		mStartButton=(Button)findViewById(R.id.start);
		mAdfPathPreviewButton = (Button)findViewById(R.id.adfPathView);
		findViewById(R.id.ADFListView).setOnClickListener(this);
		findViewById(R.id.CanvasView).setOnClickListener(this);
		mLearningModeToggleButton.setOnClickListener(this);
		mLoadADFToggleButton.setOnClickListener(this);
		mStartButton.setOnClickListener(this);
		mAdfPathPreviewButton.setOnClickListener(this);
		mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
		mIsLoadADF = mLoadADFToggleButton.isChecked();
	}
	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.loadadf:
			mIsLoadADF = mLoadADFToggleButton.isChecked();
			break;
		case R.id.learningmode:
			mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
			break;
		case R.id.start:
			StartAreaDescriptionActivity();
			break;
		case R.id.ADFListView:
			StartADFListView();
			break;
		}
	}
	
	private void StartAreaDescriptionActivity(){
		Intent startADIntent = new Intent(this,AreaDescription.class);
		startADIntent.putExtra(USE_AREA_LEARNING, mIsUseAreaLearning);
		startADIntent.putExtra(LOAD_ADF, mIsLoadADF);
		startActivity(startADIntent);
	}
	
	private void StartADFListView(){
		Intent startADFListViewIntent = new Intent(this,ADFUUIDListViewActivity.class);
		startActivity(startADFListViewIntent);
	}

}

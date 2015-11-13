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

package com.projecttango.experiments.javaarealearning;

import com.google.atap.tangoservice.Tango;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Start Activity for Area Learning app. Gives the ability to a particular config and also Manage
 * ADFs
 */
public class ALStartActivity extends Activity implements View.OnClickListener {

    public static final String USE_AREA_LEARNING = 
            "com.projecttango.areadescriptionjava.usearealearning";
    public static final String LOAD_ADF = "com.projecttango.areadescriptionjava.loadadf";
    private ToggleButton mLearningModeToggleButton;
    private ToggleButton mLoadADFToggleButton;
    private Button mStartButton;
    private boolean mIsUseAreaLearning;
    private boolean mIsLoadADF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_activity);
        setTitle(R.string.app_name);
        mLearningModeToggleButton = (ToggleButton) findViewById(R.id.learningmode);
        mLoadADFToggleButton = (ToggleButton) findViewById(R.id.loadadf);
        mStartButton = (Button) findViewById(R.id.start);
        findViewById(R.id.ADFListView).setOnClickListener(this);
        mLearningModeToggleButton.setOnClickListener(this);
        mLoadADFToggleButton.setOnClickListener(this);
        mStartButton.setOnClickListener(this);
        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.loadadf:
            mIsLoadADF = mLoadADFToggleButton.isChecked();
            break;
        case R.id.learningmode:
            mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
            break;
        case R.id.start:
            startAreaDescriptionActivity();
            break;
        case R.id.ADFListView:
            startADFListView();
            break;
        }
    }

    private void startAreaDescriptionActivity() {
        Intent startADIntent = new Intent(this, AreaLearningActivity.class);
        mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
        mIsLoadADF = mLoadADFToggleButton.isChecked();
        startADIntent.putExtra(USE_AREA_LEARNING, mIsUseAreaLearning);
        startADIntent.putExtra(LOAD_ADF, mIsLoadADF);
        startActivity(startADIntent);
    }

    private void startADFListView() {
        Intent startADFListViewIntent = new Intent(this, ADFUUIDListViewActivity.class);
        startActivity(startADFListViewIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 0) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.arealearning_permission, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

}

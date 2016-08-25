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

package com.projecttango.examples.java.helloareadescription;

import com.google.atap.tangoservice.Tango;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Start Activity for Area Description example. Gives the ability to choose a particular
 * configuration and also Manage Area Description Files (ADF).
 */
public class StartActivity extends Activity {
    // The unique key string for storing user's input.
    public static final String USE_AREA_LEARNING =
            "com.projecttango.examples.java.helloareadescription.usearealearning";
    public static final String LOAD_ADF =
            "com.projecttango.examples.java.helloareadescription.loadadf";

    // Permission request action.
    public static final int REQUEST_CODE_TANGO_PERMISSION = 0;

    // UI elements.
    private ToggleButton mLearningModeToggleButton;
    private ToggleButton mLoadAdfToggleButton;

    private boolean mIsUseAreaLearning;
    private boolean mIsLoadAdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        setTitle(R.string.app_name);

        // Setup UI elements.
        mLearningModeToggleButton = (ToggleButton) findViewById(R.id.learning_mode);
        mLoadAdfToggleButton = (ToggleButton) findViewById(R.id.load_adf);

        mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
        mIsLoadAdf = mLoadAdfToggleButton.isChecked();

        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
    }

    /**
     * The "Load ADF" button has been clicked.
     * Defined in {@code activity_start.xml}
     * */
    public void loadAdfClicked(View v) {
        mIsLoadAdf = mLoadAdfToggleButton.isChecked();
    }

    /**
     * The "Learning Mode" button has been clicked.
     * Defined in {@code activity_start.xml}
     * */
    public void learningModeClicked(View v) {
        mIsUseAreaLearning = mLearningModeToggleButton.isChecked();
    }

    /**
     * The "Start" button has been clicked.
     * Defined in {@code activity_start.xml}
     * */
    public void startClicked(View v) {
        startAreaDescriptionActivity();
    }

    /**
     * The "ADF List View" button has been clicked.
     * Defined in {@code activity_start.xml}
     * */
    public void adfListViewClicked(View v) {
        startAdfListView();
    }

    /**
     * Start the main area description activity and pass in user's configuration.
     */
    private void startAreaDescriptionActivity() {
        Intent startAdIntent = new Intent(this, HelloAreaDescriptionActivity.class);
        startAdIntent.putExtra(USE_AREA_LEARNING, mIsUseAreaLearning);
        startAdIntent.putExtra(LOAD_ADF, mIsLoadAdf);
        startActivity(startAdIntent);
    }

    /**
     * Start the ADF list activity.
     */
    private void startAdfListView() {
        Intent startAdfListViewIntent = new Intent(this, AdfUuidListViewActivity.class);
        startActivity(startAdfListViewIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The result of the permission activity.
        //
        // Note that when the permission activity is dismissed, the HelloAreaDescriptionActivity's
        // onResume() callback is called. As the TangoService is connected in the onResume()
        // function, we do not call connect here.
        //
        // Check which request we're responding to
        if (requestCode == REQUEST_CODE_TANGO_PERMISSION) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.arealearning_permission, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}

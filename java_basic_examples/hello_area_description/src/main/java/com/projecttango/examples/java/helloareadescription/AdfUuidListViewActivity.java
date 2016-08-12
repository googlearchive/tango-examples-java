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
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

/**
 * This class lets you manage ADFs between this class's Application Package folder and API private
 * space. This show cases mainly three things: Import, Export, Delete an ADF file from API private
 * space to any known and accessible file path.
 */
public class AdfUuidListViewActivity extends Activity implements SetAdfNameDialog.CallbackListener {

    private ListView mTangoSpaceAdfListView, mAppSpaceAdfListView;
    private AdfUuidArrayAdapter mTangoSpaceAdfListAdapter, mAppSpaceAdfListAdapter;
    private ArrayList<AdfData> mTangoSpaceAdfDataList, mAppSpaceAdfDataList;
    private String[] mTangoSpaceMenuStrings, mAppSpaceMenuStrings;
    private String mAppSpaceAdfFolder;
    private Tango mTango;
    private volatile boolean mIsTangoReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.uuid_list_view);
        mTangoSpaceMenuStrings = getResources().getStringArray(
                R.array.set_dialog_menu_items_api_space);
        mAppSpaceMenuStrings = getResources().getStringArray(
                R.array.set_dialog_menu_items_app_space);

        // Get API ADF ListView Ready
        mTangoSpaceAdfListView = (ListView) findViewById(R.id.uuid_list_view_tango_space);
        mTangoSpaceAdfDataList = new ArrayList<AdfData>();
        mTangoSpaceAdfListAdapter = new AdfUuidArrayAdapter(this, mTangoSpaceAdfDataList);
        mTangoSpaceAdfListView.setAdapter(mTangoSpaceAdfListAdapter);
        registerForContextMenu(mTangoSpaceAdfListView);

        // Get App Space ADF List View Ready
        mAppSpaceAdfListView = (ListView) findViewById(R.id.uuid_list_view_application_space);
        mAppSpaceAdfFolder = getAppSpaceAdfFolder();
        mAppSpaceAdfDataList = new ArrayList<AdfData>();
        mAppSpaceAdfListAdapter = new AdfUuidArrayAdapter(this, mAppSpaceAdfDataList);
        mAppSpaceAdfListView.setAdapter(mAppSpaceAdfListAdapter);
        registerForContextMenu(mAppSpaceAdfListView);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service, since we call
        // mTango.disconnect() in onPause, this will unbind Tango Service, so
        // everytime when onResume gets called, we should create a new Tango object.
        mTango = new Tango(AdfUuidListViewActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                mIsTangoReady = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (AdfUuidListViewActivity.this) {
                            updateList();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (this) {
            // Unbinds Tango Service
            mTango.disconnect();
        }
        mIsTangoReady = false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (v.getId() == R.id.uuid_list_view_tango_space) {
            menu.setHeaderTitle(mTangoSpaceAdfDataList.get(info.position).uuid);
            menu.add(mTangoSpaceMenuStrings[0]);
            menu.add(mTangoSpaceMenuStrings[1]);
            menu.add(mTangoSpaceMenuStrings[2]);
        }

        if (v.getId() == R.id.uuid_list_view_application_space) {
            menu.setHeaderTitle(mAppSpaceAdfDataList.get(info.position).uuid);
            menu.add(mAppSpaceMenuStrings[0]);
            menu.add(mAppSpaceMenuStrings[1]);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!mIsTangoReady) {
            Toast.makeText(this, R.string.tango_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String itemName = (String) item.getTitle();
        int index = info.position;

        // Rename the ADF from API storage
        if (itemName.equals(mTangoSpaceMenuStrings[0])) {
            // Delete the ADF from Tango space and update the Tango ADF Listview.
            showSetNameDialog(mTangoSpaceAdfDataList.get(index).uuid);
        } else if (itemName.equals(mTangoSpaceMenuStrings[1])) {
            // Delete the ADF from Tango space and update the Tango ADF Listview.
            deleteAdfFromTangoSpace(mTangoSpaceAdfDataList.get(index).uuid);
        } else if (itemName.equals(mTangoSpaceMenuStrings[2])) {
            // Export the ADF into application package folder and update the Listview.
            exportAdf(mTangoSpaceAdfDataList.get(index).uuid);
        } else if (itemName.equals(mAppSpaceMenuStrings[0])) {
            // Delete an ADF from App space and update the App space ADF Listview.
            deleteAdfFromAppSpace(mAppSpaceAdfDataList.get(index).uuid);
        } else if (itemName.equals(mAppSpaceMenuStrings[1])) {
            // Import an ADF from app space to Tango space.
            importAdf(mAppSpaceAdfDataList.get(index).uuid);
        }

        updateList();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.no_permissions, Toast.LENGTH_LONG).show();
            }
        }
        updateList();
    }

    /**
     * Implements SetAdfNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameOk(String name, String uuid) {
        if (!mIsTangoReady) {
            Toast.makeText(this, R.string.tango_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        TangoAreaDescriptionMetaData metadata;
        metadata = mTango.loadAreaDescriptionMetaData(uuid);
        byte[] adfNameBytes = metadata.get(TangoAreaDescriptionMetaData.KEY_NAME);
        if (adfNameBytes != name.getBytes()) {
            metadata.set(TangoAreaDescriptionMetaData.KEY_NAME, name.getBytes());
        }
        mTango.saveAreaDescriptionMetadata(uuid, metadata);
        updateList();
    }

    /**
     * Implements SetAdfNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameCancelled() {
        // Nothing to do here.
    }

    /**
     * Import an ADF from app space to Tango space.
     */
    private void importAdf(String uuid) {
        try {
            mTango.importAreaDescriptionFile(mAppSpaceAdfFolder + File.separator + uuid);
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.adf_exists_api_space, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Export an ADF from Tango space to app space.
     */
    private void exportAdf(String uuid) {
        try {
            mTango.exportAreaDescriptionFile(uuid, mAppSpaceAdfFolder);
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.adf_exists_app_space, Toast.LENGTH_SHORT).show();
        }
    }

    public void deleteAdfFromTangoSpace(String uuid) {
        try {
            mTango.deleteAreaDescription(uuid);
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.no_uuid_tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAdfFromAppSpace(String uuid) {
        File file = new File(mAppSpaceAdfFolder + File.separator + uuid);
        file.delete();
    }

    /*
     * Returns maps storage location in the App package folder. Creates a folder called Maps, if it
     * does not exist.
     */
    private String getAppSpaceAdfFolder() {
        String mapsFolder = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + "Maps";
        File file = new File(mapsFolder);
        if (!file.exists()) {
            file.mkdirs();
        }
        return mapsFolder;
    }

    /**
     * Updates the list of AdfData corresponding to the App space.
     */
    private void updateAppSpaceAdfList() {
        File file = new File(mAppSpaceAdfFolder);
        File[] adfFileList = file.listFiles();
        mAppSpaceAdfDataList.clear();

        for (int i = 0; i < adfFileList.length; ++i) {
            mAppSpaceAdfDataList.add(new AdfData(adfFileList[i].getName(), ""));
        }
    }

    /**
     * Updates the list of AdfData corresponding to the Tango space.
     */
    private void updateTangoSpaceAdfList() {
        ArrayList<String> fullUuidList;
        TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();

        try {
            // Get all ADF UUIDs.
            fullUuidList = mTango.listAreaDescriptions();
            // Get the names from the UUIDs.
            mTangoSpaceAdfDataList.clear();
            for (String uuid : fullUuidList) {
                String name;
                try {
                    metadata = mTango.loadAreaDescriptionMetaData(uuid);
                } catch (TangoErrorException e) {
                    Toast.makeText(this, R.string.tango_error, Toast.LENGTH_SHORT).show();
                }
                name = new String(metadata.get(TangoAreaDescriptionMetaData.KEY_NAME));
                mTangoSpaceAdfDataList.add(new AdfData(uuid, name));
            }
        } catch (TangoErrorException e) {
            Toast.makeText(this, R.string.tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Updates the list of AdfData from Tango and App space, and sets it to the adapters.
     */
    private void updateList() {
        // Update App space ADF Listview.
        updateAppSpaceAdfList();
        mAppSpaceAdfListAdapter.setAdfData(mAppSpaceAdfDataList);
        mAppSpaceAdfListAdapter.notifyDataSetChanged();

        // Update Tango space ADF Listview.
        updateTangoSpaceAdfList();
        mTangoSpaceAdfListAdapter.setAdfData(mTangoSpaceAdfDataList);
        mTangoSpaceAdfListAdapter.notifyDataSetChanged();
    }

    private void showSetNameDialog(String mCurrentUuid) {
        if (!mIsTangoReady) {
            Toast.makeText(this, R.string.tango_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle bundle = new Bundle();
        TangoAreaDescriptionMetaData metaData = mTango.loadAreaDescriptionMetaData(mCurrentUuid);
        byte[] adfNameBytes = metaData.get(TangoAreaDescriptionMetaData.KEY_NAME);
        if (adfNameBytes != null) {
            String fillDialogName = new String(adfNameBytes);
            bundle.putString(TangoAreaDescriptionMetaData.KEY_NAME, fillDialogName);
        }
        bundle.putString(TangoAreaDescriptionMetaData.KEY_UUID, mCurrentUuid);
        FragmentManager manager = getFragmentManager();
        SetAdfNameDialog setAdfNameDialog = new SetAdfNameDialog();
        setAdfNameDialog.setArguments(bundle);
        setAdfNameDialog.show(manager, "ADFNameDialog");
    }
}

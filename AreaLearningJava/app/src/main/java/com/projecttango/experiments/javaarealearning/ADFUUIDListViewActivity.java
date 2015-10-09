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
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;


/**
 * This class lets you manage ADFs between this class's Application Package folder and API private
 * space. This show cases mainly three things: Import, Export, Delete an ADF file from API private
 * space to any known and accessible file path.
 * 
 */
public class ADFUUIDListViewActivity extends Activity implements SetADFNameDialog.CallbackListener {
    private ADFDataSource mADFDataSource;
    private ListView mUUIDListView, mAppSpaceUUIDListView;
    ADFUUIDArrayAdapter mADFAdapter, mAppSpaceADFAdapter;
    String[] mUUIDList, mUUIDNames, mAppSpaceUUIDList, mAppSpaceUUIDNames;
    String[] mAPISpaceMenuStrings, mAppSpaceMenuStrings;
    String mAppSpaceADFFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uuid_listview);
        mAPISpaceMenuStrings = getResources().getStringArray(R.array.SetDialogMenuItemsAPISpace);
        mAppSpaceMenuStrings = getResources().getStringArray(R.array.SetDialogMenuItemsAppSpace);

        // Get API ADF ListView Ready
        mUUIDListView = (ListView) findViewById(R.id.uuidlistviewAPI);
        mADFDataSource = new ADFDataSource(this);
        mUUIDList = mADFDataSource.getFullUUIDList();
        mUUIDNames = mADFDataSource.getUUIDNames();
        mADFAdapter = new ADFUUIDArrayAdapter(this, mUUIDList, mUUIDNames);
        mUUIDListView.setAdapter(mADFAdapter);
        registerForContextMenu(mUUIDListView);

        // Get Apps Space ADF List View Ready
        mAppSpaceUUIDListView = (ListView) findViewById(R.id.uuidlistviewApplicationSpace);
        mAppSpaceADFFolder = getAppSpaceADFFolder();
        mAppSpaceUUIDList = getAppSpaceADFList();
        mAppSpaceADFAdapter = new ADFUUIDArrayAdapter(this, mAppSpaceUUIDList, null);
        mAppSpaceUUIDListView.setAdapter(mAppSpaceADFAdapter);
        registerForContextMenu(mAppSpaceUUIDListView);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTangoAdfsListView();
        updateAppListView();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (v.getId() == R.id.uuidlistviewAPI) {
            menu.setHeaderTitle(mUUIDList[info.position]);
            menu.add(mAPISpaceMenuStrings[0]);
            menu.add(mAPISpaceMenuStrings[1]);
            menu.add(mAPISpaceMenuStrings[2]);
        }

        if (v.getId() == R.id.uuidlistviewApplicationSpace) {
            menu.setHeaderTitle(mAppSpaceUUIDList[info.position]);
            menu.add(mAppSpaceMenuStrings[0]);
            menu.add(mAppSpaceMenuStrings[1]);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();
        String itemName = (String) item.getTitle();
        // Rename the ADF from API storage
        if (itemName.equals(mAPISpaceMenuStrings[0])) {
            showSetNameDialog(mUUIDList[info.position]);
        } else if (itemName.equals(mAPISpaceMenuStrings[1])) {
            // Delete the ADF from API storage and update the API ADF Listview
            mADFDataSource.deleteADFandUpdateList(mUUIDList[info.position]);
            // Update the API ADF Listview
            updateTangoAdfsListView();
        } else if (itemName.equals(mAPISpaceMenuStrings[2])) {
            // Export the ADF into application package folder and update the
            // Listview
            try {
                mADFDataSource.getTango().exportAreaDescriptionFile(mUUIDList[info.position],
                        mAppSpaceADFFolder);
            } catch (TangoErrorException e) {
                Toast.makeText(this, R.string.adf_exists_app_space, Toast.LENGTH_SHORT).show();
            }
        } else if (itemName.equals(mAppSpaceMenuStrings[0])) {
            // Delete an ADF from App space and update the App space ADF Listview.
            File file = new File(mAppSpaceADFFolder + File.separator
                    + mAppSpaceUUIDList[info.position]);
            file.delete();
            updateAppListView();
        } else if (itemName.equals(mAppSpaceMenuStrings[1])) {
            // Import an ADF into API private Storage and update the API ADF
            // Listview.
            try {
                mADFDataSource.getTango().importAreaDescriptionFile(
                        mAppSpaceADFFolder + File.separator + mAppSpaceUUIDList[info.position]);
            } catch (TangoErrorException e) {
                Toast.makeText(this, R.string.adf_exists_api_space, Toast.LENGTH_SHORT).show();
            }
        }
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
        updateTangoAdfsListView();
        updateAppListView();
    }

    /*
     * Gets the latest ADFs from Project Tango API storage and updates them in the List View.
     */
    private void updateTangoAdfsListView() {
        // Update API ADF Listview
        mUUIDList = mADFDataSource.getFullUUIDList();
        mUUIDNames = mADFDataSource.getUUIDNames();
        mADFAdapter = new ADFUUIDArrayAdapter(this, mUUIDList, mUUIDNames);
        mUUIDListView.setAdapter(mADFAdapter);
    }

    /*
     * Gets the latest ADFs from application package folder and updates them in the List View.
     */
    private void updateAppListView() {
        // Update App space ADF Listview
        mAppSpaceUUIDList = getAppSpaceADFList();
        mAppSpaceADFAdapter = new ADFUUIDArrayAdapter(this, mAppSpaceUUIDList, null);
        mAppSpaceUUIDListView.setAdapter(mAppSpaceADFAdapter);

    }

    /*
     * Returns maps storage location in the App package folder. Creates a folder called Maps, if it
     * doesnt exist.
     */
    private String getAppSpaceADFFolder() {
        String mapsFolder = getFilesDir().getAbsolutePath() + File.separator + "Maps";
        File file = new File(mapsFolder);
        if (!file.exists()) {
            file.mkdirs();
        }
        return mapsFolder;
    }

    /*
     * Returns the names of all ADFs in String array in the files/maps folder.
     */
    private String[] getAppSpaceADFList() {
        File file = new File(mAppSpaceADFFolder);
        File[] adfFileList = file.listFiles();
        String[] appSpaceADFList = new String[adfFileList.length];
        for (int i = 0; i < appSpaceADFList.length; i++) {
            appSpaceADFList[i] = adfFileList[i].getName();
        }
        Arrays.sort(appSpaceADFList);
        return appSpaceADFList;
    }

    private void showSetNameDialog(String mCurrentUUID) {
        Bundle bundle = new Bundle();
        TangoAreaDescriptionMetaData metaData = mADFDataSource.getTango()
                .loadAreaDescriptionMetaData(mCurrentUUID);
        byte[] adfNameBytes = metaData.get("name");
        if (adfNameBytes != null) {
            String fillDialogName = new String(adfNameBytes);
            bundle.putString("name", fillDialogName);
        }
        bundle.putString("id", mCurrentUUID);
        FragmentManager manager = getFragmentManager();
        SetADFNameDialog setADFNameDialog = new SetADFNameDialog();
        setADFNameDialog.setArguments(bundle);
        setADFNameDialog.show(manager, "ADFNameDialog");
    }

    /**
     * Implements SetADFNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameOk(String name, String uuid) {
        TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
        metadata = mADFDataSource.getTango().loadAreaDescriptionMetaData(uuid);
        byte[] adfNameBytes = metadata.get("name");
        if (adfNameBytes != name.getBytes()) {
            adfNameBytes = name.getBytes();
            metadata.set("name", name.getBytes());
        }
        mADFDataSource.getTango().saveAreaDescriptionMetadata(uuid, metadata);
        mUUIDList = mADFDataSource.getFullUUIDList();
        mUUIDNames = mADFDataSource.getUUIDNames();
        mADFAdapter = new ADFUUIDArrayAdapter(this, mUUIDList, mUUIDNames);
        mUUIDListView.setAdapter(mADFAdapter);
    }

    /**
     * Implements SetADFNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameCancelled() {
        // Nothing to do here.
    }
}

/**
 * This is an adapter class which maps the ListView with a Data Source(Array of strings).
 * 
 */
class ADFUUIDArrayAdapter extends ArrayAdapter<String> {
    Context mContext;
    private String[] mUUIDStringArray, mUUIDNamesStringArray;

    public ADFUUIDArrayAdapter(Context context, String[] uuids, String[] uuidNames) {
        super(context, R.layout.uuid_view, R.id.uuid, uuids);
        mContext = context;
        mUUIDStringArray = uuids;
        if (uuidNames != null) {
            mUUIDNamesStringArray = uuidNames;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflator = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View row = inflator.inflate(R.layout.uuid_view, parent, false);
        TextView uuid = (TextView) row.findViewById(R.id.uuid);
        TextView uuidName = (TextView) row.findViewById(R.id.adfName);
        uuid.setText(mUUIDStringArray[position]);

        if (mUUIDNamesStringArray != null) {
            uuidName.setText(mUUIDNamesStringArray[position]);
        } else {
            uuidName.setText(R.string.metadata_not_read);
        }
        return row;
    }
}

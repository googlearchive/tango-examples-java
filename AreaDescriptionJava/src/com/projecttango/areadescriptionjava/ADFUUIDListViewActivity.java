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

import java.io.File;
import java.util.Arrays;

import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;
import com.projecttango.areadescriptionjava.SetADFNameDialog.SetNameAndUUIDCommunicator;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
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

/**
 * This class lets you manage ADFs between this class's Application Package folder 
 * and API private space. This show cases mainly three things:
 * Import, Export, Delete an ADF file from API private space to any known and accessible file path.
 *
 */
public class ADFUUIDListViewActivity extends Activity implements SetNameAndUUIDCommunicator{
	private ADFDataSource mADFDataSource;
	private ListView mUUIDListView,mAppSpaceUUIDListView;
	ADFUUIDArrayAdapter mADFAdapter,mAppSpaceADFAdapter;
	String[] mUUIDList,mUUIDNames,mAppSpaceUUIDList,mAppSpaceUUIDNames;
	String[] mAPISpaceMenuStrings,mAppSpaceMenuStrings;
	String mAppSpaceADFFolder;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.uuid_listview);
		mAPISpaceMenuStrings = getResources().getStringArray(R.array.SetDialogMenuItemsAPISpace);
		mAppSpaceMenuStrings = getResources().getStringArray(R.array.SetDialogMenuItemsAppSpace);
		
		//Get API ADF ListView Ready
		mUUIDListView = (ListView)findViewById(R.id.uuidlistviewAPI);
		mADFDataSource = new ADFDataSource(getApplicationContext());
		mUUIDList =  mADFDataSource.getFullUUIDList();
		mUUIDNames = mADFDataSource.getUUIDNames();
		mADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(), mUUIDList, mUUIDNames);
		mUUIDListView.setAdapter(mADFAdapter);
		registerForContextMenu(mUUIDListView);
		
		//Get Apps Space ADF List View Ready
		mAppSpaceUUIDListView = (ListView) findViewById(R.id.uuidlistviewApplicationSpace);
		mAppSpaceADFFolder = getAppSpaceADFFolder();
		mAppSpaceUUIDList = getAppSpaceADFList();
		mAppSpaceADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(),mAppSpaceUUIDList,null);
		mAppSpaceUUIDListView.setAdapter(mAppSpaceADFAdapter);
		registerForContextMenu(mAppSpaceUUIDListView);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu,View v,ContextMenuInfo menuInfo){
		if(v.getId() == R.id.uuidlistviewAPI){
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
			
			menu.setHeaderTitle(mUUIDList[info.position]);
			menu.add(mAPISpaceMenuStrings[0]);
			menu.add(mAPISpaceMenuStrings[1]);
			menu.add(mAPISpaceMenuStrings[2]);
		}
		
		if(v.getId() == R.id.uuidlistviewApplicationSpace){
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
			menu.setHeaderTitle(mAppSpaceUUIDList[info.position]);
			menu.add(mAppSpaceMenuStrings[0]);
			menu.add(mAppSpaceMenuStrings[1]);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
	  AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
	  String itemName = (String) item.getTitle();
	  // Delete the ADF from API storage and update the API ADF Listview
	  if(itemName.equals(mAPISpaceMenuStrings[0])) {
		  showSetNameDialog(mUUIDList[info.position]);
	  } 
	  // Delete the ADF from API storage and update the API ADF Listview
	  else if(itemName.equals(mAPISpaceMenuStrings[1])) {
		  mADFDataSource.deleteADFandUpdateList(mUUIDList[info.position]);
		  //Update the API ADF Listview
		  mUUIDList = mADFDataSource.getFullUUIDList();
		  mUUIDNames = mADFDataSource.getUUIDNames();
		  mADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(), mUUIDList, mUUIDNames);
		  mUUIDListView.setAdapter(mADFAdapter);
	  } 
	  //Export the ADF into application package folder and update the Listview
	  else if(itemName.equals(mAPISpaceMenuStrings[2])) {
		  try{
			  mADFDataSource.getTango().exportAreaDescriptionFile(mUUIDList[info.position], mAppSpaceADFFolder);
		  }catch(TangoErrorException e){
			  Toast.makeText(getApplicationContext(), R.string.ADFExistsAppSpace, Toast.LENGTH_SHORT).show();
		  }
		  // Update App space ADF Listview
		  mAppSpaceUUIDList = getAppSpaceADFList();
		  mAppSpaceADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(),mAppSpaceUUIDList,null);
		  mAppSpaceUUIDListView.setAdapter(mAppSpaceADFAdapter);
	  }	
	  
	  //Delete an ADF from App space and update the App space ADF Listview
	  else if(itemName.equals(mAppSpaceMenuStrings[0])) {
		  File file = new File(mAppSpaceADFFolder +File.separator+ mAppSpaceUUIDList[info.position]);
		  file.delete();
		  //Update App space ADF ListView
		  mAppSpaceUUIDList = getAppSpaceADFList();
		  mAppSpaceADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(),mAppSpaceUUIDList,null);
		  mAppSpaceUUIDListView.setAdapter(mAppSpaceADFAdapter);
	  }
	  
	  //Import an ADF into API private Storage and update the API ADF Listview
	  else if(itemName.equals(mAppSpaceMenuStrings[1])) {
		  try{
			  mADFDataSource.getTango().importAreaDescriptionFile(mAppSpaceADFFolder+File.separator+mAppSpaceUUIDList[info.position]);
			  }catch(TangoErrorException e) {
				  Toast.makeText(getApplicationContext(), R.string.ADFExistsAPISpace, Toast.LENGTH_SHORT).show();
			  }
		  //Update API ADF Listview
		  mUUIDList = mADFDataSource.getFullUUIDList();
		  mUUIDNames = mADFDataSource.getUUIDNames();
		  mADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(),mUUIDList,mUUIDNames);
		  mUUIDListView.setAdapter(mADFAdapter);
		  }
	  return true;
	}
	
	/* Returns maps storage location in the App package folder. 
	 * Creates a folder called Maps, if it doesnt exist.
	 */
	private String getAppSpaceADFFolder() {
		String mapsFolder = getFilesDir().getAbsolutePath() + File.separator + "Maps";
		File file = new File(mapsFolder);
		if (!file.exists())
			file.mkdirs();
		return mapsFolder;
	}
	
	/*
	 * Returns the names of all ADFs in String array in the
	 * files/maps folder.
	 */
	private String[] getAppSpaceADFList() {
		File file = new File(mAppSpaceADFFolder);
		File[] ADFFileList = file.listFiles();
		String[] appSpaceADFList = new String[ADFFileList.length];
		for(int i=0;i<appSpaceADFList.length;i++){
			appSpaceADFList[i] = ADFFileList[i].getName();
		}
		Arrays.sort(appSpaceADFList);
		return appSpaceADFList;
	}
	
	private void showSetNameDialog(String mCurrentUUID) {
		Bundle bundle = new Bundle();
		TangoAreaDescriptionMetaData metaData = mADFDataSource.getTango().loadAreaDescriptionMetaData(mCurrentUUID);
		byte[] adfNameBytes =  metaData.get("name");
		if(adfNameBytes != null){
			String fillDialogName = new String(adfNameBytes);
			bundle.putString("name", fillDialogName);
		}
		bundle.putString("id", mCurrentUUID);
		FragmentManager manager = getFragmentManager();
		SetADFNameDialog setADFNameDialog = new SetADFNameDialog();
		setADFNameDialog.setArguments(bundle);
		setADFNameDialog.show(manager, "ADFNameDialog");
	}

	@Override
	public void SetNameAndUUID(String name, String uuid) {
		TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
		metadata = mADFDataSource.getTango().loadAreaDescriptionMetaData(uuid);
		byte[] adfNameBytes = metadata.get("name");
		if(adfNameBytes != name.getBytes()){
			adfNameBytes = name.getBytes();
			metadata.set("name", name.getBytes());
		}
		mADFDataSource.getTango().saveAreaDescriptionMetadata(uuid, metadata);
		mUUIDList = mADFDataSource.getFullUUIDList();
		mUUIDNames = mADFDataSource.getUUIDNames();
		mADFAdapter = new ADFUUIDArrayAdapter(getApplicationContext(), mUUIDList, mUUIDNames);
		mUUIDListView.setAdapter(mADFAdapter);
	}
}

/**
 * This is an adapter class which maps the ListView with 
 * a Data Source(Array of strings)
 *
 */
class ADFUUIDArrayAdapter extends ArrayAdapter<String>
{
	Context mContext;
	private String[] mUUIDStringArray,mUUIDNamesStringArray;
	public ADFUUIDArrayAdapter(Context context,String[] uuids,String[] uuidNames) {
		super(context, R.layout.uuid_view, R.id.uuid, uuids);
		mContext = context;
		mUUIDStringArray = uuids;
		if(uuidNames != null){
			mUUIDNamesStringArray = uuidNames;
		}
	}
	
	@Override
	public View getView(int position,View convertView, ViewGroup parent) {
		LayoutInflater inflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View row = inflator.inflate(R.layout.uuid_view, parent,false);
		TextView uuid = (TextView) row.findViewById(R.id.uuid);
		TextView uuidName = (TextView) row.findViewById(R.id.adfName);
		uuid.setText(mUUIDStringArray[position]);
		
		if(mUUIDNamesStringArray != null){
			uuidName.setText(mUUIDNamesStringArray[position]);
		}
		else
			uuidName.setText("Metadata cannot be read");
		return row;
	}
}
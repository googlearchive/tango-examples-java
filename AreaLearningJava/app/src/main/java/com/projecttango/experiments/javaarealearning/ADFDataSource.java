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

import android.content.Context;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * This class interfaces a Tango Object and maintains a full list of ADF UUIds. Whenever an adf is
 * deleted or added, getFullUUIDList needs to be called to update the UUIDList within this class.
 * 
 */
public class ADFDataSource {
    private Tango mTango;
    private ArrayList<String> mFullUUIDList;
    private Context mContext;

    public ADFDataSource(Context context) {
        mContext = context;
        mTango = new Tango(context);
        try {
            mFullUUIDList = mTango.listAreaDescriptions();
        } catch (TangoErrorException e) {
            Toast.makeText(mContext, R.string.tango_error, Toast.LENGTH_SHORT).show();
        }
        if (mFullUUIDList.size() == 0) {
            Toast.makeText(context, R.string.no_adfs_tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    public String[] getFullUUIDList() {
        try {
            mFullUUIDList = mTango.listAreaDescriptions();
        } catch (TangoErrorException e) {
            Toast.makeText(mContext, R.string.tango_error, Toast.LENGTH_SHORT).show();
        }
        return mFullUUIDList.toArray(new String[mFullUUIDList.size()]);
    }

    public String[] getUUIDNames() {
        TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
        String[] list = new String[mFullUUIDList.size()];
        for (int i = 0; i < list.length; i++) {
            try {
                metadata = mTango.loadAreaDescriptionMetaData(mFullUUIDList.get(i));
            } catch (TangoErrorException e) {
                Toast.makeText(mContext, R.string.tango_error, Toast.LENGTH_SHORT).show();
            }
            list[i] = new String(metadata.get("name"));
        }
        return list;
    }

    public void deleteADFandUpdateList(String uuid) {
        try {
            mTango.deleteAreaDescription(uuid);
        } catch (TangoErrorException e) {
            Toast.makeText(mContext, R.string.no_uuid_tango_error, Toast.LENGTH_SHORT).show();
        }
        mFullUUIDList.clear();
        try {
            mFullUUIDList = mTango.listAreaDescriptions();
        } catch (TangoErrorException e) {
            Toast.makeText(mContext, R.string.tango_error, Toast.LENGTH_SHORT).show();
        }
    }

    public Tango getTango() {
        return mTango;
    }
}

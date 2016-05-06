/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * This is an adapter class which maps the ListView with a Data Source(Array of strings).
 */
class AdfUuidArrayAdapter extends ArrayAdapter<String> {
    private List<AdfData> mAdfDataList;

    public AdfUuidArrayAdapter(Context context, ArrayList<AdfData> adfDataList) {
        super(context, R.layout.adf_list_row);
        setAdfData(adfDataList);
    }

    public void setAdfData(ArrayList<AdfData> adfDataList) {
        mAdfDataList = adfDataList;
    }

    @Override
    public int getCount() {
        return mAdfDataList.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row;
        if (convertView == null) {
            LayoutInflater inflator = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            row = inflator.inflate(R.layout.adf_list_row, parent, false);
        } else {
            row = convertView;
        }
        TextView uuid = (TextView) row.findViewById(R.id.adf_uuid);
        TextView name = (TextView) row.findViewById(R.id.adf_name);

        if (mAdfDataList == null) {
            name.setText(R.string.metadata_not_read);
        } else {
            name.setText(mAdfDataList.get(position).name);
            uuid.setText(mAdfDataList.get(position).uuid);
        }
        return row;
    }
}

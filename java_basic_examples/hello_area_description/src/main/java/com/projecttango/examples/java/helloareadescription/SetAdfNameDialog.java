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

import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Queries the user for an ADF name, optionally showing the ADF UUID.
 */
public class SetAdfNameDialog extends DialogFragment {

    EditText mNameEditText;
    TextView mUuidTextView;
    CallbackListener mCallbackListener;
    Button mOkButton;
    Button mCancelButton;

    interface CallbackListener {
        void onAdfNameOk(String name, String uuid);
        void onAdfNameCancelled();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbackListener = (CallbackListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflator, ViewGroup container,
                             Bundle savedInstanceState) {
        View dialogView = inflator.inflate(R.layout.set_name_dialog, container, false);
        getDialog().setTitle(R.string.set_name_dialog_title);
        mNameEditText = (EditText) dialogView.findViewById(R.id.name);
        mUuidTextView = (TextView) dialogView.findViewById(R.id.uuidDisplay);
        setCancelable(false);
        mOkButton = (Button) dialogView.findViewById(R.id.ok);
        mOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mCallbackListener.onAdfNameOk(
                        mNameEditText.getText().toString(),
                        mUuidTextView.getText().toString());
                dismiss();
            }
        });
        mCancelButton = (Button) dialogView.findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mCallbackListener.onAdfNameCancelled();
                dismiss();
            }
        });
        String name = this.getArguments().getString(TangoAreaDescriptionMetaData.KEY_NAME);
        String id = this.getArguments().getString(TangoAreaDescriptionMetaData.KEY_UUID);
        if (name != null) {
            mNameEditText.setText(name);
        }
        if (id != null) {
            mUuidTextView.setText(id);
        }
        return dialogView;
    }
}

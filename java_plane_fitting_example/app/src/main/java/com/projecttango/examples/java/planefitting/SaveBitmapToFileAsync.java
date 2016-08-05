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

package com.projecttango.examples.java.planefitting;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.File;

/**
 * Async Task which saves a bitmap to file.
 */
public class SaveBitmapToFileAsync extends AsyncTask<Bitmap, Void, String> {

    /**
     * Callback for saving bitmap to file.
     */
    public interface OnBitmapSaveListener {
        void onBitmapSaved(boolean success, String filePath);
    }

    private OnBitmapSaveListener mListener;

    public SaveBitmapToFileAsync(OnBitmapSaveListener listener) {
        mListener = listener;
    }

    protected String doInBackground(Bitmap... images) {
        File dir = ScreenshotHelper.getScreenshotFolder();
        String filename = ScreenshotHelper.generateUniqueFileName();
        File file = new File(dir, filename);
        if (ScreenshotHelper.saveBitmapToFile(file, images[0])) {
            String filePath[] = new String[1];
            filePath[0] = file.getAbsolutePath();
        }
        return file.getAbsolutePath();
    }

    protected void onPostExecute(String filename) {
        if (mListener != null) {
            mListener.onBitmapSaved(!filename.isEmpty(), filename);
        }
    }
}
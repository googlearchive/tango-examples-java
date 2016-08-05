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

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.opengl.GLException;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.microedition.khronos.opengles.GL10;

/**
 * Helper class that manages screenshots. Allows generating a Bitmap file from a GLSurface
 * and storing it in the Android Gallery.
 */
public class ScreenshotHelper {

    private static final String TAG = ScreenshotHelper.class.getSimpleName();

    private static final String FOLDER = File.separator + "Screenshots" + File.separator;
    private static final String PREFIX = "screenshot_";
    private static final String SUFFIX = ".jpg";
    private static int count = 0;

    /**
     * Generates a Bitmap from a GLSurface. Should be called from GLSurfaceView.onDrawFrame().
     *
     * @param x      Bitmap origin x coordinate.
     * @param y      Bitmap origin y coordinate.
     * @param width  Bitmap width.
     * @param height Bitmap height.
     * @param gl     the GL interface.
     * @return the generated Bitmap file, in case of success; null otherwise.
     * @throws OutOfMemoryError
     */
    protected static Bitmap getBitmap(int x, int y, int width, int height, GL10 gl)
            throws OutOfMemoryError {

        int bitmapBuffer[] = new int[width * height];
        int bitmapSource[] = new int[width * height];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            gl.glReadPixels(x, y, width, height, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < height; i++) {
                offset1 = i * width;
                offset2 = (height - i - 1) * width;
                for (int j = 0; j < width; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            Log.e(TAG, "Error generating Bitmap.", e);
            return null;
        }

        Bitmap bmp = Bitmap.createBitmap(bitmapSource, width, height, Bitmap.Config.ARGB_8888);
        return bmp;
    }

    public static boolean saveBitmapToFile(File file,
                                           Bitmap bitmap) {
        try {
            OutputStream outputStream;
            outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG,
                    100 /* max quality */,
                    outputStream);
            outputStream.flush();
            outputStream.close();

        } catch (IOException e) {
            Log.e(TAG, "Error saving image to gallery", e);
            return false;
        }
        return true;
    }

    public static File getScreenshotFolder() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath()
                + FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static synchronized String generateUniqueFileName() {
        count++;
        return PREFIX
                + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
                + "-" + count
                + SUFFIX;
    }

    public static void triggerMediaScan(Context context, String[] filePaths) {
        MediaScannerConnection.scanFile(context,
                filePaths,
                null,
                null);
    }
}
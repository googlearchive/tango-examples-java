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
package com.projecttango.experiments.javapointcloud;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * This is a thread  safe class that synchronizes the data copy between onXyzAvailable callbacks
 * and render loop.
 * Synchronization of point cloud data primarily involves registering for
 * callbacks in {@link PointCloudActivity} and passing on the necessary information to stored objects.
 * It also takes care of passing a floatbuffer which is a reference to
 * latest point cloud buffer that is to used for rendering.
 * To reduce the number of point cloud data copies between callback and render
 * threads we use three buffers which are synchronously exchanged between renderloop and callbacks
 * so that the render loop always contains the latest point cloud data.
 * 1. Callback buffer : The buffer to which pointcloud data received from Tango
 * Service callback is copied out.
 * 2. Shared buffer: This buffer is used to share the data between Service
 * callback and Render loop
 * 3. Render Buffer: This buffer is used in the renderloop to project point
 * cloud data to a 2D image plane which is of the same size as RGB image. We
 * also make sure that this buffer contains the latest point cloud data that is
 *  received from the call back.
 */
public class PointCloudManager {
    private static final int BYTES_PER_FLOAT = 4;
    private static final int POINT_TO_XYZ = 3;
    private PointCloudData mCallbackPointCloudData;
    private PointCloudData mSharedPointCloudData;
    private PointCloudData mRenderPointCloudData;
    private boolean mSwapSignal;
    private Object mPointCloudLock;


    PointCloudManager(int maxDepthPoints){
        mSwapSignal = false;
        setupBuffers(maxDepthPoints);
        mPointCloudLock = new Object();
    }

    /**
     * Sets up three buffers namely, Callback, Shared and Render buffers allocated with maximum
     * number of points a point cloud can have.
     * @param maxDepthPoints
     */
    private void setupBuffers(int maxDepthPoints){
        mCallbackPointCloudData = new PointCloudData();
        mSharedPointCloudData = new PointCloudData();
        mRenderPointCloudData = new PointCloudData();
        mCallbackPointCloudData.floatBuffer = ByteBuffer
                .allocateDirect(maxDepthPoints * BYTES_PER_FLOAT * POINT_TO_XYZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mSharedPointCloudData.floatBuffer = ByteBuffer
                .allocateDirect(maxDepthPoints * BYTES_PER_FLOAT * POINT_TO_XYZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mRenderPointCloudData.floatBuffer = ByteBuffer
                .allocateDirect(maxDepthPoints * BYTES_PER_FLOAT * POINT_TO_XYZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * Updates the callbackbuffer with latest pointcloud and swaps the
     * @param callbackBuffer
     * @param pointCount
     */
    public void updateCallbackBufferAndSwap(FloatBuffer callbackBuffer, int pointCount){
        mSharedPointCloudData.floatBuffer.position(0);
        mCallbackPointCloudData.floatBuffer.put(callbackBuffer);
        synchronized (mPointCloudLock){
            FloatBuffer temp = mSharedPointCloudData.floatBuffer;
            int tempCount = pointCount;
            mSharedPointCloudData.floatBuffer = mCallbackPointCloudData .floatBuffer;
            mSharedPointCloudData.pointCount = mCallbackPointCloudData.pointCount;
            mCallbackPointCloudData.floatBuffer = temp;
            mCallbackPointCloudData.pointCount = tempCount;
            mSwapSignal = true;
        }
    }

    /**
     * Returns a shallow copy of latest Point Cloud Render buffer.If there is a swap signal available
     * SharedPointCloud buffer is swapped with Render buffer and it is returned.
     * @return PointClouData which contains a reference to latest PointCloud Floatbuffer and count.
     */
    public PointCloudData updateAndGetLatestPointCloudRenderBuffer(){
        synchronized (mPointCloudLock){
            if(mSwapSignal) {
                FloatBuffer temp = mRenderPointCloudData.floatBuffer;
                int tempCount = mRenderPointCloudData.pointCount;
                mRenderPointCloudData.floatBuffer = mSharedPointCloudData.floatBuffer;
                mRenderPointCloudData.pointCount = mSharedPointCloudData.pointCount;
                mSharedPointCloudData.floatBuffer = temp;
                mSharedPointCloudData.pointCount = tempCount;
                mSwapSignal = false;
            }
        }
        return mRenderPointCloudData;
    }

    /**
     * A class to hold Depth data in a {@link FloatBuffer} and number of points associated with it.
     */
    public class PointCloudData{
        public FloatBuffer floatBuffer;
        public int pointCount;
    }
}

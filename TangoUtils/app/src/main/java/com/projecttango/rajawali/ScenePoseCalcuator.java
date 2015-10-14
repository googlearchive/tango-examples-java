/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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
package com.projecttango.rajawali;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Matrix;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

/**
 * Convenient class for calculating transformations from the Tango world to the OpenGL world, using
 * Rajawali specific classes and conventions.
 * Note that in order to do transformations to the OpenGL camera coordinate frame, it is necessary
 * to first call <code>setupExtrinsics</code>. The recommended time to do this is right after
 * the Tango service is connected.
 */
public class ScenePoseCalcuator {
    private static final String TAG = ScenePoseCalcuator.class.getSimpleName();

    // Transformation from the Tango Area Description or Start of Service coordinate frames
    // to the OpenGL coordinate frame
    // NOTE: Rajawali uses column-major for matrices
    public static final Matrix4 OPENGL_T_TANGO_WORLD = new Matrix4(new double[]{
            1, 0, 0, 0,
            0, 0,-1, 0,
            0, 1, 0, 0,
            0, 0, 0, 1
    });
    // Transformation from the Tango RGB camera coordinate frame to the OpenGL camera frame
    public static final Matrix4 COLOR_CAMERA_T_OPENGL_CAMERA = new Matrix4(new double[] {
            1, 0, 0, 0,
            0,-1, 0, 0,
            0, 0,-1, 0,
            0, 0, 0, 1
    });

    public static final Matrix4 DEPTH_CAMERA_T_OPENGL_CAMERA = new Matrix4(new double[]{
            1, 0, 0, 0,
            0,-1, 0, 0,
            0, 0,-1, 0,
            0, 0, 0, 1
    });

    // Transformation from the position of the Depth camera to the Device frame
    private Matrix4 mDeviceTDepthCamera;

    // Transformation from the position of the Color Camera to the Device frame
    private Matrix4 mDeviceTColorCamera;
    /**
     * Converts from TangoPoseData to a Matrix4 for transformations.
     */
    public static Matrix4 tangoPoseToMatrix(TangoPoseData tangoPose) {
        Vector3 v = new Vector3(tangoPose.translation[0],
                tangoPose.translation[1], tangoPose.translation[2]);
        Quaternion q = new Quaternion(tangoPose.rotation[3], tangoPose.rotation[0],
                tangoPose.rotation[1], tangoPose.rotation[2]);
        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.inverse();
        Matrix4 m = new Matrix4();
        m.setAll(v, new Vector3(1, 1, 1), q);
        return m;
    }

    /**
     * Given a TangoPoseData object, calculate the corresponding position and orientation for a
     * OpenGL Scene Camera in the Rajawali world.
     */
    public Pose toOpenGLCameraPose(TangoPoseData tangoPose) {
        // We can't do this calculation until Extrinsics are set-up
        if (mDeviceTColorCamera == null) {
            throw new RuntimeException("You must call setupExtrinsics first.");
        }
        Matrix4 startServiceTdevice = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame
        Matrix4 openglTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice);

        // Get OpenGL camera pose in OpenGL world frame
        Matrix4 openglWorldTOpenglCamera = openglTDevice.multiply(mDeviceTColorCamera).
                multiply(COLOR_CAMERA_T_OPENGL_CAMERA);

        // Get translation and rotation components from the resulting transformation matrix
        Vector3 p = openglWorldTOpenglCamera.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(openglWorldTOpenglCamera);

        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.inverse();

        // Notify of the new Camera information
        return new Pose(p, q);
    }

    /**
     * Given a TangoPoseData object, calculate the corresponding position and orientation for a
     * PointCloud in Depth camera coordinate system to the Rajawali world.
     */
    public Pose toOpenGLPointCloudPose(TangoPoseData tangoPose) {

        //conversion matrix to put point cloud data in Rajwali/Opengl Coordinate system.
        Matrix4 invertYandZMatrix = new Matrix4(new double[]{1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, -1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, -1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f });
        // We can't do this calculation until Extrinsics are set-up
        if (mDeviceTDepthCamera == null) {
            throw new RuntimeException("You must call setupExtrinsics first.");
        }
        Matrix4 startServiceTdevice = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame
        Matrix4 openglTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice);

        // Get OpenGL camera pose in OpenGL world frame
        Matrix4 openglWorldTOpenglCamera = openglTDevice.multiply(mDeviceTDepthCamera).
                multiply(DEPTH_CAMERA_T_OPENGL_CAMERA).multiply(invertYandZMatrix);

        // Get translation and rotation components from the resulting transformation matrix
        Vector3 p = openglWorldTOpenglCamera.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(openglWorldTOpenglCamera);

        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.inverse();

        // Notify of the new Camera information
        return new Pose(p, q);
    }

    /**
     * Given a TangoPoseData object, calculate the corresponding position and orientation for a
     * 3D object in the Rajawali world.
     */
    static public Pose toOpenGLPose(TangoPoseData tangoPose) {
        Matrix4 start_service_T_device = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame
        Matrix4 opengl_world_T_device = OPENGL_T_TANGO_WORLD.clone().multiply(start_service_T_device);

        // Get translation and rotation components from the resulting transformation matrix
        Vector3 p = opengl_world_T_device.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(opengl_world_T_device);

        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.inverse();

        // Notify of the new Camera information
        return new Pose(p, q);
    }

    public void setupExtrinsics(Matrix4 imutColorCamera, Matrix4 imuTDepthCamera, Matrix4 imuTDevice){
        mDeviceTDepthCamera = imuTDevice.clone().inverse().clone().multiply(imuTDepthCamera);
        setupExtrinsics(imutColorCamera,imuTDevice);
    }

    /**
     * Calculate and record the transformations between the Tango color camera and the device.
     */
    public void setupExtrinsics(Matrix4 imutColorCamera, Matrix4 imuTDevice) {
        // Combine both to get the transform from the device pose to the RGB camera pose
        mDeviceTColorCamera = imuTDevice.clone().inverse().clone().multiply(imutColorCamera);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    public static Matrix4 calculateProjectionMatrix(int width, int height, double fx, double fy,
                                                    double cx, double cy) {
        // Uses frustumM to create a projection matrix taking into account Calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        double near = 0.1;
        double far = 100;

        double xScale = near / fx;
        double yScale = near / fy;
        double xOffset = (cx - (width / 2.0)) * xScale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yOffset = -(cy - (height / 2.0)) * yScale;

        double m[] = new double[16];
        Matrix.frustumM(m, 0,
                xScale * -width / 2.0 - xOffset,
                xScale * width / 2.0 - xOffset,
                yScale * -height / 2.0 - yOffset,
                yScale * height / 2.0 - yOffset,
                near, far);
        return new Matrix4(m);
    }
}

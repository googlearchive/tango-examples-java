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

import android.util.Log;

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

    // Up vector in the Tango Start of Service and Area Description frame
    public static final Vector3 TANGO_WORLD_UP = new Vector3(0, 0, 1);

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
        q.conjugate();
        Matrix4 m = new Matrix4();
        m.setAll(v, new Vector3(1, 1, 1), q);
        return m;
    }

    /**
     * Converts a transform in Matrix4 format to TangoPoseData.
     */
    public static TangoPoseData matrixToTangoPose(Matrix4 transform) {
        // Get translation and rotation components from the transformation matrix
        Vector3 p = transform.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(transform);
        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.conjugate();

        TangoPoseData tangoPose = new TangoPoseData();
        double[] t = tangoPose.translation = new double[3];
        t[0] = p.x;
        t[1] = p.y;
        t[2] = p.z;
        double[] r = tangoPose.rotation = new double[4];
        r[0] = q.x;
        r[1] = q.y;
        r[2] = q.z;
        r[3] = q.w;

        return tangoPose;
    }

    /**
     * Helper method to extract a Pose object from a transformation matrix taking into account
     * Rajawali conventions.
     */
    public static Pose matrixToPose(Matrix4 m) {
        // Get translation and rotation components from the transformation matrix
        Vector3 p = m.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(m);

        // NOTE: Rajawali Quaternions use a left-hand rotation around the axis convention
        q.conjugate();

        return new Pose(p, q);
    }

    /**
     * Given a pose in start of service or area description frame, calculate the corresponding
     * position and orientation for a OpenGL Scene Camera in the Rajawali world.
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

        return matrixToPose(openglWorldTOpenglCamera);
    }

    /**
     * Given a TangoPoseData object, calculate the corresponding position and orientation for a
     * PointCloud in Depth camera coordinate system to the Rajawali world.
     */
    public Pose toOpenGLPointCloudPose(TangoPoseData tangoPose) {

        // We can't do this calculation until Extrinsics are set-up
        if (mDeviceTDepthCamera == null) {
            throw new RuntimeException("You must call setupExtrinsics first.");
        }

        //conversion matrix to put point cloud data in Rajwali/Opengl Coordinate system.
        Matrix4 invertYandZMatrix = new Matrix4(new double[]{1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, -1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, -1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f });

        Matrix4 startServiceTdevice = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame
        Matrix4 openglTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice);

        // Get OpenGL camera pose in OpenGL world frame
        Matrix4 openglWorldTOpenglCamera = openglTDevice.multiply(mDeviceTDepthCamera).
                multiply(DEPTH_CAMERA_T_OPENGL_CAMERA).multiply(invertYandZMatrix);

        return matrixToPose(openglWorldTOpenglCamera);
    }

    /**
     * Given a pose in start of service or area description frame calculate the corresponding
     * position and orientation for a 3D object in the Rajawali world.
     */
    static public Pose toOpenGLPose(TangoPoseData tangoPose) {
        Matrix4 start_service_T_device = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame
        Matrix4 opengl_world_T_device = OPENGL_T_TANGO_WORLD.clone().multiply(start_service_T_device);

        return matrixToPose(opengl_world_T_device);
    }

    /**
     * Given a point and a normal in depth camera frame and the device pose in start of service
     * frame at the time the point and normal were measured, calculate a TangoPoseData object in
     * Tango start of service frame.
     *
     * @param point      Point in depth frame where the plane has been detected.
     * @param normal     Normal of the detected plane
     * @param tangoPose  Device pose with respect to start of service at the time the plane was
     *                   fitted
     */
    public Pose planeFitToOpenGLPose(double[] point, double[] normal, TangoPoseData tangoPose) {
        if (mDeviceTDepthCamera == null) {
            throw new RuntimeException("You must call setupExtrinsics first");
        }

        Matrix4 startServiceTdevice = tangoPoseToMatrix(tangoPose);

        // Calculate the UP vector in the Depth frame at the provided measurement pose
        Vector3 depthUp = TANGO_WORLD_UP.clone();
        startServiceTdevice.clone().multiply(mDeviceTDepthCamera).inverse().rotateVector(depthUp);

        // Calculate the transform in depth frame corresponding to the plane fitting information
        Matrix4 depthTplane = matrixFromPointNormalUp(point, normal, depthUp);

        // Convert to OpenGL frame
        Matrix4 openglWorldTplane = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice)
                .multiply(mDeviceTDepthCamera).multiply(depthTplane);

        return matrixToPose(openglWorldTplane);
    }

    /**
     * Configure the scene pose calculator with the transformation between the selected
     * camera and the device.
     * Note that this requires going through the IMU since the Tango service can't calculate
     * the transform between the camera and the device directly.
     */
    public void setupExtrinsics(TangoPoseData imuTDevicePose, TangoPoseData imuTColorCameraPose,
                                TangoPoseData imuTDepthCameraPose) {
        Matrix4 deviceTImu = ScenePoseCalcuator.tangoPoseToMatrix(imuTDevicePose).inverse();
        Matrix4 imutColorCamera = ScenePoseCalcuator.tangoPoseToMatrix(imuTColorCameraPose);
        Matrix4 imuTDepthCamera = ScenePoseCalcuator.tangoPoseToMatrix(imuTDepthCameraPose);
        mDeviceTDepthCamera = deviceTImu.clone().multiply(imuTDepthCamera);
        mDeviceTColorCamera = deviceTImu.multiply(imutColorCamera);
    }

    private void throwExceptionIfNoExtrinsics() {

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


    /**
     * Calculates a transformation matrix based on a point, a normal and the up gravity vector.
     * The coordinate frame of the target transformation will be Z forward, X left, Y up.
     */
    private static Matrix4 matrixFromPointNormalUp(double[] point, double[] normal, Vector3 up) {
        Vector3 zAxis = new Vector3(normal);
        zAxis.normalize();
        Vector3 xAxis = new Vector3();
        xAxis.crossAndSet(up, zAxis);
        xAxis.normalize();
        Vector3 yAxis = new Vector3();
        yAxis.crossAndSet(xAxis, zAxis);
        yAxis.normalize();

        double[] rot = new double[16];

        rot[Matrix4.M00] = xAxis.x;
        rot[Matrix4.M10] = xAxis.y;
        rot[Matrix4.M20] = xAxis.z;

        rot[Matrix4.M01] = yAxis.x;
        rot[Matrix4.M11] = yAxis.y;
        rot[Matrix4.M21] = yAxis.z;

        rot[Matrix4.M02] = zAxis.x;
        rot[Matrix4.M12] = zAxis.y;
        rot[Matrix4.M22] = zAxis.z;

        rot[Matrix4.M33] = 1;

        Matrix4 m = new Matrix4(rot);
        m.setTranslation(point[0], point[1], point[2]);

        return m;
    }

    /**
     *  Calculates the transform between the color camera at a time t0 and the depth camera at a
     *  time t1, given the device poses at t0 and t1.
     */
    public TangoPoseData calculateColorCameraTDepthWithTime(TangoPoseData ssTdeviceT0pose,
                                                            TangoPoseData ssTdeviceT1pose) {
        if (mDeviceTDepthCamera == null) {
            throw new RuntimeException("You must call setupExtrinsics first");
        }

        // Operation at hand:
        //   rgb_t0_T_depth_t1 = rgb_T_dev * dev_t0_T_ss * ss_T_dev_t1 * dev_T_depth

        Matrix4 ssTdeviceT0 = tangoPoseToMatrix(ssTdeviceT0pose);
        Matrix4 ssTdeviceT1 = tangoPoseToMatrix(ssTdeviceT1pose);

        Matrix4 rgbt0Tdeptht1 = mDeviceTColorCamera.clone().inverse().
                multiply(ssTdeviceT0.inverse()).multiply(ssTdeviceT1).multiply(mDeviceTDepthCamera);

        return matrixToTangoPose(rgbt0Tdeptht1);
    }
}

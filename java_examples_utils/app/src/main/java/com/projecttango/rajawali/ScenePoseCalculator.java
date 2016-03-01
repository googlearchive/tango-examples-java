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

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.math.Matrix;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

/**
 * Convenient class for calculating transformations from the Tango world to the OpenGL world,
 * using Rajawali specific classes and conventions.
 */
public final class ScenePoseCalculator {
    private static final String TAG = ScenePoseCalculator.class.getSimpleName();

    /**
     * Transformation from the Tango Area Description or Start of Service coordinate frames
     * to the OpenGL coordinate frame.
     * NOTE: Rajawali uses column-major for matrices.
     */
    public static final Matrix4 OPENGL_T_TANGO_WORLD = new Matrix4(new double[]{
            1, 0,  0, 0,
            0, 0, -1, 0,
            0, 1,  0, 0,
            0, 0,  0, 1
    });

    /**
     *  Transformation from the Tango RGB camera coordinate frame to the OpenGL camera frame.
     */
    public static final Matrix4 COLOR_CAMERA_T_OPENGL_CAMERA = new Matrix4(new double[]{
            1,  0,  0, 0,
            0, -1,  0, 0,
            0,  0, -1, 0,
            0,  0,  0, 1
    });

    /**
     *  Transformation for device rotation on 270 degrees.
     */
    public static final Matrix4 ROTATION_270_T_DEFAULT = new Matrix4(new double[]{
             0, 1, 0, 0,
            -1, 0, 0, 0,
             0, 0, 0, 0,
             0, 0, 0, 1
    });

    /**
     *  Transformation for device rotation on 180 degrees.
     */
    public static final Matrix4 ROTATION_180_T_DEFAULT = new Matrix4(new double[]{
            -1,  0, 0, 0,
             0, -1, 0, 0,
             0,  0, 1, 0,
             0,  0, 0, 1
    });

    /**
     *  Transformation for device rotation on 90 degrees.
     */
    public static final Matrix4 ROTATION_90_T_DEFAULT = new Matrix4(new double[]{
            0, -1, 0, 0,
            1,  0, 0, 0,
            0,  0, 1, 0,
            0,  0, 0, 1
    });

    /**
     *  Transformation for device rotation on default orientation.
     */
    public static final Matrix4 ROTATION_0_T_DEFAULT = new Matrix4(new double[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    });

    public static final Matrix4 DEPTH_CAMERA_T_OPENGL_CAMERA = new Matrix4(new double[]{
            1,  0,  0, 0,
            0, -1,  0, 0,
            0,  0, -1, 0,
            0,  0,  0, 1
    });

    /**
     * Up vector in the Tango start of Service and Area Description frame.
     */
    public static final Vector3 TANGO_WORLD_UP = new Vector3(0, 0, 1);

    /**
     * Avoid instantiating the class since it will only be used statically.
     */
    private ScenePoseCalculator() {
    }

    /**
     * Converts from TangoPoseData to a Matrix4 for transformations.
     */
    public static Matrix4 tangoPoseToMatrix(TangoPoseData tangoPose) {
        Vector3 v = new Vector3(tangoPose.translation[0],
                tangoPose.translation[1], tangoPose.translation[2]);
        Quaternion q = new Quaternion(tangoPose.rotation[3], tangoPose.rotation[0],
                tangoPose.rotation[1], tangoPose.rotation[2]);
        // NOTE: Rajawali quaternions use a left-hand rotation around the axis convention.
        q.conjugate();
        Matrix4 m = new Matrix4();
        m.setAll(v, new Vector3(1, 1, 1), q);
        return m;
    }

    /**
     * Converts a transform in Matrix4 format to TangoPoseData.
     */
    public static TangoPoseData matrixToTangoPose(Matrix4 transform) {
        // Get translation and rotation components from the transformation matrix.
        Vector3 p = transform.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(transform);

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
        // Get translation and rotation components from the transformation matrix.
        Vector3 p = m.getTranslation();
        Quaternion q = new Quaternion();
        q.fromMatrix(m);

        // NOTE: Rajawali quaternions use a left-hand rotation around the axis convention.
        q.conjugate();

        return new Pose(p, q);
    }

    /**
     * Given a pose in start of service or area description frame calculate the corresponding
     * position and orientation for a 3D object in the Rajawali world.
     */
    public static Pose toOpenGLPose(TangoPoseData tangoPose) {
        Matrix4 startServiceTDevice = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame.
        Matrix4 openglWorldTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTDevice);

        return matrixToPose(openglWorldTDevice);
    }

    /**
     * Given a pose in start of service or area description frame and a screen rotaion calculate
     * the corresponding position and orientation for a 3D object in the Rajawali world.
     *
     * @param tangoPose     The input Tango Pose in start service or area description frame.
     * @param rotationIndex The screen rotation index, the index is following Android rotation enum.
     *                      see Android documentation for detail:
     *                      http://developer.android.com/reference/android/view/Surface.html#ROTATION_0 // NO_LINT
     */
    public static Pose toOpenGLPoseWithScreenRotation(TangoPoseData tangoPose, int rotationIndex) {
        Matrix4 startServiceTDevice = tangoPoseToMatrix(tangoPose);

        // Get device pose in OpenGL world frame.
        Matrix4 openglWorldTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTDevice);

        switch (rotationIndex) {
            case 0:
                openglWorldTDevice.multiply(ROTATION_0_T_DEFAULT);
                break;
            case 1:
                openglWorldTDevice.multiply(ROTATION_90_T_DEFAULT);
                break;
            case 2:
                openglWorldTDevice.multiply(ROTATION_180_T_DEFAULT);
                break;
            case 3:
                openglWorldTDevice.multiply(ROTATION_270_T_DEFAULT);
                break;
            default:
                openglWorldTDevice.multiply(ROTATION_0_T_DEFAULT);
                break;
        }

        return matrixToPose(openglWorldTDevice);
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    public static Matrix4 calculateProjectionMatrix(int width, int height, double fx, double fy,
                                                    double cx, double cy) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
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
     * Given the device pose in start of service frame, calculate the corresponding
     * position and orientation for a OpenGL Scene Camera in the Rajawali world.
     */
    public static Pose toOpenGlCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Matrix4 startServiceTdevice = tangoPoseToMatrix(devicePose);

        // Get device pose in OpenGL world frame.
        Matrix4 openglTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice);

        // Get OpenGL camera pose in OpenGL world frame.
        Matrix4 openglWorldTOpenglCamera =
                openglTDevice.multiply(extrinsics.getDeviceTColorCamera()).
                        multiply(COLOR_CAMERA_T_OPENGL_CAMERA);

        return matrixToPose(openglWorldTOpenglCamera);
    }

    /**
     * Given the device pose in start of service frame, calculate the position and orientation of
     * the depth sensor in OpenGL coordinate frame.
     */
    public static Pose toDepthCameraOpenGlPose(TangoPoseData devicePose,
                                               DeviceExtrinsics extrinsics) {
        Matrix4 startServiceTdevice = tangoPoseToMatrix(devicePose);

        // Get device pose in OpenGL world frame.
        Matrix4 openglTDevice = OPENGL_T_TANGO_WORLD.clone().multiply(startServiceTdevice);

        // Get OpenGL camera pose in OpenGL world frame.
        Matrix4 openglWorldTOpenglCamera =
                openglTDevice.multiply(extrinsics.getDeviceTDepthCamera());

        return matrixToPose(openglWorldTOpenglCamera);
    }

    /**
     * Given a point and a normal in depth camera frame and the device pose in start of service
     * frame at the time the point and normal were acquired, calculate a Pose object which
     * represents the position and orientation of the fitted plane with its Y vector pointing
     * up in the gravity vector, represented in the Tango start of service frame.
     *
     * @param point     Point in depth frame where the plane has been detected.
     * @param normal    Normal of the detected plane.
     * @param tangoPose Device pose with respect to start of service at the time the plane was
     *                  fitted.
     */
    public static TangoPoseData planeFitToTangoWorldPose(
            double[] point, double[] normal, TangoPoseData tangoPose, DeviceExtrinsics extrinsics) {
        Matrix4 startServiceTdevice = tangoPoseToMatrix(tangoPose);

        // Calculate the UP vector in the depth frame at the provided measurement pose.
        Vector3 depthUp = TANGO_WORLD_UP.clone();
        startServiceTdevice.clone().multiply(extrinsics.getDeviceTDepthCamera())
                .inverse().rotateVector(depthUp);

        // Calculate the transform in depth frame corresponding to the plane fitting information.
        Matrix4 depthTplane = matrixFromPointNormalUp(point, normal, depthUp);

        // Convert to OpenGL frame.
        Matrix4 tangoWorldTplane = startServiceTdevice.multiply(extrinsics.getDeviceTDepthCamera()).
                multiply(depthTplane);

        return matrixToTangoPose(tangoWorldTplane);
    }

    /**
     * Calculates a transformation matrix based on a point, a normal and the up gravity vector.
     * The coordinate frame of the target transformation will be Z forward, X left, Y up.
     */
    public static Matrix4 matrixFromPointNormalUp(double[] point, double[] normal, Vector3 up) {
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
     * Converts a point, represented as a Vector3 from it's initial refrence frame to 
     * the OpenGl world refrence frame. This allows various points to be depicted in
     * the OpenGl rendering.
     */
    public static Vector3 getPointInEngineFrame(
            Vector3 inPoint,
            TangoPoseData deviceTPointFramePose,
            TangoPoseData startServiceTDevicePose) {
        Matrix4 startServiceTDeviceMatrix = tangoPoseToMatrix(startServiceTDevicePose);
        Matrix4 deviceTPointFrameMatrix = tangoPoseToMatrix(deviceTPointFramePose);
        Matrix4 startServiceTDepthMatrix 
                = startServiceTDeviceMatrix.multiply(deviceTPointFrameMatrix);

        // Convert the depth point to a Matrix. 
        Matrix4 inPointMatrix = new Matrix4();
        inPointMatrix.setToTranslation(inPoint);

        // Transform Point from depth frame to start of service frame to OpenGl world frame.
        Matrix4 startServicePointMatrix = startServiceTDepthMatrix.multiply(inPointMatrix);
        Matrix4 openGlWorldPointMatrix 
                = OPENGL_T_TANGO_WORLD.clone().multiply(startServicePointMatrix);
        return matrixToPose(openGlWorldPointMatrix).getPosition();
    }
}

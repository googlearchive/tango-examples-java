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
package com.projecttango.experiments.floorplansample;

import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ar.TangoRajawaliRenderer;
import com.projecttango.tangosupport.TangoSupport;

/**
 * Very simple augmented reality example which displays cubes fixed in place for every
 * WallMeasurement and a continuous line for the perimeter of the floor plan.
 * Each time the user clicks on the screen, a cube is placed flush with the surface detected
 * using the point cloud data at the position clicked.
 * <p/>
 * This follows the same development model as any regular Rajawali application
 * with the following peculiarities:
 * - It extends {@code TangoRajawaliArRenderer}.
 * - It calls {@code super.initScene()} in the initialization.
 * - When an updated pose for the object is obtained after a user click, the object pose is updated
 * in the render loop
 * - The associated FloorplanActivity is taking care of updating the camera pose to match
 * the displayed RGB camera texture and produce the AR effect through a Scene Frame Callback
 * (@see FloorplanActivity)
 */
public class FloorplanRenderer extends TangoRajawaliRenderer {
    private static final float CUBE_SIDE_LENGTH = 0.3f;

    private List<Pose> mNewPoseList = new ArrayList<Pose>();
    private boolean mObjectPoseUpdated = false;
    private boolean mPlanUpdated = false;
    private Material mPlaneMaterial;
    private Object3D mPlanLine = null;
    private Stack<Vector3> mPlanPoints;
    private List<Object3D> mMeasurementObjectList = new ArrayList<Object3D>();

    public FloorplanRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Remember to call super.initScene() to allow TangoRajawaliArRenderer
        // to be set-up.
        super.initScene();

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        // Set-up a material.
        mPlaneMaterial = new Material();
        mPlaneMaterial.enableLighting(true);
        mPlaneMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mPlaneMaterial.setSpecularMethod(new SpecularMethod.Phong());
        mPlaneMaterial.setColor(0xff009900);
        mPlaneMaterial.setColorInfluence(0.5f);
        try {
            Texture t = new Texture("wall", R.drawable.wall);
            mPlaneMaterial.addTexture(t);
        } catch (ATexture.TextureException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        // Update the AR object if necessary
        // Synchronize against concurrent access with the setter below.
        synchronized (this) {
            if (mObjectPoseUpdated) {
                Iterator<Pose> poseIterator = mNewPoseList.iterator();
                Object3D object3D;
                while (poseIterator.hasNext()) {
                    Pose pose = poseIterator.next();
                    object3D = new Plane(CUBE_SIDE_LENGTH, CUBE_SIDE_LENGTH, 2, 2);
                    object3D.setMaterial(mPlaneMaterial);
                    // Rotate around X axis so the texture is applied correctly.
                    // NOTE: This may be a Rajawali bug.
                    // https://github.com/Rajawali/Rajawali/issues/1561
                    object3D.setDoubleSided(true);
                    object3D.rotate(Vector3.Axis.X, 180);
                    // Place the 3D object in the location of the detected plane.
                    object3D.setPosition(pose.getPosition());
                    object3D.rotate(pose.getOrientation());

                    getCurrentScene().addChild(object3D);
                    mMeasurementObjectList.add(object3D);
                    poseIterator.remove();
                }
                mObjectPoseUpdated = false;
            }

            if (mPlanUpdated) {
                if (mPlanLine != null) {
                    // Remove the old line.
                    getCurrentScene().removeChild(mPlanLine);
                }
                if (mPlanPoints.size() > 1) {
                    // Create a line with the points of the plan perimeter.
                    mPlanLine = new Line3D(mPlanPoints, 20, Color.RED);
                    Material m = new Material();
                    m.setColor(Color.RED);
                    mPlanLine.setMaterial(m);
                    getCurrentScene().addChild(mPlanLine);
                }
                mPlanUpdated = false;
            }
        }

        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time of the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose) {
        TangoPoseData cameraPose = TangoSupport.getPoseInEngineFrame(
                TangoSupport.TANGO_SUPPORT_COORDINATE_CONVENTION_OPENGL,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR, devicePose);
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        // Conjugation is needed because Rajawali uses left handed convention for rotations.
        getCurrentCamera().setRotation(
                new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]).conjugate());
        getCurrentCamera().setPosition(new Vector3(translation[0], translation[1], translation[2]));
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    /**
     * Add a new WallMeasurement.
     * A new cube will be added at the plane position and orientation to represent the measurement.
     */
    public synchronized void addWallMeasurement(WallMeasurement wallMeasurement) {
        TangoPoseData openGLPose = TangoSupport.getPoseInEngineFrame(
                TangoSupport.TANGO_SUPPORT_COORDINATE_CONVENTION_OPENGL,
                TangoPoseData.COORDINATE_FRAME_DEVICE,
                wallMeasurement.getPlanePose()
        );
        float[] rotation = openGLPose.getRotationAsFloats();
        float[] translation = openGLPose.getTranslationAsFloats();
        mNewPoseList.add(new Pose(new Vector3(translation[0], translation[1], translation[2]),
                new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2])));
        mObjectPoseUpdated = true;
    }

    /**
     * Update the perimeter line with the new floor plan.
     */
    public synchronized void updatePlan(Floorplan plan) {
        Stack<Vector3> points = new Stack<Vector3>();
        for (Vector3 vector3 : plan.getPlanPoints()) {
            TangoPoseData pose = new TangoPoseData();
            // Render z = 0.
            pose.translation = new double[]{vector3.x, vector3.y, 0};
            pose.baseFrame = TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION;
            // NOTE: We need to set the target frame to COORDINATE_FRAME_DEVICE because that is the
            // default target frame to place objects in the OpenGL world with
            // TangoSupport.getPoseInEngineFrame.
            pose.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
            TangoPoseData openGLPose = TangoSupport.getPoseInEngineFrame(
                    TangoSupport.TANGO_SUPPORT_COORDINATE_CONVENTION_OPENGL,
                    TangoPoseData.COORDINATE_FRAME_DEVICE, pose);
            float[] translation = openGLPose.getTranslationAsFloats();
            points.add(new Vector3(translation[0], translation[1], translation[2]));
        }
        mPlanPoints = points;
        mPlanUpdated = true;
    }

    /**
     * Remove all the measurements from the Scene.
     */
    public synchronized void removeMeasurements() {
        for (Object3D object3D : mMeasurementObjectList) {
            getCurrentScene().removeChild(object3D);
        }
    }
}

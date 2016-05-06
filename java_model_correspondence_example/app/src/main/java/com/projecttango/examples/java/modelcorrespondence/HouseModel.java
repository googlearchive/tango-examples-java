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

package com.projecttango.examples.java.modelcorrespondence;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Given data model.
 * The user is supposed to already have this.
 */
public class HouseModel {

    // Some points in model frame that the user is supposed to know to make the correspondence.
    private List<Vector3> mModelPoints;

    public HouseModel() {
        mModelPoints = new ArrayList<Vector3>();
        // Populate the points in model frame to make the correspondence. In this case they are
        // the four corners.
        mModelPoints.add(new Vector3(-9.52f, 19.46f, 0.0837f));
        mModelPoints.add(new Vector3(-9.52f, -27.44f, 0.0837f));
        mModelPoints.add(new Vector3(9.57f, -27.44f, 0.0837f));
        mModelPoints.add(new Vector3(9.57f, 19.46f, 0.0837f));
    }

    /**
     * Get the model points in OpenGl frame.
     */
    public List<Vector3> getOpenGlModelPpoints(Matrix4 openGlTHouse) {
        List<Vector3> worldPpoints = new ArrayList<Vector3>();
        for (Vector3 modelPoint : mModelPoints) {
            worldPpoints.add(modelPoint.clone().multiply(openGlTHouse));
        }
        return worldPpoints;
    }

    public int getNumberOfPoints() {
        return mModelPoints.size();
    }

}

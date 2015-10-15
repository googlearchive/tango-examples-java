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
package com.projecttango.rajawali.renderables;

import android.graphics.Color;

import org.rajawali3d.Object3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;

import java.util.Stack;

/**
 * Rajawali object representing XYZ axes in 3D space. X is Red,
 * Y is Green, and Z is Blue.
 */
public class Axes extends Object3D {
    private float mThickness = 3f;

    public Axes() {
        addAxis(new Vector3(1, 0, 0), Color.RED);
        addAxis(new Vector3(0, 1, 0), Color.GREEN);
        addAxis(new Vector3(0, 0, 1), Color.BLUE);
    }

    private void addAxis(Vector3 direction, int color) {
        Stack<Vector3> points = new Stack<Vector3>();
        points.add(new Vector3());
        points.add(direction);
        Line3D line = new Line3D(points, mThickness);
        Material material = new Material();
        material.setColor(color);
        line.setMaterial(material);
        addChild(line);
    }
}

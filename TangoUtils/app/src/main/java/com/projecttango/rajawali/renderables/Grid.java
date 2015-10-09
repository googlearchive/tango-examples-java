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
 * Rajawali primitive object which represents the 'floor' of the current scene.
 * This is a static grid placed in the scene to provide perspective in the
 * various views.
 */
public class Grid extends Object3D {
    public Grid(int size, int step) {
        Material m = new Material();
        m.setColor(Color.BLACK);

        Line3D line;
        Stack<Vector3> points;

        // Rows
        for (float i=-size/2f; i <= size/2f; i += step) {
            points = new Stack<Vector3>();
            points.add(new Vector3(i, 0, -size/2f));
            points.add(new Vector3(i, 0, size/2f));
            line = new Line3D(points, 1f);
            line.setMaterial(m);
            addChild(line);
        }

        // Columns
        for (float i=-size/2f; i <= size/2f; i += step) {
            points = new Stack<Vector3>();
            points.add(new Vector3(-size/2f, 0, i));
            points.add(new Vector3(size/2f, 0, i));
            line = new Line3D(points, 1f);
            line.setMaterial(m);
            addChild(line);
        }

        setMaterial(m);
    }
}

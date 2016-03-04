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

import org.rajawali3d.materials.Material;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;

import java.util.Arrays;
import java.util.Collections;
import java.util.Stack;

/**
 * A primitive which represents a combination of Frustum and Axes.
 */
public class FrustumAxes extends Line3D {
    private static final float FRUSTUM_WIDTH = 0.8f;
    private static final float FRUSTUM_HEIGHT = 0.6f;
    private static final float FRUSTUM_DEPTH = 0.5f;

    public FrustumAxes(float thickness) {
        super(makePoints(), thickness, makeColors());
        Material material = new Material();
        material.useVertexColors(true);
        setMaterial(material);
    }

    private static Stack<Vector3> makePoints() {
        Vector3 o = new Vector3(0, 0, 0);
        Vector3 a = new Vector3(-FRUSTUM_WIDTH / 2f, FRUSTUM_HEIGHT / 2f, -FRUSTUM_DEPTH);
        Vector3 b = new Vector3(FRUSTUM_WIDTH / 2f, FRUSTUM_HEIGHT / 2f, -FRUSTUM_DEPTH);
        Vector3 c = new Vector3(FRUSTUM_WIDTH / 2f, -FRUSTUM_HEIGHT / 2f, -FRUSTUM_DEPTH);
        Vector3 d = new Vector3(-FRUSTUM_WIDTH / 2f, -FRUSTUM_HEIGHT / 2f, -FRUSTUM_DEPTH);

        Vector3 x = new Vector3(1, 0, 0);
        Vector3 y = new Vector3(0, 1, 0);
        Vector3 z = new Vector3(0, 0, 1);

        Stack<Vector3> points = new Stack<Vector3>();
        Collections.addAll(points, o, x, o, y, o, z, o, a, b, o, b, c, o, c, d, o, d, a);

        return points;
    }

    private static int[] makeColors() {
        int[] colors = new int[18];
        Arrays.fill(colors, Color.BLACK);
        colors[0] = Color.RED;
        colors[1] = Color.RED;
        colors[2] = Color.GREEN;
        colors[3] = Color.GREEN;
        colors[4] = Color.BLUE;
        colors[5] = Color.BLUE;
        return colors;
    }
}

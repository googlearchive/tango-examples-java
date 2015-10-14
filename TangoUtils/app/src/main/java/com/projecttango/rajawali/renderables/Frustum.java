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

import java.util.Collections;
import java.util.Stack;

/**
 * Frustum primitive created from Lines.
 */
public class Frustum extends Object3D {
    private float mDepth;
    private float mHeight;
    private float mWidth;
    private float mThickness = 3f;

    public Frustum(float width, float mHeight, float mDepth) {
        this.mDepth = mDepth;
        this.mWidth = width;
        this.mHeight = mHeight;

        Material black = new Material();
        black.setColor(Color.BLACK);

        Vector3 o = new Vector3(0, 0, 0);
        Vector3 a = new Vector3(-width/2f, mHeight /2f, -mDepth);
        Vector3 b = new Vector3(width/2f, mHeight /2f, -mDepth);
        Vector3 c = new Vector3(width/2f, -mHeight /2f, -mDepth);
        Vector3 d = new Vector3(-width/2f, -mHeight /2f, -mDepth);

        Line3D line;
        Stack<Vector3> points;

        points = new Stack<Vector3>();
        Collections.addAll(points, new Vector3[]{ o, b, c, o, a, d, o });
        line = new Line3D(points, mThickness);
        line.setMaterial(black);
        addChild(line);

        points = new Stack<Vector3>();
        Collections.addAll(points, new Vector3[]{ a, b });
        line = new Line3D(points, mThickness);
        line.setMaterial(black);
        addChild(line);

        points = new Stack<Vector3>();
        Collections.addAll(points, new Vector3[]{ c, d });
        line = new Line3D(points, mThickness);
        line.setMaterial(black);
        addChild(line);

        setMaterial(black);
    }
}

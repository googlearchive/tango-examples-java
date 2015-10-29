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

import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;

/**
 * Convenience class to encapsulate a position and orientation combination using Rajawali classes.
 */
public class Pose {
    private final Quaternion mOrientation;
    private final Vector3 mPosition;

    public Pose(Vector3 position, Quaternion orientation) {
        this.mOrientation = orientation;
        this.mPosition = position;
    }

    public Quaternion getOrientation() {
        return mOrientation;
    }

    public Vector3 getPosition() {
        return mPosition;
    }

    public String toString() {
        return "p:" + mPosition + ",q:" + mOrientation;
    }
}

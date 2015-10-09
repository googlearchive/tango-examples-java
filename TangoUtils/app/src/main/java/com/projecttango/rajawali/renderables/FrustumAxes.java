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

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;

/**
 *A primitive which represents a combination of Frustum and Axes.
 */
public class FrustumAxes extends Object3D {
    private Frustum mFrustum;
    private Axes mAxes;
    public FrustumAxes() {
        mFrustum = new Frustum(0.8f, 0.6f, 0.5f);
        addChild(mFrustum);
        mAxes = new Axes();
        addChild(mAxes);
    }
}

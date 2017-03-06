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
package com.projecttango.examples.java.occlusion.meshing;

import java.util.Arrays;

/**
 * Represents a 3D index into a fixed-resolution grid.
 */
public class GridIndex {

    private static final int LARGE_PRIME_X = 1129;
    private static final int LARGE_PRIME_Y = 2141;

    private int[] mIndex;

    public GridIndex(int[] index) {
        mIndex = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o){
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GridIndex gridIndex = (GridIndex) o;
        return Arrays.equals(mIndex, gridIndex.mIndex);
    }

    /**
     * Generates a hash from the grid index.
     * Arbitrarily chosen large prime integers LARGE_PRIME_X and LARGE_PRIME_Y are
     * chosen to hash the (x, y, z) coordinates of an index.
     */
    @Override
    public int hashCode() {
        return (mIndex[0] * LARGE_PRIME_X + mIndex[1]) * LARGE_PRIME_Y + mIndex[2];
    }
}

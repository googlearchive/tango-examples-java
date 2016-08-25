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
package com.projecttango.tangoutils;

import com.google.atap.tangoservice.TangoPoseData;

import java.text.DecimalFormat;

/**
 * This is a utility class to format the Pose data in a way want to display the statistics in the
 * sample applications.
 */
public class TangoPoseUtilities {
    /**
     * Get translation string from a pose.
     *
     * @param pose          Pose from which translation string is constructed.
     * @param decimalFormat Number of decimals for each component of translation.
     * @return
     */
    public static String getTranslationString(TangoPoseData pose, DecimalFormat decimalFormat) {
        String translationString = "["
                + decimalFormat.format(pose.translation[0]) + ", "
                + decimalFormat.format(pose.translation[1]) + ", "
                + decimalFormat.format(pose.translation[2]) + "] ";
        return translationString;
    }

    /**
     * Get quaternion string from a pose.
     *
     * @param pose          Pose from which quaternion string is constructed.
     * @param decimalFormat Number of decimals for each component of translation.
     * @return
     */
    public static String getQuaternionString(TangoPoseData pose, DecimalFormat decimalFormat) {
        String quaternionString = "["
                + decimalFormat.format(pose.rotation[0]) + ", "
                + decimalFormat.format(pose.rotation[1]) + ", "
                + decimalFormat.format(pose.rotation[2]) + ", "
                + decimalFormat.format(pose.rotation[3]) + "] ";
        return quaternionString;
    }

    /**
     * Get the status of the Pose as a string.
     *
     * @param pose Pose from which status string is constructed.
     * @return
     */
    public static String getStatusString(TangoPoseData pose) {
        String poseStatus;
        switch (pose.statusCode) {
            case TangoPoseData.POSE_UNKNOWN:
                poseStatus = "unknown";
                break;
            case TangoPoseData.POSE_INVALID:
                poseStatus = "invalid";
                break;
            case TangoPoseData.POSE_INITIALIZING:
                poseStatus = "initializing";
                break;
            case TangoPoseData.POSE_VALID:
                poseStatus = "valid";
                break;
            default:
                poseStatus = "unknown";
        }
        return poseStatus;
    }
}

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

package com.projecttango.examples.java.floorplan;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain class that represents a floor plan.
 * It is represented as a set of points that are joined consecutively by straight lines.
 */
public class Floorplan {

    /**
     * Padding factor only used for the representation in 2D in a canvas.
     */
    private static final float RENDER_PADDING_SCALE_FACTOR = 0.8f;

    /**
     * The list of points of the plan. Each point is a float[3] with x, y and z coordinates.
     */
    private List<float[]> mPlanPoints = new ArrayList<float[]>();

    public Floorplan(List<float[]> planPoints) {
        mPlanPoints.addAll(planPoints);
    }

    /**
     * Draw a 2D representation of the plan with text labels for the length of each wall.
     *
     * @param canvas The Canvas to draw the plan on.
     * @param paint  The Paint object describing the colors and styles for the plan.
     */
    public void drawOnCanvas(Canvas canvas, Paint paint) {
        draw2dlines(canvas, paint);
        drawTexts(canvas, paint);
    }

    /**
     * Draws a 2D representation of the floor plan on a canvas. The plan is scaled to fill
     * the whole canvas. The {@code RENDER_PADDING_SCALE_FACTOR} is used to leave some blank
     * space around the borders of the canvas.
     */
    private void draw2dlines(Canvas canvas, Paint paint) {
        // Get center of the canvas.
        int centerX = canvas.getWidth() / 2;
        int centerY = canvas.getHeight() / 2;
        float[] lines = new float[4 * mPlanPoints.size()];
        int i = 0;
        if (!mPlanPoints.isEmpty()) {
            float[] lastPoint = null;
            Floorplan scaledFloorplan = translateAndScalePlan(canvas);
            // For every point add a line to the last point. Start from the center of the canvas.
            for (float[] nextPoint : scaledFloorplan.mPlanPoints) {
                if (lastPoint != null) {
                    lines[i++] = centerX + lastPoint[0];
                    lines[i++] = centerY + lastPoint[2];
                    lines[i++] = centerX + nextPoint[0];
                    lines[i++] = centerY + nextPoint[2];
                }
                lastPoint = nextPoint;
            }
            lines[i++] = centerX + lastPoint[0];
            lines[i++] = centerY + lastPoint[2];
            lines[i++] = centerX + scaledFloorplan.mPlanPoints.get(0)[0];
            lines[i++] = centerY + scaledFloorplan.mPlanPoints.get(0)[2];
        }
        canvas.drawLines(lines, paint);
    }

    /**
     * Draw text labels for the length of each wall.
     */
    private void drawTexts(Canvas canvas, Paint paint) {
        int centerX = canvas.getWidth() / 2;
        int centerY = canvas.getHeight() / 2;
        if (!mPlanPoints.isEmpty()) {
            float[] lastPoint = null;
            float[] nextPoint;
            float[] lastScaledPoint = null;
            float[] nextScaledPoint;
            Floorplan scaledFloorplan = translateAndScalePlan(canvas);
            for (int i = 0; i < mPlanPoints.size(); i++) {
                nextPoint = mPlanPoints.get(i);
                nextScaledPoint = scaledFloorplan.mPlanPoints.get(i);
                if (lastPoint != null) {
                    // Get the length of the original unscaled plan.
                    double length = Math.sqrt(
                            (lastPoint[0] - nextPoint[0]) * (lastPoint[0] - nextPoint[0]) +
                                    (lastPoint[2] - nextPoint[2]) * (lastPoint[2] - nextPoint[2]));
                    // Draw the label in the middle of each wall.
                    double posX = centerX + (lastScaledPoint[0] + nextScaledPoint[0]) / 2;
                    double posY = centerY + (lastScaledPoint[2] + nextScaledPoint[2]) / 2;
                    canvas.drawText(String.format("%.2f", length) + "m", (float) posX, (float)
                            posY, paint);
                }
                lastPoint = nextPoint;
                lastScaledPoint = nextScaledPoint;
            }
            // Get the length of the original unscaled plan.
            double length = Math.sqrt(
                    (lastPoint[0] - mPlanPoints.get(0)[0]) * (lastPoint[0] - mPlanPoints.get(0)[0])
                            + (lastPoint[2] - mPlanPoints.get(0)[2]) *
                            (lastPoint[2] - mPlanPoints.get(0)[2]));
            // Draw the label in the middle of each wall.
            double posX = centerX + (lastScaledPoint[0] + scaledFloorplan.mPlanPoints.get(0)[0])
                    / 2;
            double posY = centerY + (lastScaledPoint[2] + scaledFloorplan.mPlanPoints.get(0)[2])
                    / 2;
            canvas.drawText(String.format("%.2f", length) + "m", (float) posX, (float) posY, paint);
        }
    }

    /**
     * Translate the whole plan to be centered at the origin and scale it to fill the canvas.
     */
    private Floorplan translateAndScalePlan(Canvas canvas) {
        // Get center of the plan.
        float[] planCenter = getPlanCenter();
        // Get scale factor of the plan.
        float scale = getPlanScale(canvas.getHeight(), canvas.getWidth());
        List<float[]> scaledPoints = new ArrayList<float[]>();
        for (float[] nextPoint : mPlanPoints) {
            float[] newPoint = new float[3];
            for (int i = 0; i < 3; i++) {
                newPoint[i] = (nextPoint[i] - planCenter[i]) * scale;
            }
            scaledPoints.add(newPoint);
        }

        return new Floorplan(scaledPoints);
    }

    /**
     * Get the center point of the plan.
     */
    private float[] getPlanCenter() {
        float[] bounds = getPlanBounds();
        return new float[]{(bounds[0] + bounds[1]) / 2, (bounds[2] + bounds[3]) / 2,
                (bounds[4] + bounds[5]) / 2};
    }

    /**
     * Get the scale of the plan.
     * The scale is the factor the plan should be multiplied by to fill the whole canvas.
     */
    private float getPlanScale(int height, int width) {
        float[] bounds = getPlanBounds();
        float xScale = RENDER_PADDING_SCALE_FACTOR * width / (bounds[1] - bounds[0]);
        float zScale = RENDER_PADDING_SCALE_FACTOR * height / (bounds[5] - bounds[4]);
        return xScale < zScale ? xScale : zScale;
    }

    /**
     * Get the bounds of the plan.
     */
    private float[] getPlanBounds() {
        float xStart = Float.MAX_VALUE;
        float yStart = Float.MAX_VALUE;
        float zStart = Float.MAX_VALUE;
        float xEnd = Float.MIN_VALUE;
        float yEnd = Float.MIN_VALUE;
        float zEnd = Float.MIN_VALUE;
        for (float[] point : mPlanPoints) {
            if (point[0] < xStart) {
                xStart = point[0];
            }
            if (point[0] > xEnd) {
                xEnd = point[0];
            }
            if (point[1] < yStart) {
                yStart = point[1];
            }
            if (point[1] > yEnd) {
                yEnd = point[1];
            }
            if (point[2] < zStart) {
                zStart = point[2];
            }
            if (point[2] > zEnd) {
                zEnd = point[2];
            }
        }
        return new float[]{xStart, xEnd, yStart, yEnd, zStart, zEnd};
    }

    public List<float[]> getPlanPoints() {
        return new ArrayList<float[]>(mPlanPoints);
    }
}

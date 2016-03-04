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

package com.projecttango.experiments.floorplansample;

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
     * The list of points of the plan.
     */
    private List<Vector3> mPlanPoints = new ArrayList<Vector3>();

    public Floorplan(List<Vector3> planPoints) {
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
            Vector3 lastPoint = null;
            Floorplan scaledFloorplan = translateAndScalePlan(canvas);
            // For every point add a line to the last point. Start from the center of the canvas.
            for (Vector3 nextPoint : scaledFloorplan.mPlanPoints) {
                if (lastPoint != null) {
                    lines[i++] = (float) (centerX + lastPoint.x);
                    lines[i++] = (float) (centerY - lastPoint.y);
                    lines[i++] = (float) (centerX + nextPoint.x);
                    lines[i++] = (float) (centerY - nextPoint.y);
                }
                lastPoint = nextPoint;
            }
            lines[i++] = (float) (centerX + lastPoint.x);
            lines[i++] = (float) (centerY - lastPoint.y);
            lines[i++] = (float) (centerX + scaledFloorplan.mPlanPoints.get(0).x);
            lines[i++] = (float) (centerY - scaledFloorplan.mPlanPoints.get(0).y);
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
            Vector3 lastPoint = null;
            Vector3 nextPoint;
            Vector3 lastScaledPoint = null;
            Vector3 nextScaledPoint;
            Floorplan scaledFloorplan = translateAndScalePlan(canvas);
            for (int i = 0; i < mPlanPoints.size(); i++) {
                nextPoint = mPlanPoints.get(i);
                nextScaledPoint = scaledFloorplan.mPlanPoints.get(i);
                if (lastPoint != null) {
                    // Get the length of the original unscaled plan.
                    double length = Math.sqrt(
                            (lastPoint.x - nextPoint.x) * (lastPoint.x - nextPoint.x) +
                                    (lastPoint.y - nextPoint.y) * (lastPoint.y - nextPoint.y));
                    // Draw the label in the middle of each wall.
                    double posX = centerX + (lastScaledPoint.x + nextScaledPoint.x) / 2;
                    double posY = centerY - (lastScaledPoint.y + nextScaledPoint.y) / 2;
                    canvas.drawText(String.format("%.2f", length) + "m", (float) posX, (float)
                            posY, paint);
                }
                lastPoint = nextPoint;
                lastScaledPoint = nextScaledPoint;
            }
            // Get the length of the original unscaled plan.
            double length = Math.sqrt(
                    (lastPoint.x - mPlanPoints.get(0).x) * (lastPoint.x - mPlanPoints.get(0).x) +
                            (lastPoint.y - mPlanPoints.get(0).y) * (lastPoint.y - mPlanPoints.get
                                    (0).y));
            // Draw the label in the middle of each wall.
            double posX = centerX + (lastScaledPoint.x + scaledFloorplan.mPlanPoints.get(0).x) / 2;
            double posY = centerY - (lastScaledPoint.y + scaledFloorplan.mPlanPoints.get(0).y) / 2;
            canvas.drawText(String.format("%.2f", length) + "m", (float) posX, (float) posY, paint);
        }
    }

    /**
     * Translate the whole plan to be centered at the origin and scale it to fill the canvas.
     */
    private Floorplan translateAndScalePlan(Canvas canvas) {
        // Get center of the plan.
        Vector3 planCenter = getPlanCenter();
        // Get scale factor of the plan.
        double scale = getPlanScale(canvas.getHeight(), canvas.getWidth());
        List<Vector3> scaledPoints = new ArrayList<Vector3>();
        for (Vector3 nextPoint : mPlanPoints) {
            Vector3 newPoint = new Vector3(nextPoint);
            newPoint.subtract(planCenter);
            newPoint.multiply(scale);
            scaledPoints.add(newPoint);
        }

        return new Floorplan(scaledPoints);
    }

    /**
     * Get the center point of the plan.
     */
    private Vector3 getPlanCenter() {
        double bounds[] = getPlanBounds();
        return new Vector3((bounds[0] + bounds[1]) / 2, (bounds[2] + bounds[3]) / 2,
                (bounds[4] + bounds[5]) / 2);
    }

    /**
     * Get the scale of the plan.
     * The scale is the factor the plan should be multiplied by to fill the whole canvas.
     */
    private double getPlanScale(int height, int width) {
        double bounds[] = getPlanBounds();
        double xScale = RENDER_PADDING_SCALE_FACTOR * width / (bounds[1] - bounds[0]);
        double yScale = RENDER_PADDING_SCALE_FACTOR * height / (bounds[3] - bounds[2]);
        return xScale < yScale ? xScale : yScale;
    }

    /**
     * Get the bounds of the plan.
     */
    private double[] getPlanBounds() {
        double xStart = Float.MAX_VALUE;
        double yStart = Float.MAX_VALUE;
        double zStart = Float.MAX_VALUE;
        double xEnd = Float.MIN_VALUE;
        double yEnd = Float.MIN_VALUE;
        double zEnd = Float.MIN_VALUE;
        for (Vector3 point : mPlanPoints) {
            if (point.x < xStart) {
                xStart = point.x;
            }
            if (point.x > xEnd) {
                xEnd = point.x;
            }
            if (point.y < yStart) {
                yStart = point.y;
            }
            if (point.y > yEnd) {
                yEnd = point.y;
            }
            if (point.z < zStart) {
                zStart = point.z;
            }
            if (point.z > zEnd) {
                zEnd = point.z;
            }
        }
        return new double[]{xStart, xEnd, yStart, yEnd, zStart, zEnd};
    }

    public List<Vector3> getPlanPoints() {
        return new ArrayList<Vector3>(mPlanPoints);
    }
}

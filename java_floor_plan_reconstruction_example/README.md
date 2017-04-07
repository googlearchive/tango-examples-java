This sample shows how to use the experimental Floor plan API from the 3D
Reconstruction packages.

This sample is a small Java application that creates simple 2D floor
plans. This application uses the depth sensor to scan the rooms
and gets simplified 2D floor plans in the form of polygons through
callbacks. These polygons are drawn on a SurfaceView using regular
Android canvas 2D drawing functions.

The most important code parts to note are the following:
 - TangoFloorplanner.java is a convenience class that abstracts some of
   the mechanics of the Floor plan reconstruction library. It uses
   a separate thread to push point clouds through the reconstruction
   library and generates callback events whenever new floor plan polygons
   are available for consumption. You can reuse this class for your own
   application.
 - In FloorPlanReconstructionActivity.java, you can see how to tie this
   TangoFloorplanner utility with the rest of the Tango lifecycle,
   callback functions and events.

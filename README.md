Project Tango Java API Example Projects
===========================================
Copyright 2014 Google Inc.

Useful Websites
---------------
SDK Download - https://developers.google.com/project-tango/downloads

Developer Website -
https://developers.google.com/project-tango/apis/java

Contents
--------

This contains the Project Tango Java API examples.

These examples use the Gradle build system and were developed using
Android Studio 2.1.

#### Basic Examples

The **java_basic_examples** project includes basic examples showing how
to compile and run an application using different core Tango APIs in
Java:

 * **hello_motion_tracking** - Use the Motion Tracking API
   to track the position of the Tango device in 3D space.
 * **hello_area_description** - Use the Area Description
   API to create and manage Area Description Files.
 * **hello_depth_perception** - Use the depth sensor.
 * **hello_video** - Show how to render the RGB camera image using
   OpenGL.

#### Use Case Examples

Other examples in this repository show how to build an application for
different use cases of Tango technology.

Most of these examples delegate the details of OpenGL rendering to the
[Rajawali](https://github.com/Rajawali/Rajawali) engine, so that the
example code can focus on the Tango-specific aspects of the application.

 * **java_augmented_reality_example** - Achieve an augmented reality effect
   by rendering 3D objects overlaid on the camera image such that they appear
   to stay affixed in space.
 * **java_floor_plan_example** - Create a floor plan by
   using the depth sensor to detect and mesure walls in a room.
 * **java_model_correspondence_example** - Create a mapping
   between a virtual 3D object and selected points in the real world.
 * **java_motion_tracking_example** - Use Tango motion
   tracking to navigate in a virtual 3D world.
 * **java_opengl_augmented_reality_example** - Achieve an augmented reality effect
   without using any third party 3D rendering library.
 * **java_plane_fitting_example** - Build an AR application
   to detect planes in the real world to place objects in them.
 * **java_point_cloud_example** - Acquire and render a cloud
   of 3D points using the depth sensor.
 * **java_point_to_point_example** - Build a simple point to
   point measurement application using augmented reality and the depth
   sensor.

The **java_examples_utils** project contains some common utility code that
is used for many examples.

Support
-------
As a first step, view our [FAQ](http://stackoverflow.com/questions/tagged/google-project-tango?sort=faq&amp;pagesize=50)
page. You can find solutions to most issues there.

If you have general API questions related to Tango, we encourage you to
post your question to our [stack overflow
page](http://stackoverflow.com/questions/tagged/google-project-tango).

To learn more about general concepts and other information about the
project, visit [Project Tango Developer website](https://developers.google.com/project-tango/).

Contribution
------------
Want to contribute? Great! First, read this page (including the small
print at the end).

#### Before you contribute
Before we can use your code, you must sign the
[Google Individual Contributor License
Agreement](https://developers.google.com/open-source/cla/individual?csw=1)
(CLA), which you can do online. The CLA is necessary mainly because you
own the
copyright to your changes, even after your contribution becomes part of
our
codebase, so we need your permission to use and distribute your code. We
also
need to be sure of various other things—for instance, that you'll tell us
if you
know that your code infringes on other people's patents. You don't have
to sign
the CLA until after you've submitted your code for review and a member
has
approved it, but you must do it before we can put your code into our
codebase.
Before you start working on a larger contribution, you should get in
touch with
us first through the issue tracker with your idea so that we can help
out and
possibly guide you. Coordinating up front makes it much easier to avoid
frustration later on.

#### Code reviews
All submissions, including submissions by project members, require
review. We
use Github pull requests for this purpose.

#### The small print
Contributions made by corporations are covered by a different agreement
than
the one above: the Software Grant and Corporate Contributor License
Agreement.

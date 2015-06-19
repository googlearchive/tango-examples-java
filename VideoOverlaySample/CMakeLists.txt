cmake_minimum_required(VERSION 2.8)
project(video_overlay_java)

find_package(redwood_catkin REQUIRED)
redwood_build_only_if(ANDROID)

catkin_simple()

# This macro will also generate a depends.inc file with useful source and
# dependency information to pass on to ndk-build.
build_gradle_apk(JavaVideoOverlaySample
          TRACK # Files to track, trigger rebuilds when changed.
            ${SRC_FILES}
            app/src/main/AndroidManifest.xml
          JNI_DIRECTORY app/src/main/jni
)

cmake_minimum_required(VERSION 2.8)
project(video_overlay_java)

find_package(redwood_catkin REQUIRED)
redwood_build_only_if(ANDROID)

catkin_simple()

file(GLOB_RECURSE SRC_FILES "${CMAKE_CURRENT_SOURCE_DIR}/app/src/main/java" *.java)
file(GLOB_RECURSE RES_FILES "${CMAKE_CURRENT_SOURCE_DIR}/app/src/main/res" *.xml *.png)

build_gradle_apk(JavaVideoOverlaySample
          TRACK # Files to track, trigger rebuilds when changed.
            ${SRC_FILES}
            ${RES_FILES}
            app/src/main/AndroidManifest.xml
)

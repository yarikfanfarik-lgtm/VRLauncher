# VRLauncher

Android Cardboard VR launcher MVP with Air hand tracking, stereo scene, floating windows, 8x8 reveal animation and VR keyboard scaffold.

## Build

GitHub Actions workflow builds a debug APK and uploads it as `VRLauncher-debug`.

The workflow downloads the MediaPipe Hand Landmarker model automatically.

## Status

This is an MVP foundation. True third-party Android app window compositing requires platform/vendor virtual-display or XR compositor APIs; ordinary Android apps cannot arbitrarily turn every other app's window into a 3D texture.

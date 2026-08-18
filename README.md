# VRLauncher

Android Cardboard VR launcher with a glass/neon spatial desktop, Air hand tracking foundation, stereo scene, floating windows and a compact VR keyboard.

## Build

GitHub Actions builds a debug APK and uploads it as `VRLauncher-debug`.

The workflow downloads the MediaPipe Hand Landmarker model automatically.

## Status

The current build is an MVP foundation. True arbitrary third-party Android app window compositing requires platform/vendor virtual-display or XR compositor APIs; ordinary Android apps cannot arbitrarily turn every other app's window into a 3D texture.

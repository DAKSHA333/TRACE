# TRACE

TRACE is a native Android MVP for the iQOO Hackathon Productivity track. It creates timestamped memories of a physical workspace so a user can scan a desk, confirm objects, ask where an item was last observed, compare scans, and resume a project context.

## Current MVP

- Kotlin, Jetpack Compose, Material 3
- CameraX preview and image capture
- Room database for local projects, scans, known objects, observations, and relationships
- Local-only storage under app files
- Deterministic demo object detector behind a `WorkspaceObjectDetector` interface
- Manual object confirmation, rename, remove, and add flow
- Local query parser for last-seen, time-aware, list-workspace, compare-scans, and resume-workspace intents
- Unit tests for query parsing and workspace reasoning

## Build

Use Android Studio or Gradle with JDK 17. On this machine, the verified command was:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\DAKSHA MALI PATIL\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' test assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Demo Script

1. Open TRACE and complete privacy onboarding.
2. Tap Workspace scanner.
3. Capture a real desk photo or use the demo scan button.
4. Confirm and rename detected objects.
5. Save the scan.
6. Repeat with a changed arrangement.
7. Ask: "Where is my blue notebook?"
8. Ask: "What changed since my previous scan?"
9. Tap Resume workspace.

## Next Integration Step

Replace `DemoObjectDetector` with a MediaPipe implementation that loads a bundled object-detection model and returns labels, confidence scores, and normalized bounding boxes through the existing `WorkspaceObjectDetector` interface.

# TRACE Project Specification

## Product Goal

TRACE remembers a user's physical workspace using the phone camera, local AI, and timestamped structured memory. The MVP helps users recover where objects were last observed, what changed between scans, and how to resume a project workspace.

## MVP Scope

- One default project, "IoT Prototype", plus project creation support in the data layer.
- Manual workspace scan with CameraX.
- Detection confirmation with rename, remove, and manual add.
- Local Room storage for scans and object observations.
- Approximate zones: left, center, right, front, middle, back.
- Simple relationships: left of, right of, above, below.
- Five grounded query intents:
  - `LAST_SEEN`
  - `SEEN_AT_TIME`
  - `LIST_WORKSPACE`
  - `COMPARE_SCANS`
  - `RESUME_PROJECT`

## Non-Goals

- No continuous background camera access.
- No paid APIs.
- No cloud sync.
- No claim that TRACE knows current object location; it only knows saved observations.
- No full-house object tracking in the hackathon MVP.

## Privacy Rules

- Store images and structured memories locally.
- Make deletion available from Settings.
- Show evidence images for answers where possible.
- Keep camera active only during the scanner screen.

## Architecture

- UI: Compose screens in `MainActivity`.
- State: `TraceViewModel`.
- Data: `TraceRepository`, Room entities, DAO, database.
- Vision: `WorkspaceObjectDetector` interface with `DemoObjectDetector` placeholder.
- Reasoning: `WorkspaceReasoner`, `QueryParser`, `AnswerFormatter`.

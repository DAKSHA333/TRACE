# Person 2 AI Guide

## Goal

Person 2 proves that TRACE can automatically understand a workspace image without making the user type every object manually.

The implemented flow is:

```text
Captured desk image
  -> MediaPipe Object Detector
  -> object labels + confidence + bounding boxes
  -> TRACE location engine
  -> local Room memory
  -> simple query parser
  -> grounded answer + previous image
```

## What Is Implemented

- Free on-device model: `efficientdet_lite0.tflite`
- Model location: `app/src/main/assets/efficientdet_lite0.tflite`
- Detector: `MediaPipeWorkspaceObjectDetector`
- Interface: `WorkspaceObjectDetector`
- Fallback: `DemoObjectDetector` if the model/device fails
- Automatic object suggestions appear on the confirmation screen
- Approximate locations use normalized bounding boxes:
  - left / center / right
  - back / middle / front
- Basic color extraction is added from the detected object crop
- Basic object relationships are stored and used in answers
- Direct search works with short queries like `bottle`, `laptop`, `notebook`

## Current Limits

- EfficientDet Lite is trained on common COCO-style objects, so it can detect things like laptop, bottle, cup, book/notebook, phone, keyboard, mouse, and similar common objects.
- It will not reliably detect every hackathon object such as ESP32, jumper wires, USB-C hub, custom sensor boards, or sticky notes.
- The confirmation screen should stay in the product because users can rename or correct personal objects.

## Best Demo Setup

Use objects the free model is likely to recognize:

- laptop
- bottle
- notebook/book
- phone
- cup
- keyboard
- mouse

Recommended demo:

1. Put laptop in center.
2. Put bottle on right.
3. Put notebook on left.
4. Put phone or cup near laptop.
5. Scan workspace.
6. Keep/rename detected objects.
7. Tap Remember.
8. Search `bottle`.
9. Show the answer with location and previous workspace image.

## Next Improvements

- Add OCR with ML Kit to read labels on notebooks, sticky notes, and boxes.
- Add object crop storage so every remembered item has its own image.
- Add MediaPipe Image Embedder to match the same personal object across scans.
- Train a custom TFLite detector later for hackathon-specific objects like ESP32, sensor board, charger, and USB-C hub.

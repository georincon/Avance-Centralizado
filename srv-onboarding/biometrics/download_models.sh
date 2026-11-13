#!/usr/bin/env bash
# Descarga los modelos ONNX (licencia Apache 2.0, OpenCV Zoo): detector YuNet y reconocedor SFace.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p models
base=https://github.com/opencv/opencv_zoo/raw/main/models
curl -fL -o models/face_detection_yunet_2023mar.onnx  $base/face_detection_yunet/face_detection_yunet_2023mar.onnx
curl -fL -o models/face_recognition_sface_2021dec.onnx $base/face_recognition_sface/face_recognition_sface_2021dec.onnx

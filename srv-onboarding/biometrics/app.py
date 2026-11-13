"""Microservicio de vision self-hosted: comparacion facial 1:1, calidad de selfie y OCR del reverso de la cedula.

Contrato (el que consume HttpBiometricProvider del backend):
  POST /api/verify/face-match  multipart: reference (foto de la cedula), selfie
  POST /api/verify/liveness    multipart: selfie
  -> {"status": "success"|"failure", "score": float, "details": str}
  POST /api/ocr/id-back        multipart: image (reverso de la cedula)
  -> campos impresos: lugar de nacimiento, estatura, RH, expedicion, codigo impreso

IMPORTANTE: /liveness verifica CALIDAD de captura (un solo rostro, tamano, nitidez,
iluminacion). NO es deteccion anti-suplantacion (foto de pantalla/impresa); eso
requiere un modelo de presentation-attack-detection (p. ej. MiniFASNet) y queda
como mejora pendiente.
"""
import os
import re

import cv2
import numpy as np
from fastapi import FastAPI, File, UploadFile

MODELS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
DETECT_MIN_SCORE = 0.6
COSINE_MATCH = 0.363  # umbral publicado para SFace

app = FastAPI(title="onboarding-biometrics")
_detector = cv2.FaceDetectorYN.create(
    os.path.join(MODELS, "face_detection_yunet_2023mar.onnx"), "", (320, 320), DETECT_MIN_SCORE, 0.3, 5000
)
_recognizer = cv2.FaceRecognizerSF.create(os.path.join(MODELS, "face_recognition_sface_2021dec.onnx"), "")


def _decode(data: bytes):
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)  # respeta la orientacion EXIF
    if img is None:
        return None
    h, w = img.shape[:2]
    if max(h, w) > 1600:
        s = 1600 / max(h, w)
        img = cv2.resize(img, (int(w * s), int(h * s)))
    return img


def _faces(img):
    h, w = img.shape[:2]
    _detector.setInputSize((w, h))
    _, found = _detector.detect(img)
    return [] if found is None else sorted(found, key=lambda f: f[2] * f[3], reverse=True)


def _fail(details: str, score: float = 0.0):
    return {"status": "failure", "score": score, "details": details}


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/api/verify/face-match")
async def face_match(reference: UploadFile = File(...), selfie: UploadFile = File(...)):
    ref, sel = _decode(await reference.read()), _decode(await selfie.read())
    if ref is None or sel is None:
        return _fail("imagen invalida")
    ref_faces, sel_faces = _faces(ref), _faces(sel)
    if not ref_faces:
        return _fail("no se detecto rostro en la foto del documento")
    if not sel_faces:
        return _fail("no se detecto rostro en la selfie")
    a = _recognizer.feature(_recognizer.alignCrop(ref, ref_faces[0]))
    b = _recognizer.feature(_recognizer.alignCrop(sel, sel_faces[0]))
    cos = float(_recognizer.match(a, b, cv2.FaceRecognizerSF_FR_COSINE))
    return {
        "status": "success" if cos >= COSINE_MATCH else "failure",
        "score": round(cos, 4),
        "details": f"SFace coseno={cos:.3f} umbral={COSINE_MATCH}",
    }


@app.post("/api/verify/liveness")
async def liveness(selfie: UploadFile = File(...)):
    img = _decode(await selfie.read())
    if img is None:
        return _fail("imagen invalida")
    faces = _faces(img)
    if not faces:
        return _fail("no se detecto rostro")
    if len(faces) > 1 and faces[1][2] * faces[1][3] > 0.5 * faces[0][2] * faces[0][3]:
        return _fail("se detectan varios rostros")
    x, y, w, h = [int(v) for v in faces[0][:4]]
    ih, iw = img.shape[:2]
    if w < 0.18 * min(iw, ih):
        return _fail("rostro muy pequeno o lejano")
    crop = cv2.cvtColor(img[max(y, 0):y + h, max(x, 0):x + w], cv2.COLOR_BGR2GRAY)
    if crop.size == 0:
        return _fail("rostro fuera de encuadre")
    sharp = float(cv2.Laplacian(crop, cv2.CV_64F).var())
    bright = float(crop.mean())
    if sharp < 40:
        return _fail(f"selfie borrosa (nitidez={sharp:.0f})", 0.2)
    if not 50 <= bright <= 220:
        return _fail(f"iluminacion inadecuada (brillo={bright:.0f})", 0.3)
    return {"status": "success", "score": round(float(faces[0][14]), 4),
            "details": f"calidad OK (nitidez={sharp:.0f}, brillo={bright:.0f}); sin anti-suplantacion"}


# --------------------------------------------------------------------------- OCR
# El reverso de la cedula trae impresos datos que NO estan en el codigo de barras
# (lugar de nacimiento, estatura, fecha y lugar de expedicion).
_MONTHS = {"ENE": 1, "FEB": 2, "MAR": 3, "ABR": 4, "MAY": 5, "JUN": 6, "JUL": 7, "AGO": 8,
           "SEP": 9, "SET": 9, "SEPT": 9, "OCT": 10, "NOV": 11, "DIC": 12}
_DATE = re.compile(r"(\d{1,2})\s*[-–]\s*([A-Z]{3,4})\s*[-–]\s*(\d{4})")
_PRINTED_CODE = re.compile(r"[A-Z]-\d{7}-\d{8}-[MF]-\d{10}-\d{8}")
_ocr = None


def _engine():
    global _ocr
    if _ocr is None:
        from rapidocr_onnxruntime import RapidOCR  # carga diferida: tarda unos segundos
        _ocr = RapidOCR()
    return _ocr


def _iso(m):
    month = _MONTHS.get(m.group(2))
    if not month:
        return None
    try:
        return f"{int(m.group(3)):04d}-{month:02d}-{int(m.group(1)):02d}"
    except ValueError:
        return None


def _norm(t: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", t.upper())


def parse_id_back(lines):
    """lines: [(y, x, texto)] del OCR. Devuelve los campos impresos que se logren leer."""
    lines = sorted(lines)
    out = {"birthDate": None, "birthCity": None, "birthDepartment": None, "heightCm": None,
           "bloodType": None, "issueDate": None, "issuePlace": None, "printedCode": None}
    dates = []
    for y, _x, t in lines:
        m = _DATE.search(t.upper())
        if m:
            dates.append((y, m, t.upper()[m.end():].strip()))
        code = _PRINTED_CODE.search(t.upper().replace(" ", ""))
        if code:
            out["printedCode"] = code.group(0)
    if dates:
        out["birthDate"] = _iso(dates[0][1])
    if len(dates) > 1:
        out["issueDate"] = _iso(dates[1][1])
        place = re.sub(r"[^A-ZÁÉÍÓÚÑ .]", "", dates[1][2]).strip()
        out["issuePlace"] = place or None
    # lugar de nacimiento: lineas por encima de la etiqueta "LUGAR DE NACIMIENTO"
    label = next((y for y, _x, t in lines if "LUGARDENACIMIENTO" in _norm(t)), None)
    if label is not None:
        above = [t for y, _x, t in lines if label - 140 < y < label and not _DATE.search(t.upper())
                 and "NACIMIENTO" not in _norm(t)]
        text = " ".join(above)
        m = re.search(r"([A-ZÁÉÍÓÚÑ .]+?)\s*\(([^)]+)\)", text.upper())
        if m:
            out["birthCity"], out["birthDepartment"] = m.group(1).strip(), m.group(2).strip()
        elif above:
            out["birthCity"] = above[0].strip().upper()
    for _y, _x, t in lines:
        u = t.upper().strip()
        if out["heightCm"] is None and not _DATE.search(u):
            m = re.fullmatch(r"([12])[.,](\d{2})", u)
            if m:
                out["heightCm"] = int(m.group(1)) * 100 + int(m.group(2))
        if out["bloodType"] is None:
            m = re.fullmatch(r"(AB|A|B|O)\s*([+-])", u)
            if m:
                out["bloodType"] = m.group(1) + m.group(2)
    return out


@app.post("/api/ocr/id-back")
async def ocr_id_back(image: UploadFile = File(...)):
    img = _decode(await image.read())
    if img is None:
        return {"ok": False, "details": "imagen invalida"}
    result, _ = _engine()(img)
    lines = []
    for box, text, conf in (result or []):
        if float(conf) >= 0.5:
            lines.append((int(sum(p[1] for p in box) / 4), int(sum(p[0] for p in box) / 4), text))
    return {"ok": True, **parse_id_back(lines)}

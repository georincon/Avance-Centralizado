"""Datos del archivo que acompaña a un acto (reconocimiento, autenticación de firma, copia de escritura).

El archivo NO llega al emisor: el navegador calcula su huella SHA-256, su nombre y su tamaño, y solo eso se envía y
queda en el documento notarial. El servidor no puede comprobar que la huella sea de ese archivo (no lo recibe);
valida el formato y el tamaño, y normaliza los datos. Quien tenga el archivo puede comprobarlo después.
"""
import re

MAX_BYTES = 50 * 1024 * 1024  # 50 MB
CAMPOS = ("documento_nombre", "documento_sha256", "documento_bytes")


def validar(form_data: dict):
    """Devuelve None si está bien (y normaliza form_data) o un mensaje de error para la persona."""
    if "documento_sha256" not in form_data:
        return None
    huella = str(form_data.get("documento_sha256", "")).strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", huella):
        return "Falta el archivo o su huella no es válida. Elige el archivo de nuevo."
    try:
        tamano = int(str(form_data.get("documento_bytes", "")).strip())
    except ValueError:
        return "El tamaño del archivo no es válido. Elige el archivo de nuevo."
    if tamano <= 0:
        return "El archivo está vacío."
    if tamano > MAX_BYTES:
        return f"El archivo pesa más de {MAX_BYTES // (1024 * 1024)} MB, que es el máximo."
    nombre = re.sub(r"[\x00-\x1f\x7f]", "", str(form_data.get("documento_nombre", ""))).strip()
    if not nombre or len(nombre) > 255:
        return "El nombre del archivo no es válido. Elige el archivo de nuevo."
    form_data.update({"documento_sha256": huella, "documento_bytes": str(tamano), "documento_nombre": nombre})
    return None

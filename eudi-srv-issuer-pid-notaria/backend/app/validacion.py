"""Validación de los datos del formulario que el navegador no puede garantizar.

El selector de fecha del navegador ayuda, pero no es una garantía: aquí se vuelve a comprobar el formato
(AAAA-MM-DD) y la lógica de cada fecha. "Hoy" es la fecha de Colombia (UTC-5).
"""
import re
from datetime import date, datetime, timedelta, timezone

from app import documento_archivo
from app.opciones import ETIQUETAS, opciones_del_formulario

# campo: (regla, mensaje)  — pasado = no puede ser futura; futuro = no puede ser anterior a hoy
REGLAS = {
    "fecha_nacimiento": ("pasado", "La fecha de nacimiento no puede ser futura."),
    "escritura_fecha": ("pasado", "La fecha de la escritura no puede ser futura."),
    "convivencia_desde": ("pasado", "La fecha desde la que conviven no puede ser futura."),
    "vigente_hasta": ("futuro", "La fecha de vigencia no puede ser anterior a hoy."),
    "fecha_salida": ("futuro", "La fecha de salida no puede ser anterior a hoy."),
    "fecha_regreso": ("futuro", "La fecha de regreso no puede ser anterior a hoy."),
}
ETIQUETA = {"fecha_nacimiento": "fecha de nacimiento", "escritura_fecha": "fecha de la escritura", "convivencia_desde": "fecha de convivencia",
            "vigente_hasta": "fecha de vigencia", "fecha_salida": "fecha de salida", "fecha_regreso": "fecha de regreso"}


def hoy() -> date:
    return (datetime.now(timezone.utc) + timedelta(hours=-5)).date()


def _fecha(valor):
    try:
        return datetime.strptime(str(valor).strip(), "%Y-%m-%d").date()
    except ValueError:
        return None


def validar_fechas(form_data: dict):
    fechas = {}
    for campo, (regla, mensaje) in REGLAS.items():
        if campo not in form_data or form_data[campo] in ("", None):
            continue
        f = _fecha(form_data[campo])
        if f is None:
            return f"La {ETIQUETA[campo]} no es una fecha válida (usa el selector de fecha)."
        if (regla == "pasado" and f > hoy()) or (regla == "futuro" and f < hoy()):
            return mensaje
        form_data[campo] = f.isoformat()
        fechas[campo] = f
    if "fecha_salida" in fechas and "fecha_regreso" in fechas and fechas["fecha_regreso"] < fechas["fecha_salida"]:
        return "La fecha de regreso no puede ser anterior a la de salida."
    if form_data.get("hora_nacimiento") and not re.fullmatch(r"([01]\d|2[0-3]):[0-5]\d", str(form_data["hora_nacimiento"]).strip()):
        return "La hora de nacimiento no es válida (usa el formato HH:MM)."
    return None


def validar_opciones(form_data: dict):
    """Los campos de lista cerrada solo admiten una de sus opciones."""
    for campo, opciones in opciones_del_formulario(form_data.keys()).items():
        valor = form_data.get(campo)
        if valor in (None, ""):
            continue
        if str(valor).strip() not in opciones:
            return f"Elige {ETIQUETAS[campo]} de la lista."
        form_data[campo] = str(valor).strip()
    return None


def validar(form_data: dict, listas_cerradas: bool = True):
    """Devuelve None si todo está bien (y normaliza form_data) o un mensaje de error para la persona.

    `listas_cerradas`: en el formulario web los campos de lista (estado civil, entidad de destino) solo admiten sus opciones.
    El API de órdenes de la plataforma envía texto libre en esos campos (contrato publicado), así que no lo exige.
    """
    return documento_archivo.validar(form_data) or (validar_opciones(form_data) if listas_cerradas else None) or validar_fechas(form_data)

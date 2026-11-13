"""Campos del formulario que se eligen de una lista cerrada (se muestran como menú desplegable).

El servidor vuelve a comprobar que el valor enviado sea una de las opciones: el menú del navegador no es una garantía.
"""

# Estado civil reconocido en Colombia (Decreto 1260 de 1970, art. 1 y Ley 54 de 1990 para la unión marital de hecho).
ESTADO_CIVIL = [
    "Soltero(a)",
    "Casado(a)",
    "Unión marital de hecho",
    "Separado(a)",
    "Divorciado(a)",
    "Viudo(a)",
]

# Quiénes suelen pedir estos documentos (declaración de convivencia, fe de vida).
ENTIDAD_DESTINO = [
    "EPS",
    "Empleador",
    "Fondo de pensiones",
    "Colpensiones",
    "Caja de compensación familiar",
    "Aseguradora",
    "Entidad bancaria",
    "Entidad educativa",
    "Embajada o consulado",
    "Juzgado o autoridad judicial",
    "Notaría",
    "Otra entidad",
]

# Registro civil de nacimiento: sexo del inscrito, como se escribe en el registro (M o F).
SEXO = ["M", "F"]

OPCIONES = {
    "estado_civil_declarante": ESTADO_CIVIL,
    "estado_civil_companero": ESTADO_CIVIL,
    "declarante_estado_civil": ESTADO_CIVIL,
    "entidad_destino": ENTIDAD_DESTINO,
    "sexo": SEXO,
}

ETIQUETAS = {
    "estado_civil_declarante": "el estado civil del declarante",
    "estado_civil_companero": "el estado civil del compañero(a)",
    "declarante_estado_civil": "el estado civil del declarante",
    "entidad_destino": "la entidad de destino",
    "sexo": "el sexo (M o F)",
}


# Permiso de salida de menor: quién otorga el permiso y en qué calidad lo hace.
# Solo aplica a ese documento: el campo "calidad" también existe en el registro civil de nacimiento, con otro sentido.
CALIDAD_SALIDA_MENOR = [
    "Padre",
    "Madre",
    "Abuelo",
    "Abuela",
    "Tío",
    "Tía",
    "Hermano",
    "Hermana",
    "Primo",
    "Prima",
    "Padrastro",
    "Madrastra",
    "Tutor(a) legal",
    "Curador(a)",
    "Representante legal",
    "Otro familiar",
]


def opciones_del_formulario(campos) -> dict:
    """{campo: opciones} que valen para un formulario con estos campos (los del documento que se está pidiendo)."""
    campos = set(campos)
    out = {k: v for k, v in OPCIONES.items() if k in campos}
    if "calidad" in campos and "menor_documento" in campos:  # permiso de salida de menor
        out["calidad"] = CALIDAD_SALIDA_MENOR
    return out


ETIQUETAS["calidad"] = "la calidad en que actúas"

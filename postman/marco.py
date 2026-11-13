"""Marco del generador: define qué es una prueba (R), y la convierte en Postman, curl y una ejecución real.

Una sola fuente (definiciones.py) produce:
  - la colección de Postman (cada proyecto, una carpeta; cada prueba con sus comprobaciones),
  - las guías en Markdown (Postman y terminal con curl),
  - y una ejecución real contra el sistema (--verificar) para que lo documentado sea lo que de verdad ocurre.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import urlsplit, parse_qsl


@dataclass
class R:
    """Una prueba: una petición HTTP, lo que se espera de ella y lo que se guarda para las siguientes."""
    nombre: str
    metodo: str
    ruta: str                      # relativa al dominio, con {{variables}}
    desc: str = ''                 # qué hace y cómo se prueba (Markdown)
    cuerpo: Any = None             # JSON (dict/list) o texto
    form: dict | None = None       # application/x-www-form-urlencoded
    binario: str = ''              # archivo que se envía tal cual (p. ej. consulta.tsq)
    cabeceras: dict = field(default_factory=dict)
    espera: Any = 200              # código HTTP o lista de códigos aceptables
    tiene: list = field(default_factory=list)      # rutas ("a.b") que deben existir en el JSON
    igual: dict = field(default_factory=dict)      # ruta -> valor esperado en el JSON
    texto: str | None = None       # texto que debe aparecer en el cuerpo
    guarda: dict = field(default_factory=dict)     # variable -> ruta en el JSON, para las siguientes pruebas
    manual: str = ''               # paso que solo puede hacer una persona (p. ej. escanear con la wallet)
    privada: bool = False          # necesita una clave que no se comparte (va en variables, nunca escrita)
    salta_verificacion: str = ''   # motivo por el que --verificar no la ejecuta


@dataclass
class Carpeta:
    titulo: str
    desc: str
    pruebas: list[R]
    puerto_bd: str = ''


def mayus(v: str) -> str:
    return v.upper()


def a_shell(texto: str) -> str:
    """{{variable}} -> $VARIABLE para los ejemplos de terminal."""
    return re.sub(r'\{\{(\w+)\}\}', lambda m: '$' + mayus(m.group(1)), texto)


# ---------------------------------------------------------------------------------------------- Postman

def _url(ruta: str) -> dict:
    crudo = '{{base_url}}' + ruta
    partes = urlsplit(ruta)
    return {
        'raw': crudo,
        'host': ['{{base_url}}'],
        'path': [p for p in partes.path.split('/') if p],
        'query': [{'key': k, 'value': v} for k, v in parse_qsl(partes.query, keep_blank_values=True)],
    }


def _script(r: R) -> list[str]:
    esp = r.espera if isinstance(r.espera, list) else [r.espera]
    js = ['const dig = (o, p) => p.split(".").reduce((a, k) => (a == null ? a : a[k]), o);',
          'let json = null; try { json = pm.response.json(); } catch (e) {}']
    if len(esp) == 1:
        js.append(f'pm.test("HTTP {esp[0]}", () => pm.response.to.have.status({esp[0]}));')
    else:
        js.append(f'pm.test("HTTP {" o ".join(map(str, esp))}", () => pm.expect({json.dumps(esp)}).to.include(pm.response.code));')
    for ruta in r.tiene:
        js.append(f'pm.test("trae {ruta}", () => pm.expect(dig(json, {json.dumps(ruta)})).to.not.be.undefined);')
    for ruta, valor in r.igual.items():
        js.append(f'pm.test("{ruta} = {valor}", () => pm.expect(dig(json, {json.dumps(ruta)})).to.eql({json.dumps(valor)}));')
    if r.texto:
        js.append(f'pm.test("la respuesta contiene {r.texto!r}", () => pm.expect(pm.response.text()).to.include({json.dumps(r.texto)}));')
    for var, ruta in r.guarda.items():
        js.append(f'if (json && dig(json, {json.dumps(ruta)}) !== undefined) pm.collectionVariables.set({json.dumps(var)}, typeof dig(json, {json.dumps(ruta)}) === "string" ? dig(json, {json.dumps(ruta)}) : JSON.stringify(dig(json, {json.dumps(ruta)})));')
    return js


def _descripcion(r: R) -> str:
    partes = [r.desc.strip()] if r.desc else []
    if r.manual:
        partes.append(f'**Paso manual (lo hace una persona):** {r.manual}')
    if r.privada:
        partes.append('**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).')
    if r.guarda:
        partes.append('**Guarda para las siguientes pruebas:** ' + ', '.join(f'`{v}`' for v in r.guarda))
    esp = r.espera if isinstance(r.espera, list) else [r.espera]
    partes.append('**Resultado esperado:** HTTP ' + ' o '.join(map(str, esp)) + '.')
    return '\n\n'.join(partes)


def a_postman(r: R) -> dict:
    cab = [{'key': k, 'value': v} for k, v in r.cabeceras.items()]
    req: dict = {'method': r.metodo, 'header': cab, 'url': _url(r.ruta), 'description': _descripcion(r)}
    if r.binario:
        req['body'] = {'mode': 'file', 'file': {'src': r.binario}}
    elif r.form is not None:
        req['body'] = {'mode': 'urlencoded', 'urlencoded': [{'key': k, 'value': v} for k, v in r.form.items()]}
    elif r.cuerpo is not None:
        req['header'] = cab + [{'key': 'Content-Type', 'value': 'application/json'}]
        raw = r.cuerpo if isinstance(r.cuerpo, str) else json.dumps(r.cuerpo, indent=2, ensure_ascii=False)
        req['body'] = {'mode': 'raw', 'raw': raw, 'options': {'raw': {'language': 'json'}}}
    return {'name': r.nombre, 'event': [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': _script(r)}}],
            'request': req, 'response': []}


def a_coleccion(titulo: str, descripcion: str, carpetas: list[Carpeta], variables: dict) -> dict:
    return {
        'info': {'name': titulo, 'description': descripcion, '_postman_id': 'a7c1f4e2-5d1b-4f55-9a52-avance-eudi',
                 'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
        'item': [{'name': c.titulo, 'description': c.desc, 'item': [a_postman(r) for r in c.pruebas]} for c in carpetas],
        'variable': [{'key': k, 'value': v} for k, v in variables.items()],
    }


def a_entorno(nombre: str, valores: dict, secretos: set[str]) -> dict:
    return {'id': 'e1-' + nombre.lower().replace(' ', '-'), 'name': nombre,
            'values': [{'key': k, 'value': v, 'type': 'secret' if k in secretos else 'default', 'enabled': True} for k, v in valores.items()],
            '_postman_variable_scope': 'environment'}


# ----------------------------------------------------------------------------------------------- curl

def a_curl(r: R) -> str:
    ruta = a_shell(r.ruta)
    flags = ['-sk']
    if r.metodo != 'GET':
        flags += ['-X', r.metodo]
    flags_h = []
    for k, v in r.cabeceras.items():
        flags_h.append(f'-H "{k}: {a_shell(v)}"')
    cuerpo = ''
    if r.binario:
        cuerpo = f' \\\n    --data-binary @{r.binario}'
    elif r.form is not None:
        cuerpo = ' \\\n    ' + ' \\\n    '.join(f'-d {k}={a_shell(v)}' for k, v in r.form.items())
    elif r.cuerpo is not None:
        raw = r.cuerpo if isinstance(r.cuerpo, str) else json.dumps(r.cuerpo, ensure_ascii=False)
        flags_h.append('-H "Content-Type: application/json"')
        cuerpo = " \\\n    -d '" + a_shell(raw).replace("'", "'\\''") + "'"
    cmd = 'curl ' + ' '.join(flags) + ' ' + ' '.join(flags_h) + f' "$BASE_URL{ruta}"' + cuerpo
    return re.sub(r' +', ' ', cmd).replace(' \\\n ', ' \\\n  ')


def jq_ruta(ruta: str) -> str:
    """a.0.b -> .a[0].b"""
    return ''.join(f'[{p}]' if p.isdigit() else f'.{p}' for p in ruta.split('.'))


def a_curl_completo(r: R, usa_cookies: bool) -> str:
    """Comando listo para pegar. JSON -> jq; páginas/listas firmadas -> primeros bytes; rechazos -> muestra el código HTTP."""
    base = a_curl(r)
    if usa_cookies:
        base = base.replace('curl -sk', 'curl -sk -c "$JAR" -b "$JAR"', 1)
    esp = r.espera if isinstance(r.espera, list) else [r.espera]
    if esp != [200] and esp != [206]:
        # el cuerpo de un rechazo no siempre es JSON: se muestra tal cual y se añade el código
        return base.replace('curl -sk', "curl -sk -w '\\nHTTP %{http_code}\\n'", 1)
    if r.texto is not None:                       # HTML, XML, SVG o lista firmada: no es JSON
        return base + ' | head -c 300; echo'
    if r.guarda:
        var, ruta = next(iter(r.guarda.items()))
        if len(r.guarda) == 1:
            return f"{mayus(var)}=$({base} | jq -r '{jq_ruta(ruta)}')\necho \"{mayus(var)}=${mayus(var)}\""
        lineas = [f'RESP=$({base})'] + [f"{mayus(v)}=$(echo \"$RESP\" | jq -r '{jq_ruta(p)}')" for v, p in r.guarda.items()] + ['echo "$RESP" | jq .']
        return '\n'.join(lineas)
    return base + ' | jq .'

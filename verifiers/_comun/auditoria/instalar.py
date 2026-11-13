#!/usr/bin/env python3
"""Engancha la auditoría (srv-auditoria) a los portales de verifiers/. Es idempotente: se puede correr de nuevo.

  python3 verifiers/_comun/auditoria/instalar.py            # todos
  python3 verifiers/_comun/auditoria/instalar.py banco eps  # solo esos

Qué hace en cada portal:
  1. copia auditoria.js e historial.js a <portal>/src/ (la imagen de cada portal se construye con su carpeta);
  2. src/servidor.js: envuelve el cliente del verifier con auditarVerifier(), pasa un auditor al emisor (si emite) y monta
     la página del historial local <BASE>/auditoria/;
  2b. compose.yml: si el portal no tenía carpeta de datos, le monta ./datos en /datos (ahí vive historial.jsonl);
  3. src/emisor.js (solo los portales que emiten credenciales): reporta oferta, autorización y emisión.
Solo agrega: no cambia la lógica existente. Si un ancla no se encuentra, no toca el archivo y lo avisa.
"""
import re, shutil, sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]            # verifiers/
FUENTE = Path(__file__).with_name('auditoria.js')
FUENTE_HISTORIAL = Path(__file__).with_name('historial.js')
NOMBRES = {  # expresión JS con el nombre que se muestra en el panel
    'acceso': 'NOMBRE', 'aerolinea': 'aerolinea.nombre', 'banco': 'NOMBRE', 'beca': 'NOMBRE', 'cancilleria': 'cancilleria.nombre',
    'contratacion': 'NOMBRE', 'discoteca': 'NOMBRE', 'eps': 'EPS', 'gym': 'GYM', 'migracion': 'NOMBRE', 'notaria': 'notaria.nombre',
    'pensiones': 'NOMBRE',
}
EMISORES = ['aerolinea', 'banco', 'cancilleria', 'eps', 'gym', 'notaria']
POR_TRAMITE = ['aerolinea', 'cancilleria', 'notaria']   # su oferta recibe un trámite (el titular está en tramite.persona)


def una_vez(texto, viejo, nuevo, donde):
    if texto.count(viejo) != 1:
        raise SystemExit(f'  ✗ {donde}: ancla no encontrada o repetida ({texto.count(viejo)}): {viejo[:70]!r}')
    return texto.replace(viejo, nuevo)


def servidor(p):
    f = RAIZ / p / 'src' / 'servidor.js'
    s = f.read_text()
    nombre = NOMBRES[p]
    if 'montarPanelAuditoria' in s:
        return 'servidor.js ya enganchado'
    if 'auditarVerifier' in s:   # enganchado antes de existir el historial local: solo se añade el panel
        s = una_vez(s, "import { crearAuditor, auditarVerifier } from './auditoria.js'\n",
                    "import { crearAuditor, auditarVerifier } from './auditoria.js'\nimport { montarPanelAuditoria } from './historial.js'\n", f'{p}/servidor.js import panel')
        s = una_vez(s, "const app = express()\n", f"const app = express()\nmontarPanelAuditoria(app, {{ base: BASE, nombre: {nombre} }})   // historial local: <BASE>/auditoria/ (solo administración)\n", f'{p}/servidor.js panel')
        f.write_text(s)
        return 'servidor.js: panel añadido'
    s = una_vez(s, "import { crearVerifier } from './verifier.js'\n",
                "import { crearVerifier } from './verifier.js'\nimport { crearAuditor, auditarVerifier } from './auditoria.js'\nimport { montarPanelAuditoria } from './historial.js'\n", f'{p}/servidor.js import')
    s = una_vez(s, "const app = express()\n", f"const app = express()\nmontarPanelAuditoria(app, {{ base: BASE, nombre: {nombre} }})   // historial local: <BASE>/auditoria/ (solo administración)\n", f'{p}/servidor.js panel')
    m = re.search(r"^const verifier = (crearVerifier\(.*\))$", s, re.M)
    if not m:
        raise SystemExit(f'  ✗ {p}/servidor.js: no encontré «const verifier = crearVerifier(…)»')
    nuevo = (f"// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).\n"
             f"const verifier = auditarVerifier({m.group(1)}, crearAuditor({{ rol: 'verificador', identificador: URL_PUBLICA, nombre: {nombre}, proyecto: 'verifiers/{p}' }}))")
    s = s.replace(m.group(0), nuevo)
    if p in EMISORES:
        m = re.search(r"^const emisor = crearEmisor\(\{(.*) \}\)$", s, re.M)
        if not m:
            raise SystemExit(f'  ✗ {p}/servidor.js: no encontré «const emisor = crearEmisor({{…}})»')
        s = s.replace(m.group(0), f"const emisor = crearEmisor({{{m.group(1)}, auditor: crearAuditor({{ rol: 'emisor', identificador: URL_PUBLICA, nombre: {nombre}, proyecto: 'verifiers/{p}' }}) }})")
    f.write_text(s)
    return 'servidor.js enganchado'


AYUDANTES = """  // --- Auditoría: reporta al centro lo que ocurre aquí. Solo monitoreo: nunca espera, nunca lanza, nunca cambia la emisión. ---
  const docDe = (d) => (typeof d === 'string' ? DOCUMENTOS[d] : d)
  const nombresDe = (doc) => (doc?.datos ?? []).map((d) => [].concat(d)[0]).filter((x) => typeof x === 'string')
  const auditar = (e) => { try { auditor.registrar({ canal: 'api', ...e }) } catch { /* reportar no puede afectar la emisión */ } }
  const auditarEmision = (req, evento, t0, error) => {
    try {
      const a0 = req.auditoria ?? {}
      const a = { ...a0, doc: docDe(a0.doc) }   // porConfiguracion devuelve el documento o, en algunos portales, solo su nombre corto
      const base = {
        tipo: evento, referencia: a.ref, duracion_ms: Date.now() - t0,
        credencial_tipo: a.doc?.vct, credencial_nombre: a.doc?.nombre,
        sujeto: a.documento ? { numero_documento: String(a.documento) } : undefined,
      }
      if (!error) {
        auditar({ ...base, resultado: 'exitoso', elementos: evento === 'emision_credencial' ? nombresDe(a.doc).map((n) => ({ sentido: 'emitido', tipo_credencial: a.doc?.vct ?? 'desconocida', elemento: n })) : undefined })
        return
      }
      const oauth = error instanceof ErrorOAuth
      auditar({
        ...base, resultado: 'fallido',
        tipo_error: !oauth ? 'interno' : error.estado === 401 ? 'autenticacion' : error.estado >= 500 ? 'emisor' : 'validacion',
        codigo_error: oauth ? error.error : 'invalid_request', mensaje_error: String(error.message ?? error).slice(0, 300),
      })
    } catch { /* idem */ }
  }
"""


def emisor(p):
    f = RAIZ / p / 'src' / 'emisor.js'
    s = f.read_text()
    if 'auditarEmision' in s:
        return 'emisor.js ya enganchado'
    d = f'{p}/emisor.js'
    # 1. el emisor recibe un auditor (opcional: sin él no hace nada)
    s, n = re.subn(r"^(export function crearEmisor\(\{.*?)trustValidator \}\)", r"\1trustValidator, auditor = { registrar() {} } })", s, count=1, flags=re.M)
    if n != 1:
        raise SystemExit(f'  ✗ {d}: firma de crearEmisor no encontrada')
    # 2. ayudantes (tras los mapas de estado del emisor)
    s = una_vez(s, "  setInterval(limpiar, 60 * 1000).unref()\n", "  setInterval(limpiar, 60 * 1000).unref()\n\n" + AYUDANTES, d + ' ayudantes')
    # 3. oferta: la oferta recuerda su id (referencia del trámite) y se reporta
    if p in POR_TRAMITE:
        s = una_vez(s, "codigos.set(codigo, { numero: tramite.numero,", "codigos.set(codigo, { ref: id, documento: tramite.persona?.documento, numero: tramite.numero,", d + ' codigos.set')
        sujeto = 'tramite.persona?.documento'
    else:
        s = una_vez(s, "codigos.set(codigo, { documento,", "codigos.set(codigo, { ref: id, documento,", d + ' codigos.set')
        sujeto = 'documento'
    s = re.sub(r"(    codigos\.set\(codigo, \{[^\n]*\}\)\n)",
               r"\1    auditar({ tipo: 'oferta_credencial', resultado: 'en_proceso', referencia: id, credencial_tipo: doc.vct, credencial_nombre: doc.nombre, canal: 'enlace',\n"
               r"             sujeto: " + sujeto + r" ? { numero_documento: String(" + sujeto + r") } : undefined })\n", s, count=1)
    # 4. manejar: reporta el resultado del token y de la credencial
    s = una_vez(s, "  const manejar = (fn) => async (req, res) => {\n    try {\n      await fn(req, res)\n    } catch (e) {\n",
                "  const manejar = (fn) => async (req, res) => {\n"
                "    const t0 = Date.now()\n"
                "    const evento = req.path.endsWith('/token') ? 'autorizacion_emision' : req.path.endsWith('/credencial') ? 'emision_credencial' : null\n"
                "    try {\n      await fn(req, res)\n      if (evento) auditarEmision(req, evento, t0)\n    } catch (e) {\n      if (evento) auditarEmision(req, evento, t0, e)\n", d + ' manejar')
    # 5. el token: qué oferta canjea; la credencial: de qué token viene
    s = una_vez(s, "    const c = codigos.get(codigo)\n", "    const c = codigos.get(codigo)\n    req.auditoria = { ref: c?.ref, documento: c?.documento, doc: c && porConfiguracion(c.configuracion) }\n", d + ' codigos.get')
    s = una_vez(s, "    tokens.set(token, { ", "    tokens.set(token, { ref: c.ref, documento: c.documento, " if p in POR_TRAMITE else "    tokens.set(token, { ref: c.ref, ", d + ' tokens.set')
    s = una_vez(s, "    const t = tokens.get(valor)\n", "    const t = tokens.get(valor)\n    req.auditoria = { ref: t?.ref, documento: t?.documento, doc: t && porConfiguracion(t.configuracion) }\n", d + ' tokens.get')
    f.write_text(s)
    return 'emisor.js enganchado'


def compose(p):
    """Los portales sin carpeta de datos reciben ./datos en /datos para guardar su historial local."""
    f = RAIZ / p / 'compose.yml'
    s = f.read_text()
    (RAIZ / p / 'datos').mkdir(exist_ok=True)
    if './datos:/datos' in s:
        return 'compose.yml ya monta /datos'
    if '    user:' not in s:
        s = una_vez(s, '    read_only: true\n', '    read_only: true\n    # El dueño de ./datos (quien ejecutó levantar.sh): ahí se escribe el historial local (historial.jsonl).\n    user: ${UID_GID:-1000:1000}\n', f'{p}/compose.yml user')
    if re.search(r'^    volumes:\n', s, re.M):
        s = re.sub(r'^(    volumes:\n)', r'\1      - ./datos:/datos\n', s, count=1, flags=re.M)
    else:
        s = una_vez(s, '    networks:\n', '    volumes:\n      - ./datos:/datos\n    networks:\n', f'{p}/compose.yml volumes')
    f.write_text(s)
    return 'compose.yml: monta ./datos en /datos'


def main():
    pedidos = sys.argv[1:] or list(NOMBRES)
    for p in pedidos:
        if p not in NOMBRES:
            print(f'{p}: portal desconocido'); continue
        shutil.copyfile(FUENTE, RAIZ / p / 'src' / 'auditoria.js')
        shutil.copyfile(FUENTE_HISTORIAL, RAIZ / p / 'src' / 'historial.js')
        print(f'{p}: copiados auditoria.js e historial.js; {servidor(p)}' + (f'; {emisor(p)}' if p in EMISORES else '') + f'; {compose(p)}')


main()

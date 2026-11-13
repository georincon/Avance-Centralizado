// Control migratorio: comprueba un pasaporte digital de la Cancillería y lo cruza con el PID de quien lo presenta, y le
// muestra al funcionario los RETRATOS y los datos para que compare con la persona que tiene delante y decida.
//
//   1. El funcionario elige «Control de entrada» o «Control de salida» y se muestra un QR (el que escanea es el teléfono de
//      la persona viajera).
//   2. La persona lo escanea con su wallet y comparte, del PID, nombre y documento, y del pasaporte, sus datos (el retrato es el del pasaporte).
//   3. El portal muestra los datos YA VERIFICADOS (firmas, emisor en la lista, estado) más cuatro comprobaciones propias:
//      el pasaporte es de la Cancillería de confianza, está vigente, el documento del PID es el del pasaporte y los nombres
//      coinciden. El funcionario pulsa «Permitir» o «Denegar».
//
// Privacidad: el retrato y los datos viven en memoria hasta que el funcionario decide (o vencen en unos minutos) y luego se borran.
// El registro de la jornada guarda solo hora, movimiento, nombre, documento, n.º de pasaporte (parcial) y decisión, sin retratos,
// y desaparece a la hora o al reiniciar. Al log solo van «permitido» o «denegado».
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA      https://civica-desarrollo.avance.org.co/migracion (el servidor escucha bajo esa ruta)
//   VERIFIER_API     http://verifier-migracion/verifier-migracion (red interna)
//   NOMBRE_ENTIDAD   el nombre que se muestra
//   PUERTO           puerto interno (80: srv-nw-nginx llama a app-migracion:80)

import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import QRCode from 'qrcode'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const CANCILLERIA = `${new URL(URL_PUBLICA).origin}/cancilleria`   // el único emisor de pasaportes que se acepta
const NOMBRE = process.env.NOMBRE_ENTIDAD ?? 'Migración Cívica'
const PUERTO = Number(process.env.PUERTO ?? 80)
const VENCE_SOLICITUD_MS = 5 * 60 * 1000
const CONSERVA_RESULTADO_MS = 10 * 60 * 1000
const CONSERVA_REGISTRO_MS = 60 * 60 * 1000
const MAX_REGISTRO = 50

const VCT_PID = 'urn:eudi:pid:1'
const VCT_PASAPORTE = 'urn:co:cancilleria:pasaporte:1'
const DATOS_PID = ['personal_administrative_number', 'given_name', 'family_name']
const DATOS_PASAPORTE = ['pasaporte_numero', 'nombres', 'apellidos', 'documento', 'fecha_nacimiento', 'lugar_nacimiento', 'sexo', 'retrato', 'fecha_vencimiento']
// Los retratos van como data URI; solo se acepta una imagen real (se pone en un <img>, nunca como HTML).
const FOTO = /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/
const MOVIMIENTOS = ['entrada', 'salida']

// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/migracion' }))
const sesiones = new Map()     // id de cookie → { id, pendiente? } (para que la wallet vuelva al mismo navegador)
const solicitudes = new Map() // id → { transaccion, movimiento, creada, final?, decidida? }
const registro = []           // { hora, movimiento, nombre, documento, pasaporte, decision } (más reciente primero)

setInterval(() => {
  const ahora = Date.now()
  for (const [id, s] of solicitudes) {
    if (ahora - (s.finalizada ?? s.creada) > CONSERVA_RESULTADO_MS) solicitudes.delete(id)
  }
  for (const [id, s] of sesiones) if (!solicitudes.has(s.pendiente)) sesiones.delete(id)
  while (registro.length && ahora - registro[registro.length - 1].hora > CONSERVA_REGISTRO_MS) registro.pop()
}, 60 * 1000).unref()

function sesion(req, res) {
  const id = /(?:^|;\s*)migracion_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `migracion_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}

const tokens = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase().split(/\s+/).filter(Boolean).sort().join(' ')
const parcial = (n) => (n ? `${String(n).slice(0, 2)}•••${String(n).slice(-2)}` : '')

function validar(r) {
  const pid = r.credenciales.pid
  const pas = r.credenciales.pasaporte
  if (!pid || pid.vct !== VCT_PID) return 'Falta el documento de identidad digital (PID).'
  if (!pas || pas.vct !== VCT_PASAPORTE) return 'Falta el pasaporte digital.'
  for (const k of ['given_name', 'family_name', 'personal_administrative_number']) if (!pid.datos[k]) return `La wallet no compartió un dato del PID (${k}).`
  for (const k of ['pasaporte_numero', 'nombres', 'apellidos', 'documento', 'fecha_vencimiento']) if (!pas.datos[k]) return `La wallet no compartió un dato del pasaporte (${k}).`
  if (typeof pas.datos.retrato !== 'string' || !FOTO.test(pas.datos.retrato)) return 'La wallet no compartió el retrato del pasaporte.'
  return null
}

// Las comprobaciones propias de migración, además de las del verifier (firma, emisor en la lista, estado, posesión).
function comprobaciones(pid, pas) {
  const hoy = new Date().toISOString().slice(0, 10)
  const vigente = pas.datos.fecha_vencimiento >= hoy
  const mismoDocumento = String(pid.datos.personal_administrative_number) === String(pas.datos.documento)
  const mismoNombre = tokens(`${pid.datos.given_name} ${pid.datos.family_name}`) === tokens(`${pas.datos.nombres} ${pas.datos.apellidos}`)
  const deConfianza = pas.emisor === CANCILLERIA
  return [
    { nombre: 'Pasaporte de la Cancillería de confianza', cumple: deConfianza, detalle: deConfianza ? 'Emisor en la lista de confianza.' : `Emisor no reconocido: ${pas.emisor}.` },
    { nombre: 'Pasaporte vigente', cumple: vigente, detalle: vigente ? `Válido hasta ${pas.datos.fecha_vencimiento}.` : `Venció el ${pas.datos.fecha_vencimiento}.` },
    { nombre: 'El documento de identidad coincide', cumple: mismoDocumento, detalle: mismoDocumento ? 'El PID y el pasaporte son de la misma persona.' : 'El número del PID no es el del pasaporte.' },
    { nombre: 'Los nombres coinciden', cumple: mismoNombre, detalle: mismoNombre ? 'Mismo nombre en el PID y en el pasaporte.' : 'El nombre del PID no es el del pasaporte.' },
  ]
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) return fin(solicitud, { estado: 'rechazada', motivo: 'El código venció. Genera uno nuevo.' })
    return r
  }
  if (r.estado === 'error') return fin(solicitud, { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` })
  const motivo = validar(r)
  if (motivo) return fin(solicitud, { estado: 'rechazada', motivo })
  const { pid, pasaporte: pas } = r.credenciales
  const criterios = comprobaciones(pid, pas)
  const d = pas.datos
  return fin(solicitud, {
    estado: 'listo',
    movimiento: solicitud.movimiento,
    criterios,
    todoBien: criterios.every((c) => c.cumple),
    viajero: {
      nombres: d.nombres, apellidos: d.apellidos, documento: String(d.documento), pasaporte: d.pasaporte_numero,
      nacimiento: d.fecha_nacimiento ?? null, lugar: d.lugar_nacimiento ?? null, sexo: d.sexo ?? null, vence: d.fecha_vencimiento,
      retrato: d.retrato,
    },
  })
}

function fin(solicitud, final) {
  solicitud.final = final
  solicitud.finalizada = Date.now()
  return final
}

// --- Rutas ---------------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: NOMBRE, tema: {
  fondo: '#f2f5fa', panel: '#ffffff', texto: '#14213d', suave: '#4a5875', borde: '#d3dae6', titulo: '#004884',
  marca: '#004884', marcaTexto: '#ffffff', acento: '#fcd116', cabeceraFondo: '#ffffff', cabeceraTexto: '#004884',
  ok: '#17803f', okFondo: '#e3f4ea', mal: '#c8102e', malFondo: '#fdeaed', neutroFondo: '#eef2f8', hover: '#eef2f8',
  fuente: 'Inter, Helvetica Neue, Helvetica, Arial, sans-serif', pesoTitulo: 800, mayusculas: 'none', tracking: '-.01em', radio: '4px', logoAlto: 62,
  preFondo: '#00305a', preTexto: '#e3ebf7', logo: `${BASE}/publico/logo.svg`, subtitulo: '',
  fuentes: `@font-face{font-family:Inter;font-weight:300 700;font-display:swap;src:url(${BASE}/publico/fuentes/inter-latin.woff2) format(woff2);unicode-range:U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD}@font-face{font-family:Inter;font-weight:300 700;font-display:swap;src:url(${BASE}/publico/fuentes/inter-latin-ext.woff2) format(woff2);unicode-range:U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF}`,
  css: 'body::before{content:"Estado de Cívica · Portal de ejemplo";display:block;background:#3366cc;color:#fff;font-weight:700;font-size:.78rem;letter-spacing:.04em;padding:7px 28px}.cab nav a{color:#004884}.cab nav a:hover{background:#eef2f8}.cab nav a.activo{background:#eef2f8}.franja{height:6px;background:linear-gradient(90deg,#fcd116 0 50%,#003893 50% 75%,#ce1126 75%)}.marca small{display:none}',
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(express.json({ limit: '10kb' }))
const r = express.Router()
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{NOMBRE}}', NOMBRE)

r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))
r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').set('Cache-Control', 'no-store').send(pagina) })

r.post('/api/solicitud', async (req, res) => {
  const s = sesion(req, res)
  const { movimiento, dispositivo } = req.body ?? {}
  if (!MOVIMIENTOS.includes(movimiento)) return res.status(400).json({ error: 'movimiento: entrada o salida' })
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      credenciales: [
        { id: 'pid', vct: VCT_PID, datos: DATOS_PID },
        { id: 'pasaporte', vct: VCT_PASAPORTE, datos: DATOS_PASAPORTE },
      ],
      nonce: randomBytes(16).toString('base64url'),
      // En el mismo teléfono la wallet vuelve al portal al terminar.
      redireccion: dispositivo === 'mismo' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { transaccion, movimiento, sesion: s.id, creada: Date.now() })
    s.pendiente = id
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 360 }), vence_en: VENCE_SOLICITUD_MS / 1000 })
  } catch (e) {
    console.error(`[solicitud] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})

r.get('/api/solicitud/:id', async (req, res) => {
  const solicitud = solicitudes.get(req.params.id)
  if (!solicitud || solicitud.sesion !== sesion(req, res).id) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  if (solicitud.decidida) return res.json({ estado: 'rechazada', motivo: 'Este control ya se resolvió.' })
  try {
    res.json(await consultar(solicitud))
  } catch (e) {
    console.error(`[solicitud] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

// A donde vuelve la wallet en el mismo teléfono, con el código que libera el resultado. Se consulta con el código y se
// devuelve a la página, que recupera la solicitud de esta sesión (/api/pendiente) y sigue como tras escanear el QR.
r.get('/volver', async (req, res) => {
  const solicitud = solicitudes.get(sesion(req, res).pendiente)
  const fallo = (motivo) => res.redirect(`${BASE}/?error=${encodeURIComponent(motivo)}`)
  if (!solicitud || !req.query.response_code) return fallo('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')
  try {
    const final = await consultar(solicitud, String(req.query.response_code))
    if (final.estado === 'listo') return res.redirect(`${BASE}/?volver=1`)
    return fallo(final.motivo ?? 'La wallet todavía no ha respondido.')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return fallo('El servicio de verificación no está disponible.')
  }
})

// La solicitud en curso de esta sesión (para retomarla al volver la wallet; el resultado se pide después con /api/solicitud/:id).
r.get('/api/pendiente', (req, res) => {
  const id = sesion(req, res).pendiente
  const sol = solicitudes.get(id)
  if (!sol || !sol.final || sol.decidida) return res.json({})
  res.set('Cache-Control', 'no-store').json({ id })
})

// El funcionario decide. Se anota en el registro de la jornada (sin retratos) y se borran los datos de la solicitud.
r.post('/api/decision', (req, res) => {
  const { id, decision } = req.body ?? {}
  const solicitud = solicitudes.get(id)
  if (!['permitir', 'denegar'].includes(decision)) return res.status(400).json({ error: 'decision: permitir o denegar' })
  if (solicitud?.sesion !== sesion(req, res).id || solicitud.final?.estado !== 'listo' || solicitud.decidida) return res.status(409).json({ error: 'No hay un control pendiente de decidir.' })
  const f = solicitud.final
  if (decision === 'permitir' && !f.todoBien) return res.status(409).json({ error: 'Hay comprobaciones sin cumplir: no se puede permitir.' })
  const v = f.viajero
  const entrada = {
    hora: Date.now(), movimiento: f.movimiento, nombre: `${v.nombres} ${v.apellidos}`, documento: v.documento,
    pasaporte: parcial(v.pasaporte), decision: decision === 'permitir' ? 'permitido' : 'denegado',
  }
  registro.unshift(entrada)
  registro.length = Math.min(registro.length, MAX_REGISTRO)
  solicitud.decidida = true
  solicitud.final = { estado: 'rechazada', motivo: 'Este control ya se resolvió.' } // fuera los retratos de la memoria
  console.log(`[migracion] ${entrada.movimiento} ${entrada.decision}`)
  res.json({ ok: true, entrada })
})

r.get('/api/registro', (_req, res) => res.set('Cache-Control', 'no-store').json({ registro }))

app.use(BASE || '/', r)
app.listen(PUERTO, () => console.log(`${NOMBRE}: control migratorio en :${PUERTO}${BASE} (verifier ${process.env.VERIFIER_API})`))

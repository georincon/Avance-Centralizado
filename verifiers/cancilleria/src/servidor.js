// Cancillería de ejemplo: solicita tu pasaporte con la wallet, de punta a punta.
//
//   1. Identificarse      la wallet presenta el PID a SU verifier (verifier-cancilleria):
//                         documento, nombres, apellidos, nacimiento y foto.
//   2. Solicitar          lugar de nacimiento, sexo y retrato salen del PID.
//   3. Recibirlo          la Cancillería emite el pasaporte como documento de la wallet
//                         (OpenID4VCI, ver emisor.js): QR + PIN. Una sola entrega.
//   4. Mis pasaportes     reexpedir (lo perdí: el anterior queda revocado) o reportar pérdida.
//   5. Verificar          cualquiera puede pedirle a una wallet un pasaporte y ver si es
//                         auténtico, está vigente y sigue sin revocar.
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA          p. ej. https://civica-desarrollo.avance.org.co/cancilleria (también es el credential_issuer)
//   VERIFIER_API         API de su verifier por la red interna: http://verifier-cancilleria/verifier-cancilleria
//   TRUST_VALIDATOR_URL  para comprobar las WIA de las wallets (abca.js)
//   CANCILLERIA_NOMBRE · FUNCIONARIO_NOMBRE
//   SECRETOS_DIR         ds.key y ds.crt (scripts/generar-claves.sh)
//   TRAMITES_ARCHIVO     el protocolo (JSON)
//   PUERTO               80 (el nginx central llama a <prefijo>:80)

import { randomBytes, randomInt } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import { importPKCS8 } from 'jose'
import QRCode from 'qrcode'
import { crearAbca } from './abca.js'
import { DOCUMENTOS, porVct, vigenciaAnios } from './documentos.js'
import { crearEmisor } from './emisor.js'
import { crearEstado } from './estado.js'
import { iniciarAlmacen } from './almacen.js'
import { crearTramites } from './tramites.js'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const SECRETOS = process.env.SECRETOS_DIR ?? '/secretos'
const cancilleria = { nombre: process.env.CANCILLERIA_NOMBRE ?? 'Ministerio de Relaciones Exteriores de Cívica', funcionario: process.env.FUNCIONARIO_NOMBRE ?? 'Jefe de pasaportes' }
const VENCE_SOLICITUD_MS = 5 * 60 * 1000

// --- Piezas --------------------------------------------------------------------------------
const certificado = readFileSync(`${SECRETOS}/ds.crt`, 'utf8')
const firmante = {
  clave: await importPKCS8(readFileSync(`${SECRETOS}/ds.key`, 'utf8'), 'ES256'),
  x5c: [certificado.replace(/-----[^-]+-----|\s/g, '')],
}
await iniciarAlmacen(process.env.TRAMITES_ARCHIVO ?? '/datos/tramites.json', { siguiente: 1, tramites: [] })
const tramites = crearTramites(process.env.TRAMITES_ARCHIVO ?? '/datos/tramites.json')
const estado = crearEstado({ firmante, uri: `${URL_PUBLICA}/estado/1`, anulados: tramites.anulados })
const abca = crearAbca({ trustValidator: requerida('TRUST_VALIDATOR_URL'), audiencia: URL_PUBLICA })
const emisor = crearEmisor({ url: URL_PUBLICA, cancilleria, firmante, tramites, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: cancilleria.nombre, proyecto: 'verifiers/cancilleria' }) })
// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: cancilleria.nombre, proyecto: 'verifiers/cancilleria' }))

// --- Sesiones y solicitudes al verifier (en memoria: es un ejemplo) -------------------------
const sesiones = new Map()
const solicitudes = new Map() // id → { proposito: 'identificacion' | 'verificacion', tipo?, transaccion, sesion, creada, final? }

function sesion(req, res) {
  const id = /(?:^|;\s*)cancilleria_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `cancilleria_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}
setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// Mayoría de edad: se pide solo `age_equal_or_over.18` del PID, no la fecha de nacimiento.
// Además del lugar de nacimiento (municipio y departamento) y el sexo, que también salen del PID.
const DATOS_PID = [
  'personal_administrative_number', 'given_name', 'family_name', 'birthdate', 'picture',
  ['place_of_birth', 'locality'], ['place_of_birth', 'region'], 'sex',
]
// El PID guarda el sexo según ISO/IEC 5218: 1 = hombre, 2 = mujer.
const SEXO_ISO5218 = { 1: 'Masculino', 2: 'Femenino' }

function completar(solicitud, r) {
  if (solicitud.proposito === 'verificacion') {
    const tipo = porVct(r.vct)
    return {
      estado: 'listo',
      documento: {
        tipo, nombre: DOCUMENTOS[tipo]?.nombre ?? r.vct, emisor: r.emisor, de_esta_cancilleria: r.emisor === URL_PUBLICA,
        emitido: r.emitido && new Date(r.emitido * 1000).toISOString(), vence: r.vence && new Date(r.vence * 1000).toISOString(),
        datos: Object.fromEntries((DOCUMENTOS[tipo]?.datos ?? []).filter(([k]) => k in r.datos).map(([k, n]) => [n, r.datos[k]])),
        sha256: r.datos.documento_sha256 ?? null,
      },
    }
  }
  // Identificación: los datos salen del PID verificado; nadie los escribe.
  const d = r.datos
  if (!d.personal_administrative_number || !d.given_name || !d.family_name) {
    return { estado: 'rechazada', motivo: 'La wallet no compartió los datos necesarios para identificarte.' }
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(d.birthdate ?? '') || typeof d.picture !== 'string' || !d.picture.startsWith('data:image/')) {
    return { estado: 'rechazada', motivo: 'El pasaporte necesita tu fecha de nacimiento y tu retrato: comparte esos datos de tu documento de identidad.' }
  }
  // Ciudad y departamento de nacimiento y sexo: también del PID, nadie los escribe.
  const ciudad = typeof d.locality === 'string' ? d.locality.trim() : ''
  const departamento = typeof d.region === 'string' ? d.region.trim() : ''
  const sexo = SEXO_ISO5218[Number(d.sex)]
  if (!ciudad || !sexo) {
    return { estado: 'rechazada', motivo: 'El pasaporte necesita tu ciudad de nacimiento y tu sexo: comparte esos datos de tu documento de identidad.' }
  }
  const s = sesiones.get(solicitud.sesion)
  if (s) {
    s.persona = {
      ciudad_nacimiento: ciudad, departamento_nacimiento: departamento || null,
      lugar_nacimiento: departamento ? `${ciudad}, ${departamento}` : ciudad, sexo,
      documento: String(d.personal_administrative_number), nombres: d.given_name, apellidos: d.family_name, fecha_nacimiento: d.birthdate,
      foto: typeof d.picture === 'string' && d.picture.startsWith('data:image/') ? d.picture : null,
      emisor_pid: r.emisor,
    }
  }
  console.log(`[identificación] ${d.personal_administrative_number} (${d.given_name} ${d.family_name}), PID de ${r.emisor}`)
  return { estado: 'listo' }
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) solicitud.final = { estado: 'rechazada', motivo: 'La solicitud venció. Inténtalo de nuevo.' }
    return solicitud.final ?? r
  }
  if (r.estado === 'error') {
    console.log(`[${solicitud.proposito}] rechazada por el verifier: ${r.motivo}${r.detalle ? ` (${JSON.stringify(r.detalle)})` : ''}`)
    solicitud.final = { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` }
  } else {
    solicitud.final = completar(solicitud, r)
  }
  return solicitud.final
}

// --- Validación de la solicitud ---------------------------------------------------------------
const texto = (v, min, max) => typeof v === 'string' && v.trim().length >= min && v.trim().length <= max
const esMenor = (nacimiento) => {
  const mayor = new Date(`${nacimiento}T00:00:00Z`)
  mayor.setUTCFullYear(mayor.getUTCFullYear() + 18)
  return mayor.toISOString().slice(0, 10) > new Date().toISOString().slice(0, 10)
}
// Número de pasaporte: dos letras y siete dígitos.
function numeroPasaporte() {
  const letras = Array.from({ length: 2 }, () => 'ABCDEFGHJKLMNPRSTUVWXYZ'[randomInt(23)]).join('')
  return `${letras}${String(randomInt(1000000, 9999999))}`
}
function validar(tipo, d = {}, persona) {
  if (tipo !== 'pasaporte') return 'Trámite desconocido.'
  // Lugar de nacimiento y sexo vienen del PID con el que se identificó (no del formulario).
  if (!persona.lugar_nacimiento || !persona.sexo) return 'Vuelve a identificarte con tu wallet: faltan tu lugar de nacimiento y tu sexo.'
  if (d.confirmo !== true) return 'Confirma que los datos son ciertos y que quieres el pasaporte.'
  const vence = new Date()
  vence.setUTCFullYear(vence.getUTCFullYear() + vigenciaAnios(esMenor(persona.fecha_nacimiento)))
  return { lugar_nacimiento: persona.lugar_nacimiento, sexo: persona.sexo, pasaporte_numero: numeroPasaporte(), fecha_vencimiento: vence.toISOString().slice(0, 10) }
}

async function oferta(tramite) {
  const o = emisor.ofrecer(tramite)
  return { ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) }
}

// --- Rutas ------------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: cancilleria.nombre, tema: {
  fondo: '#ffffff', panel: '#ffffff', texto: '#1d2733', suave: '#5b6675', borde: '#d9dde3', titulo: '#0b2f5b',
  marca: '#0b2f5b', marcaTexto: '#ffffff', acento: '#c9a227', cabeceraFondo: '#ffffff', cabeceraTexto: '#0b2f5b',
  ok: '#146c3a', okFondo: '#e6f4ea', mal: '#b3261e', malFondo: '#fdecea', neutroFondo: '#f3f4f6', hover: '#f3f4f6',
  fuente: 'Segoe UI, system-ui, -apple-system, Roboto, sans-serif', pesoTitulo: 700, mayusculas: 'none', tracking: 'normal', radio: '6px', logoAlto: 48,
  preFondo: '#0b2f5b', preTexto: '#e3ebf7', logo: `${BASE}/publico/logo.png`, subtitulo: 'Historial local',
  css: 'body::before{content:"";display:block;height:6px;background:linear-gradient(90deg,#fcd116 0 50%,#003893 50% 75%,#ce1126 75%)}.cab{border-bottom:1px solid #d9dde3}.cab nav a:hover{background:#f3f4f6}.franja{display:none}.marca small{text-transform:uppercase;letter-spacing:.1em;font-size:.72rem}',
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.set('trust proxy', true)

// El emisor (metadata en /.well-known/…/cancilleria, token, nonce, credencial, estado).
app.use(emisor.router)

const r = express.Router()
r.use(express.json({ limit: '50kb' }))
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{CANCILLERIA}}', cancilleria.nombre).replaceAll('{{FUNCIONARIO}}', cancilleria.funcionario)

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').send(pagina) })
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))

// Pedirle algo a la wallet a través del verifier: el PID para identificarse, o un
// documento cancillerial para comprobarlo.
r.post('/api/solicitud', async (req, res) => {
  const { proposito, tipo, dispositivo } = req.body ?? {}
  const s = sesion(req, res)
  let pedido
  if (proposito === 'identificacion') pedido = { vct: 'urn:eudi:pid:1', datos: DATOS_PID }
  else if (proposito === 'verificacion' && DOCUMENTOS[tipo]) pedido = { vct: DOCUMENTOS[tipo].vct, datos: DOCUMENTOS[tipo].datos.map(([k]) => k) }
  else return res.status(400).json({ error: 'proposito: identificacion, o verificacion con un tipo de documento' })
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      ...pedido,
      nonce: randomBytes(16).toString('base64url'),
      redireccion: dispositivo === 'mismo' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { proposito, tipo, transaccion, sesion: s.id, creada: Date.now() })
    s.pendiente = id
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 320 }) })
  } catch (e) {
    console.error(`[${proposito}] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})

r.get('/api/solicitud/:id', async (req, res) => {
  const solicitud = solicitudes.get(req.params.id)
  if (!solicitud || solicitud.sesion !== sesion(req, res).id) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  try {
    res.json(await consultar(solicitud))
  } catch (e) {
    console.error(`[${solicitud.proposito}] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

// A donde vuelve la wallet en el mismo teléfono.
r.get('/volver', async (req, res) => {
  const s = sesion(req, res)
  const solicitud = solicitudes.get(s.pendiente)
  const ir = (q) => res.redirect(`${BASE}/#${q}`)
  if (!solicitud || !req.query.response_code) return ir(`error=${encodeURIComponent('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')}`)
  try {
    const final = await consultar(solicitud, String(req.query.response_code))
    if (final.estado !== 'listo') return ir(`error=${encodeURIComponent(final.motivo ?? 'La wallet todavía no ha respondido.')}`)
    return ir(solicitud.proposito === 'verificacion' ? `verificado=${s.pendiente}` : 'identificado')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return ir(`error=${encodeURIComponent('El servicio de verificación no está disponible.')}`)
  }
})

r.get('/api/yo', (req, res) => {
  const p = sesion(req, res).persona
  if (!p) return res.status(401).json({ error: 'no te has identificado' })
  res.json(p)
})
r.post('/api/salir', (req, res) => {
  // Libera la identidad: se olvida a la persona y se descartan sus solicitudes en curso.
  const s = sesion(req, res)
  delete s.persona
  for (const [id, sol] of solicitudes) if (sol.sesion === s.id) solicitudes.delete(id)
  res.json({ ok: true })
})

const conPersona = (fn) => (req, res) => {
  const p = sesion(req, res).persona
  if (!p) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
  return fn(req, res, p)
}

r.post('/api/tramites', conPersona(async (req, res, persona) => {
  const { tipo, datos } = req.body ?? {}
  if (!DOCUMENTOS[tipo]) return res.status(400).json({ error: 'Trámite desconocido.' })
  const vigente = tramites.dePersona(persona.documento).find((t) => t.estado === 'vigente' && t.datos.fecha_vencimiento >= new Date().toISOString().slice(0, 10))
  if (vigente) return res.status(409).json({ error: `Ya tienes un pasaporte vigente (${vigente.datos.pasaporte_numero}, hasta ${vigente.datos.fecha_vencimiento}). Si lo perdiste, repórtalo como perdido o pide la reexpedición.` })
  const limpio = validar(tipo, datos, persona)
  if (typeof limpio === 'string') return res.status(400).json({ error: limpio })
  // El retrato queda en el protocolo: es el que va en el pasaporte y hay que reexpedirlo si se pierde.
  const tramite = tramites.crear({ tipo, persona, datos: limpio })
  console.log(`[solicitud] ${DOCUMENTOS[tipo].nombre} ${tramite.numero}`)
  res.status(201).json({ tramite, oferta: await oferta(tramite) })
}))

r.get('/api/tramites', conPersona((_req, res, persona) => res.json(tramites.dePersona(persona.documento).map(({ persona: p, ...t }) => ({ ...t, persona: { documento: p.documento } })))))

r.post('/api/tramites/:numero/oferta', conPersona(async (req, res, persona) => {
  let tr = tramites.buscar(req.params.numero)
  if (!tr || tr.persona.documento !== persona.documento) return res.status(404).json({ error: 'No es un trámite tuyo.' })
  if (tr.estado === 'anulado') return res.status(409).json({ error: 'Este trámite está anulado.' })
  if ((tr.entregas ?? 0) > 0) {
    // Ya está en una wallet: no se entrega otra copia (acabarías con documentos repetidos). Si se perdió, se reexpide y la anterior queda revocada.
    if (req.body?.reexpedir !== true) return res.status(409).json({ error: 'Este documento ya está en una wallet. Si la perdiste, pide la reexpedición: la copia anterior dejará de valer.', ya_entregado: true })
    tr = tramites.reexpedir(tr.numero)
    estado.invalidar()
    console.log(`[reexpedición] ${DOCUMENTOS[tr.tipo].nombre} ${tr.numero}: la copia anterior queda revocada`)
  }
  res.json({ oferta: await oferta(tr) })
}))

r.post('/api/tramites/:numero/anular', conPersona((req, res, persona) => {
  const t = tramites.buscar(req.params.numero)
  if (!t || t.persona.documento !== persona.documento) return res.status(404).json({ error: 'No es un trámite tuyo.' })
  const anulado = tramites.anular(t.numero, String(req.body?.motivo ?? '').slice(0, 300) || 'Reportado como perdido por su titular')
  estado.invalidar()
  console.log(`[anulación] ${anulado.numero} de ${persona.documento}`)
  res.json(anulado)
}))

app.use(BASE || '/', r)
const PUERTO = Number(process.env.PUERTO ?? 80)
app.listen(PUERTO, () => console.log(`${cancilleria.nombre} escuchando en :${PUERTO}${BASE} (emisor ${URL_PUBLICA}, verifier ${process.env.VERIFIER_API})`))

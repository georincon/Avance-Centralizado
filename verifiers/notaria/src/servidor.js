// Notaría de ejemplo: trámites notariales con la wallet, de punta a punta.
//
//   1. Identificarse      la wallet presenta el PID a SU verifier (verifier-notaria):
//                         documento, nombres, apellidos, nacimiento y foto. Hay que
//                         ser mayor de edad para otorgar estos actos.
//   2. Hacer el trámite   declaración extraproceso, poder especial o reconocimiento
//                         de un documento (de este solo se guarda su huella SHA-256:
//                         el archivo no sale del navegador).
//   3. Recibirlo          la notaría lo emite como documento de la wallet
//                         (OpenID4VCI, ver emisor.js): QR + PIN.
//   4. Mis trámites       volver a recibirlo, o anularlo (p. ej. revocar un poder).
//   5. Verificar          cualquiera puede pedirle a una wallet un documento
//                         notarial y ver si es auténtico y sigue vigente.
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA          p. ej. https://civica-desarrollo.avance.org.co/notaria (también es el credential_issuer)
//   VERIFIER_API         API de su verifier por la red interna: http://verifier-notaria/verifier-notaria
//   TRUST_VALIDATOR_URL  para comprobar las WIA de las wallets (abca.js)
//   NOTARIA_NOMBRE · NOTARIO_NOMBRE
//   SECRETOS_DIR         ds.key y ds.crt (scripts/generar-claves.sh)
//   TRAMITES_ARCHIVO     el protocolo (JSON)
//   PUERTO               80 (el nginx central llama a <prefijo>:80)

import { randomBytes, randomInt } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import { importPKCS8 } from 'jose'
import QRCode from 'qrcode'
import { crearAbca } from './abca.js'
import { DOCUMENTOS, porVct } from './documentos.js'
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
const notaria = { nombre: process.env.NOTARIA_NOMBRE ?? 'Notaría Avance', notario: process.env.NOTARIO_NOMBRE ?? 'Notario de ejemplo' }
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
const emisor = crearEmisor({ url: URL_PUBLICA, notaria, firmante, tramites, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: notaria.nombre, proyecto: 'verifiers/notaria' }) })
// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: notaria.nombre, proyecto: 'verifiers/notaria' }))

// --- Sesiones y solicitudes al verifier (en memoria: es un ejemplo) -------------------------
const sesiones = new Map()
const solicitudes = new Map() // id → { proposito: 'identificacion' | 'verificacion', tipo?, transaccion, sesion, creada, final? }

function sesion(req, res) {
  const id = /(?:^|;\s*)notaria_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `notaria_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}
setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// Mayoría de edad: se pide solo `age_equal_or_over.18` del PID, no la fecha de nacimiento.
const DATOS_PID = ['personal_administrative_number', 'given_name', 'family_name', ['age_equal_or_over', '18'], 'picture']

function completar(solicitud, r) {
  if (solicitud.proposito === 'verificacion') {
    const tipo = porVct(r.vct)
    return {
      estado: 'listo',
      documento: {
        tipo, nombre: DOCUMENTOS[tipo]?.nombre ?? r.vct, emisor: r.emisor, de_esta_notaria: r.emisor === URL_PUBLICA,
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
  if (d['18'] !== true) {
    return { estado: 'rechazada', motivo: 'Para otorgar estos actos ante notario tienes que ser mayor de edad.' }
  }
  const s = sesiones.get(solicitud.sesion)
  if (s) {
    s.persona = {
      documento: String(d.personal_administrative_number), nombres: d.given_name, apellidos: d.family_name,
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

// --- Validación de cada trámite -------------------------------------------------------------
const texto = (v, min, max) => typeof v === 'string' && v.trim().length >= min && v.trim().length <= max
function validar(tipo, d = {}) {
  switch (tipo) {
    case 'declaracion':
      if (!texto(d.finalidad, 3, 120)) return 'Indica con destino a quién o a qué entidad es la declaración.'
      if (!texto(d.declaracion, 20, 2000)) return 'La declaración debe tener entre 20 y 2000 caracteres.'
      return { finalidad: d.finalidad.trim(), declaracion: d.declaracion.trim() }
    case 'poder':
      if (!texto(d.apoderado_nombre, 5, 120)) return 'Escribe el nombre completo del apoderado.'
      if (!/^\d{5,12}$/.test(d.apoderado_documento ?? '')) return 'El documento del apoderado debe tener solo dígitos (5 a 12).'
      if (!texto(d.facultades, 10, 1500)) return 'Describe las facultades que otorgas (10 a 1500 caracteres).'
      if (d.vigente_hasta && !(/^\d{4}-\d{2}-\d{2}$/.test(d.vigente_hasta) && d.vigente_hasta > new Date().toISOString().slice(0, 10))) {
        return 'La vigencia debe ser una fecha futura.'
      }
      return { apoderado_nombre: d.apoderado_nombre.trim(), apoderado_documento: d.apoderado_documento, facultades: d.facultades.trim(), vigente_hasta: d.vigente_hasta || null }
    case 'reconocimiento':
      if (!texto(d.documento_nombre, 1, 200)) return 'Falta el nombre del documento.'
      if (!/^[0-9a-f]{64}$/.test(d.documento_sha256 ?? '')) return 'Falta la huella SHA-256 del documento.'
      if (!Number.isInteger(d.documento_bytes) || d.documento_bytes <= 0) return 'Falta el tamaño del documento.'
      return { documento_nombre: d.documento_nombre.trim(), documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes }
    case 'fe_vida':
      if (d.confirmo !== true) return 'Confirma que compareces personalmente y que estás con vida.'
      if (d.entidad_destino && !texto(d.entidad_destino, 3, 120)) return 'La entidad de destino debe tener entre 3 y 120 caracteres.'
      return { entidad_destino: d.entidad_destino?.trim() || null }
    case 'autenticacion':
      if (!texto(d.documento_nombre, 1, 200)) return 'Falta el nombre del documento.'
      if (!/^[0-9a-f]{64}$/.test(d.documento_sha256 ?? '')) return 'Falta la huella SHA-256 del documento.'
      if (!Number.isInteger(d.documento_bytes) || d.documento_bytes <= 0) return 'Falta el tamaño del documento.'
      if (d.confirmo !== true) return 'Confirma que la firma la pones tú, en este acto.'
      return { documento_nombre: d.documento_nombre.trim(), documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes }
    case 'copia_escritura': {
      if (!/^\d{1,6}$/.test(d.escritura_numero ?? '')) return 'El número de la escritura debe tener solo dígitos.'
      if (!/^\d{4}-\d{2}-\d{2}$/.test(d.escritura_fecha ?? '') || d.escritura_fecha > new Date().toISOString().slice(0, 10)) return 'La fecha de la escritura no puede ser futura.'
      if (!texto(d.notaria_origen, 3, 120)) return 'Indica la notaría donde se otorgó la escritura.'
      if (!texto(d.documento_nombre, 1, 200)) return 'Falta el nombre de la copia.'
      if (!/^[0-9a-f]{64}$/.test(d.documento_sha256 ?? '')) return 'Falta la huella SHA-256 de la copia.'
      if (!Number.isInteger(d.documento_bytes) || d.documento_bytes <= 0) return 'Falta el tamaño de la copia.'
      if (d.confirmo !== true) return 'Confirma que entiendes que es una constancia y no la escritura.'
      return { escritura_numero: d.escritura_numero, escritura_fecha: d.escritura_fecha, notaria_origen: d.notaria_origen.trim(), documento_nombre: d.documento_nombre.trim(), documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes }
    }
    case 'registro_civil': {
      const hoy = new Date().toISOString().slice(0, 10)
      if (!['Padre', 'Madre'].includes(d.calidad)) return 'Indica si declaras como padre o como madre.'
      if (!texto(d.inscrito_nombre, 5, 120)) return 'Escribe el nombre completo del hijo o hija.'
      if (!/^\d{4}-\d{2}-\d{2}$/.test(d.fecha_nacimiento ?? '') || d.fecha_nacimiento > hoy) return 'La fecha de nacimiento no puede ser futura.'
      if (!texto(d.lugar_nacimiento, 3, 100)) return 'Indica el lugar de nacimiento.'
      if (!['Femenino', 'Masculino'].includes(d.sexo)) return 'Indica el sexo.'
      if (d.otro_progenitor && !texto(d.otro_progenitor, 5, 120)) return 'El nombre del otro progenitor debe tener entre 5 y 120 caracteres.'
      if (d.confirmo !== true) return 'Debes declarar bajo juramento que los datos son ciertos.'
      // El serial lo asigna el sistema (en la vida real, la Registraduría): 10 dígitos.
      const serial = String(randomInt(1000000000, 9999999999))
      return { inscrito_nombre: d.inscrito_nombre.trim(), inscrito_documento: serial, fecha_nacimiento: d.fecha_nacimiento, lugar_nacimiento: d.lugar_nacimiento.trim(), sexo: d.sexo, calidad: d.calidad, otro_progenitor: d.otro_progenitor?.trim() || null }
    }
    case 'convivencia': {
      if (!texto(d.companero_nombre, 5, 120)) return 'Escribe el nombre completo de tu compañero(a).'
      if (!/^\d{5,12}$/.test(d.companero_documento ?? '')) return 'El documento del compañero(a) debe tener solo dígitos (5 a 12).'
      if (!/^\d{4}-\d{2}-\d{2}$/.test(d.convivencia_desde ?? '') || d.convivencia_desde > new Date().toISOString().slice(0, 10)) return 'La fecha de inicio de la convivencia no puede ser futura.'
      if (d.confirmo !== true) return 'Debes declarar bajo juramento que los datos son ciertos.'
      return { companero_nombre: d.companero_nombre.trim(), companero_documento: d.companero_documento, convivencia_desde: d.convivencia_desde }
    }
    case 'permiso_menor': {
      const hoy = new Date().toISOString().slice(0, 10)
      const fecha = (v) => /^\d{4}-\d{2}-\d{2}$/.test(v ?? '')
      if (!['Padre', 'Madre', 'Representante legal'].includes(d.calidad)) return 'Indica en qué calidad otorgas el permiso.'
      if (!texto(d.menor_nombre, 5, 120)) return 'Escribe el nombre completo del menor.'
      if (!['Registro civil', 'Tarjeta de identidad'].includes(d.menor_tipo_documento)) return 'Indica el tipo de documento del menor.'
      if (!/^\d{5,12}$/.test(d.menor_documento ?? '')) return 'El documento del menor debe tener solo dígitos (5 a 12).'
      if (!texto(d.destino, 2, 80)) return 'Indica el país o ciudad de destino.'
      if (!fecha(d.fecha_salida) || d.fecha_salida < hoy) return 'La fecha de salida debe ser hoy o futura.'
      if (!fecha(d.fecha_regreso) || d.fecha_regreso < d.fecha_salida) return 'La fecha de regreso no puede ser anterior a la salida.'
      if ((new Date(d.fecha_regreso) - new Date(d.fecha_salida)) / 86400000 > 365) return 'El permiso no puede cubrir más de un año.'
      if (d.acompanante && !texto(d.acompanante, 5, 120)) return 'Escribe el nombre completo de quien acompaña al menor, o déjalo vacío si viaja solo.'
      if (!['Autoriza el otro progenitor', 'Único titular de la patria potestad'].includes(d.otro_progenitor)) return 'Indica la situación del otro progenitor.'
      if (d.confirmo !== true) return 'Debes declarar bajo juramento que los datos son ciertos.'
      return {
        calidad: d.calidad, menor_nombre: d.menor_nombre.trim(), menor_tipo_documento: d.menor_tipo_documento, menor_documento: d.menor_documento,
        destino: d.destino.trim(), fecha_salida: d.fecha_salida, fecha_regreso: d.fecha_regreso,
        acompanante: d.acompanante?.trim() || null, otro_progenitor: d.otro_progenitor,
      }
    }
    default:
      return 'Trámite desconocido.'
  }
}

async function oferta(tramite) {
  const o = emisor.ofrecer(tramite)
  return { ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) }
}

// --- Rutas ------------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, {
  base: BASE, nombre: notaria.nombre,
  tema: {
    fondo: '#eeeeee', panel: '#ffffff', texto: '#1b2540', suave: '#566078', borde: '#cdd6ea', titulo: '#2d447c',
    marca: '#2d447c', marcaTexto: '#ffffff', acento: '#ecc366', cabeceraFondo: '#ffffff', cabeceraTexto: '#2d447c',
    ok: '#1e7a4c', okFondo: '#e8f5ee', mal: '#b42318', malFondo: '#fef3f2', neutroFondo: '#eef1f8', hover: '#f3f6fd',
    fuente: "'Nunito Sans',system-ui,-apple-system,'Segoe UI',Roboto,sans-serif", pesoTitulo: 800, radio: '6px', logoAlto: 58,
    preFondo: '#1d3073', preTexto: '#dfe7ff', logo: `${BASE}/publico/signo.png`, subtitulo: 'Historial local',
    fuentes: `@font-face{font-family:'Nunito Sans';font-weight:400 800;font-display:swap;src:url('${BASE}/publico/fuentes/nunito-sans-latin.woff2') format('woff2')}@font-face{font-family:'Nunito Sans';font-weight:400 800;font-display:swap;src:url('${BASE}/publico/fuentes/nunito-sans-latin-ext.woff2') format('woff2')}`,
  },
})   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.set('trust proxy', true)

// El emisor (metadata en /.well-known/…/notaria, token, nonce, credencial, estado).
app.use(emisor.router)

const r = express.Router()
r.use(express.json({ limit: '50kb' }))
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{NOTARIA}}', notaria.nombre).replaceAll('{{NOTARIO}}', notaria.notario)

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').send(pagina) })
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))

// Pedirle algo a la wallet a través del verifier: el PID para identificarse, o un
// documento notarial para comprobarlo.
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
r.post('/api/salir', (req, res) => { delete sesion(req, res).persona; res.json({ ok: true }) })

const conPersona = (fn) => (req, res) => {
  const p = sesion(req, res).persona
  if (!p) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
  return fn(req, res, p)
}

r.post('/api/tramites', conPersona(async (req, res, persona) => {
  const { tipo, datos } = req.body ?? {}
  if (!DOCUMENTOS[tipo]) return res.status(400).json({ error: 'Trámite desconocido.' })
  const limpio = validar(tipo, datos)
  if (typeof limpio === 'string') return res.status(400).json({ error: limpio })
  const { foto, ...sinFoto } = persona
  const tramite = tramites.crear({ tipo, persona: sinFoto, datos: limpio })
  console.log(`[trámite] ${DOCUMENTOS[tipo].nombre} ${tramite.numero} de ${persona.documento}`)
  res.status(201).json({ tramite, oferta: await oferta(tramite) })
}))

r.get('/api/tramites', conPersona((_req, res, persona) => res.json(tramites.dePersona(persona.documento))))

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
  const anulado = tramites.anular(t.numero, String(req.body?.motivo ?? '').slice(0, 300) || 'Por voluntad de quien lo otorgó')
  estado.invalidar()
  console.log(`[anulación] ${anulado.numero} de ${persona.documento}`)
  res.json(anulado)
}))

app.use(BASE || '/', r)
const PUERTO = Number(process.env.PUERTO ?? 80)
app.listen(PUERTO, () => console.log(`${notaria.nombre} escuchando en :${PUERTO}${BASE} (emisor ${URL_PUBLICA}, verifier ${process.env.VERIFIER_API})`))

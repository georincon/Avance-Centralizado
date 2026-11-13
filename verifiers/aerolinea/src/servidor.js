// Aerolínea de ejemplo (Páramo Air): vende pasajes, identifica al pasajero con su PID, emite el tiquete a la wallet y lo
// verifica en el embarque. Muestra las dos caras del ecosistema en un mismo servicio:
//
//   Verifier   Antes de vender, la persona se identifica presentando su PID desde la wallet: nombre, documento, fecha de
//              nacimiento (para clasificarla como adulto, niño o infante) y foto, que son los datos de seguridad que una
//              aerolínea necesita porque el tiquete es nominal e intransferible. En el embarque se pide el tiquete y el PID
//              juntos (dos credenciales en un solo código) y se comprueba que son de la misma persona.
//   Emisor     El tiquete se entrega como un documento de la wallet (SD-JWT VC por OpenID4VCI), firmado por la aerolínea,
//              registrado en la lista QEAA y anulable con su status list (cancelar el tiquete lo revoca).
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA          https://civica-desarrollo.avance.org.co/aerolinea (también es el credential_issuer)
//   VERIFIER_API         http://verifier-aerolinea/verifier-aerolinea (red interna)
//   TRUST_VALIDATOR_URL  a quién pregunta si la WIA de una wallet es de un wallet-provider de confianza
//   AEROLINEA_NOMBRE     el nombre que se muestra
//   AEROLINEA_CODIGO     código de los números de vuelo
//   SECRETOS_DIR         ds.crt y ds.key (certificado y clave de firma)
//   TRAMITES_ARCHIVO     el archivo de reservas (datos/tramites.json)
//   PUERTO               80 (el nginx central llama a <prefijo>:80)

import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import { importPKCS8 } from 'jose'
import QRCode from 'qrcode'
import { crearAbca } from './abca.js'
import { DOCUMENTOS } from './documentos.js'
import { crearEmisor } from './emisor.js'
import { crearEstado } from './estado.js'
import { iniciarAlmacen } from './almacen.js'
import { crearTramites } from './tramites.js'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'
import { CIUDADES, INTERNACIONAL, OFERTAS, TARIFAS, buscar, categoria, cop, precio } from './vuelos.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const SECRETOS = process.env.SECRETOS_DIR ?? '/secretos'
const CODIGO = process.env.AEROLINEA_CODIGO ?? 'PMO'
const aerolinea = { nombre: process.env.AEROLINEA_NOMBRE ?? 'Páramo Air', codigo: CODIGO }
const VENCE_SOLICITUD_MS = 5 * 60 * 1000
const VCT_PID = 'urn:eudi:pid:1'
const VCT_TIQUETE = DOCUMENTOS.tiquete.vct
const VCT_PERMISO = 'urn:co:notaria:permiso-salida-menor:1'
// El permiso solo vale si lo emitió una notaría en la que esta aerolínea confía: la del ecosistema (mismo dominio, prefijo /notaria).
const EMISOR_PERMISO = `${new URL(URL_PUBLICA).origin}/notaria`

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
const emisor = crearEmisor({ url: URL_PUBLICA, aerolinea, firmante, tramites, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: aerolinea.nombre, proyecto: 'verifiers/aerolinea' }) })
// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: aerolinea.nombre, proyecto: 'verifiers/aerolinea' }))

// --- Sesiones y solicitudes al verifier (en memoria: es un ejemplo) -------------------------
const sesiones = new Map()
const solicitudes = new Map() // id → { proposito: 'identificacion' | 'embarque', transaccion, sesion, creada, demo?, final? }

function sesion(req, res) {
  const id = /(?:^|;\s*)aerolinea_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `aerolinea_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}
setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// Qué se le pide a la wallet. La foto va como data URI; solo se acepta una imagen real (se pone en un <img>).
const FOTO = /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/
// PEDIR_CONTACTO=false deja de pedir el correo y el celular del PID (el formulario vuelve a ser manual).
const CONTACTO_OPCIONAL = process.env.PEDIR_CONTACTO === 'false' ? [] : ['email', 'phone_number']
const PEDIDOS = {
  // Datos de seguridad del pasajero: nombre, documento, fecha de nacimiento (categoría y edad) y foto.
  // El correo y el celular del PID son OPCIONALES: si la wallet los tiene se comparten y rellenan el contacto de la compra; si no, se escriben a mano.
  identificacion: () => [{ id: 'pid', vct: VCT_PID, datos: ['personal_administrative_number', 'given_name', 'family_name', 'birthdate', 'picture'], opcionales: CONTACTO_OPCIONAL }],
  // Embarque: el tiquete y el documento de identidad de quien lo presenta, juntos.
  embarque: () => [
    { id: 'tiquete', vct: VCT_TIQUETE, multiple: true, datos: ['reserva', 'numero_tiquete', 'pasajero_nombre', 'pasajero_documento', 'pasajero_menor_de_edad', 'responsable_nombre', 'responsable_documento', 'vuelo', 'origen', 'destino', 'fecha_salida', 'hora_salida', 'tarifa', 'categoria_pasajero'] },
    { id: 'pid', vct: VCT_PID, datos: ['personal_administrative_number', 'given_name', 'family_name', 'picture'] },
  ],
  // Segundo paso del embarque de un menor en un vuelo internacional: el permiso de salida del país que le dio una notaría a su responsable.
  permiso: () => [
    { id: 'permiso', vct: VCT_PERMISO, multiple: true, datos: ['otorgante_nombre', 'otorgante_documento', 'calidad', 'menor_nombre', 'menor_documento', 'destino', 'fecha_salida', 'fecha_regreso', 'acompanante', 'otro_progenitor', 'numero', 'fecha'] },
  ],
}
const normal = (t) => String(t ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toUpperCase().replace(/\s+/g, ' ').trim()
const digitos = (t) => String(t ?? '').replace(/\D/g, '')
// Código IATA entre paréntesis («Quito (UIO)» → UIO) y su país.
const codigoDe = (texto) => /\((\w{3})\)/.exec(texto ?? '')?.[1]
const esInternacional = (origen, destino) => Boolean(CIUDADES[codigoDe(origen)] && CIUDADES[codigoDe(destino)] && INTERNACIONAL(codigoDe(origen), codigoDe(destino)))

const PAIS = { CO: 'COLOMBIA', EC: 'ECUADOR', PE: 'PERU' }

// Marca un tiquete como usado (abordó): su bit en la status list pasa a 1 y ya no se puede volver a presentar.
function abordar(numeroTramite) {
  const u = tramites.usar(numeroTramite)
  estado.invalidar()
  return u?.estado === 'usado'
}

function completar(solicitud, r) {
  // Segundo paso del embarque de menores en vuelos internacionales: los permisos de salida del país (uno por menor), cruzados con
  // los tiquetes que quedaron pendientes en el primer paso.
  if (solicitud.proposito === 'permiso') {
    const c = solicitudes.get(solicitud.previa)?.contexto
    if (!c?.pendientes?.length) return { estado: 'rechazada', motivo: 'Primero hay que verificar el tiquete.' }
    const permisos = (r.todas.permiso ?? []).filter((p) => p.vct === VCT_PERMISO)
    if (!permisos.length) return { estado: 'rechazada', motivo: 'Falta el permiso de salida del país.' }
    const resultados = c.pendientes.map(({ numero, q }) => {
      const per = permisos.find((p) => digitos(p.datos.menor_documento) !== '' && digitos(p.datos.menor_documento) === digitos(q.pasajero_documento))
      if (!per) {
        return { numero, tiquete: q.numero_tiquete, viajero: { nombre: q.pasajero_nombre, documento: q.pasajero_documento }, ok: false, abordo: false, datos: null,
          criterios: [{ nombre: 'Se presentó el permiso de este menor', cumple: false, detalle: `No hay un permiso para ${q.pasajero_nombre} (${q.pasajero_documento}) entre los que se presentaron.` }] }
      }
      const x = per.datos
      const ciudad = normal(String(q.destino).replace(/\s*\(.*\)/, ''))
      const pais = PAIS[CIUDADES[codigoDe(q.destino)]?.pais] ?? ''
      const destinoPermiso = normal(x.destino)
      const criterios = [
        { nombre: 'Permiso emitido por una notaría de confianza', cumple: per.emisor === EMISOR_PERMISO, detalle: per.emisor === EMISOR_PERMISO ? 'Firmado por la notaría y vigente (no revocado).' : `Lo emitió otra entidad (${per.emisor}).` },
        { nombre: 'Lo otorgó el adulto que se identificó', cumple: digitos(x.otorgante_documento) !== '' && digitos(x.otorgante_documento) === digitos(c.responsableDocumento), detalle: `Otorgante: ${x.otorgante_nombre} (documento ${x.otorgante_documento}).` },
        { nombre: 'Es el permiso del menor del tiquete', cumple: true, detalle: `Menor del permiso: ${x.menor_nombre} (${x.menor_documento}). Pasajero del tiquete: ${q.pasajero_nombre} (${q.pasajero_documento}).` },
        { nombre: 'El destino coincide', cumple: destinoPermiso !== '' && (destinoPermiso.includes(ciudad) || ciudad.includes(destinoPermiso) || (pais && destinoPermiso.includes(pais))), detalle: `Permiso: ${x.destino}. Vuelo: ${q.destino}.` },
        { nombre: 'La fecha del vuelo está dentro del viaje autorizado', cumple: q.fecha_salida >= x.fecha_salida && q.fecha_salida <= x.fecha_regreso, detalle: `Viaje autorizado del ${x.fecha_salida} al ${x.fecha_regreso}. Vuelo el ${q.fecha_salida}.` },
      ]
      const ok = criterios.every((k) => k.cumple)
      const abordo = ok && abordar(numero)
      return { numero, tiquete: q.numero_tiquete, viajero: { nombre: q.pasajero_nombre, documento: q.pasajero_documento }, ok, abordo, criterios,
        datos: { numero: x.numero, fecha: x.fecha, menor: x.menor_nombre, otorgante: x.otorgante_nombre, calidad: x.calidad, acompanante: x.acompanante, otro_progenitor: x.otro_progenitor } }
    })
    console.log(`[permiso] ${resultados.filter((x) => x.abordo).length}/${resultados.length} abordaron`)
    return { estado: 'listo', permiso: { ok: resultados.every((x) => x.ok), resultados } }
  }

  const pid = r.credenciales.pid
  if (!pid || pid.vct !== VCT_PID) return { estado: 'rechazada', motivo: 'Falta el documento de identidad digital (PID).' }
  const d = pid.datos
  if (!d.personal_administrative_number || !d.given_name || !d.family_name) {
    return { estado: 'rechazada', motivo: 'La wallet no compartió los datos necesarios para identificarte.' }
  }
  const foto = typeof d.picture === 'string' && FOTO.test(d.picture) ? d.picture : null

  if (solicitud.proposito === 'identificacion') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(d.birthdate ?? '')) return { estado: 'rechazada', motivo: 'La wallet no compartió la fecha de nacimiento, que la aerolínea necesita para clasificar al pasajero.' }
    const s = sesiones.get(solicitud.sesion)
    if (s) {
      s.persona = {
        documento: String(d.personal_administrative_number), nombres: d.given_name, apellidos: d.family_name,
        fecha_nacimiento: d.birthdate, foto, emisor_pid: pid.emisor,
        // Contacto que la wallet compartió desde el PID (la Registraduría lo firma); vacío si el PID no lo trae.
        contacto: { email: d.email ? String(d.email) : null, telefono: d.phone_number ? String(d.phone_number) : null },
      }
    }
    console.log(`[identificación] pasajero verificado (contacto de la wallet: correo ${d.email ? 'sí' : 'no'}, celular ${d.phone_number ? 'sí' : 'no'})`)
    return { estado: 'listo' }
  }

  // Embarque: una persona se identifica y presenta UNO O VARIOS tiquetes (el suyo y los de los menores a su cargo). Cada tiquete tiene que
  // ser de esta aerolínea, estar vigente (lo comprueba el verifier con la status list: un tiquete ya usado o cancelado no pasa) y tener
  // como responsable a quien se identifica. Los que cumplen y no necesitan permiso abordan en el acto: pasan a «usado».
  const tiquetes = (r.todas.tiquete ?? []).filter((x) => x.vct === VCT_TIQUETE)
  if (!tiquetes.length) return { estado: 'rechazada', motivo: 'Falta el tiquete.' }
  const hoy = new Date().toISOString().slice(0, 10)
  const nombrePid = `${d.given_name} ${d.family_name}`
  const pendientes = []
  const resultados = tiquetes.map((tq) => {
    const q = tq.datos
    const menor = q.pasajero_menor_de_edad === 'Sí'
    const requierePermiso = menor && esInternacional(q.origen, q.destino)
    const quien = menor ? 'el adulto responsable del menor' : 'el pasajero'
    const registro = tramites.buscarPorTiquete(q.numero_tiquete)
    const criterios = [
      { nombre: 'Tiquete emitido por esta aerolínea', cumple: tq.emisor === URL_PUBLICA, detalle: tq.emisor === URL_PUBLICA ? 'Firmado por la aerolínea.' : `Lo emitió otra entidad (${tq.emisor}).` },
      // El verifier ya rechaza un tiquete revocado, pero el control de uso es de la aerolínea: un tiquete que ya abordó no vuelve a pasar.
      { nombre: 'El tiquete está vigente (no cancelado ni usado)', cumple: registro?.estado === 'vigente', detalle: registro ? (registro.estado === 'vigente' ? 'Sin usar.' : registro.estado === 'usado' ? 'Ya se usó: el pasajero abordó.' : 'Está cancelado.') : 'No figura en el sistema de la aerolínea.' },
      { nombre: `Quien se identifica es ${quien}`, cumple: String(q.responsable_documento) === String(d.personal_administrative_number), detalle: `Responsable del tiquete: documento ${q.responsable_documento}. Documento presentado: ${d.personal_administrative_number}.` },
      { nombre: 'El nombre coincide', cumple: normal(q.responsable_nombre) === normal(nombrePid), detalle: `Tiquete: ${q.responsable_nombre}. Documento: ${nombrePid}.` },
      { nombre: solicitud.demo ? 'Fecha del vuelo (modo demostración: no se exige)' : 'El vuelo es hoy', cumple: solicitud.demo || q.fecha_salida === hoy, detalle: q.fecha_salida === hoy ? 'El vuelo sale hoy.' : `El vuelo sale el ${q.fecha_salida}.` },
    ]
    const base = criterios.every((c) => c.cumple)
    let abordo = false
    if (base && registro && !requierePermiso) abordo = abordar(registro.numero)
    if (base && registro && requierePermiso) pendientes.push({ numero: registro.numero, q })
    return {
      numero: q.numero_tiquete, reserva: q.reserva, vuelo: q.vuelo, origen: q.origen, destino: q.destino, fecha: q.fecha_salida, hora: q.hora_salida,
      tarifa: q.tarifa, categoria: q.categoria_pasajero, viajero: { nombre: q.pasajero_nombre, documento: q.pasajero_documento, menor },
      criterios, ok: base, requierePermiso, abordo,
    }
  })
  solicitud.contexto = { responsableDocumento: String(d.personal_administrative_number), pendientes }
  console.log(`[embarque] ${resultados.filter((x) => x.abordo).length}/${resultados.length} abordaron, ${pendientes.length} esperan permiso`)
  return {
    estado: 'listo',
    embarque: {
      pasajero: { nombre: nombrePid, documento: String(d.personal_administrative_number), foto },
      tiquetes: resultados,
      pendientePermiso: pendientes.length,
    },
  }
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) solicitud.final = { estado: 'rechazada', motivo: 'La solicitud venció. Inténtalo de nuevo.' }
    return solicitud.final ?? r
  }
  if (r.estado === 'error') {
    console.log(`[${solicitud.proposito}] rechazada por el verifier: ${r.motivo}`)
    const usado = solicitud.proposito === 'embarque' && /revocada|anulado/i.test(r.motivo) ? ' Uno de los tiquetes presentados ya se usó o fue cancelado.' : ''
    solicitud.final = { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.${usado}` }
  } else {
    solicitud.final = completar(solicitud, r)
  }
  return solicitud.final
}

// --- Compra --------------------------------------------------------------------------------
const HOY = () => new Date().toISOString().slice(0, 10)
const codigoReserva = () => Array.from(randomBytes(6), (b) => 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'[b % 32]).join('')
const textoCiudad = (c) => `${CIUDADES[c].nombre} (${c})`

function cotizar(nacimiento, { origen, destino, fecha, salida, tarifa }) {
  if (!CIUDADES[origen] || !CIUDADES[destino] || origen === destino) return 'Elige un origen y un destino distintos.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha ?? '') || fecha < HOY()) return 'La fecha del vuelo debe ser hoy o futura.'
  if ((new Date(fecha) - new Date(HOY())) / 86400000 > 330) return 'Los vuelos se venden con hasta 330 días de anticipación.'
  if (!TARIFAS[tarifa]) return 'Elige una tarifa.'
  const vuelo = buscar({ origen, destino, fecha, codigo: CODIGO }).find((v) => v.salida === salida)
  if (!vuelo) return 'Ese vuelo no existe.'
  const cat = categoria(nacimiento, fecha)
  if (!cat) return 'La fecha de nacimiento del pasajero no es válida.'
  return { vuelo, cat, valores: precio(vuelo, tarifa, cat) }
}

// --- Rutas ------------------------------------------------------------------------------------
const app = express()
// Versión del servidor: la página la compara en cada respuesta y, si cambió (p. ej. tras una actualización), se recarga sola para no
// quedarse con una versión vieja abierta en el navegador.
const VERSION = String(Date.now())
app.use((_req, res, next) => { res.set('X-Version', VERSION); next() })
montarPanelAuditoria(app, {
  base: BASE, nombre: aerolinea.nombre,
  tema: {
    fondo: '#f7f5f2', panel: '#ffffff', texto: '#1f1f1f', suave: '#5f5f63', borde: '#e4e0da', titulo: '#1f1f1f',
    marca: '#dc0b17', marcaTexto: '#ffffff', acento: '#dc0b17', cabeceraFondo: '#ffffff', cabeceraTexto: '#dc0b17',
    ok: '#12703d', okFondo: '#e6f4ec', mal: '#b3261e', malFondo: '#fdecea', neutroFondo: '#f1eee9', hover: '#faf8f5',
    fuente: "'Helvetica Neue',Helvetica,Arial,'Segoe UI',sans-serif", pesoTitulo: 800, radio: '4px', logoAlto: 40,
    preFondo: '#14161d', preTexto: '#e4e4ea', logo: `${BASE}/publico/logo.png`, subtitulo: 'Historial local',
  },
})   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.set('trust proxy', true)

// El emisor (metadata en /.well-known/…/aerolinea, token, nonce, credencial, estado).
app.use(emisor.router)

const r = express.Router()
r.use(express.json({ limit: '20kb' }))
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{AEROLINEA}}', aerolinea.nombre).replaceAll('{{CODIGO}}', CODIGO)

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').set('Cache-Control', 'no-store').send(pagina) })
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))

r.get('/api/ofertas', (_req, res) => res.json({ ofertas: OFERTAS(CIUDADES) }))
r.get('/api/destinos', (_req, res) => res.json({ ciudades: Object.entries(CIUDADES).map(([codigo, c]) => ({ codigo, ...c })), hoy: HOY() }))

// Vuelos de un día. Con el pasajero ya identificado, los precios son los suyos (según su categoría); si no, de adulto.
r.get('/api/vuelos', (req, res) => {
  const { origen, destino, fecha } = req.query
  if (!CIUDADES[origen] || !CIUDADES[destino] || origen === destino) return res.status(400).json({ error: 'Elige un origen y un destino distintos.' })
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha ?? '') || fecha < HOY()) return res.status(400).json({ error: 'La fecha debe ser hoy o futura.' })
  const p = sesion(req, res).persona
  // Con `nacimiento` el precio es el de un menor a cargo del comprador; si no, el del propio comprador (o de adulto si aún no se identificó).
  const nac = /^\d{4}-\d{2}-\d{2}$/.test(req.query.nacimiento ?? '') ? req.query.nacimiento : p?.fecha_nacimiento
  const cat = (nac && categoria(nac, fecha)) || { nombre: 'Adulto', factor: 1, edad: null }
  const vuelos = buscar({ origen, destino, fecha, codigo: CODIGO }).map((v) => ({
    ...v,
    tarifas: Object.fromEntries(Object.keys(TARIFAS).map((t) => [t, { ...precio(v, t, cat), nombre: TARIFAS[t].nombre, equipaje: TARIFAS[t].equipaje, cambios: TARIFAS[t].cambios }])),
  }))
  res.json({ vuelos, categoria: cat.nombre, internacional: INTERNACIONAL(origen, destino), identificado: Boolean(p) })
})

// Pedirle algo a la wallet a través del verifier: el PID para identificarse, o el tiquete y el PID en el embarque.
r.post('/api/solicitud', async (req, res) => {
  const { proposito, demo, previa, dispositivo } = req.body ?? {}
  const mismo = dispositivo === 'mismo'
  const s = sesion(req, res)
  if (!PEDIDOS[proposito]) return res.status(400).json({ error: 'proposito: identificacion, embarque o permiso' })
  if (proposito === 'permiso') {
    const anterior = solicitudes.get(previa)
    if (anterior?.sesion !== s.id || !anterior.contexto?.pendientes?.length) return res.status(400).json({ error: 'Primero hay que verificar el tiquete de un menor en un vuelo internacional.' })
  }
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({ credenciales: PEDIDOS[proposito](), nonce: randomBytes(16).toString('base64url'),
      // En el mismo teléfono la wallet vuelve a /volver al terminar.
      redireccion: mismo ? `${URL_PUBLICA}/volver` : undefined })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { proposito, transaccion, sesion: s.id, creada: Date.now(), demo: proposito === 'embarque' && demo === true, previa: proposito === 'permiso' ? previa : undefined, mismo })
    if (mismo) s.pendiente = id
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 340 }), vence_en: VENCE_SOLICITUD_MS / 1000 })
  } catch (e) {
    console.error(`[${proposito}] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})

r.get('/api/solicitud/:id', async (req, res) => {
  const solicitud = solicitudes.get(req.params.id)
  if (!solicitud || solicitud.sesion !== sesion(req, res).id) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  try {
    // Una solicitud del mismo teléfono solo se resuelve en /volver, con el response_code de la wallet.
    if (solicitud.mismo && !solicitud.final) return res.json({ estado: 'esperando' })
    res.json(await consultar(solicitud))
  } catch (e) {
    console.error(`[${solicitud.proposito}] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

// A donde vuelve la wallet en el mismo teléfono, con el código que libera el resultado. Deja a la persona en la misma
// página, que retoma el paso donde estaba (?vuelta=<id>) leyendo el resultado de la sesión.
r.get('/volver', async (req, res) => {
  const s = sesion(req, res)
  const solicitud = solicitudes.get(s.pendiente)
  const fallo = (motivo) => res.redirect(`${BASE}/?error=${encodeURIComponent(motivo)}`)
  const codigo = typeof req.query.response_code === 'string' ? req.query.response_code : ''
  if (!solicitud || solicitud.sesion !== s.id || !solicitud.mismo || !codigo) return fallo('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')
  try {
    if (solicitud.final && solicitud.codigo !== codigo) return fallo('Código de respuesta inválido.')
    const final = await consultar(solicitud, codigo)
    if (final.estado === 'listo') { solicitud.codigo = codigo; return res.redirect(`${BASE}/?vuelta=${encodeURIComponent(s.pendiente)}`) }
    return fallo(final.motivo ?? 'La wallet todavía no ha respondido.')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return fallo('El servicio de verificación no está disponible.')
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

async function oferta(reserva) {
  const o = emisor.ofrecer(reserva)
  return { ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) }
}

r.post('/api/comprar', conPersona(async (req, res, persona) => {
  const { origen, destino, fecha, salida, tarifa, email, telefono } = req.body ?? {}
  if (!/^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/.test(email ?? '')) return res.status(400).json({ error: 'Escribe un correo válido: ahí te llegará la confirmación.' })
  if (!/^\+?\d{7,15}$/.test(String(telefono ?? '').replace(/[\s-]/g, ''))) return res.status(400).json({ error: 'Escribe un teléfono de contacto válido (7 a 15 dígitos).' })

  // Una compra puede llevar varios pasajeros: el propio comprador (sus datos vienen del PID, verificados) y menores a su cargo (los
  // escribe el comprador, que responde por ellos: un menor no tiene un documento verificable en la wallet). Cada uno tiene su tiquete.
  const lista = Array.isArray(req.body?.pasajeros) ? req.body.pasajeros : [{ tipo: 'yo' }]
  if (lista.length < 1 || lista.length > 4) return res.status(400).json({ error: 'Una compra admite de 1 a 4 pasajeros.' })
  if (lista.filter((p) => p?.tipo === 'yo').length > 1) return res.status(400).json({ error: 'Tú solo puedes aparecer una vez en la compra.' })

  // Primero se valida todo (nadie queda con medio pedido) y luego se crean los tiquetes.
  const preparados = []
  for (const [i, p] of lista.entries()) {
    const quien = `Pasajero ${i + 1}`
    let nacimiento = persona.fecha_nacimiento
    let datosPasajero = {}
    if (p?.tipo === 'menor') {
      const n = String(p.nombre ?? '').trim()
      if (n.length < 5 || n.length > 120) return res.status(400).json({ error: `${quien}: escribe el nombre completo del menor.` })
      if (!['Registro civil', 'Tarjeta de identidad'].includes(p.tipo_documento)) return res.status(400).json({ error: `${quien}: indica el tipo de documento del menor.` })
      if (!/^\d{5,12}$/.test(p.documento ?? '')) return res.status(400).json({ error: `${quien}: el documento del menor debe tener solo dígitos (5 a 12).` })
      if (!/^\d{4}-\d{2}-\d{2}$/.test(p.nacimiento ?? '')) return res.status(400).json({ error: `${quien}: indica la fecha de nacimiento del menor.` })
      nacimiento = p.nacimiento
      const edad = categoria(nacimiento, fecha ?? HOY())?.edad
      if (edad === undefined || edad === null || edad >= 18) return res.status(400).json({ error: `${quien}: debe ser menor de 18 años a la fecha del vuelo.` })
      datosPasajero = { pasajero_nombre: n, pasajero_documento: `${p.tipo_documento} ${p.documento}`, pasajero_nacimiento: nacimiento }
    } else if (p?.tipo !== 'yo') {
      return res.status(400).json({ error: `${quien}: tipo de pasajero desconocido.` })
    }
    const c = cotizar(nacimiento, { origen, destino, fecha, salida, tarifa })
    if (typeof c === 'string') return res.status(400).json({ error: c })
    preparados.push({ c, datosPasajero, aCargo: p.tipo === 'menor' })
  }

  const { foto, ...sinFoto } = persona
  const reservas = []
  const codigo = codigoReserva() // una reserva (PNR) para toda la compra; cada pasajero tiene su tiquete
  for (const { c, datosPasajero, aCargo } of preparados) {
    const reserva = tramites.crear({
      tipo: 'tiquete', persona: sinFoto,
      datos: {
        reserva: codigo, vuelo: c.vuelo.vuelo, origen, destino, origen_texto: textoCiudad(origen), destino_texto: textoCiudad(destino),
        fecha, salida, llegada: c.vuelo.llegada, tarifa, tarifa_nombre: TARIFAS[tarifa].nombre, equipaje: TARIFAS[tarifa].equipaje,
        categoria: c.cat.nombre, menor: c.cat.edad < 18, ...datosPasajero, valor_pagado: cop(c.valores.total), internacional: c.vuelo.internacional,
        contacto: { email: email.trim(), telefono: String(telefono).trim() },
      },
    })
    // El número de tiquete (13 dígitos, como los de una aerolínea) sale del consecutivo.
    reserva.datos.numero_tiquete = `957${String(reserva.indice_estado).padStart(10, '0')}`
    tramites.actualizar(reserva)
    console.log(`[compra] ${reserva.datos.vuelo} ${origen}-${destino} ${fecha} (${c.cat.nombre}${aCargo ? ', menor a cargo' : ''})`)
    reservas.push({ reserva: vistaReserva(reserva), valores: c.valores, oferta: await oferta(reserva) })
  }
  res.status(201).json({ reservas, total: reservas.reduce((s, x) => s + x.valores.total, 0) })
}))

function vistaReserva(t) {
  const d = t.datos
  return {
    numero: t.numero, reserva: d.reserva, numero_tiquete: d.numero_tiquete, estado: t.estado, fecha_compra: t.fecha,
    vuelo: d.vuelo, origen: d.origen_texto, destino: d.destino_texto, fecha: d.fecha, salida: d.salida, llegada: d.llegada,
    tarifa: d.tarifa_nombre, equipaje: d.equipaje, categoria: d.categoria, valor_pagado: d.valor_pagado, internacional: d.internacional,
    entregas: t.entregas ?? 0,
    pasajero: d.pasajero_nombre ?? `${t.persona.nombres} ${t.persona.apellidos}`, menor: Boolean(d.menor),
    // Ciclo de vida del tiquete: emitido → en la wallet → abordado (usado); o cancelado.
    uso: t.estado === 'anulado' ? 'cancelado' : t.estado === 'usado' ? 'abordado' : (t.entregas ?? 0) > 0 ? 'en_wallet' : 'emitido',
    reexpediciones: t.reexpediciones ?? 0,
  }
}

r.get('/api/tiquetes', conPersona((_req, res, persona) => res.json(tramites.dePersona(persona.documento).map(vistaReserva))))

r.post('/api/tiquetes/:numero/oferta', conPersona(async (req, res, persona) => {
  let tq = tramites.buscar(req.params.numero)
  if (!tq || tq.persona.documento !== persona.documento) return res.status(404).json({ error: 'No es un tiquete tuyo.' })
  if (tq.estado === 'anulado') return res.status(409).json({ error: 'Este tiquete está cancelado.' })
  if (tq.estado === 'usado') return res.status(409).json({ error: 'Este tiquete ya se usó: el pasajero abordó.' })
  if ((tq.entregas ?? 0) > 0) {
    // Ya está en una wallet: no se entrega otra copia (terminarías con tiquetes repetidos). Si se perdió, se reexpide y la anterior queda revocada.
    if (req.body?.reexpedir !== true) return res.status(409).json({ error: 'Este tiquete ya está en una wallet. Si la perdiste, pide la reexpedición: la copia anterior dejará de valer.', ya_entregado: true })
    tq = tramites.reexpedir(tq.numero)
    estado.invalidar()
    console.log('[reexpedición] tiquete reexpedido: la copia anterior queda revocada')
  }
  res.json({ oferta: await oferta(tq) })
}))

// Vuelos con tiquetes emitidos y el manifiesto de cada uno (los pasajeros dados de alta que esperan el embarque). Datos enmascarados:
// este portal de ejemplo no tiene acceso de personal, así que no muestra nombres ni documentos completos.
const iniciales = (n) => String(n).split(/\s+/).filter(Boolean).map((x) => `${x[0].toUpperCase()}.`).join(' ')
const enmascarar = (doc) => `•••${digitos(doc).slice(-3)}`
r.get('/api/vuelos-con-tiquetes', (_req, res) => res.json({ vuelos: tramites.vuelosConTiquetes(HOY()) }))
r.get('/api/manifiesto', (req, res) => {
  const { vuelo, fecha } = req.query
  if (!vuelo || !/^\d{4}-\d{2}-\d{2}$/.test(fecha ?? '')) return res.status(400).json({ error: 'Indica el vuelo y la fecha.' })
  const lista = tramites.deVuelo(String(vuelo), fecha).map((x) => {
    const v = vistaReserva(x)
    return { reserva: v.reserva, tiquete: v.numero_tiquete, pasajero: iniciales(v.pasajero), documento: enmascarar(x.datos.pasajero_documento ?? x.persona.documento), tipo: v.categoria, menor: v.menor, uso: v.uso }
  })
  res.json({ vuelo, fecha, pasajeros: lista })
})

r.post('/api/tiquetes/:numero/cancelar', conPersona((req, res, persona) => {
  const t = tramites.buscar(req.params.numero)
  if (!t || t.persona.documento !== persona.documento) return res.status(404).json({ error: 'No es un tiquete tuyo.' })
  if (t.estado === 'usado') return res.status(409).json({ error: 'Este tiquete ya se usó: el pasajero abordó.' })
  const anulado = tramites.anular(t.numero, 'Cancelado por el pasajero')
  estado.invalidar()
  console.log('[cancelación] tiquete cancelado')
  res.json(vistaReserva(anulado))
}))

app.use(BASE || '/', r)
const PUERTO = Number(process.env.PUERTO ?? 80)
app.listen(PUERTO, () => console.log(`${aerolinea.nombre} escuchando en :${PUERTO}${BASE} (emisor ${URL_PUBLICA}, verifier ${process.env.VERIFIER_API})`))

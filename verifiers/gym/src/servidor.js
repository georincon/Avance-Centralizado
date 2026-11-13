// Gimnasio de ejemplo (Cumbre Gym): tarjeta de socio en la wallet y control de ingreso y egreso.
//
//   Portal del socio (/gym/)
//     - identificarse: la wallet presenta el PID a SU verifier (verifier-gym);
//     - hacerse socio eligiendo un plan (el pago es simulado) y renovar;
//     - recibir en la wallet la tarjeta de socio (OpenID4VCI, ver emisor.js) y revocar una copia entregada;
//     - ver sus últimas visitas.
//   Control de acceso (/gym/acceso)
//     - una pantalla de ENTRADA y otra de SALIDA por sede: muestran un código QR; el socio presenta su tarjeta
//       desde la wallet y la pantalla dice si puede pasar. Entrada y salida se anotan; el aforo se cuenta en vivo.
//     - reglas: membresía activa y vigente, tarjeta no anulada (status list), no estar ya dentro (no se entra dos
//       veces sin salir) y aforo de la sede sin completar.
//     - las pantallas piden una clave de puerta (la de administración): sin ella cualquiera podría abrir la
//       dirección desde su casa y registrar un ingreso sin estar en la sede.
//   Administración (/gym/admin, con contraseña)
//     - aforo en vivo por sede y quién está dentro; socios (suspender, reactivar, renovar); historial de accesos;
//       cierre de sede (marca la salida de todos los que siguen dentro).
//
// Del registro de accesos no sale el documento del socio: solo su número, la sede, el tipo y la hora. La foto (la del
// PID, que va en la tarjeta) se muestra en la pantalla de la puerta para que quien atiende la compare con la persona, y
// no se guarda en el registro ni en el log.
//
// Variables (las pone compose.yml): URL_PUBLICA, VERIFIER_API, TRUST_VALIDATOR_URL, GYM_NOMBRE, ADMIN_CLAVE,
// AFORO_MAXIMO, SECRETOS_DIR, SOCIOS_ARCHIVO, PUERTO.

import { randomBytes, timingSafeEqual } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import { importPKCS8 } from 'jose'
import QRCode from 'qrcode'
import { crearAbca } from './abca.js'
import { DOCUMENTOS, estadoDe } from './documentos.js'
import { crearEmisor } from './emisor.js'
import { crearEstado } from './estado.js'
import { PLANES, SEDES, diasRestantes, plan as buscarPlan } from './planes.js'
import { iniciarAlmacen } from './almacen.js'
import { crearSocios } from './socios.js'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const GYM = process.env.GYM_NOMBRE ?? 'Cumbre Gym'
const SECRETOS = process.env.SECRETOS_DIR ?? '/secretos'
const CLAVE_ADMIN = process.env.ADMIN_CLAVE ?? ''
const AFORO_MAXIMO = Number(process.env.AFORO_MAXIMO ?? 60)
const VENCE_SOLICITUD_MS = 5 * 60 * 1000

const certificado = readFileSync(`${SECRETOS}/ds.crt`, 'utf8')
const firmante = {
  clave: await importPKCS8(readFileSync(`${SECRETOS}/ds.key`, 'utf8'), 'ES256'),
  x5c: [certificado.replace(/-----[^-]+-----|\s/g, '')],
}
await iniciarAlmacen(process.env.SOCIOS_ARCHIVO ?? '/datos/socios.json', { siguiente_indice: 1, socios: [], emisiones: [], accesos: [] })
const socios = crearSocios(process.env.SOCIOS_ARCHIVO ?? '/datos/socios.json')
const estado = crearEstado({ firmante, uri: `${URL_PUBLICA}/estado/1`, anulados: socios.anulados })
const abca = crearAbca({ trustValidator: requerida('TRUST_VALIDATOR_URL'), audiencia: URL_PUBLICA })
const emisor = crearEmisor({ url: URL_PUBLICA, gimnasio: GYM, firmante, socios, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: GYM, proyecto: 'verifiers/gym' }) })
// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: GYM, proyecto: 'verifiers/gym' }))

// --- Sesiones y solicitudes (en memoria) ------------------------------------------------------
const sesiones = new Map()
const solicitudes = new Map()
function sesion(req, res) {
  const id = /(?:^|;\s*)gym_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `gym_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax; Max-Age=43200`)
  return sesiones.get(nueva)
}
setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// Qué se le pide a la wallet en cada caso. La entrada no necesita el documento del socio, pero sí su foto.
// La foto va como data URI; solo se acepta una imagen real (se pone en un <img>, nunca como HTML).
const FOTO = /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/
const fotoDe = (v) => (typeof v === 'string' && FOTO.test(v) ? v : null)
const PID = { vct: 'urn:eudi:pid:1', datos: ['personal_administrative_number', 'given_name', 'family_name', 'birthdate', 'picture'] }
const TARJETA = { vct: DOCUMENTOS.tarjeta.vct, datos: ['gimnasio', 'socio_nombre', 'numero_socio', 'plan', 'estado', 'vigente_hasta', 'picture'] }

const hora = (iso) => new Date(iso).toLocaleTimeString('es-CO', { timeZone: 'America/Bogota', hour: '2-digit', minute: '2-digit', hour12: false })
const minutosEntre = (desde, hasta = Date.now()) => Math.max(0, Math.round((hasta - Date.parse(desde)) / 60000))
const aforoDe = (sede) => ({ sede, dentro: socios.dentro(sede).length, max: AFORO_MAXIMO })

/** La tarjeta se presentó en una pantalla de entrada o de salida: decide si pasa y lo anota. */
function acceder(solicitud, r) {
  const { modo, sede } = solicitud
  const foto = fotoDe(r.datos.picture)
  const negar = (motivo, numero, nombre) => {
    socios.registrarAcceso({ numero, tipo: 'denegado', sede, motivo })
    console.log(`[acceso] ${modo} denegada en ${sede}: ${motivo}`)
    return { estado: 'listo', acceso: { ok: false, modo, sede, motivo, nombre, foto, aforo: aforoDe(sede) } }
  }
  if (r.emisor !== URL_PUBLICA) return negar('La tarjeta no la emitió este gimnasio.')
  const numero = String(r.datos.numero_socio ?? '')
  const s = socios.porNumero(numero)
  if (!s) return negar('Esta tarjeta no corresponde a ningún socio.', numero || null)
  const nombre = s.nombres
  const est = estadoDe(s)
  if (est === 'Suspendida') return negar('La membresía está suspendida. Habla con recepción.', numero, nombre)
  if (est === 'Vencida') return negar(`La membresía venció el ${s.membresia.hasta}. Renuévala en el portal.`, numero, nombre)

  const abierto = socios.ingresoAbierto(numero)
  const base = { ok: true, modo, sede, nombre, foto, plan: s.membresia.plan, hasta: s.membresia.hasta, dias_restantes: diasRestantes(s.membresia.hasta) }
  if (modo === 'entrada') {
    if (abierto) return negar(`Ya registra ingreso a las ${hora(abierto.ts)} en ${abierto.sede}. Hay que marcar la salida antes de volver a entrar.`, numero, nombre)
    if (socios.dentro(sede).length >= AFORO_MAXIMO) return negar(`El aforo de ${sede} está completo (${AFORO_MAXIMO} personas). Intenta en unos minutos.`, numero, nombre)
    socios.registrarAcceso({ numero, tipo: 'ingreso', sede })
    console.log(`[acceso] ingreso en ${sede}`)
    return { estado: 'listo', acceso: { ...base, aforo: aforoDe(sede) } }
  }
  // Salida: siempre se deja salir; si no había ingreso se anota igual (el torniquete se abre).
  socios.registrarAcceso({ numero, tipo: 'egreso', sede, ...(abierto ? {} : { motivo: 'sin_ingreso' }) })
  console.log(`[acceso] egreso en ${sede}`)
  return { estado: 'listo', acceso: { ...base, permanencia_min: abierto ? minutosEntre(abierto.ts) : null, sin_ingreso: !abierto, aforo: aforoDe(sede) } }
}

function completar(solicitud, r) {
  if (solicitud.proposito === 'acceso') return acceder(solicitud, r)
  const d = r.datos
  if (!d.personal_administrative_number || !d.given_name || !d.family_name) {
    return { estado: 'rechazada', motivo: 'La wallet no compartió los datos necesarios para identificarte.' }
  }
  const s = sesiones.get(solicitud.sesion)
  if (s) s.persona = { documento: String(d.personal_administrative_number), nombres: d.given_name, apellidos: d.family_name, fecha_nacimiento: d.birthdate ?? null, foto: fotoDe(d.picture) }
  console.log('[identificación] una persona se identificó con su PID')
  return { estado: 'listo' }
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) solicitud.final = { estado: 'rechazada', motivo: 'La solicitud venció. Inténtalo de nuevo.' }
    return solicitud.final ?? r
  }
  solicitud.final = r.estado === 'error' ? { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` } : completar(solicitud, r)
  return solicitud.final
}

const vistaSocio = (s) => s && {
  ...s,
  membresia: { ...s.membresia, estado: estadoDe(s), dias_restantes: diasRestantes(s.membresia.hasta) },
  credenciales: socios.emisionesDe(s.documento),
  visitas: socios.visitasDe(s.membresia.numero),
  dentro: Boolean(socios.ingresoAbierto(s.membresia.numero)),
}
const texto = (v, max) => String(v ?? '').trim().slice(0, max)

// --- Páginas -----------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: GYM, tema: {
  fondo: '#F7F5F2', panel: '#FFFFFF', texto: '#1A1D24', suave: '#6B6F7A', borde: '#E7E2DB', titulo: '#111318',
  marca: '#C2410C', marcaTexto: '#FFFFFF', acento: '#C6F135', cabeceraFondo: '#111318', cabeceraTexto: '#FFFFFF',
  ok: '#16A34A', okFondo: '#E3F6E8', mal: '#B42318', malFondo: '#FEF3F2', neutroFondo: '#FFF0E3', hover: '#FFF0E3',
  fuente: 'Barlow, Segoe UI, system-ui, -apple-system, Roboto, sans-serif', fuenteTitulo: 'Barlow, Segoe UI, system-ui, sans-serif', pesoTitulo: 800, mayusculas: 'none', tracking: '-.3px', radio: '8px', logoAlto: 46,
  preFondo: '#111318', preTexto: '#E7E2DB', logo: `${BASE}/publico/logo.png`, subtitulo: 'Historial local',
  fuentes: '@import url(https://fonts.googleapis.com/css2?family=Barlow:wght@400;500;600;700;800&display=swap);',
  css: '.cab nav a.activo{background:rgba(255,255,255,.1)}.marca small{text-transform:uppercase;letter-spacing:.14em;font-size:.72rem}',
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(emisor.router)

const r = express.Router()
r.use(express.json({ limit: '50kb' }))
const plantilla = (archivo) => readFileSync(new URL(`./publico/${archivo}`, import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{GYM}}', GYM).replaceAll('{{SEDES}}', JSON.stringify(SEDES))
  .replaceAll('{{PLANES}}', JSON.stringify(PLANES)).replaceAll('{{AFORO}}', String(AFORO_MAXIMO))
const paginas = { portal: plantilla('index.html'), acceso: plantilla('acceso.html'), admin: plantilla('admin.html') }

r.get('/salud', (_req, res) => res.json({ estado: 'ok', aforo_maximo: AFORO_MAXIMO }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').send(paginas.portal) })
// Las tres direcciones de control de acceso son la misma página: el menú, la entrada y la salida (ver acceso.html).
for (const ruta of ['/acceso', '/acceso/entrada', '/acceso/salida']) r.get(ruta, (req, res) => { sesion(req, res); res.type('html').set('Cache-Control', 'no-store').send(paginas.acceso) })
r.get('/admin', (req, res) => { sesion(req, res); res.type('html').send(paginas.admin) })
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))

// --- Contraseñas ---------------------------------------------------------------------------------
const claveCorrecta = (dada) => {
  const a = Buffer.from(String(dada ?? '')), b = Buffer.from(CLAVE_ADMIN)
  return CLAVE_ADMIN !== '' && a.length === b.length && timingSafeEqual(a, b)
}
const intentos = new Map() // ip → { n, hasta }: 5 fallos y se espera un minuto
function limitar(req, res) {
  const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() ?? req.socket.remoteAddress ?? '?'
  const e = intentos.get(ip)
  if (e && e.n >= 5 && e.hasta > Date.now()) { res.status(429).json({ error: 'Demasiados intentos. Espera un minuto.' }); return null }
  return ip
}
function entrar(campo) {
  return (req, res) => {
    const ip = limitar(req, res)
    if (!ip) return
    if (!claveCorrecta(req.body?.clave)) {
      const e = intentos.get(ip) ?? { n: 0 }
      intentos.set(ip, { n: e.n + 1, hasta: Date.now() + 60 * 1000 })
      return res.status(401).json({ error: 'Contraseña incorrecta.' })
    }
    intentos.delete(ip)
    sesion(req, res)[campo] = true
    res.json({ ok: true })
  }
}

// --- Solicitudes a la wallet -------------------------------------------------------------------------
r.post('/api/solicitud', async (req, res) => {
  const { proposito, modo, sede, dispositivo } = req.body ?? {}
  const s = sesion(req, res)
  let pedido, extra = {}
  if (proposito === 'identificacion') pedido = PID
  else if (proposito === 'acceso') {
    // La pantalla de la puerta tiene que haber entrado con la clave: ver /api/puerta/entrar.
    if (!s.puerta) return res.status(401).json({ error: 'Esta pantalla necesita la clave de puerta.', puerta: true })
    if (!['entrada', 'salida'].includes(modo) || !SEDES.includes(sede)) return res.status(400).json({ error: 'modo: entrada o salida; sede: una de las sedes del gimnasio.' })
    pedido = TARJETA
    extra = { modo, sede }
  } else return res.status(400).json({ error: 'proposito: identificacion o acceso' })
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      ...pedido, nonce: randomBytes(16).toString('base64url'),
      // En la puerta el QR siempre se lee desde otro dispositivo (el teléfono del socio).
      redireccion: dispositivo === 'mismo' && proposito === 'identificacion' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { proposito, transaccion, sesion: s.id, creada: Date.now(), ...extra })
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
  try { res.json(await consultar(solicitud)) } catch { res.json({ estado: 'esperando' }) }
})

r.get('/volver', async (req, res) => {
  const s = sesion(req, res)
  const solicitud = solicitudes.get(s.pendiente)
  const ir = (q) => res.redirect(`${BASE}/#${q}`)
  if (!solicitud || !req.query.response_code) return ir(`error=${encodeURIComponent('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')}`)
  try {
    const final = await consultar(solicitud, String(req.query.response_code))
    if (final.estado !== 'listo') return ir(`error=${encodeURIComponent(final.motivo ?? 'La wallet todavía no ha respondido.')}`)
    return ir('identificado')
  } catch {
    return ir(`error=${encodeURIComponent('El servicio de verificación no está disponible.')}`)
  }
})

// --- Socio -----------------------------------------------------------------------------------------------
const conPersona = (fn) => (req, res) => {
  const p = sesion(req, res).persona
  if (!p) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
  return fn(req, res, p)
}

r.get('/api/yo', conPersona((_req, res, p) => res.json({ persona: p, socio: vistaSocio(socios.buscar(p.documento)) })))
r.post('/api/salir', (req, res) => { delete sesion(req, res).persona; res.json({ ok: true }) })

// Hacerse socio o renovar: el pago es simulado, aquí solo se elige el plan y la sede.
r.post('/api/membresia', conPersona((req, res, p) => {
  const { plan, sede } = req.body ?? {}
  if (!buscarPlan(plan) || !SEDES.includes(sede)) return res.status(400).json({ error: 'Elige un plan y una sede.' })
  const existente = socios.buscar(p.documento)
  const s = existente ? socios.renovar(p.documento, plan, sede) : socios.afiliar({ ...p, plan, sede })
  if (existente) estado.invalidar()
  console.log(`[membresía] ${existente ? 'renovación' : 'nuevo socio'} (${buscarPlan(plan).nombre})`)
  res.status(existente ? 200 : 201).json(vistaSocio(s))
}))

r.post('/api/oferta', conPersona(async (req, res, p) => {
  let s = socios.buscar(p.documento)
  if (!s) return res.status(404).json({ error: 'Todavía no eres socio.' })
  // Un socio que se afilió sin foto la recibe aquí, de su PID, para que la tarjeta la lleve.
  if (!s.foto && p.foto && socios.ponerFoto(p.documento, p.foto)) s = socios.buscar(p.documento)
  if (estadoDe(s) !== 'Activa') return res.status(409).json({ error: `Tu membresía está ${estadoDe(s).toLowerCase()}.` })
  const o = emisor.ofrecer(s.documento, 'tarjeta', req.body?.reexpedir === true)
  res.json({ ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) })
}))

r.post('/api/credenciales/:indice/revocar', conPersona((req, res, p) => {
  const e = socios.revocar(p.documento, Number(req.params.indice))
  if (!e) return res.status(404).json({ error: 'No es una tarjeta tuya.' })
  estado.invalidar()
  res.json(e)
}))

// --- Puerta (pantallas de entrada y salida) ---------------------------------------------------------------------
r.post('/api/puerta/entrar', entrar('puerta'))
r.get('/api/puerta/estado', (req, res) => res.json({ autorizada: Boolean(sesion(req, res).puerta), sedes: SEDES, aforo: SEDES.map(aforoDe) }))
r.post('/api/puerta/salir', (req, res) => { delete sesion(req, res).puerta; res.json({ ok: true }) })
// El aforo de una sede, para mostrarlo en la pantalla de la puerta sin pasar por la wallet.
r.get('/api/puerta/aforo', (req, res) => (sesion(req, res).puerta ? res.json(SEDES.map(aforoDe)) : res.status(401).json({ error: 'Falta la clave de puerta.' })))

// --- Administración (contraseña) ----------------------------------------------------------------------------------
r.post('/api/admin/entrar', entrar('admin'))
r.post('/api/admin/salir', (req, res) => { delete sesion(req, res).admin; res.json({ ok: true }) })
const conAdmin = (fn) => (req, res) => (sesion(req, res).admin ? fn(req, res) : res.status(401).json({ error: 'Entra con la contraseña de administración.' }))

const nombreDe = (numero) => { const s = numero && socios.porNumero(numero); return s ? `${s.nombres} ${s.apellidos}` : null }
r.get('/api/admin/resumen', conAdmin((_req, res) => {
  const dentro = socios.dentro()
  res.json({
    aforo_maximo: AFORO_MAXIMO,
    sedes: SEDES.map((sede) => ({ ...aforoDe(sede), personas: dentro.filter((a) => a.sede === sede).map((a) => ({ numero: a.numero, nombre: nombreDe(a.numero), desde: a.ts, minutos: minutosEntre(a.ts) })) })),
    socios: socios.todos().map((s) => ({ documento: s.documento, nombre: `${s.nombres} ${s.apellidos}`, numero: s.membresia.numero, plan: s.membresia.plan, sede: s.membresia.sede, estado: estadoDe(s), hasta: s.membresia.hasta, dias_restantes: diasRestantes(s.membresia.hasta), dentro: dentro.some((a) => a.numero === s.membresia.numero) })),
    historial: socios.historial(80).map((a) => ({ ...a, nombre: nombreDe(a.numero) })),
  })
}))
r.put('/api/admin/socios/:doc', conAdmin((req, res) => {
  const b = req.body ?? {}
  let s = socios.buscar(req.params.doc)
  if (!s) return res.status(404).json({ error: 'No existe.' })
  if (b.estado) s = socios.cambiarEstado(req.params.doc, b.estado)
  if (b.plan) s = socios.renovar(req.params.doc, texto(b.plan, 20), SEDES.includes(b.sede) ? b.sede : undefined)
  if (!s) return res.status(400).json({ error: 'estado: Activa o Suspendida; plan: uno de los planes.' })
  estado.invalidar()
  res.json({ ok: true })
}))
r.post('/api/admin/cerrar', conAdmin((req, res) => {
  const sede = SEDES.includes(req.body?.sede) ? req.body.sede : undefined
  res.json({ cerrados: socios.cerrarSede(sede) })
}))

app.use(BASE || '/', r)
app.listen(Number(process.env.PUERTO ?? 80), () => console.log(`${GYM} escuchando en ${BASE} (emisor ${URL_PUBLICA}; aforo máximo ${AFORO_MAXIMO})`))

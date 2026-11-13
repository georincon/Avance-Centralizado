// Banco de ejemplo: ingreso y apertura de cuenta con la wallet, sin contraseñas.
//
//   Ingresar     la wallet presenta cédula y nombre; si hay un cliente con esa
//                cédula, entra.
//   Abrir cuenta la wallet presenta cédula, nombre, fecha de nacimiento, correo,
//                foto y vencimiento del PID; si es mayor de edad, el PID está
//                vigente y no es cliente, se le crea la cuenta (clientes.json).
//
// La verificación (firma, emisor de confianza, estado, prueba de posesión) la
// hace el verifier de su instancia (../_comun/levantar.sh banco); aquí solo se decide
// qué pedir y qué hacer con los datos.
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA       URL con la que la ve el navegador. Ej.: https://civica-desarrollo.avance.org.co/banco
//                     El servidor escucha bajo su ruta (srv-nw-nginx no quita el prefijo).
//   VERIFIER_API      API del verifier por la red interna. Ej.: http://verifier-banco/verifier-banco
//   NOMBRE_BANCO      el nombre que se muestra
//   CLIENTES_ARCHIVO  archivo JSON de clientes (se crea vacío si no existe)
//   PUERTO            puerto interno (80: srv-nw-nginx llama a app-banco:80)
//   SECRETOS_DIR      PKI del banco como emisor (ds.key, ds.crt): ./scripts/generar-claves.sh la crea
//   EMISIONES_ARCHIVO credenciales de cuenta entregadas (índices de la status list)
//   TRUST_VALIDATOR_URL  validador de confianza (para comprobar la atestación de la wallet)
//
// EMISOR: al abrir una cuenta el banco ofrece a la wallet una credencial «Cuenta bancaria» (OpenID4VCI, ver emisor.js);
// al cerrarla la ANULA (status list) y la wallet la marca como revocada.

import { randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import QRCode from 'qrcode'
import { importPKCS8 } from 'jose'
import { crearAbca } from './abca.js'
import { iniciarAlmacen } from './almacen.js'
import { crearClientes, TIPOS, MAX_ACTIVAS } from './clientes.js'
import { crearEmisiones } from './emisiones.js'
import { crearEmisor } from './emisor.js'
import { crearEstado } from './estado.js'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (nombre) => {
  const valor = process.env[nombre]
  if (!valor) throw new Error(`Falta la variable de entorno ${nombre}`)
  return valor
}

const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const NOMBRE = process.env.NOMBRE_BANCO ?? 'Banco Cívica'
const PUERTO = Number(process.env.PUERTO ?? 80)
const VENCE_SOLICITUD_MS = 5 * 60 * 1000

// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/banco' }))
await iniciarAlmacen(process.env.CLIENTES_ARCHIVO ?? '/datos/clientes.json', [])
const clientes = crearClientes(process.env.CLIENTES_ARCHIVO ?? '/datos/clientes.json')

// --- El banco como EMISOR de la credencial de cuenta ----------------------------------------
const SECRETOS = process.env.SECRETOS_DIR ?? '/secretos'
const certificado = readFileSync(`${SECRETOS}/ds.crt`, 'utf8')
const firmante = {
  clave: await importPKCS8(readFileSync(`${SECRETOS}/ds.key`, 'utf8'), 'ES256'),
  x5c: [certificado.replace(/-----[^-]+-----|\s/g, '')],
}
await iniciarAlmacen(process.env.EMISIONES_ARCHIVO ?? '/datos/emisiones.json', { siguiente_indice: 0, emisiones: [] })
const emisiones = crearEmisiones(process.env.EMISIONES_ARCHIVO ?? '/datos/emisiones.json')
const estado = crearEstado({ firmante, uri: `${URL_PUBLICA}/estado/1`, anulados: emisiones.anulados })
const abca = crearAbca({ trustValidator: requerida('TRUST_VALIDATOR_URL'), audiencia: URL_PUBLICA })
// Credenciales de cuenta por entregar a la wallet del titular: cédula → cuentas recién abiertas. La wallet las consulta
// sola (GET /api/wallet/pendientes) y cada oferta se entrega una vez; si se pierde, queda el botón «Recibir en mi wallet».
const pendientes = new Map()
const encolar = (cedula, cuenta) => { const c = String(cedula); pendientes.set(c, new Set([...(pendientes.get(c) ?? []), cuenta])) }
const emisor = crearEmisor({ url: URL_PUBLICA, banco: NOMBRE, firmante, clientes, emisiones, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/banco' }) })

// Qué se le pide a la wallet en cada caso (datos de primer nivel del PID de la Registraduría).
//
// Solo se piden datos que el PID trae SIEMPRE: si la consulta incluye un dato que el documento no
// tiene, la wallet no encuentra ningun documento que la cumpla ("documento no disponible"). Este PID
// no lleva `email` ni `date_of_expiry` (la Registraduria no los emite), por eso no se piden.
const PEDIDOS = {
  ingreso: ['personal_administrative_number', 'given_name', 'family_name'],
  registro: ['personal_administrative_number', 'given_name', 'family_name', 'birthdate', 'picture'],
  // Confirmar una operación delicada (cerrar una cuenta): la wallet vuelve a presentar el documento
  // y la cédula tiene que ser la de la sesión.
  confirmar: ['personal_administrative_number', 'given_name', 'family_name'],
}

// --- Sesiones y solicitudes en memoria (es un ejemplo: se pierden al reiniciar) -----------
const sesiones = new Map()     // id → { cedula?, pendiente? }
const solicitudes = new Map()  // id → { tipo, transaccion, sesion, creada, accion?, final? }

function sesion(req, res) {
  const id = /(?:^|;\s*)banco_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `banco_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}

setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// --- Qué hacer con los datos verificados ---------------------------------------------------
const hoy = () => new Date().toISOString().slice(0, 10)
function edad(nacimiento) {
  const n = new Date(nacimiento)
  const h = new Date()
  return h.getFullYear() - n.getFullYear() - (h < new Date(h.getFullYear(), n.getMonth(), n.getDate()) ? 1 : 0)
}

function completar(solicitud, { datos, emisor }) {
  const cedula = datos.personal_administrative_number
  if (!cedula) return { estado: 'rechazada', motivo: 'La wallet no compartió tu número de documento.' }
  const s = sesiones.get(solicitud.sesion)

  if (solicitud.tipo === 'confirmar') {
    // La operación solo se ejecuta si el documento presentado es el del titular de la sesión.
    if (!s?.cedula || String(cedula) !== s.cedula) {
      console.log(`[confirmar] rechazada: el documento presentado no es el de la sesión`)
      return { estado: 'rechazada', motivo: 'El documento que presentaste no es el del titular de esta sesión.' }
    }
    const { cuenta } = solicitud.accion
    const r = clientes.cerrarCuenta(s.cedula, cuenta)
    if (r.error) return { estado: 'rechazada', motivo: r.error }
    console.log(`[cierre] ${s.cedula} cerró la cuenta ${cuenta}${r.a ? ` (saldo trasladado a ${r.a})` : r.retirado ? ' (saldo retirado)' : ''}`)
    // Al cerrar la cuenta se ANULA su credencial: el bit de la status list pasa a 1 y la wallet la marca como revocada.
    const anuladas = emisiones.anularCuenta(cuenta)
    estado.invalidar()
    console.log(`[cierre] credencial de la cuenta anulada (${anuladas} copia(s))`)
    return { estado: 'listo', destino: `${BASE}/cuenta`, cerrada: cuenta, credencial_anulada: anuladas > 0 }
  }

  if (solicitud.tipo === 'ingreso') {
    const cliente = clientes.registrarIngreso(String(cedula))
    if (!cliente) {
      return { estado: 'rechazada', motivo: `No tenemos una cuenta para el documento ${cedula}. Ábrela con tu wallet en un minuto.` }
    }
    if (s) s.cedula = cliente.cedula
    console.log(`[ingreso] ${cliente.cedula} (${cliente.nombres}), PID de ${emisor}`)
    return { estado: 'listo', destino: `${BASE}/cuenta` }
  }

  // Apertura de cuenta: las reglas del banco sobre los datos verificados.
  for (const d of ['given_name', 'family_name', 'birthdate']) {
    if (!datos[d]) return { estado: 'rechazada', motivo: `La wallet no compartió un dato necesario (${d}).` }
  }
  if (edad(datos.birthdate) < 18) return { estado: 'rechazada', motivo: 'Para abrir una cuenta tienes que ser mayor de edad.' }
  if (datos.date_of_expiry && datos.date_of_expiry < hoy()) {
    return { estado: 'rechazada', motivo: `Tu documento venció el ${datos.date_of_expiry}. Pide uno nuevo en tu wallet.` }
  }
  if (clientes.buscar(String(cedula))) {
    return { estado: 'rechazada', motivo: 'Ya eres cliente: ingresa con tu wallet y abre otra cuenta desde «Mis cuentas».' }
  }
  const cliente = clientes.crear({
    cedula: String(cedula),
    nombres: datos.given_name,
    apellidos: datos.family_name,
    fecha_nacimiento: datos.birthdate,
    correo: datos.email ?? null,
    foto: typeof datos.picture === 'string' && datos.picture.startsWith('data:image/') ? datos.picture : null,
    // De dónde salieron los datos: el emisor del PID y cuándo se verificó.
    verificacion: { emisor, pid_vence: datos.date_of_expiry ?? null, fecha: new Date().toISOString() },
  })
  if (s) s.cedula = cliente.cedula
  encolar(cliente.cedula, cliente.cuentas[0].numero)
  console.log(`[registro] ${cliente.cedula} (${cliente.nombres} ${cliente.apellidos}), cuenta ${cliente.cuentas[0].numero}, PID de ${emisor}`)
  return { estado: 'listo', destino: `${BASE}/cuenta`, nuevo: true }
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) {
      solicitud.final = { estado: 'rechazada', motivo: 'La solicitud venció. Inténtalo de nuevo.' }
    }
    return solicitud.final ?? r
  }
  if (r.estado === 'error') {
    console.log(`[${solicitud.tipo}] rechazada por el verifier: ${r.motivo}${r.detalle ? ` (${JSON.stringify(r.detalle)})` : ''}`)
    solicitud.final = { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` }
  } else {
    solicitud.final = completar(solicitud, r)
  }
  return solicitud.final
}

// --- Rutas --------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: NOMBRE, tema: {
  fondo: '#F7F3F8', panel: '#FFFFFF', texto: '#000000', suave: '#52525D', borde: '#E4E4E4', titulo: '#000000',
  marca: '#820AD1', marcaTexto: '#FFFFFF', acento: '#ECD9FF', cabeceraFondo: '#FFFFFF', cabeceraTexto: '#000000',
  ok: '#14601A', okFondo: '#F1FDF1', mal: '#C22A2A', malFondo: '#FFF4F4', neutroFondo: '#F4F4F5', hover: '#F5ECFF',
  fuente: 'DM Sans, system-ui, -apple-system, Segoe UI, Roboto, sans-serif', fuenteTitulo: 'DM Sans, system-ui, sans-serif',
  pesoTitulo: 500, mayusculas: 'none', tracking: '-.01em', radio: '12px', logoAlto: 38, preFondo: '#F5ECFF', preTexto: '#3A0564',
  logo: `${BASE}/publico/logo.png`, subtitulo: 'Historial local',
  fuentes: '@import url(https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,700&display=swap);',
  css: '.cab{border-bottom:1px solid #E4E4E4}.cab nav a{color:#52525D;opacity:1}.cab nav a:hover{color:#000;background:#F4F4F5}.cab nav a.activo{color:#820AD1}.franja{display:none}.barra button,.barra input,.barra select{border-radius:999px;padding:8px 16px}.k{border-radius:16px}th{font-weight:500;text-transform:none;letter-spacing:0;font-size:13px}'
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(express.json())
const r = express.Router()

// Las rutas que modifican datos solo aceptan peticiones hechas desde las propias páginas del banco.
const ORIGEN = new URL(URL_PUBLICA).origin
r.use((req, res, next) => {
  if (req.method === 'GET' || req.method === 'HEAD') return next()
  const origen = req.headers.origin
  if (origen && origen !== ORIGEN) return res.status(403).json({ error: 'Origen no permitido.' })
  next()
})

const pagina = (archivo) => readFileSync(new URL(`./publico/${archivo}`, import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{NOMBRE}}', NOMBRE)
const paginas = { inicio: pagina('index.html'), cuenta: pagina('cuenta.html') }

// La hoja de estilo va aparte, para que la identidad visual (los tokens de NU
// Colombia) esté en un solo sitio y las dos páginas no la repitan.
const ESTILO = readFileSync(new URL('./publico/estilo.css', import.meta.url), 'utf8')

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/estilo.css', (_req, res) => res.type('text/css').set('cache-control', 'no-cache').send(ESTILO))
r.get('/', (req, res) => { sesion(req, res); res.type('html').send(paginas.inicio) })
r.get('/cuenta', (req, res) => {
  if (!sesion(req, res).cedula) return res.redirect(`${BASE}/`)
  res.type('html').send(paginas.cuenta)
})

// Empieza un ingreso o una apertura: devuelve el QR y el enlace para la wallet.
r.post('/api/solicitud', async (req, res) => {
  const { tipo, dispositivo, cuenta } = req.body ?? {}
  if (!PEDIDOS[tipo]) return res.status(400).json({ error: 'tipo: ingreso, registro o confirmar' })
  const s = sesion(req, res)
  let accion
  if (tipo === 'confirmar') {
    // Confirmar cierra una cuenta concreta: hay que haber ingresado y la cuenta debe estar activa.
    if (!s.cedula) return res.status(401).json({ error: 'Ingresa con tu wallet primero.' })
    const c = clientes.buscar(s.cedula)
    const objetivo = c?.cuentas.find((x) => x.numero === String(cuenta ?? ''))
    if (!objetivo || objetivo.estado !== 'activa') return res.status(404).json({ error: 'Esa cuenta no existe o ya está cerrada.' })
    accion = { cuenta: objetivo.numero }
  }
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      datos: PEDIDOS[tipo],
      nonce: randomBytes(16).toString('base64url'),
      // En el mismo teléfono la wallet vuelve al banco al terminar.
      redireccion: dispositivo === 'mismo' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { tipo, transaccion, sesion: s.id, creada: Date.now(), accion })
    s.pendiente = id
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 320 }) })
  } catch (e) {
    console.error(`[${tipo}] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})

// El navegador pregunta cada par de segundos (en el computador, mientras se escanea el QR).
r.get('/api/solicitud/:id', async (req, res) => {
  const solicitud = solicitudes.get(req.params.id)
  if (!solicitud || solicitud.sesion !== sesion(req, res).id) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  try {
    res.json(await consultar(solicitud))
  } catch (e) {
    console.error(`[${solicitud.tipo}] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

// A donde vuelve la wallet en el mismo teléfono, con el código que libera el resultado.
r.get('/volver', async (req, res) => {
  const s = sesion(req, res)
  const solicitud = solicitudes.get(s.pendiente)
  const fallo = (motivo) => res.redirect(`${BASE}/?error=${encodeURIComponent(motivo)}`)
  if (!solicitud || !req.query.response_code) return fallo('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')
  try {
    const final = await consultar(solicitud, String(req.query.response_code))
    if (final.estado === 'listo') return res.redirect(final.destino + (final.nuevo ? '?nueva=1' : final.cerrada ? `?cerrada=${encodeURIComponent(final.cerrada)}` : ''))
    return fallo(final.motivo ?? 'La wallet todavía no ha respondido.')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return fallo('El servicio de verificación no está disponible.')
  }
})

r.get('/api/yo', (req, res) => {
  const cedula = sesion(req, res).cedula
  const cliente = cedula && clientes.buscar(cedula)
  if (!cliente) return res.status(401).json({ error: 'sin sesión' })
  // Por cuenta: cuántas copias de su credencial hay vigentes (en una wallet) y cuántas anuladas.
  const cuentas = cliente.cuentas.map((c) => {
    const e = emisiones.deCuenta(c.numero)
    return { ...c, credencial: { entregadas: e.length, vigentes: e.filter((x) => x.estado === 'vigente').length, anuladas: e.filter((x) => x.estado !== 'vigente').length } }
  })
  res.json({ ...cliente, cuentas, tipos: TIPOS, max_activas: MAX_ACTIVAS })
})

/** Oferta de la credencial de una cuenta activa del cliente en sesión: enlace + PIN + QR. */
async function ofertaDeCuenta(cedula, numero) {
  const c = clientes.buscar(cedula)
  const cuenta = c?.cuentas.find((x) => x.numero === numero)
  if (!cuenta) return { error: 'Esa cuenta no existe.', estado: 404 }
  if (cuenta.estado !== 'activa') return { error: 'La cuenta está cerrada: ya no tiene credencial.', estado: 409 }
  const o = emisor.ofrecer(cedula, numero)
  return { ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) }
}

// Recibir (o volver a recibir) en la wallet la credencial de una cuenta.
r.post('/api/cuentas/:numero/credencial', async (req, res) => {
  const cedula = sesion(req, res).cedula
  if (!cedula) return res.status(401).json({ error: 'sin sesión' })
  const o = await ofertaDeCuenta(cedula, String(req.params.numero))
  if (o.error) return res.status(o.estado).json({ error: o.error })
  res.json(o)
})

// Abrir otra cuenta: ya se ingresó con la wallet, no hace falta repetir la presentación.
r.post('/api/cuentas', async (req, res) => {
  const cedula = sesion(req, res).cedula
  if (!cedula) return res.status(401).json({ error: 'sin sesión' })
  const out = clientes.abrirCuenta(cedula, String(req.body?.tipo ?? ''))
  if (out.error) return res.status(400).json({ error: out.error })
  console.log(`[apertura] ${cedula} abrió la cuenta ${out.cuenta.numero} (${out.cuenta.tipo})`)
  encolar(cedula, out.cuenta.numero)
  // Al abrir la cuenta se ofrece de una vez su credencial: la wallet la recibe escaneando el QR (con su PIN).
  const oferta = await ofertaDeCuenta(cedula, out.cuenta.numero)
  res.status(201).json({ ...out.cuenta, oferta: oferta.error ? undefined : oferta })
})

// La wallet pregunta si hay credenciales de cuenta por recibir y recibe sus ofertas (sin PIN, una sola vez).
r.get('/api/wallet/pendientes', (req, res) => {
  const cedula = String(req.query.doc ?? '').replace(/\D/g, '')
  const cuentas = [...(pendientes.get(cedula) ?? [])]
  pendientes.delete(cedula)
  const c = cedula && clientes.buscar(cedula)
  const ofertas = c ? cuentas.filter((n) => c.cuentas.some((x) => x.numero === n && x.estado === 'activa')).map((n) => emisor.ofrecer(cedula, n, true, true).enlace) : []
  if (ofertas.length) console.log(`[wallet] ${ofertas.length} credencial(es) de cuenta entregadas a la wallet`)
  res.set('Cache-Control', 'no-store').json({ ofertas })
})

r.post('/api/salir', (req, res) => {
  delete sesion(req, res).cedula
  res.json({ ok: true })
})

// El emisor cuelga de la misma ruta del banco (y de /.well-known/…/banco, que pide la especificación).
app.use(emisor.router)
app.use(BASE || '/', r)
app.listen(PUERTO, () => console.log(`${NOMBRE} escuchando en :${PUERTO}${BASE} (verifier ${process.env.VERIFIER_API})`))

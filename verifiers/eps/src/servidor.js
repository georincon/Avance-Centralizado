// EPS de ejemplo: afiliación e historia clínica con la wallet.
//
//   Portal del afiliado (/eps/)
//     - identificarse: la wallet presenta el PID a SU verifier (verifier-eps);
//     - afiliarse, si aún no lo está;
//     - ver su afiliación y su historia clínica;
//     - recibir en la wallet el carné de afiliado y el resumen de historia
//       clínica (OpenID4VCI, ver emisor.js), y revocar una copia entregada.
//   Portal del médico (/eps/medico)
//     - el médico entra presentando su PID con la wallet; pasa si el
//       administrador lo registró y está activo. Sin contraseñas.
//     - buscar afiliados, actualizar su historia y su afiliación. Al guardar,
//       las historias entregadas antes quedan reemplazadas (status list).
//   Administración (/eps/admin, con contraseña)
//     - registrar médicos (cédula, nombre, registro médico, especialidad) y
//       activarlos o desactivarlos.
//   Verificar (/eps/#verificar)
//     - una farmacia o IPS comprueba el carné; otro médico recibe de la wallet
//       del paciente SOLO las partes de la historia que el paciente acepte.
//
// Los datos de salud no van al log.
//
// Variables (las pone compose.yml): URL_PUBLICA, VERIFIER_API, TRUST_VALIDATOR_URL,
// EPS_NOMBRE, ADMIN_CLAVE, SECRETOS_DIR, AFILIADOS_ARCHIVO, MEDICOS_ARCHIVO, PUERTO.

import { randomBytes, timingSafeEqual } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import { importPKCS8 } from 'jose'
import QRCode from 'qrcode'
import { crearAbca } from './abca.js'
import { iniciarAlmacen } from './almacen.js'
import { crearAfiliados } from './afiliados.js'
import { DOCUMENTOS, porVct } from './documentos.js'
import { crearEmisor } from './emisor.js'
import { crearEstado } from './estado.js'
import { crearMedicos } from './medicos.js'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const EPS = process.env.EPS_NOMBRE ?? 'EPS Salud Avance'
const SECRETOS = process.env.SECRETOS_DIR ?? '/secretos'
const CLAVE_ADMIN = process.env.ADMIN_CLAVE ?? ''
const VENCE_SOLICITUD_MS = 5 * 60 * 1000
const IPS = ['IPS Avance Centro', 'IPS Avance Norte', 'IPS Avance Sur']

const certificado = readFileSync(`${SECRETOS}/ds.crt`, 'utf8')
const firmante = {
  clave: await importPKCS8(readFileSync(`${SECRETOS}/ds.key`, 'utf8'), 'ES256'),
  x5c: [certificado.replace(/-----[^-]+-----|\s/g, '')],
}
await iniciarAlmacen(process.env.AFILIADOS_ARCHIVO ?? '/datos/afiliados.json', { siguiente_indice: 1, afiliados: JSON.parse(readFileSync(new URL('./semilla.json', import.meta.url), 'utf8')), emisiones: [] })
const afiliados = crearAfiliados(process.env.AFILIADOS_ARCHIVO ?? '/datos/afiliados.json', new URL('./semilla.json', import.meta.url))
await iniciarAlmacen(process.env.MEDICOS_ARCHIVO ?? '/datos/medicos.json', [])
const medicos = crearMedicos(process.env.MEDICOS_ARCHIVO ?? '/datos/medicos.json')
const estado = crearEstado({ firmante, uri: `${URL_PUBLICA}/estado/1`, anulados: afiliados.anulados })
const abca = crearAbca({ trustValidator: requerida('TRUST_VALIDATOR_URL'), audiencia: URL_PUBLICA })
const emisor = crearEmisor({ url: URL_PUBLICA, eps: EPS, firmante, afiliados, estado, abca, trustValidator: process.env.TRUST_VALIDATOR_URL, auditor: crearAuditor({ rol: 'emisor', identificador: URL_PUBLICA, nombre: EPS, proyecto: 'verifiers/eps' }) })
// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: EPS, proyecto: 'verifiers/eps' }))

// --- Sesiones y solicitudes (en memoria) ------------------------------------------------------
const sesiones = new Map()
const solicitudes = new Map()
function sesion(req, res) {
  const id = /(?:^|;\s*)eps_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva })
  res.append('Set-Cookie', `eps_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}
setInterval(() => {
  const limite = Date.now() - 30 * 60 * 1000
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 60 * 1000).unref()

// Qué se le pide a la wallet en cada caso.
const PID = { vct: 'urn:eudi:pid:1', datos: ['personal_administrative_number', 'given_name', 'family_name', 'birthdate'] }
const CARNE = { vct: DOCUMENTOS.carne.vct, datos: ['eps', 'afiliado_nombre', 'afiliado_documento', 'regimen', 'plan', 'ips_primaria', 'estado'] }
const NOTARIA_URLS = [new URL('/notaria', URL_PUBLICA).href, new URL('/notaria-issuer/api', URL_PUBLICA).href]
const CONVIVENCIA = { vct: 'urn:co:notaria:convivencia:1', datos: ['numero', 'declarante_nombre', 'declarante_documento', 'companero_nombre', 'companero_documento', 'convivencia_desde', 'vigente_hasta'] }
const REGISTRO_CIVIL = { vct: 'urn:co:notaria:registro-civil:1', datos: ['numero', 'inscrito_nombre', 'inscrito_documento', 'fecha_nacimiento', 'progenitor_documento', 'calidad'] }
const PARTES_HISTORIA = ['grupo_sanguineo', 'alergias', 'condiciones_cronicas', 'medicamentos', 'vacunas', 'ultima_consulta', 'observaciones']

// El afiliado presenta su declaración de convivencia (notaría de confianza) para afiliar a su compañero(a).
function afiliarBeneficiario(solicitud, r) {
  const rechazo = (motivo) => ({ estado: 'rechazada', motivo })
  const d = r.datos
  const persona = sesiones.get(solicitud.sesion)?.persona
  if (!persona) return rechazo('Primero identifícate con tu wallet.')
  const hijo = r.vct === REGISTRO_CIVIL.vct
  if (!hijo && r.vct !== CONVIVENCIA.vct) return rechazo('La wallet no presentó una declaración de convivencia ni un registro civil.')
  if (!NOTARIA_URLS.includes(r.emisor)) return rechazo('El documento no lo emitió una notaría de confianza de este ecosistema.')
  const hoy = new Date().toISOString().slice(0, 10)
  if (hijo) {
    if (String(d.progenitor_documento) !== persona.documento) return rechazo('El registro civil no te señala como padre o madre: el declarante no coincide con quien se identificó.')
    // Hijos hasta los 18 años (simplificación del simulador; la norma real admite más edad en ciertos casos, sin verificar).
    const mayor = new Date(`${d.fecha_nacimiento}T00:00:00Z`)
    mayor.setUTCFullYear(mayor.getUTCFullYear() + 18)
    if (mayor.toISOString().slice(0, 10) <= hoy) return rechazo('El hijo o hija ya es mayor de edad: en este ejemplo solo se afilian menores de 18 años.')
  } else {
    if (String(d.declarante_documento) !== persona.documento) return rechazo('La declaración no es tuya: el declarante no coincide con quien se identificó.')
    if (!d.vigente_hasta || d.vigente_hasta < hoy) return rechazo('La declaración está vencida. Pide una nueva en la notaría.')
  }
  const a = afiliados.buscar(persona.documento)
  if (!a) return rechazo('No estás afiliado.')
  if (a.afiliacion.estado !== 'Activo') return rechazo('Tu afiliación no está activa.')
  if (a.afiliacion.tipo === 'Beneficiario') return rechazo('Eres beneficiario: solo el cotizante puede afiliar beneficiarios.')
  const b = hijo
    ? { documento: String(d.inscrito_documento), nombre: d.inscrito_nombre, parentesco: 'Hijo(a)', respaldo: `Registro civil ${d.numero}` }
    : { documento: String(d.companero_documento), nombre: d.companero_nombre, parentesco: 'Compañero(a) permanente', respaldo: `Declaración de convivencia ${d.numero}` }
  const res = afiliados.agregarBeneficiario(a.documento, b)
  if (res?.ya) return rechazo('Esa persona ya es tu beneficiaria.')
  console.log(`[beneficiario] afiliado con ${hijo ? 'registro civil' : 'declaración de convivencia'}`)
  return { estado: 'listo', beneficiario: { nombre: b.nombre, documento: b.documento } }
}

function completar(solicitud, r) {
  if (['convivencia', 'registro-civil'].includes(solicitud.proposito)) return afiliarBeneficiario(solicitud, r)
  if (!['identificacion', 'medico', 'registro-medico'].includes(solicitud.proposito)) {
    const tipo = porVct(r.vct)
    const def = DOCUMENTOS[tipo]
    return {
      estado: 'listo',
      documento: {
        tipo, nombre: def?.nombre ?? r.vct, emisor: r.emisor, de_esta_eps: r.emisor === URL_PUBLICA,
        emitido: r.emitido && new Date(r.emitido * 1000).toISOString(),
        // Solo lo que llegó, con el nombre legible de cada dato.
        datos: (def?.datos ?? []).filter(([k]) => k in r.datos).map(([k, n]) => [n, r.datos[k]]),
      },
    }
  }
  const d = r.datos
  if (!d.personal_administrative_number || !d.given_name || !d.family_name) {
    return { estado: 'rechazada', motivo: 'La wallet no compartió los datos necesarios para identificarte.' }
  }
  const s = sesiones.get(solicitud.sesion)
  if (solicitud.proposito === 'registro-medico') {
    // El administrador registra a un médico: los datos quedan en la solicitud
    // (verificados por el verifier) y de ahí los toma POST /api/admin/medicos.
    solicitud.persona = { documento: String(d.personal_administrative_number), nombre: `${d.given_name} ${d.family_name}` }
    const ya = medicos.buscar(solicitud.persona.documento)
    return { estado: 'listo', persona: solicitud.persona, ya_registrado: !!ya }
  }
  if (solicitud.proposito === 'medico') {
    // Solo médicos registrados por el administrador, y activos.
    const m = medicos.buscar(String(d.personal_administrative_number))
    if (!m) {
      // No registrado: puede solicitarlo con estos datos del PID (y su registro médico).
      if (s) s.candidato = { documento: String(d.personal_administrative_number), nombre: `${d.given_name} ${d.family_name}` }
      return { estado: 'listo', candidato: true }
    }
    if (m.pendiente) return { estado: 'rechazada', motivo: 'Tu solicitud de acceso como médico está pendiente: el administrador de la EPS tiene que aprobarla.' }
    if (!m.activo) return { estado: 'rechazada', motivo: 'Tu acceso como médico está desactivado.' }
    medicos.cambiar(m.documento, { ultimo_ingreso: new Date().toISOString() })
    if (s) s.medico = { documento: m.documento, nombre: `${d.given_name} ${d.family_name}`, registro: m.registro, especialidad: m.especialidad }
    console.log('[médico] entró un médico registrado')
    return { estado: 'listo' }
  }
  if (s) s.persona = { documento: String(d.personal_administrative_number), nombres: d.given_name, apellidos: d.family_name, fecha_nacimiento: d.birthdate ?? null }
  console.log('[identificación] un afiliado se identificó con su PID')
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

const vistaAfiliado = (a) => a && { ...a, credenciales: afiliados.emisionesDe(a.documento) }
const lista = (v) => (Array.isArray(v) ? v : String(v ?? '').split(/\n|;/)).map((x) => String(x).trim()).filter(Boolean).slice(0, 30)
const texto = (v, max) => String(v ?? '').trim().slice(0, max)

// --- Rutas ------------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: EPS, tema: {
  fondo: '#F3F8FC', panel: '#FFFFFF', texto: '#14234F', suave: '#5B6B85', borde: '#D8E6F0', titulo: '#14234F',
  marca: '#0B5A7A', marcaTexto: '#FFFFFF', acento: '#FF7A70', cabeceraFondo: '#14234F', cabeceraTexto: '#FFFFFF',
  ok: '#0B7A70', okFondo: '#E1F5F3', mal: '#B42318', malFondo: '#FEF3F2', neutroFondo: '#E6F3FB', hover: '#E6F3FB',
  fuente: 'Nunito, Segoe UI, system-ui, -apple-system, Roboto, sans-serif', fuenteTitulo: 'Nunito, Segoe UI, system-ui, sans-serif',
  pesoTitulo: 800, mayusculas: 'none', tracking: '-.5px', radio: '10px', logoAlto: 44, preFondo: '#14234F', preTexto: '#CFE0F2',
  logo: `${BASE}/publico/logo.png`, subtitulo: 'Salud con tu wallet',
  css: 'th{background:#E6F3FB;color:#0B5A7A;font-weight:700}.barra button{border-radius:10px}.k{border-radius:16px}'
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(emisor.router)

const r = express.Router()
r.use(express.json({ limit: '50kb' }))
const plantilla = (archivo) => readFileSync(new URL(`./publico/${archivo}`, import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{EPS}}', EPS).replaceAll('{{IPS}}', JSON.stringify(IPS))
const paginas = { portal: plantilla('index.html'), medico: plantilla('medico.html'), admin: plantilla('admin.html') }

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').send(paginas.portal) })
r.get('/medico', (req, res) => { sesion(req, res); res.type('html').send(paginas.medico) })
r.get('/admin', (req, res) => { sesion(req, res); res.type('html').send(paginas.admin) })
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))

r.post('/api/solicitud', async (req, res) => {
  const { proposito, partes, dispositivo } = req.body ?? {}
  const s = sesion(req, res)
  let pedido
  if (proposito === 'identificacion' || proposito === 'medico') pedido = PID
  else if (proposito === 'registro-medico') {
    if (!s.admin) return res.status(401).json({ error: 'Entra con la contraseña de administración.' })
    pedido = PID
  }
  else if (proposito === 'carne') pedido = CARNE
  else if (proposito === 'convivencia') {
    if (!s.persona) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
    pedido = CONVIVENCIA
  } else if (proposito === 'registro-civil') {
    if (!s.persona) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
    pedido = REGISTRO_CIVIL
  }
  else if (proposito === 'historia') {
    // El médico que recibe elige qué partes pedir; el paciente, en su wallet, cuáles dar.
    const elegidas = (Array.isArray(partes) ? partes : []).filter((p) => PARTES_HISTORIA.includes(p))
    if (!elegidas.length) return res.status(400).json({ error: 'Elige al menos una parte de la historia.' })
    pedido = { vct: DOCUMENTOS.historia.vct, datos: ['paciente_nombre', 'paciente_documento', ...elegidas] }
  } else return res.status(400).json({ error: 'proposito: identificacion, medico, carne, historia o convivencia' })
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      ...pedido, nonce: randomBytes(16).toString('base64url'),
      // El registro de un médico es siempre en otro dispositivo: el QR en la
      // pantalla del administrador, la wallet en el teléfono del médico.
      redireccion: dispositivo === 'mismo' && proposito !== 'registro-medico' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { proposito, transaccion, sesion: s.id, creada: Date.now() })
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
    if (final.estado !== 'listo') {
      const motivo = encodeURIComponent(final.motivo ?? 'La wallet todavía no ha respondido.')
      return solicitud.proposito === 'medico' ? res.redirect(`${BASE}/medico#error=${motivo}`) : ir(`error=${motivo}`)
    }
    if (solicitud.proposito === 'medico') return res.redirect(`${BASE}/medico#entrado`)
    if (['convivencia', 'registro-civil'].includes(solicitud.proposito)) return ir('mi-eps')
    return ir(solicitud.proposito === 'identificacion' ? 'identificado' : `verificado=${s.pendiente}`)
  } catch {
    return ir(`error=${encodeURIComponent('El servicio de verificación no está disponible.')}`)
  }
})

// --- Afiliado -------------------------------------------------------------------------------------
const conPersona = (fn) => (req, res) => {
  const p = sesion(req, res).persona
  if (!p) return res.status(401).json({ error: 'Primero identifícate con tu wallet.' })
  return fn(req, res, p)
}

r.get('/api/yo', conPersona((_req, res, p) => res.json({ persona: p, afiliado: vistaAfiliado(afiliados.buscar(p.documento)), ips: IPS })))
r.post('/api/salir', (req, res) => { delete sesion(req, res).persona; res.json({ ok: true }) })

r.post('/api/afiliacion', conPersona((req, res, p) => {
  const { regimen, tipo, ips } = req.body ?? {}
  if (!['Contributivo', 'Subsidiado'].includes(regimen) || !IPS.includes(ips)) return res.status(400).json({ error: 'Elige régimen e IPS.' })
  const tipos = regimen === 'Contributivo' ? ['Cotizante', 'Beneficiario'] : ['Cabeza de familia', 'Beneficiario']
  try {
    const a = afiliados.afiliar({ ...p, regimen, tipo: tipos.includes(tipo) ? tipo : tipos[0], ips })
    console.log('[afiliación] nueva afiliación')
    res.status(201).json(vistaAfiliado(a))
  } catch {
    res.status(409).json({ error: 'Ya estás afiliado.' })
  }
}))

r.post('/api/oferta', conPersona(async (req, res, p) => {
  const tipo = req.body?.tipo
  const a = afiliados.buscar(p.documento)
  if (!a) return res.status(404).json({ error: 'No estás afiliado.' })
  if (!DOCUMENTOS[tipo]) return res.status(400).json({ error: 'tipo: carne o historia' })
  if (tipo === 'carne' && a.afiliacion.estado !== 'Activo') return res.status(409).json({ error: `Tu afiliación está ${a.afiliacion.estado.toLowerCase()}.` })
  const o = emisor.ofrecer(a.documento, tipo, req.body?.reexpedir === true)
  res.json({ ...o, qr: await QRCode.toDataURL(o.enlace, { margin: 1, width: 320 }) })
}))

r.post('/api/credenciales/:indice/revocar', conPersona((req, res, p) => {
  const e = afiliados.revocar(p.documento, Number(req.params.indice))
  if (!e) return res.status(404).json({ error: 'No es una credencial tuya.' })
  estado.invalidar()
  res.json(e)
}))

// --- Médico (entra con la wallet; ver completar) ----------------------------------------------------
const conMedico = (fn) => (req, res) => {
  const m = sesion(req, res).medico
  if (!m) return res.status(401).json({ error: 'Entra con tu wallet.' })
  // Si el administrador lo desactiva, se le cierra la sesión en la siguiente petición.
  if (!medicos.buscar(m.documento)?.activo) { delete sesion(req, res).medico; return res.status(403).json({ error: 'Tu acceso como médico está desactivado.' }) }
  return fn(req, res, `${m.nombre} (RM ${m.registro})`)
}
r.post('/api/medico/salir', (req, res) => { const s = sesion(req, res); delete s.medico; delete s.candidato; res.json({ ok: true }) })

// Autorregistro: quien se identificó con su PID y no está registrado pide acceso.
// Nombre y cédula salen del PID; él solo pone su registro médico y especialidad.
r.get('/api/medico/candidato', (req, res) => {
  const c = sesion(req, res).candidato
  return c ? res.json(c) : res.status(404).json({ error: 'Identifícate primero con tu wallet.' })
})
r.post('/api/medico/solicitud', (req, res) => {
  const s = sesion(req, res)
  if (!s.candidato) return res.status(401).json({ error: 'Identifícate primero con tu wallet.' })
  const registro = texto(req.body?.registro, 30)
  if (!registro) return res.status(400).json({ error: 'Falta tu registro médico (RETHUS).' })
  try {
    const m = medicos.registrar({ ...s.candidato, registro, especialidad: texto(req.body?.especialidad, 80) || 'Medicina general', solicitud: true })
    delete s.candidato
    console.log('[médico] nueva solicitud de acceso (pendiente de aprobación)')
    res.status(201).json(m)
  } catch {
    res.status(409).json({ error: 'Ya hay una solicitud o un registro con tu documento.' })
  }
})
r.get('/api/medico/yo', conMedico((req, res) => res.json({ medico: sesion(req, res).medico, ips: IPS })))
r.get('/api/medico/afiliados', conMedico((req, res) => {
  const q = texto(req.query.q, 60).toLowerCase()
  res.json(afiliados.todos()
    .filter((a) => !q || a.documento.includes(q) || `${a.nombres} ${a.apellidos}`.toLowerCase().includes(q))
    .map((a) => ({ documento: a.documento, nombre: `${a.nombres} ${a.apellidos}`, estado: a.afiliacion.estado, actualizada: a.historia.actualizada })))
}))
r.get('/api/medico/afiliados/:doc', conMedico((req, res) => {
  const a = afiliados.buscar(req.params.doc)
  return a ? res.json(vistaAfiliado(a)) : res.status(404).json({ error: 'No existe.' })
}))
r.put('/api/medico/afiliados/:doc/historia', conMedico((req, res, medico) => {
  const b = req.body ?? {}
  const c = b.ultima_consulta ?? {}
  const a = afiliados.actualizarHistoria(req.params.doc, {
    grupo_sanguineo: ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', ''].includes(b.grupo_sanguineo) ? b.grupo_sanguineo : '',
    alergias: lista(b.alergias), condiciones: lista(b.condiciones), medicamentos: lista(b.medicamentos), vacunas: lista(b.vacunas),
    ultima_consulta: c.fecha ? { fecha: texto(c.fecha, 10), servicio: texto(c.servicio, 60), motivo: texto(c.motivo, 120), diagnostico: texto(c.diagnostico, 120) } : null,
    observaciones: texto(b.observaciones, 500),
  }, medico)
  if (!a) return res.status(404).json({ error: 'No existe.' })
  estado.invalidar()
  console.log('[médico] historia actualizada (las entregadas antes quedan reemplazadas)')
  res.json(vistaAfiliado(a))
}))
r.put('/api/medico/afiliados/:doc/afiliacion', conMedico((req, res) => {
  const b = req.body ?? {}
  const cambios = {}
  if (['Activo', 'Suspendido', 'Retirado'].includes(b.estado)) cambios.estado = b.estado
  if (IPS.includes(b.ips)) cambios.ips = b.ips
  const a = afiliados.actualizarAfiliacion(req.params.doc, cambios)
  if (!a) return res.status(404).json({ error: 'No existe.' })
  estado.invalidar()
  res.json(vistaAfiliado(a))
}))

// --- Administración (contraseña): registrar médicos ------------------------------------------------
const claveAdmin = (dada) => {
  const a = Buffer.from(String(dada ?? '')), b = Buffer.from(CLAVE_ADMIN)
  return CLAVE_ADMIN !== '' && a.length === b.length && timingSafeEqual(a, b)
}
r.post('/api/admin/entrar', (req, res) => {
  if (!claveAdmin(req.body?.clave)) return res.status(401).json({ error: 'Contraseña incorrecta.' })
  sesion(req, res).admin = true
  res.json({ ok: true })
})
r.post('/api/admin/salir', (req, res) => { delete sesion(req, res).admin; res.json({ ok: true }) })
const conAdmin = (fn) => (req, res) => (sesion(req, res).admin ? fn(req, res) : res.status(401).json({ error: 'Entra con la contraseña de administración.' }))
r.get('/api/admin/medicos', conAdmin((_req, res) => res.json(medicos.todos())))
// Registrar un médico: su cédula y nombre NO los escribe el administrador; salen
// del PID que el médico presentó (solicitud 'registro-medico' de esta sesión).
r.post('/api/admin/medicos', conAdmin((req, res) => {
  const b = req.body ?? {}
  const sol = solicitudes.get(String(b.solicitud ?? ''))
  if (!sol || sol.sesion !== sesion(req, res).id || sol.proposito !== 'registro-medico' || sol.final?.estado !== 'listo' || !sol.persona) {
    return res.status(400).json({ error: 'Primero el médico tiene que presentar su documento de identidad desde su wallet.' })
  }
  if (!texto(b.registro, 30)) return res.status(400).json({ error: 'Falta el registro médico (RETHUS).' })
  try {
    const m = medicos.registrar({ ...sol.persona, registro: texto(b.registro, 30), especialidad: texto(b.especialidad, 80) || 'Medicina general' })
    solicitudes.delete(String(b.solicitud))
    res.status(201).json(m)
  } catch {
    res.status(409).json({ error: 'Ese médico ya está registrado.' })
  }
}))
r.put('/api/admin/medicos/:doc', conAdmin((req, res) => {
  const b = req.body ?? {}
  const cambios = {}
  // Activar o desactivar también resuelve una solicitud pendiente (aprobar / rechazar).
  if (typeof b.activo === 'boolean') Object.assign(cambios, { activo: b.activo, pendiente: false, ...(b.activo ? { aprobado: new Date().toISOString() } : {}) })
  if (b.especialidad !== undefined) cambios.especialidad = texto(b.especialidad, 80)
  if (b.registro !== undefined) cambios.registro = texto(b.registro, 30)
  const m = medicos.cambiar(req.params.doc, cambios)
  return m ? res.json(m) : res.status(404).json({ error: 'No existe.' })
}))
r.delete('/api/admin/medicos/:doc', conAdmin((req, res) => (medicos.eliminar(req.params.doc) ? res.json({ ok: true }) : res.status(404).json({ error: 'No existe.' }))))

app.use(BASE || '/', r)
app.listen(Number(process.env.PUERTO ?? 80), () => console.log(`${EPS} escuchando en ${BASE} (emisor ${URL_PUBLICA})`))

// Portal de admisiones y becas (Corporación Avance): decide si una persona cumple los requisitos de una inscripción o
// de una beca combinando DOS credenciales de emisores distintos en una sola presentación:
//
//   PID (Registraduría)            solo «mayor de 18 años» (age_equal_or_over.18). Ni nombre ni fecha de nacimiento.
//   Credencial académica (UIS)     nivel y estado (filtros por valor: la wallet solo ofrece las que los cumplen),
//                                  más programa, título y promedio acumulado.
//
// Es la presentación combinada del ARF (tema K): una universidad pre-evalúa con varias atestaciones sin saber quién
// es la persona, y la identificación se pide DESPUÉS, en otra presentación, solo si la persona decide continuar.
// Los umbrales (promedio mínimo) se comprueban aquí: la credencial no permite una prueba de «promedio ≥ X» sin
// revelar el promedio, y eso se le dice a la persona antes de aceptar.
//
// La verificación (firmas, emisores de confianza, estado y prueba de posesión de cada credencial) la hace el verifier
// de su instancia (../_comun/levantar.sh beca); aquí solo se decide qué pedir y qué se acepta.
//
// Privacidad: no se guarda nada en disco ni en el log (solo «convocatoria, paso, resultado»). Los resultados viven en
// memoria unos minutos para mostrarse y se borran.
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA      https://civica-desarrollo.avance.org.co/beca (el servidor escucha bajo esa ruta)
//   VERIFIER_API     http://verifier-beca/verifier-beca (red interna)
//   NOMBRE_ENTIDAD   el nombre que se muestra
//   PUERTO           puerto interno (80: srv-nw-nginx llama a app-beca:80)

import { randomBytes, randomInt } from 'node:crypto'
import { readFileSync } from 'node:fs'
import express from 'express'
import QRCode from 'qrcode'
import { crearVerifier } from './verifier.js'
import { crearAuditor, auditarVerifier } from './auditoria.js'
import { montarPanelAuditoria } from './historial.js'

const requerida = (n) => process.env[n] || (() => { throw new Error(`Falta la variable de entorno ${n}`) })()
const URL_PUBLICA = requerida('URL_PUBLICA').replace(/\/$/, '')
const BASE = new URL(URL_PUBLICA).pathname.replace(/\/$/, '')
const NOMBRE = process.env.NOMBRE_ENTIDAD ?? 'Admisiones y Becas Avance'
const PUERTO = Number(process.env.PUERTO ?? 80)
const VENCE_SOLICITUD_MS = 5 * 60 * 1000
const CONSERVA_RESULTADO_MS = 15 * 60 * 1000

const VCT_PID = 'urn:eudi:pid:1'
const VCT_ACADEMICA = 'urn:eudi:academic:credential:1'

// Las convocatorias. `nivel` y `estado` son los valores exactos que emite la universidad (academic_level y
// academic_status); `promedio_min` es el mínimo del promedio acumulado (escala 0 a 5 de la UIS).
const CONVOCATORIAS = [
  {
    clave: 'inscripcion-maestria', tipo: 'Inscripción', nombre: 'Inscripción a maestría',
    descripcion: 'Para personas graduadas de pregrado que quieren inscribirse a una maestría.',
    nivel: 'PREGRADO', estado: 'GRADUADO', promedio_min: 3.0,
  },
  {
    clave: 'beca-excelencia', tipo: 'Beca', nombre: 'Beca de excelencia para maestría',
    descripcion: 'Cubre la matrícula de la maestría a graduados de pregrado con promedio sobresaliente.',
    nivel: 'PREGRADO', estado: 'GRADUADO', promedio_min: 4.0,
  },
  {
    clave: 'beca-continuidad', tipo: 'Beca', nombre: 'Beca de continuidad',
    descripcion: 'Apoya a estudiantes activos de pregrado con buen rendimiento para que sigan estudiando.',
    nivel: 'PREGRADO', estado: 'ACTIVO', promedio_min: 3.8,
  },
]
const convocatoriaDe = (clave) => CONVOCATORIAS.find((c) => c.clave === clave)

const ESTADOS = { GRADUADO: 'graduado/a', ACTIVO: 'estudiante activo/a' }
const NIVELES = { PREGRADO: 'pregrado' }

// Qué se pide en cada paso.
const DATOS_PID_PREEVALUACION = [['age_equal_or_over', '18']]
const DATOS_ACADEMICOS = ['accumulated_average', 'program_name', 'awarded_title']
const DATOS_PID_IDENTIDAD = ['personal_administrative_number', 'given_name', 'family_name']

// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/beca' }))
const solicitudes = new Map() // id → { paso, convocatoria, transaccion, sesion, creada, final? }
const pendientes = new Map()  // sesión → última solicitud (a la que vuelve la wallet en el mismo teléfono)
const sesiones = new Set()    // ids de sesión (cookie): cada resultado solo se entrega a la sesión que lo pidió
const sesionDe = (req) => /(?:^|;\s*)beca_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
function sesion(req, res) {
  const id = sesionDe(req)
  if (id && sesiones.has(id)) return id
  const nueva = randomBytes(24).toString('base64url')
  sesiones.add(nueva)
  res.append('Set-Cookie', `beca_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return nueva
}

setInterval(() => {
  const ahora = Date.now()
  for (const [id, s] of solicitudes) {
    if (ahora - (s.finalizada ?? s.creada) > CONSERVA_RESULTADO_MS) solicitudes.delete(id)
  }
  const vivas = new Set([...solicitudes.values()].map((s) => s.sesion))
  for (const id of sesiones) if (!vivas.has(id)) { sesiones.delete(id); pendientes.delete(id) }   // solo se conservan las que tienen solicitudes
}, 60 * 1000).unref()

function pedido(convocatoria, paso) {
  if (paso === 'identidad') return [{ id: 'pid', vct: VCT_PID, datos: DATOS_PID_IDENTIDAD }]
  return [
    { id: 'pid', vct: VCT_PID, datos: DATOS_PID_PREEVALUACION },
    {
      id: 'academica', vct: VCT_ACADEMICA,
      filtros: { academic_level: convocatoria.nivel, academic_status: convocatoria.estado },
      datos: DATOS_ACADEMICOS,
    },
  ]
}

// --- Qué se acepta y cómo se evalúa --------------------------------------------------------------
function evaluar(convocatoria, { pid, academica }) {
  const criterios = []
  const mayor = pid.datos['18'] === true
  criterios.push({ nombre: 'Mayor de 18 años', cumple: mayor, detalle: mayor ? 'Confirmado con el documento de identidad digital.' : 'No se pudo confirmar la mayoría de edad.' })

  const a = academica.datos
  const nivelOk = a.academic_level === convocatoria.nivel
  const estadoOk = a.academic_status === convocatoria.estado
  criterios.push({
    nombre: `${ESTADOS[convocatoria.estado][0].toUpperCase()}${ESTADOS[convocatoria.estado].slice(1)} de ${NIVELES[convocatoria.nivel]}`,
    cumple: nivelOk && estadoOk,
    detalle: nivelOk && estadoOk ? (a.program_name ? `Programa: ${a.program_name}.` : 'Credencial académica verificada.') : 'La credencial no corresponde a lo que pide la convocatoria.',
  })

  const promedio = Number(String(a.accumulated_average ?? '').replace(',', '.'))
  const hay = Number.isFinite(promedio) && a.accumulated_average !== undefined && a.accumulated_average !== ''
  criterios.push({
    nombre: `Promedio acumulado de ${convocatoria.promedio_min.toFixed(1)} o más`,
    cumple: hay && promedio >= convocatoria.promedio_min,
    detalle: hay ? `Tu promedio acumulado es ${promedio.toFixed(2)}.` : 'La credencial no trae el promedio.',
  })

  return {
    cumple: criterios.every((c) => c.cumple),
    criterios,
    datos: {
      programa: a.program_name ?? null,
      titulo: a.awarded_title ?? null,
      promedio: hay ? promedio : null,
      institucion: a.issuing_institution_name ?? a.issuing_authority ?? null,
    },
  }
}

function validar(solicitud, r) {
  const { pid, academica } = r.credenciales
  if (!pid || pid.vct !== VCT_PID) return 'Falta el documento de identidad digital (PID).'
  if (solicitud.paso === 'identidad') {
    for (const d of DATOS_PID_IDENTIDAD) if (!pid.datos[d]) return `La wallet no compartió un dato necesario (${d}).`
    return null
  }
  if (!academica || academica.vct !== VCT_ACADEMICA) return 'Falta la credencial académica.'
  return null
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) {
      return fin(solicitud, { estado: 'rechazada', motivo: 'La solicitud venció. Genera un código nuevo.' })
    }
    return r
  }
  if (r.estado === 'error') return fin(solicitud, { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` })
  const motivo = validar(solicitud, r)
  if (motivo) return fin(solicitud, { estado: 'rechazada', motivo })

  if (solicitud.paso === 'identidad') {
    const d = r.credenciales.pid.datos
    const radicado = `${solicitud.convocatoria.tipo === 'Beca' ? 'BEC' : 'INS'}-${new Date().getFullYear()}-${String(randomInt(0, 100000)).padStart(5, '0')}`
    return fin(solicitud, {
      estado: 'listo',
      radicado,
      identidad: { nombres: d.given_name, apellidos: d.family_name, documento: String(d.personal_administrative_number) },
    })
  }
  const evaluacion = evaluar(solicitud.convocatoria, r.credenciales)
  solicitud.cumple = evaluacion.cumple
  return fin(solicitud, { estado: 'listo', evaluacion })
}

function fin(solicitud, final) {
  solicitud.final = final
  solicitud.finalizada = Date.now()
  // Solo el resultado, nunca datos de la persona: es lo único que sale de aquí y del log.
  const resultado = final.estado === 'listo' ? (final.evaluacion ? (final.evaluacion.cumple ? 'cumple' : 'no cumple') : 'radicada') : 'rechazada'
  console.log(`[${solicitud.paso}] ${solicitud.convocatoria.clave}: ${resultado}`)
  return final
}

// --- Rutas ---------------------------------------------------------------------------------------
const app = express()
montarPanelAuditoria(app, {
  base: BASE, nombre: NOMBRE,
  tema: {
    fondo: '#f2f2f2', panel: '#ffffff', texto: '#1a1a1a', suave: '#5b5b5d', borde: '#dcdcdc', titulo: '#1a1a1a',
    marca: '#A31F34', marcaTexto: '#ffffff', acento: '#A31F34', cabeceraFondo: '#ffffff', cabeceraTexto: '#1a1a1a',
    ok: '#14733f', okFondo: '#e7f4ec', mal: '#A31F34', malFondo: '#fbeaed', neutroFondo: '#ececec', hover: '#f7f7f7',
    fuente: "'Inter','Helvetica Neue',Helvetica,Arial,sans-serif", pesoTitulo: 600, tracking: '-.02em', radio: '0px', logoAlto: 34,
    preFondo: '#1a1a1a', preTexto: '#e6e6e6', logo: `${BASE}/publico/marca.svg`, subtitulo: 'Historial local',
    fuentes: `@font-face{font-family:'Inter';font-weight:300 700;font-display:swap;src:url('${BASE}/publico/fuentes/inter-latin.woff2') format('woff2')}@font-face{font-family:'Inter';font-weight:300 700;font-display:swap;src:url('${BASE}/publico/fuentes/inter-latin-ext.woff2') format('woff2')}`,
  },
})   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(express.json())
const r = express.Router()
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{NOMBRE}}', NOMBRE)

r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))
r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (req, res) => { sesion(req, res); res.type('html').set('Cache-Control', 'no-store').send(pagina) })
r.get('/api/convocatorias', (_req, res) => res.json({
  convocatorias: CONVOCATORIAS.map(({ clave, tipo, nombre, descripcion, nivel, estado, promedio_min }) => ({
    clave, tipo, nombre, descripcion, nivel, estado, promedio_min,
    requisitos: ['Ser mayor de 18 años', `${ESTADOS[estado][0].toUpperCase()}${ESTADOS[estado].slice(1)} de ${NIVELES[nivel]}`, `Promedio acumulado de ${promedio_min.toFixed(1)} o más`],
  })),
  pide: { pid: ['Solo «mayor de 18 años»'], academica: ['Nivel y estado académico', 'Programa y título', 'Promedio acumulado'] },
}))

// Crea la solicitud de una convocatoria (paso 'preevaluacion') o la confirmación de identidad (paso 'identidad', que
// continúa una pre-evaluación que cumplió).
r.post('/api/solicitud', async (req, res) => {
  const { convocatoria: clave, paso = 'preevaluacion', previa, dispositivo } = req.body ?? {}
  const ses = sesion(req, res)
  if (!['preevaluacion', 'identidad'].includes(paso)) return res.status(400).json({ error: 'paso: preevaluacion o identidad' })
  const convocatoria = convocatoriaDe(clave)
  if (!convocatoria) return res.status(400).json({ error: 'convocatoria desconocida' })
  if (paso === 'identidad') {
    const anterior = solicitudes.get(previa)
    if (anterior?.sesion !== ses || anterior?.final?.estado !== 'listo' || anterior.cumple !== true || anterior.convocatoria.clave !== clave) {
      return res.status(400).json({ error: 'primero hay que cumplir los requisitos de esa convocatoria' })
    }
  }
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      credenciales: pedido(convocatoria, paso),
      nonce: randomBytes(16).toString('base64url'),
      // En el mismo teléfono la wallet vuelve al portal al terminar.
      redireccion: dispositivo === 'mismo' ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { paso, convocatoria, transaccion, sesion: ses, creada: Date.now() })
    pendientes.set(ses, id)
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 360 }), vence_en: VENCE_SOLICITUD_MS / 1000 })
  } catch (e) {
    console.error(`[${paso}] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})

r.get('/api/solicitud/:id', async (req, res) => {
  const solicitud = solicitudes.get(req.params.id)
  if (!solicitud || solicitud.sesion !== sesionDe(req)) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  try {
    // `clave` y `paso` permiten a la página reubicar el resultado cuando vuelve de la wallet (mismo teléfono).
    res.json({ ...(await consultar(solicitud)), clave: solicitud.convocatoria.clave, paso: solicitud.paso })
  } catch (e) {
    console.error(`[${solicitud.paso}] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

// A donde vuelve la wallet en el mismo teléfono, con el código que libera el resultado. Deja a la persona en la
// portada con ?volver=<id>: la página pide el resultado igual que cuando el sondeo lo encuentra tras el escaneo.
r.get('/volver', async (req, res) => {
  const ses = sesionDe(req)
  const id = pendientes.get(ses)
  const solicitud = ses && solicitudes.get(id)
  const fallo = (motivo) => res.redirect(`${BASE}/?error=${encodeURIComponent(motivo)}`)
  if (!solicitud || solicitud.sesion !== ses || !req.query.response_code) return fallo('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')
  try {
    const final = await consultar(solicitud, String(req.query.response_code))
    if (final.estado === 'listo') return res.redirect(`${BASE}/?volver=${encodeURIComponent(id)}`)
    return fallo(final.motivo ?? 'La wallet todavía no ha respondido.')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return fallo('El servicio de verificación no está disponible.')
  }
})

app.use(BASE || '/', r)
app.listen(PUERTO, () => console.log(`${NOMBRE}: admisiones y becas en :${PUERTO}${BASE} (verifier ${process.env.VERIFIER_API})`))

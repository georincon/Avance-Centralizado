// Portal de un fondo de pensiones (ficticio): comprueba la SUPERVIVENCIA de un pensionado, que es lo que cada cierto tiempo exige un fondo
// para seguir pagando la mesada. Con la wallet se hace sin ir a una oficina:
//
//   Fe de vida (notaría)   constancia de que la persona vive, con su vigencia (30 días). La emite una notaría con su PID.
//   PID (Registraduría)    nombre y documento de quien se presenta.
//
// Las dos van en UNA presentación (dos credenciales de emisores distintos). El fondo comprueba que la fe de vida la emitió una notaría en la que
// confía, que no está vencida y que es de la misma persona que se identificó. No se guarda nada: el registro de la jornada vive en memoria y
// solo lleva iniciales y los últimos dígitos del documento.
//
// Variables de entorno (las pone compose.yml a partir de .env):
//   URL_PUBLICA      https://civica-desarrollo.avance.org.co/pensiones
//   VERIFIER_API     http://verifier-pensiones/verifier-pensiones (red interna)
//   NOMBRE_ENTIDAD   el nombre que se muestra
//   PUERTO           80

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
const NOMBRE = process.env.NOMBRE_ENTIDAD ?? 'Fondo de Pensiones Horizonte'
const PUERTO = Number(process.env.PUERTO ?? 80)
const VENCE_SOLICITUD_MS = 5 * 60 * 1000
const CONSERVA_MS = 60 * 60 * 1000

const VCT_PID = 'urn:eudi:pid:1'
const VCT_FE_DE_VIDA = 'urn:co:notaria:fe-de-vida:1'
// La fe de vida solo vale si la emitió una notaría en la que el fondo confía: la del ecosistema (mismo dominio, prefijo /notaria).
const ORIGEN = new URL(URL_PUBLICA).origin
const EMISORES_NOTARIA = [`${ORIGEN}/notaria`, `${ORIGEN}/notaria-issuer/api`]

// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API') }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/pensiones' }))
const solicitudes = new Map()  // id → { transaccion, creada, final? }
const sesiones = new Map()     // id → { id, pendiente? } (cookie HttpOnly: la wallet vuelve en el mismo navegador)
const pensionados = new Map()  // documento → { hora, vence } última supervivencia acreditada (la mesada depende de ella)
const certificados = new Map() // código → datos del certificado emitido (para verificarlo)
const acreditados = []         // { hora, pensionado, documento, vence } más reciente primero

const normal = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase().replace(/\s+/g, ' ').trim()
const digitos = (t) => String(t ?? '').replace(/\D/g, '')
const iniciales = (n) => String(n).split(/\s+/).filter(Boolean).map((x) => `${x[0].toUpperCase()}.`).join(' ')

setInterval(() => {
  const ahora = Date.now()
  for (const [id, s] of solicitudes) if (ahora - (s.finalizada ?? s.creada) > 15 * 60 * 1000) solicitudes.delete(id)
  for (const [id, x] of sesiones) if (ahora - x.creada > 60 * 60 * 1000) sesiones.delete(id)
  while (acreditados.length && ahora - acreditados[acreditados.length - 1].hora > CONSERVA_MS) acreditados.pop()
}, 60 * 1000).unref()

function sesion(req, res) {
  const id = /(?:^|;\s*)pensiones_sesion=([\w-]+)/.exec(req.headers.cookie ?? '')?.[1]
  if (id && sesiones.has(id)) return sesiones.get(id)
  const nueva = randomBytes(24).toString('base64url')
  sesiones.set(nueva, { id: nueva, creada: Date.now() })
  res.append('Set-Cookie', `pensiones_sesion=${nueva}; Path=${BASE || '/'}; HttpOnly; Secure; SameSite=Lax`)
  return sesiones.get(nueva)
}

const PEDIDO_PID = { id: 'pid', vct: VCT_PID, datos: ['personal_administrative_number', 'given_name', 'family_name'] }
// 'ingreso': solo el PID (inicio de sesión). 'supervivencia': la fe de vida; el PID ya lo dio al ingresar.
const PEDIDO = (tipo) => tipo === 'ingreso' ? [PEDIDO_PID] : [
  { id: 'fe', vct: VCT_FE_DE_VIDA, datos: ['numero', 'fecha', 'titular_nombre', 'titular_documento', 'entidad_destino', 'vigente_hasta'] },
]

function evaluar({ fe, pid }) {
  const f = fe.datos
  const d = pid.datos
  const hoy = new Date().toISOString().slice(0, 10)
  const nombrePid = `${d.given_name} ${d.family_name}`
  const criterios = [
    { nombre: 'Fe de vida emitida por una notaría de confianza', cumple: EMISORES_NOTARIA.includes(fe.emisor), detalle: EMISORES_NOTARIA.includes(fe.emisor) ? `Constancia n.º ${f.numero}, firmada por la notaría y vigente (no revocada).` : `La emitió otra entidad (${fe.emisor}).` },
    { nombre: 'Es de la persona que se identificó (documento)', cumple: digitos(f.titular_documento) !== '' && digitos(f.titular_documento) === digitos(d.personal_administrative_number), detalle: `Titular de la constancia: documento ${f.titular_documento}. Documento presentado: ${d.personal_administrative_number}.` },
    { nombre: 'El nombre coincide', cumple: normal(f.titular_nombre) === normal(nombrePid), detalle: `Constancia: ${f.titular_nombre}. Documento: ${nombrePid}.` },
    { nombre: 'La fe de vida está vigente', cumple: /^\d{4}-\d{2}-\d{2}$/.test(f.vigente_hasta ?? '') && f.vigente_hasta >= hoy, detalle: `Válida hasta el ${f.vigente_hasta}.` },
  ]
  return {
    acreditada: criterios.every((c) => c.cumple),
    criterios,
    datos: { pensionado: nombrePid, documento: String(d.personal_administrative_number), diligencia: f.fecha, vence: f.vigente_hasta, notaria_numero: f.numero, destino: f.entidad_destino ?? null },
  }
}

async function consultar(solicitud, responseCode) {
  if (solicitud.final) return solicitud.final
  const r = await verifier.resultado(solicitud.transaccion, responseCode)
  if (r.estado === 'esperando') {
    if (Date.now() - solicitud.creada > VENCE_SOLICITUD_MS) return fin(solicitud, { estado: 'rechazada', motivo: 'El código venció. Genera uno nuevo.' })
    return r
  }
  if (r.estado === 'error') return fin(solicitud, { estado: 'rechazada', motivo: `No se pudo verificar: ${r.motivo}.` })
  if (solicitud.tipo === 'ingreso') {
    const pid = r.credenciales.pid
    if (!pid || pid.vct !== VCT_PID) return fin(solicitud, { estado: 'rechazada', motivo: 'Falta el documento de identidad digital (PID).' })
    const nombre = `${pid.datos.given_name} ${pid.datos.family_name}`
    const documento = String(pid.datos.personal_administrative_number)
    const ses = sesiones.get(solicitud.sesion)
    if (ses) ses.pensionado = { nombre, documento }
    console.log('[ingreso] sesión iniciada con PID')
    return fin(solicitud, { estado: 'listo', ingreso: { nombre, documento: `•••${digitos(documento).slice(-3)}` } })
  }
  const ses = sesiones.get(solicitud.sesion)
  if (!ses?.pensionado) return fin(solicitud, { estado: 'rechazada', motivo: 'Inicia sesión con tu documento de identidad (PID) para continuar.' })
  const { fe } = r.credenciales
  const pid = { vct: VCT_PID, datos: { given_name: ses.pensionado.nombre.split(' ')[0], family_name: ses.pensionado.nombre.split(' ').slice(1).join(' '), personal_administrative_number: ses.pensionado.documento } }
  if (!fe || fe.vct !== VCT_FE_DE_VIDA) return fin(solicitud, { estado: 'rechazada', motivo: 'Falta la fe de vida.' })
  if (!pid || pid.vct !== VCT_PID) return fin(solicitud, { estado: 'rechazada', motivo: 'Falta el documento de identidad digital (PID).' })
  const ev = evaluar({ fe, pid })
  if (ev.acreditada) {
    pensionados.set(digitos(ev.datos.documento), { hora: Date.now(), vence: ev.datos.vence, notaria_numero: ev.datos.notaria_numero })
    acreditados.unshift({ hora: Date.now(), pensionado: iniciales(ev.datos.pensionado), documento: `•••${digitos(ev.datos.documento).slice(-3)}`, vence: ev.datos.vence })
    acreditados.length = Math.min(acreditados.length, 50)
  }
  console.log(`[supervivencia] ${ev.acreditada ? 'acreditada' : 'no acreditada'}`)
  return fin(solicitud, { estado: 'listo', supervivencia: ev })
}
function fin(solicitud, final) { solicitud.final = final; solicitud.finalizada = Date.now(); return final }

// ---- Certificado de pensionado y desprendible de mesada (datos simulados, deterministas por documento) ----
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
const SMMLV = 1_423_500
const pesos = (n) => Math.round(n)
function mesadaBase(documento) {
  const h = [...digitos(documento)].reduce((a, c) => (a * 31 + Number(c)) % 9973, 7)
  return SMMLV + (h % 30) * 100_000   // entre 1 y ~3,9 SMMLV
}
const hoyISO = () => new Date().toISOString().slice(0, 10)
const vigente = (documento) => { const a = pensionados.get(digitos(documento)); return a && a.vence >= hoyISO() ? a : null }
function desprendible(p, aaaa, mm) {
  const base = mesadaBase(p.documento)
  const adicional = (mm === 6 || mm === 12) ? base : 0
  const salud = pesos(base * 0.12)
  const solidaridad = base > 4 * SMMLV ? pesos(base * 0.01) : 0
  const devengado = base + adicional
  const descuentos = salud + solidaridad
  const actual = aaaa === new Date().getUTCFullYear() && mm === new Date().getUTCMonth() + 1
  const estado = actual && !vigente(p.documento) ? 'retenida' : 'pagada'
  return {
    periodo: `${MESES[mm - 1]} de ${aaaa}`, clave: `${aaaa}-${String(mm).padStart(2, '0')}`, estado,
    pago: estado === 'pagada' ? `${aaaa}-${String(mm).padStart(2, '0')}-25` : null,
    conceptos: [
      { concepto: 'Mesada pensional', devengado: base, deduccion: 0 },
      ...(adicional ? [{ concepto: `Mesada adicional (${MESES[mm - 1]})`, devengado: adicional, deduccion: 0 }] : []),
      { concepto: 'Aporte a salud (12%)', devengado: 0, deduccion: salud },
      ...(solidaridad ? [{ concepto: 'Fondo de solidaridad pensional (1%)', devengado: 0, deduccion: solidaridad }] : []),
    ],
    total_devengado: devengado, total_deducciones: descuentos, neto: devengado - descuentos,
    nota: estado === 'retenida' ? 'Retenida hasta acreditar la prueba de supervivencia vigente.' : null,
  }
}
function requierePensionado(req, res) {
  const p = sesion(req, res).pensionado
  if (!p) { res.status(401).json({ error: 'Inicia sesión con tu documento de identidad (PID).' }); return null }
  return p
}

const PREGUNTAS = [
  ['Prueba de supervivencia', '¿Qué es la prueba de supervivencia?', 'Es la comprobación periódica de que el pensionado sigue con vida. Mientras esté vigente, la mesada se paga normalmente; si no se acredita, la mesada queda retenida.'],
  ['Prueba de supervivencia', '¿Qué necesito para hacerla?', 'Tu wallet con dos documentos: tu documento de identidad digital (PID) y una fe de vida emitida por una notaría de confianza. Inicia sesión con el PID y comparte la fe de vida cuando se te pida.'],
  ['Prueba de supervivencia', '¿Cuánto dura la fe de vida y cada cuánto debo repetir la prueba?', 'La fe de vida vale 30 días. La prueba queda vigente hasta esa fecha, que ves en el resultado como "Próxima prueba antes del"; hazla de nuevo antes de que venza.'],
  ['Prueba de supervivencia', '¿Por qué me rechazan la prueba?', 'Las causas habituales: la fe de vida no la emitió una notaría de confianza, está vencida o revocada, el nombre o el documento de la constancia no coinciden con tu PID, o falta alguno de los dos documentos. El resultado detalla cuál criterio falló.'],
  ['Prueba de supervivencia', '¿Puedo hacer la prueba por otra persona?', 'No. La constancia debe ser del mismo titular que inicia sesión con su PID. Cada persona hace su propia prueba.'],
  ['Cuenta y wallet', '¿Cómo inicio sesión?', 'Con el botón "Ingresar con mi wallet": escanea el código QR (o abre la wallet si estás en el teléfono) y comparte tu PID. No hay usuario ni contraseña.'],
  ['Cuenta y wallet', '¿Qué datos del PID se usan?', 'Solo tu nombre y tu número de documento. No se guarda nada más, y la sesión se borra al pulsar "Salir" o al cerrar el servicio.'],
  ['Cuenta y wallet', '¿Cómo consigo la fe de vida?', 'Solicítala en la notaría del ecosistema y recíbela en la wallet. Debe ser de la constancia "Fe de vida" con tu mismo documento.'],
  ['Cuenta y wallet', 'La wallet rechaza o no reconoce las tarjetas del fondo', 'En la wallet entra a Ajustes › Listas de confianza › Actualizar y vuelve a intentar.'],
  ['Mesada', '¿Dónde veo el desprendible de mi mesada?', 'Inicia sesión y abre "Desprendible de mesada": verás los últimos 6 meses con devengados, deducciones y neto, y puedes imprimirlo o guardarlo como PDF.'],
  ['Mesada', '¿Por qué mi mesada aparece retenida?', 'Porque no tienes una prueba de supervivencia vigente. Haz la prueba y, al acreditarla, la mesada del mes queda habilitada.'],
  ['Mesada', '¿Qué descuentos tiene la mesada?', 'Aporte a salud del 12 % y, si la mesada supera 4 salarios mínimos, 1 % al fondo de solidaridad pensional. En junio y diciembre se paga además la mesada adicional.'],
  ['Certificados', '¿Cómo obtengo mi certificado de pensionado?', 'Inicia sesión y abre "Certificado de pensionado". Sale ACTIVO si tu supervivencia está vigente. Lleva un código para verificarlo y puedes imprimirlo.'],
  ['Certificados', '¿Estos documentos tienen validez legal?', 'No. {{NOMBRE}} es una entidad ficticia para probar el ecosistema EUDI Wallet; los valores y documentos son simulados.'],
].map(([categoria, pregunta, respuesta]) => ({ categoria, pregunta, respuesta: respuesta.replaceAll('{{NOMBRE}}', NOMBRE) }))

const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: NOMBRE, tema: {
  fondo: '#f1f2f4', panel: '#ffffff', texto: '#1f2937', suave: '#5f6b7a', borde: '#dcdfe4', titulo: '#0b2a5b',
  marca: '#0b2a5b', marcaTexto: '#ffffff', acento: '#ffd200', cabeceraFondo: '#ffffff', cabeceraTexto: '#0b2a5b',
  ok: '#146c3a', okFondo: '#e6f4ea', mal: '#b3261e', malFondo: '#fdecea', neutroFondo: '#e8eaee', hover: '#f1f2f4',
  fuente: 'Montserrat, Segoe UI, system-ui, -apple-system, Roboto, sans-serif', pesoTitulo: 800, mayusculas: 'none', tracking: 'normal', radio: '6px', logoAlto: 46,
  preFondo: '#0b2a5b', preTexto: '#e6ecf7', logo: `${BASE}/publico/marca.svg`, subtitulo: 'Historial local',
  css: '.cab{border-bottom:1px solid #dcdfe4}.cab nav a:hover{background:#f1f2f4}.marca b{font-weight:800}',
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
app.use(express.json({ limit: '10kb' }))
const r = express.Router()
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8').replaceAll('{{BASE}}', BASE).replaceAll('{{NOMBRE}}', NOMBRE)
r.use('/publico', express.static(new URL('./publico', import.meta.url).pathname, { index: false }))
r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (_req, res) => res.type('html').set('Cache-Control', 'no-store').send(pagina))

r.post('/api/solicitud', async (req, res) => {
  const { dispositivo, tipo: t } = req.body ?? {}
  const mismo = dispositivo === 'mismo'
  const ses = sesion(req, res)
  const tipo = t === 'ingreso' ? 'ingreso' : 'supervivencia'
  if (tipo === 'supervivencia' && !ses.pensionado) return res.status(401).json({ error: 'Inicia sesión con tu documento de identidad (PID) primero.' })
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud({
      credenciales: PEDIDO(tipo),
      nonce: randomBytes(16).toString('base64url'),
      // En el mismo teléfono la wallet vuelve al portal al terminar.
      redireccion: mismo ? `${URL_PUBLICA}/volver` : undefined,
    })
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { transaccion, creada: Date.now(), sesion: ses.id, mismo, tipo })
    ses.pendiente = id
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 340 }), vence_en: VENCE_SOLICITUD_MS / 1000 })
  } catch (e) {
    console.error(`[solicitud] ${e.message}`)
    res.status(503).json({ error: 'El servicio de verificación no está disponible. Inténtalo en unos minutos.' })
  }
})
r.get('/api/solicitud/:id', async (req, res) => {
  const s = solicitudes.get(req.params.id)
  if (!s || s.sesion !== sesion(req, res).id) return res.status(404).json({ estado: 'rechazada', motivo: 'Solicitud desconocida.' })
  // En el mismo teléfono el resultado solo se libera con el response_code que trae /volver: aquí no se sondea el verifier.
  if (s.mismo && !s.final) return res.json({ estado: 'esperando' })
  try { res.json(await consultar(s)) } catch (e) { console.error(`[solicitud] ${e.message}`); res.json({ estado: 'esperando' }) }
})

// A donde vuelve la wallet en el mismo teléfono, con el código que libera el resultado. Se consulta aquí (solo con la
// sesión que creó la solicitud) y se deja el resultado en la solicitud; la página lo recoge en /api/pendiente.
r.get('/volver', async (req, res) => {
  const ses = sesion(req, res)
  const s = solicitudes.get(ses.pendiente)
  const fallo = (motivo) => res.redirect(`${BASE}/?error=${encodeURIComponent(motivo)}`)
  if (!s || !s.mismo || s.sesion !== ses.id || !req.query.response_code) return fallo('No encontramos la solicitud. Inténtalo de nuevo desde este navegador.')
  try {
    const final = await consultar(s, String(req.query.response_code))
    if (final.estado === 'listo') return res.redirect(`${BASE}/?volvio=1`)
    return fallo(final.motivo ?? 'La wallet todavía no ha respondido.')
  } catch (e) {
    console.error(`[volver] ${e.message}`)
    return fallo('El servicio de verificación no está disponible.')
  }
})
// El resultado ya liberado de la solicitud de esta sesión (lo pide la página tras volver de la wallet).
r.get('/api/pendiente', (req, res) => {
  const ses = sesion(req, res)
  const s = solicitudes.get(ses.pendiente)
  res.set('Cache-Control', 'no-store')
  if (!s || s.sesion !== ses.id || !s.final) return res.status(404).json({ estado: 'rechazada', motivo: 'No hay un resultado pendiente.' })
  res.json(s.final)
})
r.get('/api/sesion', (req, res) => {
  const p = sesion(req, res).pensionado
  res.set('Cache-Control', 'no-store').json({ pensionado: p ? { nombre: p.nombre, documento: `•••${digitos(p.documento).slice(-3)}` } : null })
})
r.post('/api/salir', (req, res) => { const ses = sesion(req, res); delete ses.pensionado; delete ses.pendiente; res.json({ ok: true }) })
r.get('/api/certificado', (req, res) => {
  const p = requierePensionado(req, res); if (!p) return
  const v = vigente(p.documento)
  const emitido = hoyISO()
  const codigo = randomBytes(6).toString('hex').toUpperCase().replace(/(.{4})(?=.)/g, '$1-')
  const datos = {
    codigo, emitido, entidad: NOMBRE, pensionado: p.nombre, documento: p.documento,
    estado: v ? 'ACTIVO' : 'PENDIENTE DE PRUEBA DE SUPERVIVENCIA',
    supervivencia_vence: v?.vence ?? null, mesada: mesadaBase(p.documento), tipo: 'Pensión de vejez',
  }
  certificados.set(codigo, { ...datos, pensionado: iniciales(p.nombre), documento: `•••${digitos(p.documento).slice(-3)}` })
  res.set('Cache-Control', 'no-store').json(datos)
})
r.get('/api/verificar/:codigo', (req, res) => {
  const c = certificados.get(String(req.params.codigo).toUpperCase())
  res.json(c ? { valido: true, ...c } : { valido: false })
})
r.get('/api/desprendibles', (req, res) => {
  const p = requierePensionado(req, res); if (!p) return
  const ahora = new Date(), lista = []
  for (let i = 0; i < 6; i++) {
    const f = new Date(Date.UTC(ahora.getUTCFullYear(), ahora.getUTCMonth() - i, 1))
    lista.push(desprendible(p, f.getUTCFullYear(), f.getUTCMonth() + 1))
  }
  res.set('Cache-Control', 'no-store').json({ pensionado: p.nombre, documento: p.documento, desprendibles: lista })
})
r.get('/api/preguntas', (req, res) => {
  const q = normal(req.query.q ?? '')
  res.json({ preguntas: q ? PREGUNTAS.filter((x) => normal(`${x.pregunta} ${x.respuesta}`).includes(q)) : PREGUNTAS })
})
r.get('/api/acreditados', (_req, res) => res.set('Cache-Control', 'no-store').json({ acreditados }))

app.use(BASE || '/', r)
app.listen(PUERTO, () => console.log(`${NOMBRE}: prueba de supervivencia en :${PUERTO}${BASE} (verifier ${process.env.VERIFIER_API})`))

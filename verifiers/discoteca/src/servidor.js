// Discoteca de ejemplo: control de edad en la puerta con la wallet.
//
// El personal tiene esta página abierta en una tablet o un teléfono. Muestra un
// QR; la persona lo escanea con su wallet, que le dice qué se pide (solo «mayor
// de 18») y para qué, y al aceptar la pantalla se pone en verde o en rojo. Luego
// vuelve sola a un QR nuevo.
//
// Privacidad, que es el punto:
//   - a la wallet solo se le pide age_equal_or_over.<EDAD>: no llega nombre,
//     documento, foto ni fecha de nacimiento;
//   - aquí no se guarda nada: ni en disco ni en el log. Solo dos contadores en
//     memoria (entradas y rechazos de la noche), sin nada de las personas;
//   - su verifier tampoco lo conserva: su instancia guarda cada presentación
//     5 minutos (CONSERVAR_PRESENTACIONES=PT5M en ../_comun/instancias/discoteca/.env).
//
// Variables de entorno (las pone compose.yml):
//   URL_PUBLICA    https://civica-desarrollo.avance.org.co/discoteca (escucha bajo esa ruta)
//   VERIFIER_API   http://verifier-discoteca/verifier-discoteca (red interna)
//   NOMBRE         el nombre del local
//   EDAD_MINIMA    18
//   PUERTO         80

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
const NOMBRE = process.env.NOMBRE ?? 'Discoteca'
const EDAD = Number(process.env.EDAD_MINIMA ?? 18)
const VENCE_MS = 5 * 60 * 1000

// Auditoría: reporta cada solicitud y resultado de verificación al centro (solo monitoreo; nunca afecta al portal).
const verifier = auditarVerifier(crearVerifier({ api: requerida('VERIFIER_API'), edad: EDAD }), crearAuditor({ rol: 'verificador', identificador: URL_PUBLICA, nombre: NOMBRE, proyecto: 'verifiers/discoteca' }))
const solicitudes = new Map() // id → { transaccion, creada, final? }  (nada de la persona)
const noche = { entradas: 0, rechazos: 0, desde: new Date().toISOString() }

setInterval(() => {
  const limite = Date.now() - VENCE_MS
  for (const [id, s] of solicitudes) if (s.creada < limite) solicitudes.delete(id)
}, 30 * 1000).unref()

const app = express()
montarPanelAuditoria(app, { base: BASE, nombre: NOMBRE, tema: {
  fondo: '#070707', panel: '#121212', texto: '#FFFFFF', suave: '#B9B2A4', borde: '#2B2418', titulo: '#F5A623',
  marca: '#F5A623', marcaTexto: '#070707', acento: '#F5A623', cabeceraFondo: '#000000', cabeceraTexto: '#F5A623',
  ok: '#4ADE80', okFondo: '#0F2A1B', mal: '#FF6B6B', malFondo: '#33100F', neutroFondo: '#241E12', hover: '#1E1810',
  fuente: 'Raleway, system-ui, -apple-system, Segoe UI, sans-serif', fuenteTitulo: 'Anton, Impact, Arial Narrow Bold, sans-serif',
  pesoTitulo: 400, mayusculas: 'uppercase', tracking: '.18em', radio: '8px', logoAlto: 30, preFondo: '#000000', preTexto: '#FFD27A',
  subtitulo: 'Historial de puerta',
  fuentes: '@import url(https://fonts.googleapis.com/css2?family=Anton&family=Raleway:wght@500;700&display=swap);',
  css: '.marca::before{content:"";width:30px;height:30px;flex:none;background:url("data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 200 200%27%3E%3Cpath d=%27M130 22a82 82 0 1 0 48 118A68 68 0 0 1 130 22z%27 fill=%27%23F5A623%27/%3E%3C/svg%3E") center/contain no-repeat}.cab{border-bottom:2px solid #F5A623;box-shadow:0 8px 30px rgba(245,166,35,.12)}.marca b{color:#F5A623;text-shadow:0 0 18px rgba(245,166,35,.45)}.barra button.g{color:#F5A623}'
} })   // historial local: <BASE>/auditoria/ (solo administración)
app.disable('x-powered-by')
const r = express.Router()
const pagina = readFileSync(new URL('./publico/index.html', import.meta.url), 'utf8')
  .replaceAll('{{BASE}}', BASE).replaceAll('{{NOMBRE}}', NOMBRE).replaceAll('{{EDAD}}', String(EDAD))

r.get('/salud', (_req, res) => res.json({ estado: 'ok' }))
r.get('/', (_req, res) => res.type('html').set('Cache-Control', 'no-store').send(pagina))

r.post('/api/solicitud', async (_req, res) => {
  try {
    const { transaccion, enlace } = await verifier.crearSolicitud(randomBytes(16).toString('base64url'))
    const id = randomBytes(16).toString('base64url')
    solicitudes.set(id, { transaccion, creada: Date.now() })
    res.json({ id, enlace, qr: await QRCode.toDataURL(enlace, { margin: 1, width: 480 }) })
  } catch (e) {
    console.error(`[puerta] ${e.message}`)
    res.status(503).json({ error: 'El verificador no responde.' })
  }
})

// La puerta se desactivó: se cancela el código pendiente (queda «cancelada» en el historial, no «sin respuesta»).
r.post('/api/solicitud/:id/cancelar', (req, res) => {
  const s = solicitudes.get(req.params.id)
  if (s && !s.final) { s.final = { estado: 'cancelada' }; verifier.cancelar?.(s.transaccion, 'Puerta desactivada por el operador') }
  res.json({ ok: true })
})

r.get('/api/solicitud/:id', async (req, res) => {
  const s = solicitudes.get(req.params.id)
  if (!s) return res.status(404).json({ estado: 'error', motivo: 'Solicitud desconocida.' })
  if (s.final) return res.json(s.final)
  try {
    const resultado = await verifier.resultado(s.transaccion)
    if (resultado.estado === 'esperando') {
      return res.json(Date.now() - s.creada > VENCE_MS ? { estado: 'vencida' } : { estado: 'esperando' })
    }
    if (resultado.estado === 'listo' && resultado.mayor === true) {
      s.final = { estado: 'entra' }
      noche.entradas++
    } else if (resultado.estado === 'listo' && resultado.mayor === false) {
      s.final = { estado: 'menor' }
      noche.rechazos++
    } else {
      s.final = { estado: 'error', motivo: resultado.motivo ?? 'El documento no trae la edad. Pide el PID de nuevo en tu wallet.' }
    }
    // Solo el resultado, nunca datos: es lo único que sale de aquí y del log.
    console.log(`[puerta] ${s.final.estado}`)
    res.json(s.final)
  } catch (e) {
    console.error(`[puerta] ${e.message}`)
    res.json({ estado: 'esperando' })
  }
})

r.get('/api/noche', (_req, res) => res.json(noche))

app.use(BASE || '/', r)
app.listen(Number(process.env.PUERTO ?? 80), () => console.log(`${NOMBRE}: control de edad (+${EDAD}) en ${BASE}`))

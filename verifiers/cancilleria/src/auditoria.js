// Reporte de actividad al centro de auditoría (srv-auditoria): cada oferta, autorización, emisión y verificación que
// atiende este portal, exitosa o con error. Contrato: srv-auditoria/docs/CONTRATO-EVENTOS-SERVICIOS.md
//
// ES SOLO MONITOREO y no puede afectar al portal:
//   - nunca espera: el envío es en segundo plano y la ruta del usuario no lo aguarda;
//   - nunca lanza: cualquier fallo al reportar se traga;
//   - tiempo máximo corto (2 s) y cola acotada en memoria con reintentos; si el centro no responde, se descarta lo más viejo;
//   - AUDITORIA_ACTIVA=false lo apaga por completo.
// Cada hecho se guarda además en el historial LOCAL del portal (historial.js) y se puede consultar en <BASE>/auditoria/.
// Nunca se envían valores de datos personales (solo nombres de atributos), ni tokens, códigos o claves.
//
// Esta es la ÚNICA fuente de este archivo (verifiers/_comun/auditoria/auditoria.js). Cada portal tiene una copia en
// su src/ (su imagen se construye con su propia carpeta): ./_comun/auditoria/instalar.sh la vuelve a copiar.

import { randomUUID } from 'node:crypto'
import { guardarLocal } from './historial.js'

const ACTIVA = process.env.AUDITORIA_ACTIVA !== 'false'
const URL_CENTRO = (process.env.AUDITORIA_URL || 'http://srv-auditoria:8111').replace(/\/$/, '')
const CLAVE = process.env.AUDITORIA_CLAVE_SERVICIOS || ''
const MAX_COLA = 500
const TIEMPO_MAX_MS = 2000
const REINTENTO_MS = 15000
const PID = 'urn:eudi:pid:1'

/** Crea el reportero de un servicio. `rol`: 'emisor' | 'verificador'. */
export function crearAuditor({ rol, identificador, nombre, proyecto }) {
  const servicio = { rol, identificador, nombre, proyecto }
  const cola = []
  let enviando = false
  let reintento = null

  async function enviarUno(e) {
    const r = await fetch(`${URL_CENTRO}/api/v1/servicios/eventos`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...(CLAVE ? { 'x-api-key': CLAVE } : {}) },
      body: JSON.stringify(e),
      signal: AbortSignal.timeout(TIEMPO_MAX_MS),
    })
    // 400 = el evento no cumple el contrato: reintentar no lo arreglaría, se descarta. 5xx/red = se reintenta.
    if (r.status >= 500) throw new Error('centro no disponible')
  }

  async function vaciar() {
    if (enviando) return
    enviando = true
    try {
      while (cola.length) {
        try { await enviarUno(cola[0]); cola.shift() } catch { programarReintento(); return }
      }
    } finally { enviando = false }
  }

  function programarReintento() {
    if (reintento) return
    reintento = setTimeout(() => { reintento = null; vaciar().catch(() => {}) }, REINTENTO_MS)
    reintento.unref?.()
  }

  /**
   * Registra un hecho. No devuelve nada que haya que esperar. Campos de `evento`: tipo, resultado, referencia,
   * credencial_tipo, credencial_nombre, sujeto {numero_documento, nombre}, canal, duracion_ms, tipo_error,
   * codigo_error, mensaje_error, elementos [{sentido, tipo_credencial, elemento}], detalle.
   */
  function registrar(evento) {
    if (!ACTIVA) return
    try {
      const completo = { ocurrido_en: new Date().toISOString(), ...evento }
      guardarLocal({ rol, ...completo })   // historial local del propio portal (ver historial.js)
      if (cola.length >= MAX_COLA) cola.shift()
      cola.push({ evento_id: randomUUID(), servicio, evento: completo })
      vaciar().catch(() => {})
    } catch { /* reportar nunca debe afectar al portal */ }
  }

  return { registrar }
}

// ───────── verificador ─────────

const textoError = (e) => String(e?.message ?? e ?? '').slice(0, 300)

/** Clasifica el motivo que devuelve el cliente del verifier en un tipo de error del contrato. */
function clasificar(motivo = '') {
  const m = motivo.toLowerCase()
  if (/no aceptó|no acept|no quiso|access_denied|cancel/.test(m)) return { tipo_error: 'usuario', codigo_error: 'access_denied', resultado: 'cancelado' }
  if (/revocad|suspendid|status/.test(m)) return { tipo_error: 'revocacion', codigo_error: 'attestation_status_check_failed', resultado: 'fallido' }
  if (/venci|expir/.test(m)) return { tipo_error: 'validacion', codigo_error: 'presentation_expired', resultado: 'fallido' }
  if (/no pudo leer|failedtoretrieve/.test(m)) return { tipo_error: 'red', codigo_error: 'request_not_retrieved', resultado: 'fallido' }
  if (/confian|trust|emisor/.test(m)) return { tipo_error: 'confianza', codigo_error: 'untrusted_issuer', resultado: 'fallido' }
  if (/rechaz|invál|inval|no trae|respuesta/.test(m)) return { tipo_error: 'validacion', codigo_error: 'invalid_presentation', resultado: 'fallido' }
  return { tipo_error: 'verificador', codigo_error: 'verification_failed', resultado: 'fallido' }
}

/** De los argumentos de crearSolicitud saca qué credenciales y qué datos se pidieron (solo nombres). */
function pedido(args) {
  const a = Array.isArray(args) ? args[0] : args
  if (typeof a === 'string' || a == null) return [{ vct: PID, datos: [] }]
  if (Array.isArray(a.credenciales)) return a.credenciales.map((c) => ({ vct: c.vct, datos: [...Object.keys(c.filtros ?? {}), ...(c.datos ?? [])] }))
  return [{ vct: a.vct ?? PID, datos: [...Object.keys(a.filtros ?? {}), ...(a.datos ?? [])] }]
}
const nombreDato = (d) => [].concat(d).join('.')

/** Credenciales presentadas, normalizadas: [{ vct, emisor, datos }] (los portales devuelven tres formas distintas). */
function presentadas(r) {
  if (r.credenciales && typeof r.credenciales === 'object') return Object.values(r.credenciales).map((c) => ({ vct: c.vct, emisor: c.emisor, datos: c.datos ?? {} }))
  if (r.datos || r.emisor) return [{ vct: r.vct, emisor: r.emisor, datos: r.datos ?? {} }]
  return []
}

/**
 * Quién se presentó (solo cédula y nombre; los valores de los demás datos nunca se guardan):
 *   1) el PID: personal_administrative_number + given_name/family_name;
 *   2) cualquier otra credencial con el documento de su titular: <rol>_documento (afiliado_documento, paciente_documento,
 *      socio_documento, pasajero_documento…) y, si existe, su <rol>_nombre; también numero_documento / cedula / documento.
 */
function sujetoDe(creds) {
  for (const c of creds) {
    const d = c.datos ?? {}
    if (d.personal_administrative_number) {
      return { numero_documento: String(d.personal_administrative_number), nombre: [d.given_name, d.family_name].filter(Boolean).join(' ') || undefined }
    }
  }
  for (const c of creds) {
    const d = c.datos ?? {}
    for (const [k, v] of Object.entries(d)) {
      if (v == null || typeof v === 'object' || String(v).length > 30 || String(v).trim() === '') continue
      const m = /^(.*)_documento$/.exec(k)
      if (m) return { numero_documento: String(v), nombre: d[m[1] + '_nombre'] ? String(d[m[1] + '_nombre']).slice(0, 120) : undefined }
      if (k === 'numero_documento' || k === 'cedula' || k === 'documento') return { numero_documento: String(v), nombre: d.nombre ? String(d.nombre).slice(0, 120) : undefined }
    }
  }
  return undefined
}

/**
 * Envuelve el cliente del verifier de un portal (`crearVerifier(...)`) para auditar cada solicitud y su resultado, sin
 * cambiarle nada: las mismas funciones, los mismos argumentos y los mismos resultados (y los mismos errores).
 */
export function auditarVerifier(verifier, auditor) {
  const abiertas = new Map()   // transacción → { t0, pedidos, canal }
  const cerradas = new Set()
  const limpiar = () => { if (abiertas.size > 2000) abiertas.delete(abiertas.keys().next().value); if (cerradas.size > 5000) cerradas.delete(cerradas.values().next().value) }

  async function crearSolicitud(...args) {
    const t0 = Date.now()
    const pedidos = (() => { try { return pedido(args) } catch { return [] } })()
    const canal = args[0]?.redireccion ? 'mismo_dispositivo' : 'qr'
    const elementos = pedidos.flatMap((p) => p.datos.map((d) => ({ sentido: 'solicitado', tipo_credencial: p.vct ?? 'desconocida', elemento: nombreDato(d) })))
    const cred = pedidos.map((p) => p.vct).filter(Boolean).join(', ') || undefined
    try {
      const r = await verifier.crearSolicitud(...args)
      try {
        abiertas.set(r.transaccion, { t0, pedidos, canal }); limpiar()
        auditor.registrar({ tipo: 'solicitud_verificacion', resultado: 'en_proceso', referencia: r.transaccion, credencial_tipo: cred, canal, elementos })
      } catch { /* nunca afecta al portal */ }
      return r
    } catch (e) {
      try { auditor.registrar({ tipo: 'solicitud_verificacion', resultado: 'fallido', credencial_tipo: cred, canal, duracion_ms: Date.now() - t0, tipo_error: 'verificador', codigo_error: 'request_not_created', mensaje_error: textoError(e), elementos }) } catch { /* idem */ }
      throw e
    }
  }

  async function resultado(transaccion, ...resto) {
    const r = await verifier.resultado(transaccion, ...resto)
    try {
      if ((r?.estado === 'listo' || r?.estado === 'error') && !cerradas.has(transaccion)) {
        cerradas.add(transaccion)
        const a = abiertas.get(transaccion); abiertas.delete(transaccion); limpiar()
        const base = { referencia: transaccion, canal: a?.canal, duracion_ms: a ? Date.now() - a.t0 : undefined }
        const pedidos = a?.pedidos ?? []
        const cred = pedidos.map((p) => p.vct).filter(Boolean).join(', ') || undefined
        if (r.estado === 'listo') {
          const creds = presentadas(r)
          auditor.registrar({
            ...base, tipo: 'verificacion_credencial', resultado: 'exitoso', credencial_tipo: creds.map((c) => c.vct).filter(Boolean).join(', ') || cred,
            sujeto: sujetoDe(creds),
            elementos: creds.flatMap((c) => Object.keys(c.datos).map((d) => ({ sentido: 'recibido', tipo_credencial: c.vct ?? 'desconocida', elemento: d }))),
            detalle: { emisores_de_las_credenciales: [...new Set(creds.map((c) => c.emisor).filter(Boolean))], ...(typeof r.mayor === 'boolean' ? { mayor_de_edad: r.mayor } : {}) },
          })
        } else {
          const k = clasificar(r.motivo)
          auditor.registrar({ ...base, tipo: 'verificacion_credencial', resultado: k.resultado, credencial_tipo: cred, tipo_error: k.tipo_error, codigo_error: k.codigo_error, mensaje_error: textoError(r.motivo), detalle: r.detalle ? { causa: textoError(r.detalle) } : undefined })
        }
      }
    } catch { /* nunca afecta al portal */ }
    return r
  }

  /** Una solicitud abierta se cierra desde el portal (p. ej. la puerta se desactivó): queda como «cancelada», no como «sin respuesta». */
  function cancelar(transaccion, motivo) {
    try {
      if (!transaccion || cerradas.has(transaccion)) return
      cerradas.add(transaccion)
      const a = abiertas.get(transaccion); abiertas.delete(transaccion)
      auditor.registrar({
        tipo: 'verificacion_credencial', resultado: 'cancelado', referencia: transaccion, canal: a?.canal, duracion_ms: a ? Date.now() - a.t0 : undefined,
        credencial_tipo: (a?.pedidos ?? []).map((p) => p.vct).filter(Boolean).join(', ') || undefined,
        tipo_error: 'usuario', codigo_error: 'cancelled_by_operator', mensaje_error: textoError(motivo || 'Cancelada desde el portal'),
      })
    } catch { /* nunca afecta al portal */ }
  }

  return { ...verifier, crearSolicitud, resultado, cancelar }
}

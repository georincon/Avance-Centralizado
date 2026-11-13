// Lo único que la discoteca le pregunta a la wallet: ¿mayor de N años?
//
// Pide al verifier (eudi-srv-verifier-endpoint) el PID con UN solo dato:
// age_equal_or_over.<N>. La wallet revela solo ese booleano: ni nombre, ni
// documento, ni fecha de nacimiento. El verifier comprueba lo demás (firma de un
// emisor de la lista PID, estado del PID, que lo presenta su titular con el nonce
// de esta solicitud) y aquí solo se lee el sí o el no.
//
// Nada de lo que llega se guarda ni se escribe en el log.

import { createHash } from 'node:crypto'

const FALLOS = {
  'FailedToRetrieve request': 'la wallet no pudo leer la solicitud',
  'Wallet failed to post response': 'el documento no pasó la verificación',
  'Presentation expired': 'la solicitud venció',
  'Attestation status check failed': 'el documento de identidad fue revocado o suspendido',
}

export function crearVerifier({ api, edad }) {
  api = api.replace(/\/$/, '')
  let usoId

  async function llamar(ruta, opciones = {}) {
    const r = await fetch(api + ruta, {
      ...opciones,
      headers: { accept: 'application/json', ...(opciones.body ? { 'content-type': 'application/json' } : {}) },
      signal: AbortSignal.timeout(20000),
    })
    const texto = await r.text()
    return { estado: r.status, cuerpo: texto ? JSON.parse(texto) : {} }
  }

  async function intendedUse() {
    if (usoId === undefined) usoId = (await llamar('/ui/intended-uses')).cuerpo.intended_uses?.[0]?.intended_use_id ?? null
    return usoId
  }

  async function crearSolicitud(nonce) {
    const { estado, cuerpo } = await llamar('/ui/presentations/v2', {
      method: 'POST',
      body: JSON.stringify({
        dcql_query: {
          credentials: [{
            id: 'edad',
            format: 'dc+sd-jwt',
            meta: { vct_values: ['urn:eudi:pid:1'] },
            claims: [{ path: ['age_equal_or_over', String(edad)] }],
          }],
        },
        nonce,
        profile: 'haip',
        intended_use_id: await intendedUse(),
        request_uri_method: 'get',
      }),
    })
    if (estado !== 200) throw new Error(`el verifier no creó la solicitud (HTTP ${estado})`)
    return { transaccion: cuerpo.transaction_id, enlace: cuerpo.authorization_request_uri }
  }

  /** { estado: 'esperando' } | { estado: 'listo', mayor: true|false|null } | { estado: 'error', motivo } */
  async function resultado(transaccion) {
    const t = encodeURIComponent(transaccion)
    const { estado, cuerpo } = await llamar(`/ui/presentations/${t}`)
    if (estado === 404) return { estado: 'error', motivo: 'la solicitud ya no existe' }
    if (estado === 400) {
      const { cuerpo: ev } = await llamar(`/ui/presentations/${t}/events`)
      const fallo = (ev.events ?? []).find((e) => FALLOS[e.event])
      return fallo ? { estado: 'error', motivo: FALLOS[fallo.event] } : { estado: 'esperando' }
    }
    if (cuerpo.error) return { estado: 'error', motivo: cuerpo.error === 'access_denied' ? 'la persona no aceptó compartir el dato' : 'la wallet devolvió un error' }
    const presentado = [].concat(cuerpo.vp_token?.edad ?? [])[0]
    if (typeof presentado !== 'string') return { estado: 'error', motivo: 'la respuesta no trae el dato' }
    return { estado: 'listo', mayor: leerEdad(presentado, edad) }
  }

  return { crearSolicitud, resultado }
}

// Recorre el SD-JWT de verdad: el dato está en age_equal_or_over.<N>, y tanto
// el objeto como la clave pueden venir como divulgaciones referenciadas por
// su hash en `_sd`. Devuelve true/false, o null si no vino.
function leerEdad(presentado, edad) {
  const [jwt, ...partes] = presentado.split('~')
  const decodificar = (b64) => JSON.parse(Buffer.from(b64, 'base64url').toString())
  const porHash = new Map(partes.slice(0, -1).filter(Boolean).map((d) => [createHash('sha256').update(d).digest('base64url'), decodificar(d)]))
  const campo = (obj, nombre) => {
    if (obj && Object.hasOwn(obj, nombre)) return obj[nombre]
    for (const h of obj?._sd ?? []) {
      const d = porHash.get(h)
      if (d?.length === 3 && d[1] === nombre) return d[2]
    }
    return undefined
  }
  const valor = campo(campo(decodificar(jwt.split('.')[1]), 'age_equal_or_over'), String(edad))
  return typeof valor === 'boolean' ? valor : null
}

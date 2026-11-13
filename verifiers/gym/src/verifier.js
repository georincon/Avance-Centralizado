// Cliente de la API del verifier (eudi-srv-verifier-endpoint) del gimnasio.
//
// El gimnasio lo usa dos veces:
//   - para identificar a quien se hace socio (le pide su PID), y
//   - para comprobar la tarjeta de socio que alguien le presenta en la entrada
//     o en la salida de una sede.
// En los dos casos el verifier ya validó todo al entregar los datos: firma del
// emisor, que el emisor está en su lista (PID o QEAA, por el trust-validator),
// el estado (status list) y la prueba de posesión con el nonce de esta solicitud.
// Igual que en ../notaria/src/verifier.js.

const ERRORES = {
  'FailedToRetrieve request': 'la wallet no pudo leer la solicitud',
  'Wallet failed to post response': 'la wallet respondió, pero el verifier rechazó la presentación',
  'Presentation expired': 'la solicitud venció',
  'Attestation status check failed': 'el documento fue anulado o suspendido por quien lo emitió',
}

export function crearVerifier({ api }) {
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
    if (usoId === undefined) {
      const { cuerpo } = await llamar('/ui/intended-uses')
      usoId = cuerpo.intended_uses?.[0]?.intended_use_id ?? null
    }
    return usoId
  }

  /**
   * Abre una transacción que pide un SD-JWT VC de tipo `vct` con los datos indicados.
   * Con `redireccion` (mismo dispositivo) la wallet vuelve a esa URL con
   * ?response_code=…, y el resultado solo se entrega con ese código.
   */
  async function crearSolicitud({ vct, datos, nonce, redireccion }) {
    const cuerpo = {
      dcql_query: {
        credentials: [{ id: 'doc', format: 'dc+sd-jwt', meta: { vct_values: [vct] }, claims: datos.map((d) => ({ path: [d] })) }],
      },
      nonce,
      profile: 'haip',
      intended_use_id: await intendedUse(),
      request_uri_method: 'get',
      ...(redireccion ? { wallet_response_redirect_uri_template: `${redireccion}?response_code={RESPONSE_CODE}` } : {}),
    }
    const { estado, cuerpo: r } = await llamar('/ui/presentations/v2', { method: 'POST', body: JSON.stringify(cuerpo) })
    if (estado !== 200) throw new Error(`el verifier no creó la transacción (HTTP ${estado}: ${JSON.stringify(r)})`)
    return { transaccion: r.transaction_id, enlace: r.authorization_request_uri }
  }

  /** { estado: 'esperando' } | { estado: 'listo', emisor, datos } | { estado: 'error', motivo } */
  async function resultado(transaccion, responseCode) {
    const t = encodeURIComponent(transaccion)
    const q = responseCode ? `?response_code=${encodeURIComponent(responseCode)}` : ''
    const { estado, cuerpo } = await llamar(`/ui/presentations/${t}${q}`)
    if (estado === 404) return { estado: 'error', motivo: 'la solicitud ya no existe en el verifier' }
    if (estado === 400 && cuerpo.error === 'InvalidResponseCode') return { estado: 'error', motivo: 'código de respuesta inválido' }
    if (estado === 400) {
      const { cuerpo: ev } = await llamar(`/ui/presentations/${t}/events`)
      const fallo = (ev.events ?? []).find((e) => ERRORES[e.event])
      if (!fallo) return { estado: 'esperando' }
      // El motivo concreto va en la causa: el que más importa aquí es el estado.
      const causa = JSON.stringify(fallo.cause ?? '')
      const motivo = /StatusNotValid|status/i.test(causa) ? 'el documento fue anulado o suspendido por quien lo emitió' : ERRORES[fallo.event]
      return { estado: 'error', motivo, detalle: fallo.cause }
    }
    if (cuerpo.error) {
      return { estado: 'error', motivo: cuerpo.error === 'access_denied' ? 'no se aceptó compartir los datos' : cuerpo.error_description || cuerpo.error }
    }
    const presentado = [].concat(cuerpo.vp_token?.doc ?? [])[0]
    if (typeof presentado !== 'string') return { estado: 'error', motivo: 'la respuesta no trae el documento pedido' }
    return { estado: 'listo', ...leerSdJwt(presentado) }
  }

  return { crearSolicitud, resultado }
}

// Lee un SD-JWT ya validado por el verifier: emisor, tipo, fechas y los datos
// divulgados de primer nivel.
function leerSdJwt(presentado) {
  const [jwt, ...partes] = presentado.split('~')
  const decodificar = (b64) => JSON.parse(Buffer.from(b64, 'base64url').toString())
  const carga = decodificar(jwt.split('.')[1])
  const datos = {}
  for (const d of partes.slice(0, -1)) {
    if (!d) continue
    const v = decodificar(d)
    if (v.length === 3 && !(v[1] in datos)) datos[v[1]] = v[2]
  }
  return { emisor: carga.iss, vct: carga.vct, emitido: carga.iat, vence: carga.exp, datos }
}

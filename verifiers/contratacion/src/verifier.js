// Cliente del verifier (eudi-srv-verifier-endpoint) para el portal del empleador.
//
// El portal no habla OpenID4VP con la wallet: eso lo hace su verifier. Aquí solo se le pide una transacción
// con lo que se quiere saber (DCQL) y se lee el resultado. Cuando el verifier entrega los datos, ya validó la
// firma del emisor, que el emisor está en la lista de confianza (PUBEAA), el estado de la credencial (status
// list) y que la presenta su titular (KB-JWT con el nonce de esta solicitud).
//
// Nada de lo que llega se guarda ni se escribe en el log.

const FALLOS = {
  'FailedToRetrieve request': 'la wallet no pudo leer la solicitud',
  'Wallet failed to post response': 'la credencial no pasó la verificación',
  'Presentation expired': 'la solicitud venció',
  'Attestation status check failed': 'la credencial fue revocada o suspendida',
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
    if (usoId === undefined) usoId = (await llamar('/ui/intended-uses')).cuerpo.intended_uses?.[0]?.intended_use_id ?? null
    return usoId
  }

  /**
   * Pide una credencial SD-JWT VC de tipo `vct`.
   * `filtros` son datos con valor fijo ({ academic_level: 'MAESTRIA' }): la wallet solo ofrece las credenciales
   * que los cumplen. `datos` son los demás datos que se piden (se divulgan solo esos).
   */
  async function crearSolicitud({ vct, filtros = {}, datos, nonce, redireccion }) {
    const claims = [
      ...Object.entries(filtros).map(([dato, valor]) => ({ path: [dato], values: [valor] })),
      ...datos.map((dato) => ({ path: [dato] })),
    ]
    const { estado, cuerpo } = await llamar('/ui/presentations/v2', {
      method: 'POST',
      body: JSON.stringify({
        dcql_query: { credentials: [{ id: 'credencial', format: 'dc+sd-jwt', meta: { vct_values: [vct] }, claims }] },
        nonce,
        profile: 'haip',
        intended_use_id: await intendedUse(),
        request_uri_method: 'get',
        // Mismo dispositivo: al terminar la wallet vuelve a esta URL con ?response_code=…, y el resultado solo
        // se entrega con ese código.
        ...(redireccion ? { wallet_response_redirect_uri_template: `${redireccion}?response_code={RESPONSE_CODE}` } : {}),
      }),
    })
    if (estado !== 200) throw new Error(`el verifier no creó la solicitud (HTTP ${estado})`)
    return { transaccion: cuerpo.transaction_id, enlace: cuerpo.authorization_request_uri }
  }

  /** { estado: 'esperando' } | { estado: 'listo', emisor, vct, datos } | { estado: 'error', motivo } */
  async function resultado(transaccion, responseCode) {
    const t = encodeURIComponent(transaccion)
    const q = responseCode ? `?response_code=${encodeURIComponent(responseCode)}` : ''
    const { estado, cuerpo } = await llamar(`/ui/presentations/${t}${q}`)
    if (estado === 404) return { estado: 'error', motivo: 'la solicitud ya no existe' }
    if (estado === 400 && cuerpo.error === 'InvalidResponseCode') return { estado: 'error', motivo: 'código de respuesta inválido' }
    if (estado === 400) {
      const { cuerpo: ev } = await llamar(`/ui/presentations/${t}/events`)
      const fallo = (ev.events ?? []).find((e) => FALLOS[e.event])
      return fallo ? { estado: 'error', motivo: FALLOS[fallo.event] } : { estado: 'esperando' }
    }
    if (cuerpo.error) {
      return { estado: 'error', motivo: cuerpo.error === 'access_denied' ? 'la persona no aceptó compartir los datos' : 'la wallet devolvió un error' }
    }
    const presentado = [].concat(cuerpo.vp_token?.credencial ?? [])[0]
    if (typeof presentado !== 'string') return { estado: 'error', motivo: 'la respuesta no trae la credencial' }
    return { estado: 'listo', ...leerSdJwt(presentado) }
  }

  return { crearSolicitud, resultado }
}

// Lee un SD-JWT ya validado por el verifier: el tipo (vct), el emisor, los datos que siempre viajan (los de la
// institución y las fechas de la credencial) y los datos divulgados de primer nivel.
function leerSdJwt(presentado) {
  const [jwt, ...partes] = presentado.split('~')
  const decodificar = (b64) => JSON.parse(Buffer.from(b64, 'base64url').toString())
  const payload = decodificar(jwt.split('.')[1])
  const tecnicos = new Set(['iss', 'iat', 'nbf', 'exp', 'cnf', 'vct', 'vct#integrity', 'status', '_sd', '_sd_alg', 'jti', 'sub', 'aud'])
  const datos = Object.fromEntries(Object.entries(payload).filter(([k]) => !tecnicos.has(k)))
  for (const d of partes.slice(0, -1)) {   // el último es el KB-JWT
    if (!d) continue
    const v = decodificar(d)
    if (v.length === 3 && !(v[1] in datos)) datos[v[1]] = v[2]
  }
  return { emisor: payload.iss, vct: payload.vct, datos }
}

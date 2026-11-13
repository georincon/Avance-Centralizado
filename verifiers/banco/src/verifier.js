// Cliente de la API del verifier (eudi-srv-verifier-endpoint) para el banco.
//
// El banco no habla OpenID4VP con la wallet: eso lo hace su verifier. El banco
// solo le pide una transacción con lo que quiere saber (DCQL), le muestra al
// usuario el QR o el enlace, y pregunta por el resultado. Cuando el verifier lo
// entrega, ya validó la firma del emisor, que el emisor está en la lista PID
// (trust-validator), el estado del PID (status list) y la prueba de posesión
// (KB-JWT con el nonce de esta transacción).

const ERRORES = {
  'FailedToRetrieve request': 'la wallet no pudo leer la solicitud',
  'Wallet failed to post response': 'la wallet respondió, pero el verifier rechazó la presentación',
  'Presentation expired': 'la solicitud venció',
  'Attestation status check failed': 'el documento está revocado o suspendido',
}

export function crearVerifier({ api }) {
  api = api.replace(/\/$/, '')
  let usoId

  async function llamar(ruta, opciones = {}) {
    const r = await fetch(api + ruta, {
      ...opciones,
      headers: { accept: 'application/json', ...(opciones.body ? { 'content-type': 'application/json' } : {}) },
    })
    const texto = await r.text()
    return { estado: r.status, cuerpo: texto ? JSON.parse(texto) : {} }
  }

  // El verifier rechaza las transacciones sin intended use (el propósito que la
  // wallet le muestra al usuario). Se usa el primero configurado.
  async function intendedUse() {
    if (usoId === undefined) {
      const { cuerpo } = await llamar('/ui/intended-uses')
      usoId = cuerpo.intended_uses?.[0]?.intended_use_id ?? null
    }
    return usoId
  }

  /**
   * Abre una transacción que pide del PID (SD-JWT) los datos indicados.
   * Con `redireccion` (mismo dispositivo) la wallet vuelve a esa URL con
   * ?response_code=… al terminar, y el resultado solo se entrega con ese código.
   */
  async function crearSolicitud({ datos, nonce, redireccion }) {
    const cuerpo = {
      dcql_query: {
        credentials: [{
          id: 'pid',
          format: 'dc+sd-jwt',
          meta: { vct_values: ['urn:eudi:pid:1'] },
          claims: datos.map((d) => ({ path: [d] })),
        }],
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

  /** { estado: 'esperando' } | { estado: 'listo', datos, emisor } | { estado: 'error', motivo } */
  async function resultado(transaccion, responseCode) {
    const q = responseCode ? `?response_code=${encodeURIComponent(responseCode)}` : ''
    const { estado, cuerpo } = await llamar(`/ui/presentations/${encodeURIComponent(transaccion)}${q}`)
    if (estado === 404) return { estado: 'error', motivo: 'la solicitud ya no existe en el verifier' }
    if (estado === 400 && cuerpo.error === 'InvalidResponseCode') return { estado: 'error', motivo: 'código de respuesta inválido' }
    if (estado === 400) {
      // Todavía no hay respuesta válida: ¿falló algo o la wallet no ha contestado?
      const { cuerpo: ev } = await llamar(`/ui/presentations/${encodeURIComponent(transaccion)}/events`)
      const fallo = (ev.events ?? []).find((e) => ERRORES[e.event])
      return fallo
        ? { estado: 'error', motivo: ERRORES[fallo.event], detalle: fallo.cause }
        : { estado: 'esperando' }
    }
    if (cuerpo.error) {
      // La wallet respondió con un error OAuth (p. ej. el usuario no aceptó).
      return { estado: 'error', motivo: cuerpo.error === 'access_denied' ? 'no aceptaste compartir los datos' : cuerpo.error_description || cuerpo.error }
    }
    const presentado = [].concat(cuerpo.vp_token?.pid ?? [])[0]
    if (typeof presentado !== 'string') return { estado: 'error', motivo: 'la respuesta no trae el PID' }
    return { estado: 'listo', ...leerSdJwt(presentado) }
  }

  return { crearSolicitud, resultado }
}

// Lee un SD-JWT ya validado por el verifier: el emisor y los datos divulgados
// de primer nivel (los únicos que pide el banco).
function leerSdJwt(presentado) {
  const [jwt, ...partes] = presentado.split('~')
  const decodificar = (b64) => JSON.parse(Buffer.from(b64, 'base64url').toString())
  const emisor = decodificar(jwt.split('.')[1]).iss
  const datos = {}
  for (const d of partes.slice(0, -1)) {   // el último es el KB-JWT
    if (!d) continue
    const v = decodificar(d)
    if (v.length === 3 && !(v[1] in datos)) datos[v[1]] = v[2]
  }
  return { emisor, datos }
}

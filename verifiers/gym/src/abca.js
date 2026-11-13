// Autenticación de la wallet ante la EPS (Attestation-Based Client
// Authentication, draft-ietf-oauth-attestation-based-client-auth).
//
// La wallet manda en el token endpoint dos cabeceras:
//   OAuth-Client-Attestation      la WIA: un JWT del wallet-provider que dice
//                                 «esta instancia de wallet es legítima» y lleva
//                                 su clave pública en cnf.jwk
//   OAuth-Client-Attestation-PoP  un JWT recién firmado con esa clave, para la
//                                 EPS (aud) y con un jti de un solo uso
//
// Se acepta si la firma de la WIA encadena (trust-validator, contexto
// WalletProviderAttestation) a un wallet-provider de la lista WP del
// tl-publisher, y la PoP prueba que quien llama tiene la clave. Es lo mismo que
// hace la extensión ABCA de Keycloak para el pid-issuer: solo wallets
// certificadas reciben documentos.

import { decodeProtectedHeader, importJWK, importX509, jwtVerify } from 'jose'

const pem = (b64) => `-----BEGIN CERTIFICATE-----\n${b64.match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----\n`

export function crearAbca({ trustValidator, audiencia, toleranciaS = 300 }) {
  const usados = new Map() // jti → vence (ms): una PoP no se puede reusar

  async function confiable(x5c) {
    const r = await fetch(trustValidator, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ chain: x5c, verificationContext: 'WalletProviderAttestation' }),
      signal: AbortSignal.timeout(15000),
    })
    if (!r.ok) throw new Error(`el trust-validator respondió ${r.status}`)
    return (await r.json()).trusted === true
  }

  /** Devuelve { cliente, wallet } o lanza un Error con el motivo (→ 401). */
  return async function verificar(cabeceras) {
    const wia = cabeceras['oauth-client-attestation']
    const pop = cabeceras['oauth-client-attestation-pop']
    if (!wia || !pop) throw new Error('falta la atestación de la wallet (OAuth-Client-Attestation y -PoP)')

    const { x5c, alg } = decodeProtectedHeader(wia)
    if (!x5c?.length) throw new Error('la WIA no trae x5c: no se puede saber qué wallet-provider la firmó')
    const { payload: atestacion } = await jwtVerify(wia, await importX509(pem(x5c[0]), alg ?? 'ES256'), { clockTolerance: toleranciaS })
    if (!atestacion.cnf?.jwk) throw new Error('la WIA no trae cnf.jwk')
    if (!(await confiable(x5c))) throw new Error('el wallet-provider que firmó la WIA no está en la lista de confianza (WP)')

    const { payload: prueba } = await jwtVerify(pop, await importJWK(atestacion.cnf.jwk, 'ES256'), {
      audience: audiencia,
      clockTolerance: toleranciaS,
      maxTokenAge: `${toleranciaS}s`,
    })
    if (prueba.iss && atestacion.sub && prueba.iss !== atestacion.sub) throw new Error('la PoP no es del cliente de la WIA')
    if (prueba.jti) {
      const ahora = Date.now()
      for (const [j, v] of usados) if (v < ahora) usados.delete(j)
      if (usados.has(prueba.jti)) throw new Error('la PoP ya se usó')
      usados.set(prueba.jti, ahora + 2 * toleranciaS * 1000)
    }
    return { cliente: atestacion.sub, wallet: atestacion.wallet_name ?? atestacion.sub, proveedor: atestacion.iss }
  }
}

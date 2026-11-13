// Status list de la cancillería (draft-ietf-oauth-status-list): un bit por documento,
// 0 = vigente, 1 = anulado (un poder revocado, una declaración retractada).
//
// Cada documento lleva en `status.status_list` su índice y esta URL. Quien lo
// verifica (el verifier, la wallet) descarga el token, descomprime la lista y
// mira su bit. Se sirve como JWT firmado por el mismo certificado que firma
// los documentos (typ statuslist+jwt).

import { deflateSync } from 'node:zlib'
import { SignJWT } from 'jose'

const VALIDEZ_S = 60 // el token dura 1 min: una revocación (pérdida de pasaporte) se ve como mucho 1 min después

export function crearEstado({ firmante, uri, anulados }) {
  let cache = null

  async function token() {
    const ahora = Math.floor(Date.now() / 1000)
    if (cache && cache.vence - 60 > ahora) return cache.jwt
    const indices = anulados()
    const bytes = Buffer.alloc(Math.max(16, Math.ceil((Math.max(-1, ...indices) + 1) / 8)))
    // bits: 1 → el bit i va en el byte i/8, posición i%8 (el menos significativo primero).
    for (const i of indices) bytes[i >> 3] |= 1 << (i & 7)
    const jwt = await new SignJWT({
      sub: uri,
      iat: ahora,
      exp: ahora + VALIDEZ_S,
      ttl: VALIDEZ_S,
      status_list: { bits: 1, lst: deflateSync(bytes).toString('base64url') },
    })
      .setProtectedHeader({ alg: 'ES256', typ: 'statuslist+jwt', x5c: firmante.x5c })
      .sign(firmante.clave)
    cache = { jwt, vence: ahora + VALIDEZ_S }
    return jwt
  }

  return { uri, token, invalidar: () => { cache = null } }
}

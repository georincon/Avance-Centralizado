// Emisión de SD-JWT VC (draft-ietf-oauth-sd-jwt-vc), lo justo para la EPS.
//
//   <JWT del emisor>~<divulgación 1>~<divulgación 2>~…~
//
// Cada dato va en una divulgación aparte ([sal, nombre, valor] en base64url) y el
// JWT solo lleva su hash en `_sd`: así la wallet puede mostrar a un verifier unos
// datos sin revelar los demás. El JWT lo firma el certificado de firma de la
// EPS, que va en `x5c`: la wallet y los verifiers lo encadenan hasta la CA
// raíz registrada en la lista QEAA.

import { createHash, randomBytes } from 'node:crypto'
import { SignJWT } from 'jose'

const b64url = (b) => Buffer.from(b).toString('base64url')

export async function emitirSdJwt({ firmante, iss, vct, datos, cnf, estado, validezDias = 3650 }) {
  const divulgaciones = Object.entries(datos)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => b64url(JSON.stringify([b64url(randomBytes(16)), k, v])))
  const resumenes = divulgaciones.map((d) => createHash('sha256').update(d).digest('base64url'))
  // Ordenados: que la posición de cada hash no delate qué dato es.
  resumenes.sort()

  const ahora = Math.floor(Date.now() / 1000)
  const jwt = await new SignJWT({
    vct,
    iat: ahora,
    exp: ahora + validezDias * 86400,
    cnf: { jwk: cnf },
    ...(estado ? { status: { status_list: { idx: estado.indice, uri: estado.uri } } } : {}),
    _sd_alg: 'sha-256',
    _sd: resumenes,
  })
    .setProtectedHeader({ alg: 'ES256', typ: 'dc+sd-jwt', x5c: firmante.x5c })
    .setIssuer(iss)
    .sign(firmante.clave)

  return `${[jwt, ...divulgaciones].join('~')}~`
}

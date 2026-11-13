// Emisor OpenID4VCI 1.0 de la notaría, con código preautorizado.
//
// No hay login en el emisor: la persona ya se identificó en la página de la
// notaría (presentando su PID) e hizo el trámite. Al terminar, la notaría le
// muestra una OFERTA (QR o enlace) con un código de un solo uso y un PIN
// (tx_code) que la página enseña aparte. La wallet:
//
//   1. lee la oferta                    GET  /notaria/oferta/{id}
//   2. lee la metadata                  GET  /.well-known/openid-credential-issuer/notaria
//                                       GET  /.well-known/oauth-authorization-server/notaria
//   3. canjea código + PIN por un token POST /notaria/token   (con su WIA: ver abca.js; DPoP)
//   4. pide un nonce                    POST /notaria/nonce
//   5. pide el documento, probando que  POST /notaria/credencial
//      tiene la clave a la que quedará
//      ligado (proof JWT con el nonce)
//
// El documento sale firmado por el certificado de firma de la notaría (x5c), con
// su índice en la status list (GET /notaria/estado/1).

import { createHash, randomBytes, randomInt } from 'node:crypto'
import express from 'express'
import { CompactEncrypt, calculateJwkThumbprint, compactDecrypt, decodeProtectedHeader, exportJWK, generateKeyPair, importJWK, importX509, jwtVerify } from 'jose'
import { DOCUMENTOS, datosDelDocumento, porConfiguracion, validezDias } from './documentos.js'
import { emitirSdJwt } from './sdjwt.js'

const TIPO_PREAUTORIZADO = 'urn:ietf:params:oauth:grant-type:pre-authorized_code'
const VENCE_OFERTA_MS = 15 * 60 * 1000
const VENCE_TOKEN_S = 300
const VENCE_NONCE_MS = 5 * 60 * 1000
const azar = (n = 24) => randomBytes(n).toString('base64url')
const pem = (b64) => `-----BEGIN CERTIFICATE-----\n${b64.match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----\n`

/** Error OAuth: { error, error_description } con su código HTTP. */
class ErrorOAuth extends Error {
  constructor(estado, error, descripcion) {
    super(descripcion)
    Object.assign(this, { estado, error })
  }
}

// Clave con la que la wallet cifra su petición de credencial (OpenID4VCI §11.2.4). Si el emisor reinicia
// se genera otra; la wallet vuelve a leer los metadatos en cada emisión.
const claveCifrado = await generateKeyPair('ECDH-ES', { crv: 'P-256', extractable: true })
const jwkCifrado = { ...(await exportJWK(claveCifrado.publicKey)), use: 'enc', alg: 'ECDH-ES', kid: 'notaria-enc-1' }

export function crearEmisor({ url, notaria, firmante, tramites, estado, abca, trustValidator, auditor = { registrar() {} } }) {
  const ruta = new URL(url).pathname.replace(/\/$/, '')
  const ofertas = new Map()   // id de la oferta → { codigo, … }
  const codigos = new Map()   // código preautorizado → { numero, configuracion, pin, vence, intentos }
  const tokens = new Map()    // access token → { numero, configuracion, vence, jkt }
  const nonces = new Map()    // c_nonce → vence

  const limpiar = () => {
    const ahora = Date.now()
    for (const m of [ofertas, codigos, tokens]) for (const [k, v] of m) if (v.vence < ahora) m.delete(k)
    for (const [k, v] of nonces) if (v < ahora) nonces.delete(k)
  }
  setInterval(limpiar, 60 * 1000).unref()

  // --- Auditoría: reporta al centro lo que ocurre aquí. Solo monitoreo: nunca espera, nunca lanza, nunca cambia la emisión. ---
  const docDe = (d) => (typeof d === 'string' ? DOCUMENTOS[d] : d)
  const nombresDe = (doc) => (doc?.datos ?? []).map((d) => [].concat(d)[0]).filter((x) => typeof x === 'string')
  const auditar = (e) => { try { auditor.registrar({ canal: 'api', ...e }) } catch { /* reportar no puede afectar la emisión */ } }
  const auditarEmision = (req, evento, t0, error) => {
    try {
      const a0 = req.auditoria ?? {}
      const a = { ...a0, doc: docDe(a0.doc) }   // porConfiguracion devuelve el documento o, en algunos portales, solo su nombre corto
      const base = {
        tipo: evento, referencia: a.ref, duracion_ms: Date.now() - t0,
        credencial_tipo: a.doc?.vct, credencial_nombre: a.doc?.nombre,
        sujeto: a.documento ? { numero_documento: String(a.documento) } : undefined,
      }
      if (!error) {
        auditar({ ...base, resultado: 'exitoso', elementos: evento === 'emision_credencial' ? nombresDe(a.doc).map((n) => ({ sentido: 'emitido', tipo_credencial: a.doc?.vct ?? 'desconocida', elemento: n })) : undefined })
        return
      }
      const oauth = error instanceof ErrorOAuth
      auditar({
        ...base, resultado: 'fallido',
        tipo_error: !oauth ? 'interno' : error.estado === 401 ? 'autenticacion' : error.estado >= 500 ? 'emisor' : 'validacion',
        codigo_error: oauth ? error.error : 'invalid_request', mensaje_error: String(error.message ?? error).slice(0, 300),
      })
    } catch { /* idem */ }
  }

  // --- Metadata ---------------------------------------------------------------------------
  const logo = { uri: `${url}/publico/logo.png`, alt_text: notaria.nombre }
  const metadata = {
    credential_issuer: url,
    // El servidor de autorización es el propio emisor. El SDK de la wallet lo
    // necesita explícito: sin él no encuentra su metadata («at null»).
    authorization_servers: [url],
    credential_endpoint: `${url}/credencial`,
    nonce_endpoint: `${url}/nonce`,
    credential_request_encryption: {
      jwks: { keys: [jwkCifrado] }, enc_values_supported: ['A128GCM', 'A256GCM'], encryption_required: false,
    },
    // La wallet exige poder cifrar la respuesta (CredentialResponseEncryptionPolicy.REQUIRED): se ofrece, sin imponerla.
    credential_response_encryption: {
      alg_values_supported: ['ECDH-ES'], enc_values_supported: ['A128GCM', 'A256GCM'], encryption_required: false,
    },
    display: [{ name: notaria.nombre, locale: 'es', logo }],
    credential_configurations_supported: Object.fromEntries(Object.values(DOCUMENTOS).map((d) => [d.configuracion, {
      format: 'dc+sd-jwt',
      scope: d.configuracion,
      vct: d.vct,
      cryptographic_binding_methods_supported: ['jwk'],
      credential_signing_alg_values_supported: ['ES256'],
      proof_types_supported: { jwt: { proof_signing_alg_values_supported: ['ES256'], key_attestations_required: {} } },
      credential_metadata: {
        display: [{
          name: d.nombre, locale: 'es', description: d.descripcion, background_color: d.color, text_color: '#FFFFFF', logo,
          // La wallet lo descarga una vez (mismo host que el emisor, PNG/JPEG/WebP, ≤ 320 KB).
          ...(d.fondo ? { background_image: { uri: `${url}/publico/fondos/${d.fondo}` } } : {}),
        }],
        claims: d.datos.map(([clave, nombre]) => ({ path: [clave], display: [{ name: nombre, locale: 'es' }] })),
      },
    }])),
  }
  const servidorAutorizacion = {
    issuer: url,
    token_endpoint: `${url}/token`,
    grant_types_supported: [TIPO_PREAUTORIZADO],
    'pre-authorized_grant_anonymous_access_supported': true,
    token_endpoint_auth_methods_supported: ['attest_jwt_client_auth'],
    // Con estos dos el SDK de la wallet sabe que puede autenticarse con su WIA
    // (si faltan, se niega: «Client attestation based authentication is not
    // supported by the authorization server»). Son los que publica Keycloak.
    client_attestation_signing_alg_values_supported: ['ES256'],
    client_attestation_pop_signing_alg_values_supported: ['ES256'],
    dpop_signing_alg_values_supported: ['ES256'],
    response_types_supported: [],
  }

  /** Crea la oferta del documento de un trámite. Devuelve el enlace para la wallet y el PIN. */
  function ofrecer(tramite) {
    const doc = DOCUMENTOS[tramite.tipo]
    const codigo = azar()
    const pin = String(randomInt(0, 10000)).padStart(4, '0')
    const id = azar(16)
    const vence = Date.now() + VENCE_OFERTA_MS
    codigos.set(codigo, { ref: id, documento: tramite.persona?.documento, numero: tramite.numero, configuracion: doc.configuracion, pin, vence, intentos: 0 })
    auditar({ tipo: 'oferta_credencial', resultado: 'en_proceso', referencia: id, credencial_tipo: doc.vct, credencial_nombre: doc.nombre, canal: 'enlace',
             sujeto: tramite.persona?.documento ? { numero_documento: String(tramite.persona?.documento) } : undefined })
    ofertas.set(id, {
      vence,
      oferta: {
        credential_issuer: url,
        credential_configuration_ids: [doc.configuracion],
        grants: {
          [TIPO_PREAUTORIZADO]: {
            'pre-authorized_code': codigo,
            tx_code: { length: 4, input_mode: 'numeric', description: 'El PIN que te muestra la página de la notaría' },
          },
        },
      },
    })
    const enlace = `openid-credential-offer://?${new URLSearchParams({ credential_offer_uri: `${url}/oferta/${id}` })}`
    return { enlace, pin, vence: new Date(vence).toISOString() }
  }

  // --- DPoP (RFC 9449): si la wallet manda una prueba, el token queda ligado a su clave -----
  async function verificarDpop(cabecera, metodo, direccion, accessToken) {
    const { jwk, typ, alg } = decodeProtectedHeader(cabecera)
    if (typ !== 'dpop+jwt' || !jwk) throw new ErrorOAuth(400, 'invalid_dpop_proof', 'prueba DPoP mal formada')
    const { payload } = await jwtVerify(cabecera, await importJWK(jwk, alg ?? 'ES256'), { maxTokenAge: '300s', clockTolerance: 60 })
    if (payload.htm !== metodo || payload.htu?.split('?')[0] !== direccion) {
      throw new ErrorOAuth(400, 'invalid_dpop_proof', 'la prueba DPoP no es para esta petición')
    }
    if (accessToken && payload.ath !== createHash('sha256').update(accessToken).digest('base64url')) {
      throw new ErrorOAuth(401, 'invalid_dpop_proof', 'la prueba DPoP no es para este token')
    }
    return calculateJwkThumbprint(jwk)
  }

  // --- La clave a la que se liga el documento: proof JWT (OpenID4VCI §8.2.1) --------------
  async function claveDeLaPrueba(prueba) {
    const cab = decodeProtectedHeader(prueba)
    if (cab.typ !== 'openid4vci-proof+jwt') throw new ErrorOAuth(400, 'invalid_proof', 'la prueba debe ser openid4vci-proof+jwt')
    let jwk = cab.jwk
    if (!jwk && cab.key_attestation) {
      // La clave viene en una key attestation del wallet-provider: se comprueba quién la firmó.
      const ka = cab.key_attestation
      const { x5c, alg } = decodeProtectedHeader(ka)
      const { payload } = await jwtVerify(ka, await importX509(pem(x5c[0]), alg ?? 'ES256'))
      const r = await fetch(trustValidator, {
        method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json' },
        body: JSON.stringify({ chain: x5c, verificationContext: 'WalletProviderAttestation' }),
      })
      if (!r.ok || (await r.json()).trusted !== true) throw new ErrorOAuth(400, 'invalid_proof', 'key attestation de un wallet-provider no confiable')
      jwk = payload.attested_keys?.[0]
    }
    if (!jwk) throw new ErrorOAuth(400, 'invalid_proof', 'la prueba no dice qué clave ligar (jwk o key_attestation)')
    const { payload } = await jwtVerify(prueba, await importJWK(jwk, cab.alg ?? 'ES256'), { audience: url, maxTokenAge: '300s', clockTolerance: 60 })
    if (!payload.nonce || !nonces.has(payload.nonce)) throw new ErrorOAuth(400, 'invalid_nonce', 'nonce desconocido o vencido')
    nonces.delete(payload.nonce)
    const { kty, crv, x, y } = jwk
    return { kty, crv, x, y }
  }

  // --- Rutas ---------------------------------------------------------------------------------
  const r = express.Router()
  const json = (res, cuerpo, estadoHttp = 200) => res.status(estadoHttp).set('Cache-Control', 'no-store').json(cuerpo)
  const manejar = (fn) => async (req, res) => {
    const t0 = Date.now()
    const evento = req.path.endsWith('/token') ? 'autorizacion_emision' : req.path.endsWith('/credencial') ? 'emision_credencial' : null
    try {
      await fn(req, res)
      if (evento) auditarEmision(req, evento, t0)
    } catch (e) {
      if (evento) auditarEmision(req, evento, t0, e)
      const est = e instanceof ErrorOAuth ? e.estado : 400
      const error = e instanceof ErrorOAuth ? e.error : 'invalid_request'
      console.log(`[emisor] ${req.method} ${req.path} → ${est} ${error}: ${e.message}`)
      json(res, { error, error_description: e.message }, est)
    }
  }

  // Metadata en las dos ubicaciones: la de la especificación (/.well-known insertado
  // antes de la ruta) y al final de la ruta, que algunas wallets también prueban.
  // Son públicas y con CORS: la app de la wallet las lee desde su webview para
  // saber que este emisor solo entrega por oferta (sin authorization_code).
  const publica = (cuerpo) => (_req, res) => res.set('Access-Control-Allow-Origin', '*').json(cuerpo)
  for (const p of [`/.well-known/openid-credential-issuer${ruta}`, `${ruta}/.well-known/openid-credential-issuer`]) {
    r.get(p, publica(metadata))
  }
  for (const p of [`/.well-known/oauth-authorization-server${ruta}`, `${ruta}/.well-known/oauth-authorization-server`]) {
    r.get(p, publica(servidorAutorizacion))
  }

  r.get(`${ruta}/oferta/:id`, (req, res) => {
    const o = ofertas.get(req.params.id)
    if (!o || o.vence < Date.now()) return res.status(404).json({ error: 'oferta desconocida o vencida' })
    json(res, o.oferta)
  })

  r.post(`${ruta}/token`, express.urlencoded({ extended: false }), manejar(async (req, res) => {
    const { grant_type, 'pre-authorized_code': codigo, tx_code: pin } = req.body ?? {}
    if (grant_type !== TIPO_PREAUTORIZADO) throw new ErrorOAuth(400, 'unsupported_grant_type', 'solo pre-authorized_code')
    const c = codigos.get(codigo)
    req.auditoria = { ref: c?.ref, documento: c?.documento, doc: c && porConfiguracion(c.configuracion) }
    if (!c || c.vence < Date.now()) throw new ErrorOAuth(400, 'invalid_grant', 'el código venció o ya se usó: pide otra oferta en la notaría')

    // Solo una wallet certificada por un wallet-provider de la lista WP.
    let wallet
    try {
      wallet = await abca(req.headers)
    } catch (e) {
      throw new ErrorOAuth(401, 'invalid_client', e.message)
    }
    if (pin !== c.pin) {
      if (++c.intentos >= 3) codigos.delete(codigo)
      throw new ErrorOAuth(400, 'invalid_grant', 'PIN incorrecto')
    }
    codigos.delete(codigo)

    const jkt = req.headers.dpop ? await verificarDpop(req.headers.dpop, 'POST', `${url}/token`) : null
    const token = azar(32)
    tokens.set(token, { ref: c.ref, documento: c.documento, numero: c.numero, configuracion: c.configuracion, vence: Date.now() + VENCE_TOKEN_S * 1000, jkt })
    console.log(`[emisor] token para el trámite ${c.numero} (wallet ${wallet.cliente}, ${jkt ? 'DPoP' : 'Bearer'})`)
    json(res, { access_token: token, token_type: jkt ? 'DPoP' : 'Bearer', expires_in: VENCE_TOKEN_S })
  }))

  r.post(`${ruta}/nonce`, (_req, res) => {
    const c_nonce = azar()
    nonces.set(c_nonce, Date.now() + VENCE_NONCE_MS)
    json(res, { c_nonce })
  })

  r.post(`${ruta}/credencial`, express.json({ limit: '200kb' }), express.text({ type: 'application/jwt', limit: '200kb' }), manejar(async (req, res) => {
    if (typeof req.body === 'string') {
      // Petición cifrada (JWE compacto): se descifra con la clave anunciada en los metadatos.
      try {
        const { plaintext } = await compactDecrypt(req.body, claveCifrado.privateKey)
        req.body = JSON.parse(new TextDecoder().decode(plaintext))
      } catch {
        throw new ErrorOAuth(400, 'invalid_encryption_parameters', 'no se pudo descifrar la petición')
      }
    }
    const [esquema, valor] = (req.headers.authorization ?? '').split(' ')
    const t = tokens.get(valor)
    req.auditoria = { ref: t?.ref, documento: t?.documento, doc: t && porConfiguracion(t.configuracion) }
    if (!t || t.vence < Date.now()) throw new ErrorOAuth(401, 'invalid_token', 'token desconocido o vencido')
    if (t.jkt) {
      if (esquema !== 'DPoP' || !req.headers.dpop) throw new ErrorOAuth(401, 'invalid_token', 'este token exige DPoP')
      if ((await verificarDpop(req.headers.dpop, 'POST', `${url}/credencial`, valor)) !== t.jkt) {
        throw new ErrorOAuth(401, 'invalid_dpop_proof', 'la prueba DPoP es de otra clave')
      }
    }
    const pedido = req.body?.credential_configuration_id ?? t.configuracion
    if (pedido !== t.configuracion) throw new ErrorOAuth(400, 'invalid_credential_request', 'este token es para otro documento')

    const pruebas = req.body?.proofs?.jwt ?? (req.body?.proof?.jwt ? [req.body.proof.jwt] : [])
    if (!pruebas.length) throw new ErrorOAuth(400, 'invalid_proof', 'falta la prueba de posesión de la clave')

    const tramite = tramites.buscar(t.numero)
    if (!tramite) throw new ErrorOAuth(400, 'invalid_credential_request', 'el trámite ya no existe')
    if (tramite.estado === 'anulado') throw new ErrorOAuth(400, 'invalid_credential_request', 'el trámite fue anulado')
    const doc = porConfiguracion(t.configuracion)
    const datos = datosDelDocumento(tramite, notaria)

    const credentials = []
    for (const prueba of pruebas) {
      const cnf = await claveDeLaPrueba(prueba)
      credentials.push({
        credential: await emitirSdJwt({
          firmante, iss: url, vct: doc.vct, datos, cnf,
          estado: { indice: tramite.indice_estado, uri: estado.uri },
          validezDias: validezDias(tramite),
        }),
      })
    }
    tokens.delete(valor)
    tramites.registrarEntrega(tramite.numero)
    console.log(`[emisor] entregado ${doc.nombre} ${tramite.numero} (${credentials.length} copia(s))`)
    const cifrado = req.body?.credential_response_encryption
    if (cifrado) {
      // La respuesta va cifrada para la clave que mandó la wallet (OpenID4VCI §8.3.1.2): un JWE compacto.
      const { jwk, enc } = cifrado
      if (!jwk || !['A128GCM', 'A256GCM'].includes(enc)) throw new ErrorOAuth(400, 'invalid_encryption_parameters', 'cifrado de la respuesta no soportado')
      const clave = await importJWK({ ...jwk, alg: jwk.alg ?? 'ECDH-ES' }, jwk.alg ?? 'ECDH-ES')
      const jwe = await new CompactEncrypt(new TextEncoder().encode(JSON.stringify({ credentials })))
        .setProtectedHeader({ alg: jwk.alg ?? 'ECDH-ES', enc, ...(jwk.kid ? { kid: jwk.kid } : {}) }).encrypt(clave)
      return res.status(200).set('Cache-Control', 'no-store').type('application/jwt').send(jwe)
    }
    json(res, { credentials })
  }))

  r.get(`${ruta}/estado/1`, async (_req, res) => {
    res.type('application/statuslist+jwt').set('Cache-Control', 'no-store').send(await estado.token())
  })

  return { router: r, ofrecer, metadata }
}

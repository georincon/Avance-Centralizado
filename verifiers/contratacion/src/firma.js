// Firma del contrato de trabajo desde la wallet ("Firmar → Escanear QR").
//
// El portal hace de "servicio solicitante" de una firma remota (EUDI RQES, recuperación de documentos):
//
//   1. Genera el contrato en PDF con los datos ya verificados y lo publica (/firma/documento/<id>.pdf).
//   2. Publica una solicitud firmada (JAR, /firma/solicitud/<id>) que dice dónde está el documento y su huella.
//      La firma con el certificado de acceso del empleador (el mismo de su verifier): la wallet comprueba que
//      el nombre DNS del certificado es el del portal y que la cadena llega a una raíz en la que confía.
//   3. El QR trae solo la dirección de esa solicitud: eudi-rqes://?client_id=<dominio>&request_uri=<https…>
//   4. La wallet baja el documento, comprueba la huella, firma con su prestador de firma remota (CSC) y devuelve
//      el PDF firmado a /firma/respuesta/<id> (direct_post). El portal comprueba la firma y la muestra.
//
// Formato de la solicitud: el de eudi-lib-jvm-rqes-csc-kt (paquete documentretrieval): JWT ES256 con x5c,
// client_id_scheme x509_san_dns, response_mode direct_post y response_uri en el mismo dominio que client_id.
// OJO: la librería de la wallet solo acepta signatureQualifier = "eu_eidas_qes". Es una etiqueta del protocolo, no
// una afirmación: en Colombia no existe la firma cualificada y esto es una firma electrónica de prueba.

import { createHash, createPrivateKey, randomBytes, sign as firmar, X509Certificate } from 'node:crypto'
import { spawn } from 'node:child_process'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const OID_SHA256 = '2.16.840.1.101.3.4.2.1'
const ETIQUETA_CUALIFICADA = 'eu_eidas_qes'

const b64url = (b) => Buffer.from(b).toString('base64url')

/** Lee el certificado de acceso del empleador: la cadena (hoja + CA) y la clave privada. */
export function cargarAcceso({ cadena, clave }) {
  const pem = readFileSync(cadena, 'utf8')
  const certs = pem.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g) ?? []
  if (certs.length === 0) throw new Error('la cadena del certificado de acceso está vacía')
  const x5c = certs.map((c) => new X509Certificate(c).raw.toString('base64'))
  const hoja = new X509Certificate(certs[0])
  const dns = /DNS:([^,\s]+)/.exec(hoja.subjectAltName ?? '')?.[1]
  if (!dns) throw new Error('el certificado de acceso no tiene nombre DNS (SAN)')
  return { x5c, dns, privada: createPrivateKey(readFileSync(clave, 'utf8')) }
}

// --- El contrato en PDF ----------------------------------------------------------------------------
// PDF mínimo pero válido (una página A4, Helvetica con codificación WinAnsi): sin dependencias.

const esc = (s) => s.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)')
const latin1 = (s) => Buffer.from(s.normalize('NFC').replace(/[^ -ÿ]/g, '?'), 'latin1')

function ajustar(texto, ancho) {
  const lineas = []
  let actual = ''
  for (const palabra of texto.split(/\s+/)) {
    if ((actual + ' ' + palabra).trim().length > ancho) { lineas.push(actual); actual = palabra } else actual = (actual + ' ' + palabra).trim()
  }
  if (actual) lineas.push(actual)
  return lineas
}

export function crearContratoPdf({ empleador, nombres, apellidos, tipoDocumento, documento, cargo, formacion, fecha }) {
  const f = formacion ?? {}
  const fechaGrado = f.fecha_grado ? new Date(f.fecha_grado).toLocaleDateString('es-CO', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' }) : '—'
  const parrafos = [
    ['b', 'CONTRATO INDIVIDUAL DE TRABAJO'],
    ['i', 'Documento de prueba del ecosistema EUDI de Avance. No tiene validez legal.'],
    ['', ''],
    ['p', `Entre ${empleador}, en adelante EL EMPLEADOR, y ${nombres} ${apellidos}, identificado/a con ${tipoDocumento ? tipoDocumento + ' ' : ''}No. ${documento}, en adelante EL TRABAJADOR, se celebra el presente contrato de trabajo, regido por las cláusulas siguientes.`],
    ['', ''],
    ['b', 'PRIMERA. Objeto.'],
    ['p', `EL TRABAJADOR se obliga a prestar sus servicios personales al EMPLEADOR en el cargo de ${cargo}, bajo su subordinación y con los conocimientos propios de su formación.`],
    ['b', 'SEGUNDA. Formación acreditada.'],
    ['p', `EL TRABAJADOR acreditó, mediante una credencial verificable presentada desde su wallet y comprobada por EL EMPLEADOR, el nivel ${f.nivel ?? '—'}, programa ${f.programa ?? '—'}, título ${f.titulo ?? '—'}, con fecha de grado ${fechaGrado}${f.institucion ? ', emitida por ' + f.institucion : ''}.`],
    ['b', 'TERCERA. Duración y remuneración.'],
    ['p', 'El contrato es a término indefinido y la remuneración será la acordada por las partes en el anexo de condiciones.'],
    ['b', 'CUARTA. Firma.'],
    ['p', 'EL TRABAJADOR firma este documento desde su wallet con una firma electrónica de prueba. La firma queda incrustada en el propio documento, junto con el certificado del firmante y un sello de tiempo.'],
    ['', ''],
    ['p', `Fecha: ${fecha}`],
  ]
  const lineas = []
  for (const [tipo, texto] of parrafos) {
    if (tipo === 'b') lineas.push({ fuente: 'F2', tam: 12, texto })
    else if (tipo === 'i') lineas.push({ fuente: 'F1', tam: 9, texto })
    else if (!texto) lineas.push({ fuente: 'F1', tam: 8, texto: '' })
    else for (const l of ajustar(texto, 88)) lineas.push({ fuente: 'F1', tam: 11, texto: l })
  }
  let y = 790
  const partes = ['BT']
  let ultima = null
  for (const l of lineas) {
    partes.push(`/${l.fuente} ${l.tam} Tf 1 0 0 1 60 ${y} Tm (${esc(l.texto)}) Tj`)
    y -= l.tam + 6
    ultima = l
  }
  partes.push('ET')
  const flujo = latin1(partes.join('\n'))

  const objetos = [
    Buffer.from('<< /Type /Catalog /Pages 2 0 R >>'),
    Buffer.from('<< /Type /Pages /Kids [3 0 R] /Count 1 >>'),
    Buffer.from('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>'),
    Buffer.concat([Buffer.from(`<< /Length ${flujo.length} >>\nstream\n`), flujo, Buffer.from('\nendstream')]),
    Buffer.from('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>'),
    Buffer.from('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>'),
  ]
  void ultima
  const trozos = [Buffer.from('%PDF-1.7\n%\xe2\xe3\xcf\xd3\n', 'latin1')]
  const desplazamientos = []
  let largo = trozos[0].length
  objetos.forEach((o, i) => {
    desplazamientos.push(largo)
    const b = Buffer.concat([Buffer.from(`${i + 1} 0 obj\n`), o, Buffer.from('\nendobj\n')])
    trozos.push(b); largo += b.length
  })
  const xref = [`xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n`, ...desplazamientos.map((d) => `${String(d).padStart(10, '0')} 00000 n \n`)].join('')
  trozos.push(Buffer.from(`${xref}trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${largo}\n%%EOF\n`))
  return Buffer.concat(trozos)
}

export const huella = (pdf) => createHash('sha256').update(pdf).digest('base64')

// --- La solicitud firmada (JAR) -----------------------------------------------------------------------

/** JWT ES256 con la cadena del certificado de acceso en x5c. */
export function crearSolicitudJar({ acceso, urlPublica, id, pdf, etiqueta, nonce, state, vigenciaSeg = 300 }) {
  const ahora = Math.floor(Date.now() / 1000)
  const cabecera = { alg: 'ES256', typ: 'oauth-authz-req+jwt', x5c: acceso.x5c }
  const cuerpo = {
    iss: acceso.dns,
    client_id: acceso.dns,
    client_id_scheme: 'x509_san_dns',
    response_type: 'vp_token',
    response_mode: 'direct_post',
    response_uri: `${urlPublica}/firma/respuesta/${id}`,
    nonce,
    state,
    iat: ahora,
    exp: ahora + vigenciaSeg,
    signatureQualifier: ETIQUETA_CUALIFICADA,
    hashAlgorithmOID: OID_SHA256,
    documentDigests: [{ hash: huella(pdf), label: etiqueta }],
    documentLocations: [{ uri: `${urlPublica}/firma/documento/${id}.pdf`, method: { type: 'public' } }],
  }
  const firmando = `${b64url(JSON.stringify(cabecera))}.${b64url(JSON.stringify(cuerpo))}`
  const firma = firmar('sha256', Buffer.from(firmando), { key: acceso.privada, dsaEncoding: 'ieee-p1363' })
  return `${firmando}.${b64url(firma)}`
}

/** El QR trae solo la dirección de la solicitud. */
export const enlaceFirma = ({ acceso, urlPublica, id }) =>
  `eudi-rqes://?client_id=${encodeURIComponent(acceso.dns)}&request_uri=${encodeURIComponent(`${urlPublica}/firma/solicitud/${id}`)}`

// --- Comprobar el PDF firmado --------------------------------------------------------------------------

function openssl(args, { entrada, dir } = {}) {
  return new Promise((resolve) => {
    const p = spawn('openssl', args, { cwd: dir, stdio: ['pipe', 'pipe', 'pipe'] })
    let salida = '', error = ''
    p.stdout.on('data', (d) => { salida += d }); p.stderr.on('data', (d) => { error += d })
    p.on('error', (e) => resolve({ ok: false, salida, error: e.message }))
    p.on('close', (codigo) => resolve({ ok: codigo === 0, salida, error }))
    p.stdin.end(entrada)
  })
}

const BYTE_RANGE = /\/ByteRange\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\]/g

/**
 * Comprueba un PDF firmado devuelto por la wallet. Devuelve { valida, ... } con lo que se comprobó:
 *   - que el PDF empieza por EXACTAMENTE el contrato original (la firma se añade al final, sin tocarlo),
 *   - que la firma cubre todo el archivo,
 *   - la firma CMS y su cadena hasta la raíz de confianza (openssl cms -verify),
 *   - el firmante, y si lleva sello de tiempo, que ese sello es válido para esa firma.
 */
export async function verificarFirmado({ firmado, original, raiz }) {
  const r = { valida: false, comprobaciones: [] }
  const ok = (texto, bien, detalle) => { r.comprobaciones.push({ texto, ok: bien, detalle }); return bien }
  if (!ok('Es el mismo contrato que se envió', firmado.length > original.length && firmado.subarray(0, original.length).equals(original))) return r

  const texto = firmado.toString('latin1')
  const rangos = [...texto.matchAll(BYTE_RANGE)]
  if (!ok('Tiene una firma incrustada', rangos.length > 0)) return r
  const [a, b, c, e] = rangos.at(-1).slice(1).map(Number)
  if (!ok('La firma cubre todo el documento', a === 0 && c + e === firmado.length)) return r

  const hex = texto.slice(a + b + 1, c - 1)
  if (!ok('El hueco de la firma tiene contenido', /[1-9a-fA-F]/.test(hex))) return r
  const crudo = Buffer.from(hex.replace(/[^0-9a-fA-F]/g, ''), 'hex')
  let longitud
  if (crudo[0] !== 0x30) { ok('La firma es un bloque CMS', false); return r }
  if (crudo[1] & 0x80) { const n = crudo[1] & 0x7f; longitud = 2 + n + crudo.readUIntBE(2, n) } else longitud = 2 + crudo[1]
  const cms = crudo.subarray(0, longitud)
  const cubierto = Buffer.concat([firmado.subarray(a, a + b), firmado.subarray(c, c + e)])

  const dir = await mkdtemp(join(tmpdir(), 'firma-'))
  try {
    await writeFile(join(dir, 'firma.der'), cms)
    await writeFile(join(dir, 'datos.bin'), cubierto)
    const v = await openssl(['cms', '-verify', '-inform', 'DER', '-in', 'firma.der', '-content', 'datos.bin', '-binary',
      '-CAfile', raiz, '-purpose', 'any', '-signer', 'firmante.pem', '-out', '/dev/null'], { dir })
    if (!ok('La firma es válida y la cadena llega a la raíz de confianza', v.ok, v.ok ? undefined : v.error.split('\n')[0])) return r

    const info = await openssl(['x509', '-in', join(dir, 'firmante.pem'), '-noout', '-subject', '-issuer', '-dates', '-nameopt', 'multiline,utf8,-esc_msb'])
    const dato = (k) => new RegExp(`${k}\\s*=\\s*(.+)`).exec(info.salida)?.[1]?.trim()
    r.firmante = { nombre: dato('commonName'), documento: dato('serialNumber')?.replace(/^CC/, ''), emisor: /issuer=[\s\S]*?commonName\s*=\s*(.+)/.exec(info.salida)?.[1]?.trim() }

    // Sello de tiempo: atributo id-aa-signatureTimeStampToken (1.2.840.113549.1.9.16.2.14).
    const arbol = await openssl(['asn1parse', '-inform', 'DER', '-in', join(dir, 'firma.der'), '-i'])
    const lineas = arbol.salida.split('\n')
    const iSello = lineas.findIndex((l) => l.includes('id-smime-aa-timeStampToken'))
    if (iSello < 0) {
      r.sello = null
      ok('Lleva sello de tiempo', false, 'La firma no tiene sello de tiempo')
    } else {
      const offset = Number(lineas[iSello + 2].split(':')[0].trim())
      const firmaValor = lineas.filter((l) => /^\s*\d+:d=5 .*OCTET STRING.*HEX DUMP/.test(l)).at(-1)?.split('HEX DUMP]:')[1]?.trim()
      await openssl(['asn1parse', '-inform', 'DER', '-in', join(dir, 'firma.der'), '-strparse', String(offset), '-noout', '-out', join(dir, 'sello.der')])
      const digest = createHash('sha256').update(Buffer.from(firmaValor ?? '', 'hex')).digest('hex')
      const t = await openssl(['ts', '-verify', '-token_in', '-in', join(dir, 'sello.der'), '-digest', digest, '-sha256', '-CAfile', raiz])
      if (ok('El sello de tiempo es válido para esta firma', t.ok, t.ok ? undefined : t.error.split('\n').find((l) => l.includes('error')) ?? 'sello inválido')) {
        const txt = await openssl(['ts', '-reply', '-token_in', '-in', join(dir, 'sello.der'), '-text'])
        const hora = /Time stamp: (.+)/.exec(txt.salida)?.[1]?.trim()
        r.sello = { fecha: hora ? new Date(hora.replace(/ GMT$/, ' UTC')).toISOString() : null }
      }
    }
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
  r.valida = r.comprobaciones.every((x) => x.ok || x.texto === 'Lleva sello de tiempo')
  return r
}

export const nuevoId = () => randomBytes(16).toString('base64url')

// --- Sello de tiempo --------------------------------------------------------------------------------------
// El SDK de la wallet firma en nivel B-B (sin sello) y manda ese PDF tal cual. El portal lo convierte en B-T
// (PAdES con sello de tiempo, ETSI EN 319 122-1): pide a la autoridad de sellado un sello sobre el VALOR de la
// firma y lo añade como atributo no firmado (id-aa-signatureTimeStampToken) dentro del bloque CMS. Un atributo no
// firmado no forma parte de lo que cubre la firma, y el bloque cabe en el hueco que el PDF ya reservó (se rellena
// con ceros hasta el mismo tamaño), así que ni /ByteRange ni un solo byte firmado cambian.

// Mínimo de DER para recorrer y reescribir el CMS.
function tlv(buf, pos) {
  const tag = buf[pos]
  let len = buf[pos + 1], cab = 2
  if (len & 0x80) { const n = len & 0x7f; len = n ? buf.readUIntBE(pos + 2, n) : -1; cab = 2 + n }
  if (len < 0) throw new Error('DER de longitud indefinida: no soportado')
  return { tag, pos, cab, ini: pos + cab, fin: pos + cab + len }
}
function hijos(buf, t) {
  const r = []
  for (let p = t.ini; p < t.fin;) { const h = tlv(buf, p); r.push(h); p = h.fin }
  return r
}
function der(tag, contenido) {
  const n = contenido.length
  const cab = n < 0x80 ? [tag, n] : n < 0x100 ? [tag, 0x81, n] : n < 0x10000 ? [tag, 0x82, n >> 8, n & 0xff] : [tag, 0x83, n >> 16, (n >> 8) & 0xff, n & 0xff]
  return Buffer.concat([Buffer.from(cab), contenido])
}
const trozo = (buf, t) => buf.subarray(t.pos, t.fin)

/** Devuelve el CMS con el sello añadido a su único firmante. `token` es el TimeStampToken (ContentInfo, DER). */
function cmsConSello(cms, token) {
  const contentInfo = tlv(cms, 0)
  const [oid, envoltura] = hijos(cms, contentInfo)             // OID signedData, [0] EXPLICIT
  const signedData = tlv(cms, envoltura.ini)
  const partes = hijos(cms, signedData)
  const signerInfos = partes.at(-1)                             // SET OF SignerInfo (el último)
  if (signerInfos.tag !== 0x31) throw new Error('el CMS no tiene signerInfos')
  const firmantes = hijos(cms, signerInfos)
  if (firmantes.length !== 1) throw new Error('se esperaba un único firmante')
  const si = firmantes[0]
  const campos = hijos(cms, si)
  const atributo = der(0x30, Buffer.concat([der(0x06, Buffer.from('2a864886f70d010910020e', 'hex')), der(0x31, token)]))
  let nuevoSi
  if (campos.at(-1).tag === 0xa1) {                             // ya había atributos no firmados: se añade uno
    const ua = campos.at(-1)
    nuevoSi = der(0x30, Buffer.concat([cms.subarray(si.ini, ua.pos), der(0xa1, Buffer.concat([cms.subarray(ua.ini, ua.fin), atributo]))]))
  } else {
    nuevoSi = der(0x30, Buffer.concat([cms.subarray(si.ini, si.fin), der(0xa1, atributo)]))
  }
  const nuevosSignerInfos = der(0x31, nuevoSi)
  const nuevoSignedData = der(0x30, Buffer.concat([cms.subarray(signedData.ini, signerInfos.pos), nuevosSignerInfos]))
  return der(0x30, Buffer.concat([trozo(cms, oid), der(0xa0, nuevoSignedData)]))
}

/** El valor de la firma del único firmante (OCTET STRING de SignerInfo) y si ya lleva sello. */
function leerFirma(cms) {
  const signedData = tlv(cms, hijos(cms, tlv(cms, 0))[1].ini)
  const si = tlv(cms, hijos(cms, hijos(cms, signedData).at(-1))[0].pos)
  const campos = hijos(cms, si)
  const octetos = campos.filter((c) => c.tag === 0x04)
  const valor = cms.subarray(octetos.at(-1).ini, octetos.at(-1).fin)
  const sellado = campos.at(-1).tag === 0xa1 && cms.subarray(campos.at(-1).ini, campos.at(-1).fin).includes(Buffer.from('2a864886f70d010910020e', 'hex'))
  return { valor, sellado }
}

/** TimeStampReq (RFC 3161) con SHA-256, sin política y con certReq para que el sello traiga su cadena. */
function consultaSello(huellaBytes) {
  // INTEGER positivo en DER mínimo: primer byte entre 0x01 y 0x7f, sin cero de relleno.
  const nonce = randomBytes(8); nonce[0] = 1 + (nonce[0] % 0x7f)
  const imprint = der(0x30, Buffer.concat([der(0x30, Buffer.concat([der(0x06, Buffer.from('608648016503040201', 'hex')), Buffer.from([0x05, 0x00])])), der(0x04, huellaBytes)]))
  return der(0x30, Buffer.concat([Buffer.from([0x02, 0x01, 0x01]), imprint, der(0x02, nonce), Buffer.from([0x01, 0x01, 0xff])]))
}

/**
 * Añade un sello de tiempo a la firma del PDF. `pedirSello(consultaDer)` debe devolver la TimeStampResp (DER).
 * Devuelve el PDF nuevo (mismo tamaño) o lanza un error: quien llama conserva el PDF original si falla.
 */
export async function sellarFirmado({ firmado, pedirSello }) {
  const texto = firmado.toString('latin1')
  const m = [...texto.matchAll(BYTE_RANGE)].at(-1)
  if (!m) throw new Error('el PDF no tiene firma')
  const [a, b, c, e] = m.slice(1).map(Number)
  const ini = a + b + 1, fin = c - 1
  const crudo = Buffer.from(texto.slice(ini, fin).replace(/[^0-9a-fA-F]/g, ''), 'hex')
  const raiz = tlv(crudo, 0)
  const cms = crudo.subarray(0, raiz.fin)
  const { valor, sellado } = leerFirma(cms)
  if (sellado) return firmado
  const resp = await pedirSello(consultaSello(createHash('sha256').update(valor).digest()))
  const respuesta = tlv(resp, 0)
  const [estado, token] = hijos(resp, respuesta)
  const codigo = resp[hijos(resp, estado)[0].ini]
  if (!token || (codigo !== 0 && codigo !== 1)) throw new Error('la autoridad de sellado rechazó la solicitud')
  const nuevo = cmsConSello(cms, trozo(resp, token))
  const huecoHex = fin - ini
  if (nuevo.length * 2 > huecoHex) throw new Error('el sello no cabe en el hueco de la firma')
  const salida = Buffer.from(firmado)
  Buffer.from(nuevo.toString('hex').toUpperCase().padEnd(huecoHex, '0'), 'latin1').copy(salida, ini)
  return salida
}

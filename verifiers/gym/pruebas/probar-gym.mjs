// Prueba de punta a punta del gimnasio SIN teléfono ni verifier real.
//
// Levanta otro servidor del gimnasio (puerto 8099, datos en un directorio temporal, aforo de 2 personas) y un
// verifier simulado (puerto 8098) que entrega tarjetas reales: las firma con la clave del propio emisor. Recorre
// hacerse socio, entrar, salir, el aforo, la suspensión, el vencimiento y los rechazos.
//
// Se ejecuta dentro del contenedor del gimnasio (tiene las dependencias y la clave de firma):
//
//     docker exec -i gym-gym-1 node --input-type=module < pruebas/probar-gym.mjs

import { spawn } from 'node:child_process'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import http from 'node:http'
import { tmpdir } from 'node:os'
import { importPKCS8 } from 'jose'
import { DOCUMENTOS, datosDelDocumento } from '/app/src/documentos.js'
import { emitirSdJwt } from '/app/src/sdjwt.js'
import { crearSocios } from '/app/src/socios.js'

const PUERTO = 8099, PUERTO_V = 8098
const URL_PUBLICA = 'https://prueba.local/gym'
const CLAVE = 'clave-de-prueba'
// PNG de 1×1: lo que haría el PID con la foto, en miniatura.
const FOTO = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
const dir = mkdtempSync(`${tmpdir()}/gym-`)
const archivo = `${dir}/socios.json`
const socios = crearSocios(archivo)

const certificado = readFileSync('/secretos/ds.crt', 'utf8')
const firmante = { clave: await importPKCS8(readFileSync('/secretos/ds.key', 'utf8'), 'ES256'), x5c: [certificado.replace(/-----[^-]+-----|\s/g, '')] }
const cnf = { kty: 'EC', crv: 'P-256', x: 'f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU', y: 'x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0' }

// --- Verifier simulado ---------------------------------------------------------------------------------
const programado = new Map() // transacción → SD-JWT que "presenta la wallet"
const pedidos = []
let n = 0
const verifier = http.createServer((req, res) => {
  const ok = (cuerpo, estado = 200) => { res.writeHead(estado, { 'content-type': 'application/json' }); res.end(JSON.stringify(cuerpo)) }
  const u = req.url.replace('/verifier-gym', '')
  if (u === '/ui/intended-uses') return ok({ intended_uses: [{ intended_use_id: 1 }] })
  if (req.method === 'POST' && u === '/ui/presentations/v2') {
    let c = ''; req.on('data', (x) => (c += x)); req.on('end', () => { const id = `tx-${++n}`; pedidos.push({ id, cuerpo: JSON.parse(c) }); ok({ transaction_id: id, authorization_request_uri: `openid4vp://prueba/${id}` }) })
    return
  }
  const m = /^\/ui\/presentations\/(tx-\d+)(\/events)?/.exec(u)
  if (m && m[2]) return ok({ events: [] })
  if (m) return programado.has(m[1]) ? ok({ vp_token: { doc: [programado.get(m[1])] } }) : ok({}, 400)
  ok({}, 404)
})
await new Promise((r) => verifier.listen(PUERTO_V, r))

// --- Servidor del gimnasio ------------------------------------------------------------------------------
const servidor = spawn('node', ['/app/src/servidor.js'], {
  env: { ...process.env, URL_PUBLICA, VERIFIER_API: `http://127.0.0.1:${PUERTO_V}/verifier-gym`, TRUST_VALIDATOR_URL: 'http://127.0.0.1:1/x', GYM_NOMBRE: 'Cumbre Gym', ADMIN_CLAVE: CLAVE, AFORO_MAXIMO: '2', SECRETOS_DIR: '/secretos', SOCIOS_ARCHIVO: archivo, PUERTO: String(PUERTO) },
  stdio: ['ignore', 'pipe', 'pipe'],
})
let salidaServidor = ''
servidor.stdout.on('data', (d) => (salidaServidor += d)); servidor.stderr.on('data', (d) => (salidaServidor += d))
for (let i = 0; i < 40; i++) { try { if ((await fetch(`http://127.0.0.1:${PUERTO}/gym/salud`)).ok) break } catch { /* esperar */ } await new Promise((r) => setTimeout(r, 250)) }

// --- Utilidades ---------------------------------------------------------------------------------------
let fallos = 0, total = 0
const prueba = (nombre, condicion, detalle = '') => { total++; if (!condicion) fallos++; console.log(`${condicion ? '  ✔' : '  ✘'} ${nombre}${condicion ? '' : `  → ${detalle}`}`) }
function sesion(cabeceras = {}) {
  let cookie = ''
  return async (ruta, { metodo = 'GET', cuerpo } = {}) => {
    const r = await fetch(`http://127.0.0.1:${PUERTO}/gym${ruta}`, { method: metodo, headers: { 'content-type': 'application/json', ...cabeceras, ...(cookie ? { cookie } : {}) }, body: cuerpo ? JSON.stringify(cuerpo) : undefined })
    const sc = r.headers.get('set-cookie'); if (sc) cookie = sc.split(';')[0]
    const texto = await r.text()
    let json; try { json = JSON.parse(texto) } catch { json = null }
    return { estado: r.status, json, texto }
  }
}
const pid = (doc, nombres, apellidos) => emitirSdJwt({ firmante, iss: 'https://pid.prueba', vct: 'urn:eudi:pid:1', cnf, datos: { personal_administrative_number: doc, given_name: nombres, family_name: apellidos, birthdate: '1990-05-05', picture: FOTO } })
const tarjeta = (socio, iss = URL_PUBLICA, numero) => {
  const datos = datosDelDocumento('tarjeta', socio, 'Cumbre Gym')
  if (numero) datos.numero_socio = numero
  return emitirSdJwt({ firmante, iss, vct: DOCUMENTOS.tarjeta.vct, cnf, datos, estado: { indice: socios.registrarEmision(socio.documento, 'tarjeta').indice, uri: `${URL_PUBLICA}/estado/1` } })
}
/** Crea la solicitud, "presenta" lo dado y devuelve el resultado final que ve la pantalla. */
async function presentar(s, cuerpo, sdjwt) {
  const r = await s('/api/solicitud', { metodo: 'POST', cuerpo })
  if (r.estado !== 200) return r
  programado.set(pedidos.at(-1).id, sdjwt)
  for (let i = 0; i < 10; i++) { const p = await s(`/api/solicitud/${r.json.id}`); if (p.json?.estado !== 'esperando') return p }
  return { estado: 0, json: { estado: 'esperando' } }
}
const dentroDelArchivo = () => JSON.parse(readFileSync(archivo, 'utf8'))

try {
  console.log('\nBásicos')
  const p = sesion()
  prueba('el servicio responde', (await p('/salud')).json?.estado === 'ok')
  for (const ruta of ['/', '/acceso', '/acceso/entrada', '/acceso/salida', '/admin']) { const r = await p(ruta); prueba(`la página ${ruta} carga`, r.estado === 200 && r.texto.includes('<html'), `HTTP ${r.estado}`) }
  prueba('las páginas no dejan marcadores sin reemplazar', !(await p('/')).texto.includes('{{') && !(await p('/acceso')).texto.includes('{{') && !(await p('/admin')).texto.includes('{{'))
  const meta = await fetch(`http://127.0.0.1:${PUERTO}/.well-known/openid-credential-issuer/gym`).then((r) => r.json())
  prueba('la metadata anuncia la tarjeta de socio', meta.credential_configuration_ids === undefined && meta.credential_configurations_supported?.['co.gym.tarjeta_socio']?.vct === DOCUMENTOS.tarjeta.vct)
  prueba('el emisor ofrece 10 datos en la tarjeta (con la foto)', meta.credential_configurations_supported['co.gym.tarjeta_socio'].credential_metadata.claims.length === 10)

  console.log('\nPuerta: sin clave no se registra nada')
  const puerta = sesion()
  prueba('sin clave de puerta, la solicitud de acceso se rechaza (401)', (await puerta('/api/solicitud', { metodo: 'POST', cuerpo: { proposito: 'acceso', modo: 'entrada', sede: 'Cumbre Centro' } })).estado === 401)
  prueba('clave de puerta incorrecta → 401', (await puerta('/api/puerta/entrar', { metodo: 'POST', cuerpo: { clave: 'mala' } })).estado === 401)
  prueba('clave correcta activa la pantalla', (await puerta('/api/puerta/entrar', { metodo: 'POST', cuerpo: { clave: CLAVE } })).estado === 200)
  // Otra IP (el servidor mira x-forwarded-for): el freno es por IP y no debe bloquear al administrador legítimo.
  const intrusa = sesion({ 'x-forwarded-for': '203.0.113.9' })
  let ultimo
  for (let i = 0; i < 6; i++) ultimo = await intrusa('/api/admin/entrar', { metodo: 'POST', cuerpo: { clave: `x${i}` } })
  prueba('tras 5 fallos se frena la prueba de claves (429)', ultimo.estado === 429, `HTTP ${ultimo.estado}`)

  console.log('\nHacerse socio')
  const ana = sesion()
  const rid = await presentar(ana, { proposito: 'identificacion' }, await pid('71010', 'Ana', 'Prueba'))
  prueba('se identifica con su PID', rid.json?.estado === 'listo', JSON.stringify(rid.json))
  const pedidoId = JSON.stringify(pedidos.at(-1).cuerpo.dcql_query)
  prueba('la identificación pide nombre, documento y la foto del PID, y nada más', pedidoId.includes('personal_administrative_number') && pedidoId.includes('picture') && !pedidoId.includes('address'))
  prueba('antes de afiliarse no es socia', (await ana('/api/yo')).json?.socio === null)
  prueba('plan inexistente → 400', (await ana('/api/membresia', { metodo: 'POST', cuerpo: { plan: 'gratis', sede: 'Cumbre Centro' } })).estado === 400)
  const alta = await ana('/api/membresia', { metodo: 'POST', cuerpo: { plan: 'mensual', sede: 'Cumbre Centro' } })
  prueba('se hace socia con el plan mensual (201)', alta.estado === 201 && alta.json.membresia.estado === 'Activa' && /^GYM-\d{6}$/.test(alta.json.membresia.numero), JSON.stringify(alta.json))
  prueba('la foto del PID queda guardada en el socio', socios.buscar('71010')?.foto === FOTO)
  prueba('el portal le devuelve su propia foto para su tarjeta', alta.json?.foto === FOTO)
  prueba('el plan mensual dura 30 días contando hoy', alta.json.membresia.dias_restantes === 29, `quedan ${alta.json?.membresia?.dias_restantes}`)
  prueba('no puede afiliarse dos veces con otro plan (renueva)', (await ana('/api/membresia', { metodo: 'POST', cuerpo: { plan: 'dia', sede: 'Cumbre Norte' } })).estado === 200)
  // Se vuelve al mensual para el resto de la prueba.
  await ana('/api/membresia', { metodo: 'POST', cuerpo: { plan: 'mensual', sede: 'Cumbre Centro' } })
  const oferta = await ana('/api/oferta', { metodo: 'POST', cuerpo: {} })
  prueba('recibe una oferta de tarjeta con PIN de 4 dígitos', oferta.estado === 200 && /^\d{4}$/.test(oferta.json?.pin) && oferta.json.enlace.startsWith('openid-credential-offer://'), JSON.stringify(oferta.json))

  const anaSocio = socios.buscar('71010')
  const t1 = await tarjeta(anaSocio)
  prueba('la tarjeta emitida lleva la foto en el dato picture', datosDelDocumento('tarjeta', anaSocio, 'Cumbre Gym').picture === FOTO)

  console.log('\nEntrada y salida')
  const entrada = (sdjwt, sede = 'Cumbre Centro') => presentar(puerta, { proposito: 'acceso', modo: 'entrada', sede }, sdjwt)
  const salida = (sdjwt, sede = 'Cumbre Centro') => presentar(puerta, { proposito: 'acceso', modo: 'salida', sede }, sdjwt)
  let r = await entrada(t1)
  prueba('Ana entra', r.json?.acceso?.ok === true && r.json.acceso.nombre === 'Ana' && r.json.acceso.aforo.dentro === 1, JSON.stringify(r.json))
  const claims = JSON.stringify(pedidos.at(-1).cuerpo.dcql_query)
  prueba('la entrada NO pide el documento del socio, pero sí la foto', !claims.includes('socio_documento') && claims.includes('numero_socio') && claims.includes('picture'), claims)
  prueba('la pantalla de la puerta recibe la foto para compararla', r.json?.acceso?.foto === FOTO)
  r = await entrada(t1)
  prueba('no puede entrar dos veces sin salir (y se le muestra la foto igual)', r.json?.acceso?.ok === false && /Ya registra ingreso/.test(r.json.acceso.motivo) && r.json.acceso.foto === FOTO, JSON.stringify(r.json))
  r = await entrada(await tarjeta(anaSocio), 'Cumbre Norte')
  prueba('tampoco entrando por otra sede', r.json?.acceso?.ok === false, JSON.stringify(r.json))
  r = await salida(t1)
  prueba('Ana sale y se registra su permanencia', r.json?.acceso?.ok === true && typeof r.json.acceso.permanencia_min === 'number' && r.json.acceso.aforo.dentro === 0, JSON.stringify(r.json))
  r = await salida(t1)
  prueba('una salida sin ingreso se deja pasar y se anota', r.json?.acceso?.ok === true && r.json.acceso.sin_ingreso === true, JSON.stringify(r.json))

  console.log('\nMembresía suspendida, vencida y reactivada')
  const admin = sesion()
  prueba('el administrador entra', (await admin('/api/admin/entrar', { metodo: 'POST', cuerpo: { clave: CLAVE } })).estado === 200)
  prueba('sin sesión de administrador no se ve el resumen (401)', (await sesion()('/api/admin/resumen')).estado === 401)
  prueba('suspende a Ana', (await admin('/api/admin/socios/71010', { metodo: 'PUT', cuerpo: { estado: 'Suspendida' } })).estado === 200)
  r = await entrada(t1)
  prueba('suspendida no entra aunque tenga la tarjeta', r.json?.acceso?.ok === false && /suspendida/.test(r.json.acceso.motivo), JSON.stringify(r.json))
  prueba('suspendida no puede pedir tarjeta nueva (409)', (await ana('/api/oferta', { metodo: 'POST', cuerpo: {} })).estado === 409)
  await admin('/api/admin/socios/71010', { metodo: 'PUT', cuerpo: { estado: 'Activa' } })
  prueba('al reactivar, la tarjeta anterior queda reemplazada en la status list', dentroDelArchivo().emisiones.filter((e) => e.documento === '71010' && e.estado === 'vigente').length === 0)
  const t2 = await tarjeta(socios.buscar('71010'))
  r = await entrada(t2)
  prueba('reactivada y con tarjeta nueva, entra', r.json?.acceso?.ok === true, JSON.stringify(r.json))
  await salida(t2)
  const datosArchivo = dentroDelArchivo(); datosArchivo.socios[0].membresia.hasta = '2020-01-01'; writeFileSync(archivo, JSON.stringify(datosArchivo))
  r = await entrada(t2)
  prueba('con la membresía vencida no entra', r.json?.acceso?.ok === false && /venció/.test(r.json.acceso.motivo), JSON.stringify(r.json))
  const ren = await ana('/api/membresia', { metodo: 'POST', cuerpo: { plan: 'trimestral', sede: 'Cumbre Sur' } })
  prueba('renueva desde hoy porque ya había vencido (90 días)', ren.json?.membresia?.estado === 'Activa' && ren.json.membresia.dias_restantes === 89, JSON.stringify(ren.json?.membresia))
  const t3 = await tarjeta(socios.buscar('71010'))
  r = await entrada(t3)
  prueba('con la renovación y la tarjeta nueva, entra', r.json?.acceso?.ok === true, JSON.stringify(r.json))

  console.log('\nAforo y rechazos')
  const beto = socios.afiliar({ documento: '72020', nombres: 'Beto', apellidos: 'Prueba', fecha_nacimiento: '1985-01-01', plan: 'mensual', sede: 'Cumbre Centro' })
  const carla = socios.afiliar({ documento: '73030', nombres: 'Carla', apellidos: 'Prueba', fecha_nacimiento: '1992-01-01', plan: 'anual', sede: 'Cumbre Centro' })
  r = await entrada(await tarjeta(beto), 'Cumbre Centro')
  prueba('Beto entra (aforo 2/2 con Ana)', r.json?.acceso?.ok === true && r.json.acceso.aforo.dentro === 2, JSON.stringify(r.json))
  r = await entrada(await tarjeta(carla), 'Cumbre Centro')
  prueba('con el aforo completo, Carla no entra', r.json?.acceso?.ok === false && /aforo/i.test(r.json.acceso.motivo), JSON.stringify(r.json))
  r = await entrada(await tarjeta(carla), 'Cumbre Norte')
  prueba('en otra sede con cupo sí entra (el aforo es por sede)', r.json?.acceso?.ok === true, JSON.stringify(r.json))
  r = await entrada(await tarjeta(carla, 'https://otro-gimnasio.test/gym'), 'Cumbre Sur')
  prueba('una tarjeta de otro emisor se rechaza', r.json?.acceso?.ok === false && /no la emitió/.test(r.json.acceso.motivo), JSON.stringify(r.json))
  r = await entrada(await tarjeta(carla, URL_PUBLICA, 'GYM-000000'), 'Cumbre Sur')
  prueba('una tarjeta con un número desconocido se rechaza', r.json?.acceso?.ok === false && /ningún socio/.test(r.json.acceso.motivo), JSON.stringify(r.json))
  prueba('un modo inválido se rechaza (400)', (await puerta('/api/solicitud', { metodo: 'POST', cuerpo: { proposito: 'acceso', modo: 'abrir', sede: 'Cumbre Centro' } })).estado === 400)
  prueba('una sede inventada se rechaza (400)', (await puerta('/api/solicitud', { metodo: 'POST', cuerpo: { proposito: 'acceso', modo: 'entrada', sede: 'Cumbre Luna' } })).estado === 400)

  console.log('\nAdministración')
  const resumen = (await admin('/api/admin/resumen')).json
  const centro = resumen.sedes.find((s) => s.sede === 'Cumbre Centro')
  prueba('el aforo en vivo cuenta a Ana y a Beto en el Centro', centro.dentro === 2 && centro.personas.some((x) => x.nombre === 'Ana Prueba') && centro.personas.some((x) => x.nombre === 'Beto Prueba'), JSON.stringify(centro))
  prueba('el historial muestra nombres y los denegados', resumen.historial.some((a) => a.tipo === 'denegado') && resumen.historial.some((a) => a.nombre === 'Ana Prueba'))
  prueba('la lista de socios trae su estado y días', resumen.socios.length === 3 && resumen.socios.every((s) => ['Activa', 'Vencida', 'Suspendida'].includes(s.estado)))
  const cierre = await admin('/api/admin/cerrar', { metodo: 'POST', cuerpo: { sede: 'Cumbre Centro' } })
  prueba('cerrar la sede marca la salida de los que siguen dentro', cierre.json?.cerrados === 2 && (await admin('/api/admin/resumen')).json.sedes.find((s) => s.sede === 'Cumbre Centro').dentro === 0, JSON.stringify(cierre.json))
  r = await entrada(await tarjeta(socios.buscar('71010')), 'Cumbre Centro')
  prueba('tras el cierre puede volver a entrar', r.json?.acceso?.ok === true, JSON.stringify(r.json))

  console.log('\nPrivacidad y tarjetas')
  const archivoFinal = readFileSync(archivo, 'utf8')
  const accesos = JSON.parse(archivoFinal).accesos
  prueba('la foto no se guarda en el registro de accesos ni sale en el log', !JSON.stringify(accesos).includes('data:image') && !salidaServidor.includes('data:image'))
  prueba('el registro de accesos no guarda documentos ni nombres', accesos.length > 5 && accesos.every((a) => !('documento' in a) && !('nombre' in a)) && !JSON.stringify(accesos).match(/71010|72020|73030|Ana|Beto|Carla/))
  const e = socios.emisionesDe('71010').find((x) => x.estado === 'vigente')
  const rev = await ana(`/api/credenciales/${e.indice}/revocar`, { metodo: 'POST' })
  prueba('el socio puede revocar su tarjeta', rev.estado === 200 && rev.json.estado === 'revocada', JSON.stringify(rev.json))
  prueba('no puede revocar la tarjeta de otro (404)', (await ana('/api/credenciales/999/revocar', { metodo: 'POST' })).estado === 404)
  prueba('el log del servidor no trae documentos de identidad', !/71010|72020|73030/.test(salidaServidor), salidaServidor.match(/.*(1010|2020|3030).*/)?.[0])
} catch (e) {
  fallos++; console.log(`  ✘ la prueba se interrumpió: ${e.stack}`)
} finally {
  servidor.kill(); verifier.close()
  console.log(`\n${total - fallos}/${total} pruebas correctas${fallos ? `, ${fallos} con fallos` : ''}`)
  if (fallos) console.log(`--- salida del servidor ---\n${salidaServidor}`)
  process.exit(fallos ? 1 : 0)
}

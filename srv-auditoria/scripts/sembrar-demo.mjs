#!/usr/bin/env node
// Siembra datos FICTICIOS de demostración a través de la API de ingesta (también sirve como prueba de extremo a extremo).
//   AUDITORIA_URL=http://localhost:8110 AUDITORIA_CLAVE_WALLET=... node scripts/sembrar-demo.mjs [usuarios] [dias]
import { randomUUID } from 'node:crypto';

const URL_BASE = process.env.AUDITORIA_URL || 'http://localhost:8110';
const CLAVE = process.env.AUDITORIA_CLAVE_WALLET;
const N = Number(process.argv[2] || 24);
const DIAS = Number(process.argv[3] || 30);
if (!CLAVE) { console.error('Falta AUDITORIA_CLAVE_WALLET'); process.exit(1); }

// Generador determinista para que la demo sea repetible.
let semilla = 20261110;
const rnd = () => ((semilla = (semilla * 1664525 + 1013904223) % 4294967296) / 4294967296);
const elige = (a) => a[Math.floor(rnd() * a.length)];
const ponderado = (pares) => { let r = rnd() * pares.reduce((s, p) => s + p[1], 0); for (const [v, w] of pares) { if ((r -= w) <= 0) return v; } return pares[0][0]; };

const NOMBRES = ['Camila Andrea', 'Juan Sebastián', 'Valentina', 'Santiago', 'Laura Daniela', 'Andrés Felipe', 'Mariana', 'Carlos Eduardo', 'Daniela', 'Miguel Ángel', 'Sofía', 'Julián David', 'Paula Andrea', 'Nicolás', 'Isabella', 'Felipe', 'Natalia', 'Sergio Alejandro', 'Gabriela', 'Diego Armando', 'Lucía', 'Mateo', 'Carolina', 'Esteban'];
const APELLIDOS = ['Rojas Pérez', 'Gómez Martínez', 'Hernández Díaz', 'Ramírez Castro', 'Torres Vargas', 'López Mejía', 'Moreno Silva', 'Jiménez Ortiz', 'Ruiz Cardona', 'Parra Salazar', 'Cortés Arango', 'Mendoza Ríos', 'Vélez Duque', 'Cano Restrepo', 'Peña Naranjo'];
const CIUDADES = ['Bogotá D.C.', 'Medellín (Antioquia)', 'Cali (Valle del Cauca)', 'Barranquilla (Atlántico)', 'Bucaramanga (Santander)', 'Cartagena (Bolívar)', 'Pereira (Risaralda)', 'Manizales (Caldas)'];
const MODELOS = [['samsung', 'SM-A546E', '14'], ['samsung', 'SM-S911B', '15'], ['Xiaomi', 'Redmi Note 12', '13'], ['motorola', 'moto g84', '14'], ['Google', 'Pixel 8', '15'], ['HONOR', 'X8b', '14']];

const EMISORES = [
  { tipo: 'emisor', identificador: 'https://civica-desarrollo.avance.org.co/pid-registraduria', nombre: 'Registraduría Nacional', cred: 'eu.europa.ec.eudi.pid.1', nombre_cred: 'Cédula digital (PID)' },
  { tipo: 'emisor', identificador: 'https://civica-desarrollo.avance.org.co/pid-transito', nombre: 'Tránsito (ITT)', cred: 'org.iso.18013.5.1.mDL', nombre_cred: 'Licencia de conducción' },
  { tipo: 'emisor', identificador: 'https://civica-desarrollo.avance.org.co/uis-issuer', nombre: 'Universidad UIS', cred: 'co.edu.uis.diploma.1', nombre_cred: 'Título académico' },
];
const VERIFICADORES = ['Banco Avance', 'EPS Salud Total', 'Aerolínea Andina', 'Gimnasio Fit24', 'Notaría 12', 'Beca ICETEX', 'Cancillería', 'Discoteca Neón'].map((n) => ({ tipo: 'verificador', identificador: 'https://civica-desarrollo.avance.org.co/verifiers/' + n.toLowerCase().replace(/[^a-z0-9]+/g, '-'), nombre: n }));
const ATRIBUTOS = ['family_name', 'given_name', 'birth_date', 'document_number', 'age_over_18', 'nationality', 'portrait', 'issue_date'];
const ERRORES = {
  emisor: [['red', 'TIMEOUT', 'Tiempo de espera agotado al contactar al emisor'], ['emisor', 'invalid_grant', 'El emisor rechazó el código de autorización'], ['emisor', 'issuer_untrusted', 'El emisor no figura en la lista de confianza'], ['validacion', 'ATTESTATION_INVALID', 'La atestación de la wallet no pudo validarse']],
  verificador: [['verificador', 'untrusted_verifier', 'El verificador no es de confianza'], ['red', 'UnknownHostException', 'No se pudo resolver el servidor del verificador'], ['usuario', 'user_cancelled', 'El usuario no autorizó compartir los datos'], ['autenticacion', 'KEY_UNLOCK_FAILED', 'Falló el desbloqueo de la clave del dispositivo']],
  sesion: [['autenticacion', 'PIN_INCORRECTO', 'PIN incorrecto'], ['autenticacion', 'BIOMETRIA_FALLIDA', 'La biometría no coincide'], ['autenticacion', 'BLOQUEO_TEMPORAL', 'Demasiados intentos: bloqueo temporal']],
};

async function enviar(e) {
  const r = await fetch(URL_BASE + '/api/v1/eventos', { method: 'POST', headers: { 'content-type': 'application/json', 'x-api-key': CLAVE }, body: JSON.stringify(e) });
  if (!r.ok) throw new Error(r.status + ' ' + await r.text());
}

const ahora = Date.now();
let total = 0;
for (let i = 0; i < N; i++) {
  const nombres = NOMBRES[i % NOMBRES.length]; const apellidos = elige(APELLIDOS);
  const doc = String(1000000000 + Math.floor(rnd() * 90000000));
  const [fab, mod, andr] = elige(MODELOS);
  const wallet = { wallet_id: randomUUID(), dispositivo: { identificador_android: randomUUID().replace(/-/g, '').slice(0, 16), fabricante: fab, marca: fab.toLowerCase(), modelo: mod, producto: mod.toLowerCase().replace(/\W+/g, '_'), hardware: elige(['qcom', 'exynos', 'mt6789']), version_android: andr, nivel_api: 30 + Number(andr), parche_seguridad: '2026-0' + (5 + (i % 4)) + '-01', version_app: '1.0.' + (3 + (i % 4)), codigo_version_app: 3 + (i % 4), arquitectura: 'arm64-v8a', memoria_ram_mb: elige([4096, 6144, 8192]), pantalla: '1080x2400 @ 420 dpi', idioma: 'es-CO', zona_horaria: 'America/Bogota', biometria_disponible: rnd() < 0.9, bloqueo_pantalla: true } };
  const usuario = {
    tipo_documento: 'CC', numero_documento: doc, nombres, apellidos,
    fecha_nacimiento: `${1965 + Math.floor(rnd() * 40)}-${String(1 + Math.floor(rnd() * 12)).padStart(2, '0')}-${String(1 + Math.floor(rnd() * 28)).padStart(2, '0')}`,
    lugar_nacimiento: elige(CIUDADES), genero: i % 2 === 0 ? 'F' : 'M', grupo_sanguineo: elige(['O+', 'A+', 'B+', 'O-', 'AB+']), estatura_cm: 150 + Math.floor(rnd() * 40),
    fecha_expedicion: '2012-06-15', lugar_expedicion: elige(CIUDADES), validacion_decision: 'APPROVED', validacion_puntaje_rostro: 80 + Math.round(rnd() * 19),
  };
  const slug = (nombres.split(' ')[0] + '.' + apellidos.split(' ')[0]).toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  const contactos = [{ tipo: 'celular', valor: '3' + String(100000000 + Math.floor(rnd() * 99999999)), verificado: true }, { tipo: 'correo', valor: `${slug}@correo-demo.co`, verificado: true }];
  const inicio = ahora - (DIAS - Math.floor(rnd() * 6)) * 86400000;
  const tiene = { pid: false, mdl: rnd() < 0.5, uis: rnd() < 0.3 };

  const ev = (tipo, ms, resultado, extra = {}, sesion) => ({
    evento_id: randomUUID(), wallet, usuario, contactos, sesion,
    actividad: { tipo, ocurrido_en: new Date(ms).toISOString(), resultado, ...extra },
  });
  const cola = [];
  cola.push(ev('registro_usuario', inicio, 'exitoso', { duracion_ms: 180000 + Math.floor(rnd() * 120000), detalle: { metodo: 'cédula + selfie' } }));
  cola.push(ev('creacion_pin', inicio + 240000, 'exitoso'));

  // Credenciales recibidas (PID primero).
  let t = inicio + 600000;
  for (const em of EMISORES) {
    const quiere = em.cred.includes('pid') || (em.cred.includes('mDL') && tiene.mdl) || (em.cred.includes('uis') && tiene.uis);
    if (!quiere) continue;
    const intentos = rnd() < 0.3 ? 2 : 1;
    for (let k = 0; k < intentos; k++) {
      const falla = intentos === 2 && k === 0;
      const [te, ce, me] = elige(ERRORES.emisor);
      cola.push(ev('solicitud_credencial', t, 'exitoso', { contraparte: em, credencial_tipo: em.cred, canal: 'internet' }));
      cola.push(ev('emision_credencial', t + 4000, falla ? 'fallido' : 'exitoso', { contraparte: em, credencial_tipo: em.cred, canal: 'internet', duracion_ms: 2500 + Math.floor(rnd() * 5000), detalle: { nombre: em.nombre_cred }, ...(falla ? { tipo_error: te, codigo_error: ce, mensaje_error: me } : {}) }));
      t += 3600000 * (1 + rnd() * 20);
    }
  }

  // Uso diario: sesiones con presentaciones.
  const nSes = 8 + Math.floor(rnd() * 28);
  for (let s = 0; s < nSes; s++) {
    const ms = inicio + 86400000 + rnd() * (ahora - inicio - 86400000);
    const sesion = { sesion_id: randomUUID(), metodo_autenticacion: rnd() < 0.7 ? 'biometria' : 'pin' };
    if (rnd() < 0.12) {
      const [te, ce, me] = elige(ERRORES.sesion);
      cola.push(ev('inicio_sesion', ms - 20000, 'fallido', { tipo_error: te, codigo_error: ce, mensaje_error: me }, { ...sesion, sesion_id: randomUUID() }));
    }
    cola.push(ev('inicio_sesion', ms, 'exitoso', {}, sesion));
    let u = ms;
    const k = ponderado([[0, 4], [1, 4], [2, 2], [3, 1]]);
    for (let j = 0; j < k; j++) {
      const ver = elige(VERIFICADORES); u += 60000 + rnd() * 240000;
      const canal = ponderado([['internet', 5], ['qr', 3], ['nfc', 1], ['ble', 1]]);
      cola.push(ev('solicitud_presentacion', u, 'exitoso', { contraparte: ver, canal, credencial_tipo: 'eu.europa.ec.eudi.pid.1' }, sesion));
      const r = ponderado([['exitoso', 80], ['fallido', 14], ['cancelado', 6]]);
      const err = r === 'fallido' ? elige(ERRORES.verificador) : r === 'cancelado' ? ERRORES.verificador[2] : null;
      const elementos = r === 'exitoso' ? [...new Set([elige(ATRIBUTOS), elige(ATRIBUTOS), 'age_over_18'])].map((x) => ({ sentido: 'compartido', tipo_credencial: 'eu.europa.ec.eudi.pid.1', elemento: x })) : [];
      cola.push(ev('presentacion_credencial', u + 9000, r, { contraparte: ver, canal, credencial_tipo: 'eu.europa.ec.eudi.pid.1', duracion_ms: 1200 + Math.floor(rnd() * 4000), elementos, ...(err ? { tipo_error: err[0], codigo_error: err[1], mensaje_error: err[2] } : {}) }, sesion));
    }
    cola.push(ev('cierre_sesion', u + 30000 + rnd() * 120000, 'exitoso', { detalle: { motivo: elige(['usuario', 'segundo_plano', 'segundo_plano']) } }, sesion));
  }
  cola.sort((a, b) => Date.parse(a.actividad.ocurrido_en) - Date.parse(b.actividad.ocurrido_en));
  for (const e of cola) { await enviar(e); total++; }
}
console.log(`Sembrados ${N} usuarios y ${total} actividades (datos ficticios).`);

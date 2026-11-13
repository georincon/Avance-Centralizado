import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import QRCode from 'qrcode';
import { timingSafeEqual } from 'node:crypto';
import { extname, join, normalize } from 'node:path';
import pg from 'pg';
import { registrarEvento, ErrorValidacion } from './ingesta.js';
import * as q from './consultas.js';
import { registrarEventoServicio } from './ingesta-servicios.js';
import * as qs from './consultas-servicios.js';

pg.types.setTypeParser(1082, (v) => v);   // columnas DATE como texto AAAA-MM-DD, sin corrimiento de zona horaria
const PUERTO = Number(process.env.PUERTO || 8110);
const CLAVE_WALLET = process.env.AUDITORIA_CLAVE_WALLET;      // la usan las wallets para reportar
const TOKEN_PANEL = process.env.AUDITORIA_TOKEN_PANEL;        // la usa el dashboard (solo lectura)
const APK = process.env.RUTA_APK || '/salidas/apk/dev/debug/app-dev-debug.apk';
const URL_PUBLICA = (process.env.URL_PUBLICA || 'https://civica-desarrollo.avance.org.co/srv-auditoria').replace(/\/$/, '');
const WALLET = { nombre: process.env.WALLET_NOMBRE || 'wallet-auditoria', paquete: process.env.WALLET_PAQUETE || '', version: process.env.WALLET_VERSION || '', android_minimo: process.env.WALLET_ANDROID_MINIMO || '10' };
const DIR_FRONT = process.env.DIR_FRONTEND || '/app/frontend';
if (!CLAVE_WALLET || !TOKEN_PANEL) { console.error('Faltan AUDITORIA_CLAVE_WALLET y AUDITORIA_TOKEN_PANEL'); process.exit(1); }

const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 10 });
// Las consultas del dashboard corren con un rol que solo puede leer.
const lectura = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 5, options: '-c search_path=auditoria,public -c default_transaction_read_only=on' });
pool.on('connect', (c) => c.query('SET search_path = auditoria, public'));

const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.webp': 'image/webp', '.pdf': 'application/pdf' };

const iguales = (a, b) => { const x = Buffer.from(a || ''); const y = Buffer.from(b || ''); return x.length === y.length && timingSafeEqual(x, y); };
const json = (res, st, obj) => { res.writeHead(st, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }); res.end(JSON.stringify(obj)); };
const ip = (req) => (req.headers['x-real-ip'] || req.socket.remoteAddress || '').replace('::ffff:', '') || null;

async function cuerpo(req, max = 1_000_000) {
  const partes = []; let n = 0;
  for await (const p of req) { n += p.length; if (n > max) throw new ErrorValidacion('cuerpo demasiado grande'); partes.push(p); }
  try { return JSON.parse(Buffer.concat(partes).toString('utf8') || '{}'); } catch { throw new ErrorValidacion('JSON inválido'); }
}

async function ingesta(req, res, ruta) {
  if (!iguales(req.headers['x-api-key'], CLAVE_WALLET)) return json(res, 401, { error: 'clave de wallet inválida' });
  const b = await cuerpo(req);
  const lista = ruta.endsWith('/lote') ? b.eventos : [b];
  if (!Array.isArray(lista) || lista.length > 200) throw new ErrorValidacion('lote inválido (máximo 200 eventos)');
  const resultados = [];
  for (const e of lista) {
    try { resultados.push({ evento_id: e?.evento_id, ...(await registrarEvento(pool, e, ip(req))) }); }
    catch (err) {
      if (!(err instanceof ErrorValidacion)) throw err;
      resultados.push({ evento_id: e?.evento_id, error: err.message });
    }
  }
  if (!ruta.endsWith('/lote') && resultados[0].error) return json(res, 400, resultados[0]);
  return json(res, 202, ruta.endsWith('/lote') ? { resultados } : resultados[0]);
}

async function panel(req, res, url) {
  const auth = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!iguales(auth, TOKEN_PANEL)) return json(res, 401, { error: 'no autorizado' });
  const p = url.pathname.replace(/^\/api\/panel/, '');
  const f = Object.fromEntries(url.searchParams);
  if (p !== '/eventos') pool.query('INSERT INTO auditoria.consulta_dashboard (direccion_ip, ruta, parametros) VALUES ($1,$2,$3)', [ip(req), p, JSON.stringify(f)]).catch(() => {});
  let m;
  if (p === '/eventos') return eventos(req, res);
  if (p === '/resumen') return json(res, 200, await q.resumen(lectura, f.dias));
  if (p === '/series') return json(res, 200, await q.series(lectura, f.dias));
  if (p === '/distribucion') return json(res, 200, await q.distribucion(lectura, f.dias));
  if (p === '/catalogos') return json(res, 200, await q.catalogos(lectura));
  if (p === '/contrapartes') return json(res, 200, await q.contrapartes(lectura));
  if (p === '/actividades') return json(res, 200, await q.actividades(lectura, f));
  if (p === '/actividades.csv') {
    res.writeHead(200, { 'content-type': 'text/csv; charset=utf-8', 'content-disposition': 'attachment; filename="actividades.csv"' });
    return res.end('﻿' + await q.actividadesCsv(lectura, f));
  }
  if ((m = p.match(/^\/actividades\/(\d+)$/))) { const r = await q.actividad(lectura, m[1]); return r ? json(res, 200, r) : json(res, 404, { error: 'no existe' }); }
  // Actividad de los emisores y de los verificadores (lo que reportan los propios servicios).
  if ((m = p.match(/^\/(emisores|verificadores)\/([a-z.]+)(?:\/(\d+))?$/))) {
    const rol = m[1] === 'emisores' ? 'emisor' : 'verificador'; const que = m[2];
    if (que === 'resumen') return json(res, 200, await qs.resumen(lectura, rol, f.dias));
    if (que === 'series') return json(res, 200, await qs.series(lectura, rol, f.dias));
    if (que === 'distribucion') return json(res, 200, await qs.distribucion(lectura, rol, f.dias));
    if (que === 'servicios') return json(res, 200, await qs.servicios(lectura, rol, f.dias));
    if (que === 'catalogos') return json(res, 200, await qs.catalogos(lectura, rol));
    if (que === 'actividades') return json(res, 200, await qs.actividades(lectura, rol, f));
    if (que === 'actividades.csv') {
      res.writeHead(200, { 'content-type': 'text/csv; charset=utf-8', 'content-disposition': `attachment; filename="actividad-${m[1]}.csv"` });
      return res.end('﻿' + await qs.actividadesCsv(lectura, rol, f));
    }
    if (que === 'actividad' && m[3]) { const r = await qs.actividad(lectura, rol, m[3]); return r ? json(res, 200, r) : json(res, 404, { error: 'no existe' }); }
  }
  if (p === '/wallets') return json(res, 200, await q.wallets(lectura, f));
  if ((m = p.match(/^\/wallets\/([0-9a-f-]{36})$/i))) { const r = await q.wallet(lectura, m[1]); return r ? json(res, 200, r) : json(res, 404, { error: 'no existe' }); }
  if (p === '/usuarios') return json(res, 200, await q.usuarios(lectura, f));
  if ((m = p.match(/^\/usuarios\/([0-9a-f-]{36})$/i))) { const r = await q.usuario(lectura, m[1]); return r ? json(res, 200, r) : json(res, 404, { error: 'no existe' }); }
  return json(res, 404, { error: 'ruta desconocida' });
}

// ── Descarga de la wallet (pública: la usan los teléfonos; el APK no contiene secretos de usuarios) ──
let huella = { clave: '', sha256: '' };
async function infoApk() {
  const st = await stat(APK);
  const clave = `${st.size}:${st.mtimeMs}`;
  if (huella.clave !== clave) {   // el SHA-256 de ~490 MB se calcula una vez por compilación
    const h = createHash('sha256');
    for await (const c of createReadStream(APK)) h.update(c);
    huella = { clave, sha256: h.digest('hex') };
  }
  return { ...WALLET, enlace: `${URL_PUBLICA}/descargas/wallet-auditoria.apk`, archivo: 'wallet-auditoria.apk', tamano_bytes: st.size,
           compilada_en: st.mtime.toISOString(), sha256: huella.sha256, servidor: URL_PUBLICA };
}
async function descargas(req, res, ruta) {
  try {
    if (ruta === '/descargas/info.json') return json(res, 200, await infoApk());
    if (ruta === '/descargas/qr.svg') {
      const svg = await QRCode.toString(`${URL_PUBLICA}/descargas/wallet-auditoria.apk`, { type: 'svg', margin: 1, errorCorrectionLevel: 'M', color: { dark: '#0f1f3d', light: '#ffffff' } });
      res.writeHead(200, { 'content-type': 'image/svg+xml', 'cache-control': 'no-cache' }); return res.end(svg);
    }
    if (ruta === '/descargas/wallet-auditoria.apk') {
      const st = await stat(APK);
      res.writeHead(200, { 'content-type': 'application/vnd.android.package-archive', 'content-length': st.size,
        'content-disposition': 'attachment; filename="wallet-auditoria.apk"', 'cache-control': 'no-cache' });
      if (req.method === 'HEAD') return res.end();
      return createReadStream(APK).pipe(res);
    }
  } catch (e) {
    if (e.code === 'ENOENT') return json(res, 404, { error: 'La wallet aún no ha sido compilada' });
    throw e;
  }
  return json(res, 404, { error: 'no existe' });
}

// ── Tiempo real: PostgreSQL avisa (NOTIFY) y la API reenvía a los dashboards abiertos (SSE) ──
const oyentes = new Set();
function difundir(texto) { for (const r of oyentes) r.write(`event: cambio\ndata: ${texto}\n\n`); }
async function escucharBase() {
  const c = new pg.Client({ connectionString: process.env.DATABASE_URL });
  const reintentar = () => { c.removeAllListeners(); c.end().catch(() => {}); setTimeout(escucharBase, 3000); };
  c.on('error', (e) => { console.error('LISTEN:', e.message); reintentar(); });
  c.on('end', reintentar);
  c.on('notification', (m) => difundir(m.payload));
  try { await c.connect(); await c.query('LISTEN auditoria_cambios'); console.log('Escuchando cambios de la base (LISTEN auditoria_cambios)'); difundir(JSON.stringify({ tabla: 'conexion', op: 'RECONECTADA' })); }
  catch (e) { console.error('LISTEN:', e.message); reintentar(); }
}
escucharBase();

function eventos(req, res) {
  res.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache, no-transform', 'x-accel-buffering': 'no', connection: 'keep-alive' });
  res.write('retry: 3000\n\nevent: listo\ndata: {}\n\n');
  oyentes.add(res);
  const latido = setInterval(() => res.write(': latido\n\n'), 20000);
  req.on('close', () => { clearInterval(latido); oyentes.delete(res); });
}

async function estatico(res, ruta) {
  const rel = normalize(ruta === '/' ? '/index.html' : ruta).replace(/^(\.\.[/\\])+/, '');
  try {
    const dato = await readFile(join(DIR_FRONT, rel));
    res.writeHead(200, { 'content-type': MIME[extname(rel)] || 'application/octet-stream', 'cache-control': 'no-cache' });
    res.end(dato);
  } catch { res.writeHead(404); res.end('no encontrado'); }
}

// ── Ingesta de EMISORES y VERIFICADORES ──────────────────────────────────────────────────────────
// Escucha en un puerto aparte que NO publica el proxy único: solo la alcanzan los contenedores de la red del ecosistema.
// Es solo monitoreo y los servicios no esperan la respuesta; si el centro falla, ellos siguen funcionando.
const PUERTO_SERVICIOS = Number(process.env.PUERTO_SERVICIOS || 8111);
const CLAVE_SERVICIOS = process.env.AUDITORIA_CLAVE_SERVICIOS || '';   // opcional: si se define, los servicios deben enviarla
async function ingestaServicios(req, res, ruta) {
  if (CLAVE_SERVICIOS && !iguales(req.headers['x-api-key'], CLAVE_SERVICIOS)) return json(res, 401, { error: 'clave de servicios inválida' });
  const b = await cuerpo(req);
  const lista = ruta.endsWith('/lote') ? b.eventos : [b];
  if (!Array.isArray(lista) || lista.length > 200) throw new ErrorValidacion('lote inválido (máximo 200 eventos)');
  const resultados = [];
  for (const e of lista) {
    try { resultados.push({ evento_id: e?.evento_id, ...(await registrarEventoServicio(pool, e, ip(req))) }); }
    catch (err) { if (!(err instanceof ErrorValidacion)) throw err; resultados.push({ evento_id: e?.evento_id, error: err.message }); }
  }
  if (!ruta.endsWith('/lote') && resultados[0].error) return json(res, 400, resultados[0]);
  return json(res, 202, ruta.endsWith('/lote') ? { resultados } : resultados[0]);
}
http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    if (url.pathname === '/salud') return json(res, 200, { estado: 'ok' });
    if (req.method === 'POST' && /^\/api\/v1\/servicios\/eventos(\/lote)?$/.test(url.pathname)) return await ingestaServicios(req, res, url.pathname);
    json(res, 404, { error: 'no existe' });
  } catch (err) {
    if (err instanceof ErrorValidacion) return json(res, 400, { error: err.message });
    console.error(err);
    json(res, 500, { error: 'error interno' });
  }
}).listen(PUERTO_SERVICIOS, () => console.log(`ingesta de servicios escuchando en :${PUERTO_SERVICIOS} (solo red interna)`));

http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    if (url.pathname === '/salud') return json(res, 200, { estado: 'ok' });
    if (req.method === 'POST' && /^\/api\/v1\/eventos(\/lote)?$/.test(url.pathname)) return await ingesta(req, res, url.pathname);
    if (req.method === 'GET' && url.pathname.startsWith('/api/panel/')) return await panel(req, res, url);
    if ((req.method === 'GET' || req.method === 'HEAD') && url.pathname.startsWith('/descargas/')) return await descargas(req, res, url.pathname);
    if (req.method === 'GET') return await estatico(res, url.pathname);
    json(res, 405, { error: 'método no permitido' });
  } catch (err) {
    if (err instanceof ErrorValidacion) return json(res, 400, { error: err.message });
    console.error(err);
    json(res, 500, { error: 'error interno' });
  }
}).listen(PUERTO, () => console.log(`srv-auditoria escuchando en :${PUERTO}`));

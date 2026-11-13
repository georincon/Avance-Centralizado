// Historial LOCAL de un portal: guarda en su propio proyecto cada emisión de credencial (si emite) y cada verificación
// (si verifica), y lo muestra en una página de consulta del propio portal:  <BASE>/auditoria/
//
//   - Se guarda en la BASE DE DATOS PROPIA del portal (Postgres, variable HISTORIAL_DB_URL): tabla `evento`, solo de inserción
//     (un disparador impide UPDATE y DELETE), con índices y las vistas v_emisiones y v_verificaciones.
//   - Si la base no responde, el portal NO se detiene: el hecho se anota en un archivo (historial.jsonl en HISTORIAL_DIR) y se pasa
//     a la base cuando vuelve. Sin HISTORIAL_DB_URL todo el historial va solo al archivo.
//   - Solo nombres de datos (nunca valores) y, como persona atendida, la cédula/nombre si el portal ya los conocía.
//   - Transparente: guardar es en segundo plano y cualquier error se ignora.
//   - La página es de administración: el proxy único (srv-nw-nginx, mapa $es_admin) solo la deja abrir desde IPs de administración.
//
// Esta es la ÚNICA fuente (verifiers/_comun/auditoria/historial.js); cada portal tiene una copia en su src/ (instalar.py).

import { appendFile, readFile, stat, mkdir, rename, rm } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { join } from 'node:path'
import pg from 'pg'

const DIR = process.env.HISTORIAL_DIR || '/datos'
const ARCHIVO = join(DIR, 'historial.jsonl')
const MAX_LEIDOS = 50000
const URL_DB = process.env.HISTORIAL_DB_URL || ''

// ───────── base de datos propia ─────────
const ESQUEMA = `
CREATE TABLE IF NOT EXISTS evento (
  id                uuid PRIMARY KEY,
  ocurrido_en       timestamptz NOT NULL,
  registrado_en     timestamptz NOT NULL DEFAULT now(),
  rol               text NOT NULL CHECK (rol IN ('emisor','verificador')),
  tipo              text NOT NULL,
  resultado         text NOT NULL,
  referencia        text,
  credencial_tipo   text,
  credencial_nombre text,
  sujeto_documento  text,
  sujeto_nombre     text,
  canal             text,
  duracion_ms       integer,
  tipo_error        text,
  codigo_error      text,
  mensaje_error     text,
  elementos         jsonb NOT NULL DEFAULT '[]'::jsonb,
  detalle           jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS evento_fecha_ix ON evento (ocurrido_en DESC);
CREATE INDEX IF NOT EXISTS evento_rol_resultado_ix ON evento (rol, resultado, ocurrido_en DESC);
CREATE INDEX IF NOT EXISTS evento_referencia_ix ON evento (referencia);
CREATE INDEX IF NOT EXISTS evento_sujeto_ix ON evento (sujeto_documento);
CREATE OR REPLACE FUNCTION evento_solo_insercion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'La tabla evento es de solo inserción (auditoría): no se permite %', TG_OP; END $$;
DROP TRIGGER IF EXISTS evento_solo_insercion ON evento;
CREATE TRIGGER evento_solo_insercion BEFORE UPDATE OR DELETE ON evento FOR EACH ROW EXECUTE FUNCTION evento_solo_insercion();
CREATE OR REPLACE VIEW v_verificaciones AS SELECT * FROM evento WHERE rol = 'verificador';
CREATE OR REPLACE VIEW v_emisiones AS SELECT * FROM evento WHERE rol = 'emisor';
`
let pool = null
let esquema = null
const conectar = () => (pool ??= new pg.Pool({ connectionString: URL_DB, max: 4, connectionTimeoutMillis: 3000, idleTimeoutMillis: 30000 }))
/** true cuando la base está lista; si falla, se reintenta en la siguiente llamada. */
function baseLista() {
  if (!URL_DB) return Promise.resolve(false)
  esquema ??= conectar().query(ESQUEMA).then(() => true, () => { esquema = null; return false })
  return esquema
}
const recortar = (v, n) => (v == null || v === '' ? null : String(v).slice(0, n))
async function insertarDb(e) {
  await conectar().query(
    `INSERT INTO evento (id, ocurrido_en, rol, tipo, resultado, referencia, credencial_tipo, credencial_nombre, sujeto_documento, sujeto_nombre,
        canal, duracion_ms, tipo_error, codigo_error, mensaje_error, elementos, detalle)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17) ON CONFLICT (id) DO NOTHING`,
    [e.id, e.ocurrido_en ?? new Date().toISOString(), e.rol, recortar(e.tipo, 60), e.resultado, recortar(e.referencia, 200), recortar(e.credencial_tipo, 200),
     recortar(e.credencial_nombre, 200), recortar(e.sujeto?.numero_documento, 30), recortar(e.sujeto?.nombre, 200), recortar(e.canal, 30),
     Number.isFinite(Number(e.duracion_ms)) && e.duracion_ms != null ? Math.trunc(Number(e.duracion_ms)) : null,
     recortar(e.tipo_error, 40), recortar(e.codigo_error, 80), recortar(e.mensaje_error, 1000),
     JSON.stringify(Array.isArray(e.elementos) ? e.elementos : []), JSON.stringify(e.detalle && typeof e.detalle === 'object' ? e.detalle : {})])
}

// ───────── respaldo en archivo ─────────
let dirListo = null
const asegurarDir = () => (dirListo ??= mkdir(DIR, { recursive: true }).then(() => true, () => false))
const escribirArchivo = (e) => asegurarDir().then((ok) => ok && appendFile(ARCHIVO, JSON.stringify(e) + '\n')).catch(() => {})

/** Pasa a la base lo que quedó en el archivo (hechos que no se pudieron guardar a tiempo, o el historial anterior a la base). */
let vaciando = false
async function vaciarPendientes() {
  if (!URL_DB || vaciando) return
  vaciando = true
  try {
    if (!(await baseLista())) return
    try { await stat(ARCHIVO) } catch { return }
    const tmp = `${ARCHIVO}.pendiente-${Date.now()}`
    await rename(ARCHIVO, tmp)   // atómico: lo que se escriba mientras tanto va a un archivo nuevo
    const lineas = (await readFile(tmp, 'utf8')).split('\n').filter(Boolean)
    const fallidas = []
    for (const l of lineas) {
      try { const e = JSON.parse(l); if (!e.id) e.id = randomUUID(); await insertarDb(e) } catch (err) { if (err instanceof SyntaxError) continue; fallidas.push(l) }
    }
    if (fallidas.length) { await appendFile(ARCHIVO, fallidas.join('\n') + '\n'); await rm(tmp, { force: true }) }
    else await rename(tmp, join(DIR, `historial-importado-${Date.now()}.jsonl`))   // se conserva como respaldo
  } catch { /* se reintenta luego */ } finally { vaciando = false }
}
if (URL_DB) { setTimeout(vaciarPendientes, 5000).unref(); setInterval(vaciarPendientes, 30000).unref() }

/** Agrega un evento al historial. No devuelve nada que haya que esperar y nunca lanza. */
export function guardarLocal(evento) {
  try {
    const e = { id: randomUUID(), ...evento }
    if (!URL_DB) { escribirArchivo(e); return }
    baseLista().then((ok) => (ok ? insertarDb(e) : Promise.reject(new Error('sin base')))).catch(() => escribirArchivo(e))
  } catch { /* guardar el historial nunca debe afectar al portal */ }
}

let cache = { mtime: 0, filas: [] }
async function leer() {
  try {
    const st = await stat(ARCHIVO)
    if (st.mtimeMs !== cache.mtime) {
      const lineas = (await readFile(ARCHIVO, 'utf8')).split('\n').filter(Boolean).slice(-MAX_LEIDOS)
      cache = { mtime: st.mtimeMs, filas: lineas.map((l) => { try { return JSON.parse(l) } catch { return null } }).filter(Boolean) }
    }
  } catch { cache = { mtime: 0, filas: [] } }
  return cache.filas
}

/** Consulta del panel directamente en la base: filtros, totales y página, sin cargar todo en memoria. */
async function datosDesdeDb(q) {
  const pag = Math.max(parseInt(q.pagina, 10) || 1, 1); const tam = 25
  const p = []; const w = []
  const add = (sql, v) => { p.push(v); w.push(sql.replace('$#', '$' + p.length)) }
  if (q.rol === 'emisor' || q.rol === 'verificador') add('rol = $#', q.rol)
  if (q.resultado) add('resultado = $#', String(q.resultado))
  if (q.desde && /^\d{4}-\d{2}-\d{2}$/.test(q.desde)) add("ocurrido_en >= ($#::date AT TIME ZONE 'America/Bogota')", q.desde)
  if (q.hasta && /^\d{4}-\d{2}-\d{2}$/.test(q.hasta)) add("ocurrido_en < (($#::date + 1) AT TIME ZONE 'America/Bogota')", q.hasta)
  const txt = String(q.q ?? '').trim()
  if (txt) add(`(concat_ws(' ', credencial_tipo, credencial_nombre, sujeto_documento, sujeto_nombre, codigo_error, mensaje_error, referencia, tipo) ILIKE $#)`, '%' + txt.replace(/[\\%_]/g, '\\$&') + '%')
  const where = w.length ? 'WHERE ' + w.join(' AND ') : ''
  const db = conectar()
  const [tot, filas, res] = await Promise.all([
    db.query(`SELECT count(*)::int n FROM evento ${where}`, p),
    db.query(`SELECT id, ocurrido_en, rol, tipo, resultado, referencia, credencial_tipo, credencial_nombre, sujeto_documento, sujeto_nombre, canal, duracion_ms,
                     tipo_error, codigo_error, mensaje_error, elementos, detalle FROM evento ${where}
              ORDER BY ocurrido_en DESC, id LIMIT ${tam} OFFSET ${(pag - 1) * tam}`, p),
    db.query(`SELECT count(*) FILTER (WHERE rol='emisor' AND tipo='oferta_credencial')::int ofertas,
                     count(*) FILTER (WHERE rol='emisor' AND tipo='autorizacion_emision' AND resultado='fallido')::int autorizaciones,
                     count(*) FILTER (WHERE rol='emisor' AND tipo='emision_credencial' AND resultado='exitoso')::int emitidas,
                     count(*) FILTER (WHERE rol='emisor' AND tipo='emision_credencial' AND resultado<>'exitoso')::int fallidas,
                     count(*) FILTER (WHERE rol='verificador' AND tipo='solicitud_verificacion')::int solicitudes,
                     count(*) FILTER (WHERE rol='verificador' AND tipo='verificacion_credencial' AND resultado='exitoso')::int exitosas,
                     count(*) FILTER (WHERE rol='verificador' AND tipo='verificacion_credencial' AND resultado='cancelado')::int canceladas,
                     count(*) FILTER (WHERE rol='verificador' AND tipo='verificacion_credencial' AND resultado NOT IN ('exitoso','cancelado'))::int rechazadas,
                     count(DISTINCT sujeto_documento)::int personas FROM evento`)])
  const r = res.rows[0]
  return {
    resumen: { emision: { ofertas: r.ofertas, autorizaciones: r.autorizaciones, emitidas: r.emitidas, fallidas: r.fallidas },
               verificacion: { solicitudes: r.solicitudes, exitosas: r.exitosas, rechazadas: r.rechazadas, canceladas: r.canceladas }, personas: r.personas },
    total: tot.rows[0].n, pagina: pag, tamano: tam,
    filas: filas.rows.map((f) => ({ id: f.id, ocurrido_en: f.ocurrido_en, rol: f.rol, tipo: f.tipo, resultado: f.resultado, referencia: f.referencia,
      credencial_tipo: f.credencial_tipo, credencial_nombre: f.credencial_nombre, canal: f.canal, duracion_ms: f.duracion_ms, tipo_error: f.tipo_error,
      codigo_error: f.codigo_error, mensaje_error: f.mensaje_error, elementos: f.elementos, detalle: f.detalle,
      sujeto: f.sujeto_documento || f.sujeto_nombre ? { numero_documento: f.sujeto_documento, nombre: f.sujeto_nombre } : undefined })),
  }
}

function resumen(filas) {
  const r = { emision: { ofertas: 0, autorizaciones: 0, emitidas: 0, fallidas: 0 }, verificacion: { solicitudes: 0, exitosas: 0, rechazadas: 0, canceladas: 0 }, personas: new Set() }
  for (const f of filas) {
    if (f.sujeto?.numero_documento) r.personas.add(f.sujeto.numero_documento)
    if (f.rol === 'emisor') {
      if (f.tipo === 'oferta_credencial') r.emision.ofertas++
      if (f.tipo === 'autorizacion_emision' && f.resultado === 'fallido') r.emision.autorizaciones++
      if (f.tipo === 'emision_credencial') f.resultado === 'exitoso' ? r.emision.emitidas++ : r.emision.fallidas++
    } else {
      if (f.tipo === 'solicitud_verificacion') r.verificacion.solicitudes++
      if (f.tipo === 'verificacion_credencial') f.resultado === 'exitoso' ? r.verificacion.exitosas++ : f.resultado === 'cancelado' ? r.verificacion.canceladas++ : r.verificacion.rechazadas++
    }
  }
  return { ...r, personas: r.personas.size }
}

const PAGINA = `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Historial · __NOMBRE__</title>
<style>
__FUENTES__
:root{__VARIABLES__}
*{box-sizing:border-box}body{margin:0;font:15px/1.45 var(--fuente);background:var(--fondo);color:var(--texto)}
.cab{background:var(--cab-fondo);color:var(--cab-texto)}.cab-in{max-width:1280px;margin:0 auto;padding:14px 24px;display:flex;align-items:center;gap:18px;flex-wrap:wrap}
.marca{display:flex;align-items:center;gap:12px;color:inherit;text-decoration:none}.marca img{height:var(--logo-alto);width:auto;display:block}
.marca b{display:block;font-family:var(--fuente-titulo);font-size:1.15rem;letter-spacing:var(--tracking);text-transform:var(--mayusculas);font-weight:var(--peso-titulo)}.marca small{display:block;opacity:.8;font-size:.78rem}
.cab nav{margin-left:auto;display:flex;gap:4px;flex-wrap:wrap}.cab nav a{color:inherit;text-decoration:none;padding:.5rem .9rem;border-radius:var(--radio);font-weight:600;font-size:.9rem;opacity:.85;border-bottom:3px solid transparent}
.cab nav a:hover{opacity:1;background:rgba(255,255,255,.12)}.cab nav a.activo{opacity:1;border-bottom-color:var(--acento)}
.franja{height:4px;background:var(--acento)}
main{padding:20px 24px;max-width:1280px;margin:0 auto}
h1.t{font-family:var(--fuente-titulo);font-weight:var(--peso-titulo);text-transform:var(--mayusculas);letter-spacing:var(--tracking);font-size:1.5rem;margin:0 0 4px;color:var(--titulo)}p.sub{margin:0 0 16px;color:var(--suave)}
.kpis{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:clamp(6px,.9vw,12px);margin-bottom:16px}
.k{background:var(--panel);border:1px solid var(--borde);border-left:5px solid var(--c,var(--marca));border-radius:var(--radio);padding:clamp(6px,1vw,12px) clamp(7px,1.1vw,14px);min-width:0}.k b{display:block;font-size:clamp(17px,2.1vw,26px);line-height:1.15;font-family:var(--fuente-titulo);font-weight:var(--peso-titulo);color:var(--c,var(--titulo))}.k span{display:block;color:var(--suave);font-size:clamp(9.5px,.95vw,12.5px);line-height:1.25;overflow-wrap:anywhere}
.k.ok{--c:var(--ok)}.k.mal{--c:var(--mal)}.k.ac{--c:var(--acento)}.k.ac b{color:var(--titulo)}
.barra{display:flex;flex-wrap:wrap;gap:8px;margin:14px 0}.barra input,.barra select,.barra button{font:inherit;padding:8px 10px;border:1px solid var(--borde);border-radius:var(--radio);background:var(--panel);color:var(--texto)}
.barra button{background:var(--marca);color:var(--marca-texto);border-color:var(--marca);cursor:pointer;font-weight:600}.barra button.g{background:var(--panel);color:var(--marca);border-color:var(--borde)}
table{width:100%;border-collapse:collapse;background:var(--panel);border:1px solid var(--borde);border-radius:var(--radio);overflow:hidden}
th,td{padding:9px 12px;text-align:left;border-bottom:1px solid var(--borde);vertical-align:top;font-size:13.5px}th{background:var(--marca);color:var(--marca-texto);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.05em}
tr.f{cursor:pointer}tr.f:hover td{background:var(--hover)}small{display:block;color:var(--suave)}code{font-size:12px}
.chip{display:inline-block;padding:2px 9px;border-radius:99px;font-size:12px;font-weight:700}.exitoso{background:var(--ok-fondo);color:var(--ok)}.fallido{background:var(--mal-fondo);color:var(--mal)}.cancelado,.en_proceso{background:var(--neutro-fondo);color:var(--suave)}
.ficha{background:var(--hover);border-radius:8px;padding:12px 16px;margin:6px 0}.ficha dl{display:grid;grid-template-columns:max-content 1fr;gap:4px 18px;margin:0}.ficha dt{color:var(--suave);font-size:12px}.ficha dd{margin:0;font-size:13px;word-break:break-all}.ficha h4{margin:12px 0 4px;font-size:13px}.grupo{margin:2px 0}.grupo b{font-size:12px;margin-right:8px;color:var(--suave)}.el{display:inline-block;background:var(--neutro-fondo);border-radius:99px;padding:1px 9px;margin:2px 4px 2px 0;font-size:12px}
pre{background:var(--pre-fondo);color:var(--pre-texto);padding:12px;border-radius:8px;overflow:auto;font-size:12px;margin:6px 0 0}
.pie{display:flex;justify-content:space-between;align-items:center;margin-top:10px;color:var(--suave)}.pie button{font:inherit;padding:6px 12px;border:1px solid var(--borde);border-radius:var(--radio);background:var(--panel);color:var(--marca);cursor:pointer}.vacio{padding:24px;text-align:center;color:var(--suave);background:var(--panel);border:1px solid var(--borde);border-radius:var(--radio)}
__CSS_EXTRA__
</style></head><body>
<header class="cab"><div class="cab-in"><a class="marca" href="__VOLVER__">__LOGO__<span><b>__NOMBRE__</b><small>__SUBTITULO__</small></span></a>
<nav><a href="__VOLVER__">← Volver al portal</a><a class="activo" href="#">Historial</a></nav></div></header><div class="franja"></div>
<main><h1 class="t">Historial de emisiones y verificaciones</h1><p class="sub">Lo que este portal ha emitido y verificado, guardado en su propio proyecto. Solo consulta; se actualiza solo.</p><div class="kpis" id="kpis"></div>
<form class="barra" id="f"><select name="rol"><option value="">Emisión y verificación</option><option value="emisor">Solo emisiones</option><option value="verificador">Solo verificaciones</option></select>
<select name="resultado"><option value="">Todo resultado</option><option value="exitoso">Exitosa</option><option value="fallido">Fallida</option><option value="cancelado">Cancelada</option><option value="en_proceso">En proceso</option></select>
<input type="search" name="q" placeholder="Buscar: cédula, nombre, credencial, error…" size="34"><input type="date" name="desde"><input type="date" name="hasta"><button>Buscar</button><button type="button" class="g" id="l">Limpiar</button></form>
<div id="t"></div></main>
<script>
const $=s=>document.querySelector(s),B=location.pathname.replace(/\\/$/,''),E={},TXT={oferta_credencial:'Oferta de credencial',autorizacion_emision:'Autorización de la wallet',emision_credencial:'Emisión de credencial',revocacion_credencial:'Revocación',solicitud_verificacion:'Solicitud de verificación',verificacion_credencial:'Resultado de la verificación'}
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),fh=v=>new Date(v).toLocaleString('es-CO',{timeZone:'America/Bogota',dateStyle:'medium',timeStyle:'medium'})
const ETQ={ocurrido_en:'Fecha y hora',rol:'Rol',tipo:'Evento',resultado:'Resultado',referencia:'Referencia del trámite',credencial_tipo:'Tipo de credencial',credencial_nombre:'Nombre de la credencial',canal:'Canal',duracion_ms:'Duración',tipo_error:'Tipo de error',codigo_error:'Código de error',mensaje_error:'Mensaje de error',id:'Identificador'}
const SENT={solicitado:'Datos solicitados',recibido:'Datos recibidos',emitido:'Datos emitidos'}
function ficha(f){const v=(k)=>{let x=f[k];if(x==null||x==='')return null;if(k==='ocurrido_en')x=fh(x);if(k==='duracion_ms')x=(x/1000).toFixed(1)+' s';if(k==='rol')x=x==='emisor'?'Emisor':'Verificador';if(k==='tipo')x=TXT[x]||x;return x}
let g='<div class="ficha"><dl>'+['id','ocurrido_en','rol','tipo','resultado','referencia','credencial_tipo','credencial_nombre','canal','duracion_ms','tipo_error','codigo_error','mensaje_error'].map(k=>v(k)==null?'':'<dt>'+ETQ[k]+'</dt><dd>'+esc(v(k))+'</dd>').join('')
if(f.sujeto)g+='<dt>Persona atendida</dt><dd>'+esc(f.sujeto.nombre||'')+' · CC '+esc(f.sujeto.numero_documento)+'</dd>'
g+='</dl>';const el=Array.isArray(f.elementos)?f.elementos:[];const por={};el.forEach(e=>{(por[e.sentido]??={});(por[e.sentido][e.tipo_credencial||'—']??=[]).push(e.elemento)})
Object.keys(por).forEach(s=>{g+='<h4>'+esc(SENT[s]||s)+'</h4>';Object.keys(por[s]).forEach(c=>{g+='<div class="grupo"><b>'+esc(c)+'</b>'+por[s][c].map(x=>'<span class="el">'+esc(x)+'</span>').join('')+'</div>'})})
return g+'</div>'}
let pag=1,abierto=null
async function cargar(){const q=new URLSearchParams({...E,pagina:pag});const r=await fetch(B+'/api/datos?'+q);const d=await r.json()
$('#kpis').innerHTML=[['Credenciales emitidas',d.resumen.emision.emitidas,'ok'],['Emisiones fallidas',d.resumen.emision.fallidas,'mal'],['Ofertas',d.resumen.emision.ofertas,''],['Verificaciones exitosas',d.resumen.verificacion.exitosas,'ok'],['Verificaciones rechazadas',d.resumen.verificacion.rechazadas,'mal'],['Solicitudes',d.resumen.verificacion.solicitudes,''],['Personas',d.resumen.personas,'ac']].map(([a,b,c])=>'<div class="k '+c+'"><b>'+b+'</b><span>'+a+'</span></div>').join('')
$('#t').innerHTML=d.filas.length?'<table><thead><tr><th>Fecha y hora</th><th>Evento</th><th>Credencial</th><th>Persona</th><th>Resultado</th><th>Detalle</th></tr></thead><tbody>'+d.filas.map(f=>'<tr class="f" data-i="'+esc(f.id)+'"><td>'+esc(fh(f.ocurrido_en))+'</td><td>'+esc(TXT[f.tipo]||f.tipo)+'<small>'+(f.rol==='emisor'?'emisión':'verificación')+'</small></td><td>'+esc(f.credencial_nombre||f.credencial_tipo||'—')+(f.credencial_nombre?'<small>'+esc(f.credencial_tipo||'')+'</small>':'')+'</td><td>'+(f.sujeto?esc(f.sujeto.nombre||'')+'<small>CC '+esc(f.sujeto.numero_documento)+'</small>':'—')+'</td><td><span class="chip '+esc(f.resultado)+'">'+esc(f.resultado)+'</span></td><td>'+(f.mensaje_error?'<code>'+esc(f.codigo_error||'')+'</code><small>'+esc(f.mensaje_error)+'</small>':'—')+'</td></tr>'+(abierto===f.id?'<tr><td colspan="6">'+ficha(f)+'</td></tr>':'')).join('')+'</tbody></table><div class="pie"><span>'+d.total+' registros · página '+d.pagina+' de '+Math.max(1,Math.ceil(d.total/d.tamano))+'</span><span><button class="g" id="a" '+(pag<=1?'disabled':'')+'>← Anterior</button> <button class="g" id="s" '+(pag*d.tamano>=d.total?'disabled':'')+'>Siguiente →</button></span></div>':'<div class="vacio">Todavía no hay registros con esos filtros.</div>'
document.querySelectorAll('tr.f').forEach(tr=>tr.onclick=()=>{abierto=abierto===tr.dataset.i?null:tr.dataset.i;cargar()});if($('#a'))$('#a').onclick=()=>{pag--;cargar()};if($('#s'))$('#s').onclick=()=>{pag++;cargar()}}
$('#f').onsubmit=e=>{e.preventDefault();Object.keys(E).forEach(k=>delete E[k]);new FormData(e.target).forEach((v,k)=>{if(v)E[k]=v});pag=1;cargar()};$('#l').onclick=()=>{$('#f').reset();Object.keys(E).forEach(k=>delete E[k]);pag=1;cargar()}
cargar();setInterval(()=>{if(!document.hidden)cargar().catch(()=>{})},10000)
</script></body></html>`

const TEMA_BASE = {
  fondo: '#f4f6fb', panel: '#ffffff', texto: '#18223a', suave: '#6b7690', borde: '#e3e8f1', titulo: '#0f1f3d',
  marca: '#0f1f3d', marcaTexto: '#ffffff', acento: '#2f6fe4', cabeceraFondo: null, cabeceraTexto: null,
  ok: '#17803d', okFondo: '#e3f4ea', mal: '#c0322b', malFondo: '#fbe5e3', neutroFondo: '#eef1f7', hover: '#f6f8fd',
  fuente: "system-ui,'Segoe UI',Roboto,sans-serif", fuenteTitulo: null, pesoTitulo: 700, mayusculas: 'none', tracking: 'normal',
  radio: '10px', logoAlto: 40, preFondo: '#0f1f3d', preTexto: '#d7e2ff',
}
const limpio = (v) => String(v ?? '').replace(/[<>{};"']/g, '')   // los valores van a CSS: nada que cierre el bloque

/**
 * Monta la página y su API de lectura en el portal: <base>/auditoria/ y <base>/auditoria/api/datos.
 * `tema` (opcional) hace que la página use la identidad del portal:
 *   fondo, panel, texto, suave, borde, titulo, marca, marcaTexto, acento, cabeceraFondo, cabeceraTexto, ok, mal, hover,
 *   fuente, fuenteTitulo, pesoTitulo, mayusculas ('uppercase'|'none'), tracking, radio, logoAlto,
 *   logo (URL), subtitulo, fuentes (CSS @font-face), css (CSS extra al final).
 */
export function montarPanelAuditoria(app, { base = '', nombre = 'Portal', tema = {} } = {}) {
  const t = { ...TEMA_BASE, ...tema }
  const v = {
    fondo: t.fondo, panel: t.panel, texto: t.texto, suave: t.suave, borde: t.borde, titulo: t.titulo, marca: t.marca, 'marca-texto': t.marcaTexto,
    acento: t.acento, 'cab-fondo': t.cabeceraFondo ?? t.marca, 'cab-texto': t.cabeceraTexto ?? t.marcaTexto, ok: t.ok, 'ok-fondo': t.okFondo, mal: t.mal,
    'mal-fondo': t.malFondo, 'neutro-fondo': t.neutroFondo, hover: t.hover, fuente: t.fuente, 'fuente-titulo': t.fuenteTitulo ?? t.fuente,
    'peso-titulo': t.pesoTitulo, mayusculas: t.mayusculas, tracking: t.tracking, radio: t.radio, 'logo-alto': `${Number(t.logoAlto) || 40}px`,
    'pre-fondo': t.preFondo, 'pre-texto': t.preTexto,
  }
  const variables = Object.entries(v).map(([k, val]) => `--${k}:${String(val).replace(/[<>{};]/g, '')}`).join(';')
  const logo = t.logo ? `<img src="${limpio(t.logo)}" alt="">` : ''
  const html = PAGINA
    .replaceAll('__NOMBRE__', String(nombre).replace(/[<>&"']/g, ''))
    .replaceAll('__SUBTITULO__', limpio(t.subtitulo || 'Historial local'))
    .replaceAll('__VOLVER__', limpio(base) + '/')
    .replaceAll('__LOGO__', logo)
    .replaceAll('__VARIABLES__', variables)
    .replaceAll('__FUENTES__', String(t.fuentes ?? ''))
    .replaceAll('__CSS_EXTRA__', String(t.css ?? ''))
  app.get([`${base}/auditoria`, `${base}/auditoria/`], (req, res) => {
    if (!req.path.endsWith('/')) return res.redirect(301, `${base}/auditoria/`)
    res.set('Cache-Control', 'no-store').type('html').send(html)
  })
  // Respuesta mínima: la página principal del portal la usa para mostrar el enlace «Historial» solo a quien puede abrirlo
  // (el proxy responde 403 fuera de las IPs de administración).
  app.get(`${base}/auditoria/api/ping`, (_req, res) => res.set('Cache-Control', 'no-store').status(204).end())
  app.get(`${base}/auditoria/api/datos`, async (req, res) => {
    try {
      const q = req.query
      if (URL_DB && (await baseLista())) { try { return res.set('Cache-Control', 'no-store').json(await datosDesdeDb(q)) } catch { /* sin base: se usa el archivo */ } }
      const pag = Math.max(parseInt(q.pagina, 10) || 1, 1); const tam = 25
      const todas = await leer()
      const txt = String(q.q ?? '').trim().toLowerCase()
      const desde = q.desde ? Date.parse(q.desde) : null; const hasta = q.hasta ? Date.parse(q.hasta) + 86400000 : null
      const filas = todas.filter((f) =>
        (!q.rol || f.rol === q.rol) && (!q.resultado || f.resultado === q.resultado) &&
        (desde == null || Date.parse(f.ocurrido_en) >= desde) && (hasta == null || Date.parse(f.ocurrido_en) < hasta) &&
        (!txt || JSON.stringify([f.credencial_tipo, f.credencial_nombre, f.sujeto, f.codigo_error, f.mensaje_error, f.referencia, f.tipo]).toLowerCase().includes(txt)))
        .sort((a, b) => Date.parse(b.ocurrido_en) - Date.parse(a.ocurrido_en))
      res.set('Cache-Control', 'no-store').json({ resumen: resumen(todas), total: filas.length, pagina: pag, tamano: tam, filas: filas.slice((pag - 1) * tam, pag * tam) })
    } catch { res.status(500).json({ error: 'no se pudo leer el historial' }) }
  })
}

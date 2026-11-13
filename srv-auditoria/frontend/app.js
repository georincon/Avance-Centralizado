// Dashboard de auditoría: solo lectura. Sin dependencias; los gráficos son SVG propio.
const BASE = location.pathname.startsWith('/srv-auditoria') ? '/srv-auditoria/' : '/';
const $ = (s) => document.querySelector(s);
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtN = (n) => new Intl.NumberFormat('es-CO').format(n ?? 0);
const TZ = 'America/Bogota';
const fFecha = (v) => v ? new Date(v).toLocaleString('es-CO', { timeZone: TZ, day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '—';
const fDia = (v) => v ? new Date(v.length === 10 ? v + 'T12:00:00' : v).toLocaleDateString('es-CO', { timeZone: TZ, day: '2-digit', month: 'long', year: 'numeric' }) : '—';
const fDur = (ms) => ms == null ? '—' : ms < 1000 ? ms + ' ms' : (ms / 1000).toFixed(1) + ' s';
const pct = (a, b) => b ? Math.round((a / b) * 1000) / 10 : 0;
const RESULT = { exitoso: 'Exitosa', fallido: 'Fallida', cancelado: 'Cancelada', en_proceso: 'En proceso' };
const MARCA = { exitoso: '✓', fallido: '✕', cancelado: '–', en_proceso: '…' };
const TIPO_ERROR = { red: 'Red', autenticacion: 'Autenticación', validacion: 'Validación', emisor: 'Emisor', verificador: 'Verificador', usuario: 'Usuario', interno: 'Interno', sin_clasificar: 'Sin clasificar' };
const chip = (r) => `<span class="chip ${esc(r)}">${esc(RESULT[r] || r)}</span>`;
const iniciales = (n) => (n || '?').split(' ').filter(Boolean).slice(0, 2).map((x) => x[0]).join('').toUpperCase();

let token = sessionStorage.getItem('auditoria_token') || '';

async function api(ruta, params = {}) {
  const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== '' && v != null));
  const r = await fetch(`${BASE}api/panel/${ruta}${q.size ? '?' + q : ''}`, { headers: { authorization: 'Bearer ' + token } });
  if (r.status === 401) { cerrarSesion(); throw new Error('Sesión no autorizada'); }
  if (r.status === 404 || r.status === 403) throw new Error('Acceso denegado por la red (' + r.status + '): tu IP no está autorizada para abrir el panel de auditoría.');
  if (!r.ok) throw new Error('Error ' + r.status);
  return r.json();
}

// ───────── gráficos ─────────
function barrasDiarias(serie) {
  const W = 760, H = 220, m = { l: 34, b: 24, t: 8 };
  const max = Math.max(1, ...serie.map((d) => d.total));
  const techo = Math.ceil(max / 10) * 10 || 10;
  const bw = (W - m.l) / serie.length;
  const y = (v) => H - m.b - (v / techo) * (H - m.b - m.t);
  let s = '';
  for (let i = 0; i <= 4; i++) { const v = (techo / 4) * i; s += `<line x1="${m.l}" x2="${W}" y1="${y(v)}" y2="${y(v)}" stroke="#e3e8f1"/><text x="${m.l - 6}" y="${y(v) + 4}" text-anchor="end">${Math.round(v)}</text>`; }
  serie.forEach((d, i) => {
    const x = m.l + i * bw + bw * 0.14, w = bw * 0.72, ok = d.total - d.fallidas;
    s += `<g><title>${esc(fDia(d.dia))}: ${d.total} actividades (${d.fallidas} fallidas)</title>
      <rect x="${x}" y="${y(ok)}" width="${w}" height="${Math.max(0, H - m.b - y(ok))}" fill="#2f6fe4" rx="2"/>
      <rect x="${x}" y="${y(d.total)}" width="${w}" height="${Math.max(0, y(ok) - y(d.total))}" fill="#c0322b" rx="2"/></g>`;
    if (serie.length <= 31 ? i % Math.ceil(serie.length / 8) === 0 : i % Math.ceil(serie.length / 10) === 0)
      s += `<text x="${x + w / 2}" y="${H - 6}" text-anchor="middle">${esc(d.dia.slice(8))}/${esc(d.dia.slice(5, 7))}</text>`;
  });
  return `<svg class="grafico" viewBox="0 0 ${W} ${H}" role="img" aria-label="Actividad diaria">${s}</svg>`;
}
function barrasHora(horas) {
  const W = 520, H = 160, m = { l: 26, b: 22, t: 6 }, max = Math.max(1, ...horas.map((h) => h.total)), bw = (W - m.l) / 24;
  let s = '';
  horas.forEach((h, i) => {
    const alto = (h.total / max) * (H - m.b - m.t), x = m.l + i * bw + 2;
    s += `<rect x="${x}" y="${H - m.b - alto}" width="${bw - 4}" height="${alto}" fill="#2f6fe4" rx="2"><title>${h.hora}:00 — ${h.total}</title></rect>`;
    if (i % 3 === 0) s += `<text x="${x + (bw - 4) / 2}" y="${H - 6}" text-anchor="middle">${h.hora}h</text>`;
  });
  return `<svg class="grafico" viewBox="0 0 ${W} ${H}" role="img" aria-label="Actividad por hora del día">${s}</svg>`;
}
const PALETA = ['#0f1f3d', '#2f6fe4', '#7aa6f0', '#c0322b', '#9a5b00', '#6b7690', '#17803d', '#b9c6e4'];
function dona(items) {
  const tot = items.reduce((a, i) => a + i.total, 0) || 1; let ang = -Math.PI / 2, s = '';
  items.forEach((it, i) => {
    const a2 = ang + (it.total / tot) * Math.PI * 2, r = 70, ri = 44, g = a2 - ang > 6.2;
    const p = (rr, a) => [90 + rr * Math.cos(a), 90 + rr * Math.sin(a)];
    const [x1, y1] = p(r, ang), [x2, y2] = p(r, g ? ang + 3.1 : a2), [x3, y3] = p(ri, g ? ang + 3.1 : a2), [x4, y4] = p(ri, ang), lg = a2 - ang > Math.PI ? 1 : 0;
    s += g ? `<circle cx="90" cy="90" r="${(r + ri) / 2}" fill="none" stroke="${PALETA[i % 8]}" stroke-width="${r - ri}"/>`
      : `<path d="M${x1} ${y1}A${r} ${r} 0 ${lg} 1 ${x2} ${y2}L${x3} ${y3}A${ri} ${ri} 0 ${lg} 0 ${x4} ${y4}Z" fill="${PALETA[i % 8]}"><title>${esc(it.nombre)}: ${it.total}</title></path>`;
    ang = a2;
  });
  return `<div class="dona"><svg viewBox="0 0 180 180" width="170" role="img" aria-label="Distribución"><text x="90" y="88" text-anchor="middle" style="font-size:22px;font-weight:700;fill:#18223a">${fmtN(tot)}</text><text x="90" y="106" text-anchor="middle">en total</text>${s}</svg>
  <ul>${items.map((it, i) => `<li><i style="background:${PALETA[i % 8]}"></i>${esc(it.nombre)} <b>${fmtN(it.total)}</b> <span style="color:var(--gris)">(${pct(it.total, tot)}%)</span></li>`).join('')}</ul></div>`;
}
function barrasH(items, { total = 'total', mal = 'fallidas', nombre = 'nombre' } = {}) {
  const max = Math.max(1, ...items.map((i) => i[total]));
  return items.map((i) => `<div class="barra-h"><span title="${esc(i[nombre])}">${esc(i[nombre])}</span>
    <div class="pista"><div class="ok" style="width:${((i[total] - (i[mal] || 0)) / max) * 100}%"></div><div class="mal" style="width:${((i[mal] || 0) / max) * 100}%"></div></div>
    <span class="n">${fmtN(i[total])}${i[mal] ? ` · <span style="color:var(--mal)">${fmtN(i[mal])} fallidas</span>` : ''}</span></div>`).join('') || '<p class="vacio">Sin datos en el período.</p>';
}
const leyendaOkMal = '<div class="leyenda"><span><i style="background:#2f6fe4"></i>Exitosas / otras</span><span><i style="background:#c0322b"></i>Fallidas</span></div>';
const kpi = (etq, val, nota = '', cls = '') => `<div class="tarjeta kpi ${cls}"><span class="etq">${etq}</span><span class="val">${val}</span><span class="nota">${nota}</span></div>`;

// ───────── descarga de la wallet (QR + enlace + datos) ─────────
const fMB = (b) => (b / 1048576).toFixed(1).replace('.', ',') + ' MB';
async function tarjetaDescarga() {
  try {
    const r = await fetch(BASE + 'descargas/info.json'); if (!r.ok) throw new Error();
    const i = await r.json();
    return `<div class="descarga"><img class="qr" src="${BASE}descargas/qr.svg" alt="Código QR para descargar ${esc(i.nombre)}">
      <div><h3>Descarga ${esc(i.nombre)}</h3><p class="sub">Escanea el QR con la cámara del teléfono o abre el enlace.</p>
      <dl><dt>Enlace</dt><dd><a class="vinculo" href="${esc(i.enlace)}">${esc(i.enlace)}</a></dd>
      <dt>Versión</dt><dd>${esc(i.version || '—')}</dd><dt>Paquete</dt><dd><code>${esc(i.paquete || '—')}</code></dd>
      <dt>Compilada</dt><dd>${esc(fFecha(i.compilada_en))}</dd><dt>Tamaño</dt><dd>${esc(fMB(i.tamano_bytes))}</dd>
      <dt>Android</dt><dd>${esc(i.android_minimo)} o superior</dd><dt>SHA-256</dt><dd><code>${esc(i.sha256)}</code></dd>
      <dt>Servidor</dt><dd>${esc(i.servidor)}</dd></dl>
      <div class="acciones"><a class="boton boton-primario" href="${esc(i.enlace)}">Descargar APK</a><button class="boton" type="button" data-copiar="${esc(i.enlace)}">Copiar enlace</button></div>
      <p class="sub" style="margin-top:12px">Al instalar, Android pedirá permitir instalaciones de este origen.</p></div></div>`;
  } catch { return '<div class="descarga"><p class="vacio">La wallet aún no está compilada o el enlace no está disponible.</p></div>'; }
}
function activarCopiar(raiz) {
  raiz.querySelectorAll('[data-copiar]').forEach((b) => b.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(b.dataset.copiar); b.textContent = '¡Copiado!'; } catch { b.textContent = 'Copia manualmente'; }
    setTimeout(() => { b.textContent = 'Copiar enlace'; }, 2000);
  }));
}

// ───────── vistas ─────────
async function vPanorama() {
  const dias = Number(sessionStorage.getItem('auditoria_dias') || 30);
  const [r, s, d] = await Promise.all([api('resumen', { dias }), api('series', { dias }), api('distribucion', { dias })]);
  const p = r.periodo;
  const descarga = await tarjetaDescarga();
  const [bloqueEmisores, bloqueVerificadores] = await Promise.all([htmlResumenServicio('emisor', dias), htmlResumenServicio('verificador', dias)]);
  const seg = [7, 30, 90].map((n) => `<button data-dias="${n}" class="${n === dias ? 'on' : ''}">${n} días</button>`).join('');
  return `<div class="barra"><div class="segmentos" id="seg-dias">${seg}</div><span class="espacio"></span><span style="color:var(--gris)">Período: ${dias} días · hora de Colombia</span></div>
  <section class="tarjeta" id="tarjeta-descarga" style="margin-bottom:16px">${descarga}</section>
  <h3 class="sec-panorama" style="margin-top:0">Wallets</h3>
  <div class="rejilla k4">
    ${kpi('Usuarios registrados', fmtN(r.usuarios), `${fmtN(r.wallets)} wallets · ${fmtN(r.wallets_activas_24h)} activas en 24 h`, 'destacado')}
    ${kpi('Actividades', fmtN(p.total), `${fmtN(r.actividades_24h)} en las últimas 24 h`)}
    ${kpi('Tasa de éxito', pct(p.exitosas, p.total) + '%', `${fmtN(p.fallidas)} fallidas · ${fmtN(p.canceladas)} canceladas`)}
    ${kpi('Inicios de sesión', fmtN(p.ingresos), `${fmtN(r.sesiones_abiertas)} sesiones abiertas ahora`)}
    ${kpi('Credenciales recibidas', fmtN(p.credenciales_emitidas), `${fmtN(r.credenciales_vigentes)} vigentes en total`)}
    ${kpi('Presentaciones exitosas', fmtN(p.presentaciones), `${fmtN(r.verificadores)} verificadores · ${fmtN(r.emisores)} emisores`)}
    ${kpi('Duración media de trámites', fmtDur(p.duracion_media_ms), 'solo actividades con medición')}
  </div>
  <div class="rejilla dos" style="margin-top:16px">
    <section class="tarjeta" style="grid-column:1/-1"><h3>Actividad diaria</h3><p class="sub">Cada barra es un día; la parte roja son actividades fallidas.</p>${leyendaOkMal}${barrasDiarias(s)}</section>
    <section class="tarjeta"><h3>Qué hacen las wallets</h3><p class="sub">Actividades por tipo</p>${barrasH(d.tipos)}</section>
    <section class="tarjeta"><h3>Con quién se conectan</h3><p class="sub">Emisores y verificadores más activos</p>${barrasH(d.contrapartes)}</section>
    <section class="tarjeta"><h3>Cuándo se usan</h3><p class="sub">Actividades por hora del día</p>${barrasHora(d.horas)}</section>
    <section class="tarjeta"><h3>Por qué fallan</h3><p class="sub">Tipo de error en actividades no exitosas</p>${d.errores.length ? dona(d.errores.map((e) => ({ nombre: TIPO_ERROR[e.tipo_error] || e.tipo_error, total: e.total }))) : '<p class="vacio">Sin errores en el período.</p>'}</section>
    <section class="tarjeta" style="grid-column:1/-1"><h3>Errores más frecuentes</h3><p class="sub">Códigos de error de actividades fallidas</p>
      <div class="tabla-marco"><table><thead><tr><th>Código</th><th>Tipo</th><th>Ejemplo de mensaje</th><th class="num">Veces</th></tr></thead><tbody>
      ${d.codigos.map((c) => `<tr><td><code>${esc(c.codigo_error)}</code></td><td>${esc(TIPO_ERROR[c.tipo_error] || c.tipo_error || '—')}</td><td>${esc(c.ejemplo || '—')}</td><td class="num">${fmtN(c.total)}</td></tr>`).join('') || '<tr><td colspan="4" class="vacio">Sin errores.</td></tr>'}
      </tbody></table></div></section>
  </div>
  ${bloqueEmisores}
  ${bloqueVerificadores}`;
}
const fmtDur = fDur;

function filtrosExtracto(f, fijo) {
  return `<form class="barra" id="filtros">
    <div class="buscador"><svg viewBox="0 0 24 24"><path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1014 15.5l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 110-9 4.5 4.5 0 010 9z"/></svg>
      <input type="search" name="q" value="${esc(f.q || '')}" placeholder="Buscar por persona, cédula, emisor/verificador, error, wallet…" aria-label="Buscar"></div>
    <select name="categoria" aria-label="Categoría"><option value="">Toda categoría</option>${['sesion:Sesión', 'credencial:Credenciales', 'conexion:Conexiones', 'perfil:Perfil', 'sistema:Sistema'].map((x) => { const [v, t] = x.split(':'); return `<option value="${v}" ${f.categoria === v ? 'selected' : ''}>${t}</option>`; }).join('')}</select>
    <select name="tipo" id="f-tipo" aria-label="Actividad"><option value="">Toda actividad</option></select>
    <select name="resultado" aria-label="Resultado"><option value="">Todo resultado</option>${Object.entries(RESULT).map(([v, t]) => `<option value="${v}" ${f.resultado === v ? 'selected' : ''}>${t}</option>`).join('')}</select>
    <select name="tipo_error" id="f-error" aria-label="Tipo de error"><option value="">Todo error</option></select>
    <select name="contraparte" id="f-contra" aria-label="Emisor o verificador"><option value="">Todo emisor/verificador</option></select>
    <input type="date" name="desde" value="${esc(f.desde || '')}" aria-label="Desde"><input type="date" name="hasta" value="${esc(f.hasta || '')}" aria-label="Hasta">
    <button class="boton boton-primario" type="submit">Buscar</button>
    <button class="boton" type="button" id="limpiar">Limpiar</button>
    <a class="boton" id="csv" href="#" role="button">Exportar CSV</a></form>`;
}
function tablaActividades(res, { sinPersona = false, marcarDesde = Infinity } = {}) {
  if (!res.filas.length) return '<div class="tarjeta vacio">No hay actividades con esos filtros.</div>';
  return `<div class="tabla-marco"><table class="t-ext"><thead><tr><th class="c-fecha">Fecha y hora</th>${sinPersona ? '' : '<th class="c-persona">Persona</th>'}<th class="c-actividad">Actividad</th><th class="c-con">Con quién</th><th class="c-res">Resultado</th><th class="c-error">Detalle del error</th><th class="num c-dur">Duración</th></tr></thead><tbody>
  ${res.filas.map((a) => `<tr class="fila${Number(a.actividad_id) > marcarDesde ? ' nueva' : ''}" data-id="${a.actividad_id}" tabindex="0">
    <td class="c-fecha">${esc(fFecha(a.ocurrido_en))}</td>
    ${sinPersona ? '' : `<td class="c-persona">${a.usuario_id ? `<a class="vinculo" href="#/usuarios/${esc(a.usuario_id)}">${esc(a.nombre_completo)}</a><small>CC ${esc(a.numero_documento)}</small>` : '—'}</td>`}
    <td class="c-actividad">${esc(a.actividad)}${a.credencial_nombre ? `<small class="cred-nombre">${esc(a.credencial_nombre)}</small>` : ''}${a.credencial_tipo ? `<small>${esc(a.credencial_tipo)}</small>` : ''}</td>
    <td class="c-con">${a.contraparte ? `${esc(a.contraparte)}<small>${esc(a.contraparte_tipo)}${a.canal ? ' · ' + esc(a.canal) : ''}</small>` : `<small>${esc(a.canal || '—')}</small>`}</td>
    <td class="c-res">${chip(a.resultado)}</td>
    <td class="c-error">${a.resultado === 'exitoso' ? '—' : `${a.codigo_error ? `<code>${esc(a.codigo_error)}</code>` : ''}<small>${esc([TIPO_ERROR[a.tipo_error], a.mensaje_error].filter(Boolean).join(' · '))}</small>`}</td>
    <td class="num c-dur">${fDur(a.duracion_ms)}</td></tr>`).join('')}</tbody></table></div>
  ${paginador(res)}`;
}
function paginador(res) {
  const pags = Math.max(1, Math.ceil(res.total / res.tamano));
  return `<div class="pie-tabla"><span>${fmtN(res.total)} resultados · página ${res.pagina} de ${pags}</span><span>
    <button class="boton" data-pag="${res.pagina - 1}" ${res.pagina <= 1 ? 'disabled' : ''}>← Anterior</button>
    <button class="boton" data-pag="${res.pagina + 1}" ${res.pagina >= pags ? 'disabled' : ''}>Siguiente →</button></span></div>`;
}

async function cargarCatalogos(f) {
  const c = await api('catalogos');
  const llenar = (sel, items, actual) => { const el = $(sel); if (el) items.forEach(([v, t]) => el.insertAdjacentHTML('beforeend', `<option value="${esc(v)}" ${actual === v ? 'selected' : ''}>${esc(t)}</option>`)); };
  llenar('#f-tipo', c.tipos.map((t) => [t.codigo, t.nombre]), f.tipo);
  llenar('#f-error', c.tipos_error.map((t) => [t, TIPO_ERROR[t] || t]), f.tipo_error);
  llenar('#f-contra', c.contrapartes.map((t) => [t.nombre, `${t.nombre} (${t.tipo})`]), f.contraparte);
}

/** Extracto reutilizable: en la vista general y en la ficha de cada usuario (filtro fijo). */
let recargarExtracto = null;
function montarExtracto(contenedor, fijo, estado, { sinPersona = false, conFiltros = true } = {}) {
  let maxVisto = null;   // mayor actividad_id ya mostrada: lo que llegue después se resalta
  const cargar = async () => {
    const params = { ...estado, ...fijo, tamano: 25 };
    const lista = contenedor.querySelector('.lista');
    if (maxVisto === null) lista.innerHTML = '<div class="cargando">Cargando…</div>';
    try {
      const res = await api('actividades', params);
      const previo = maxVisto;
      maxVisto = Math.max(maxVisto ?? 0, ...res.filas.map((f) => Number(f.actividad_id)));
      lista.innerHTML = tablaActividades(res, { sinPersona, marcarDesde: previo ?? Infinity });
      lista.querySelectorAll('[data-pag]').forEach((b) => b.addEventListener('click', () => { estado.pagina = Number(b.dataset.pag); cargar(); }));
      lista.querySelectorAll('tr.fila').forEach((tr) => {
        const abrir = (e) => { if (e.target.closest('a')) return; abrirDetalle(tr.dataset.id); };
        tr.addEventListener('click', abrir); tr.addEventListener('keydown', (e) => { if (e.key === 'Enter') abrir(e); });
      });
    } catch (e) { lista.innerHTML = `<div class="error-caja">${esc(e.message)}</div>`; }
  };
  if (conFiltros) {
    const form = contenedor.querySelector('#filtros');
    form.addEventListener('submit', (e) => { e.preventDefault(); Object.keys(estado).forEach((k) => delete estado[k]); new FormData(form).forEach((v, k) => { if (v) estado[k] = v; }); estado.pagina = 1; cargar(); });
    contenedor.querySelector('#limpiar').addEventListener('click', () => { form.reset(); Object.keys(estado).forEach((k) => delete estado[k]); cargar(); });
    contenedor.querySelector('#csv').addEventListener('click', async (e) => {
      e.preventDefault();
      const q = new URLSearchParams({ ...estado, ...fijo }); q.delete('pagina');
      const r = await fetch(`${BASE}api/panel/actividades.csv?${q}`, { headers: { authorization: 'Bearer ' + token } });
      const a = document.createElement('a'); a.href = URL.createObjectURL(await r.blob()); a.download = 'actividades.csv'; a.click();
    });
    cargarCatalogos(estado);
  }
  recargarExtracto = cargar;
  cargar();
}

const estadoExtracto = {};
async function vExtracto() {
  return `<div id="ext">${filtrosExtracto(estadoExtracto)}<div class="lista"></div></div>`;
}
function postExtracto() { montarExtracto($('#ext'), {}, estadoExtracto); }

async function vUsuarios() {
  const q = sessionStorage.getItem('auditoria_q_usuarios') || '';
  return `<form class="barra" id="busca-usuarios"><div class="buscador"><svg viewBox="0 0 24 24"><path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1014 15.5l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 110-9 4.5 4.5 0 010 9z"/></svg>
    <input type="search" name="q" value="${esc(q)}" placeholder="Nombre, cédula, correo o celular" aria-label="Buscar usuario"></div><button class="boton boton-primario">Buscar</button></form><div id="lista-usuarios"></div>`;
}
let recargarLista = null;
async function postUsuarios() {
  let paginaActual = 1;
  const cargar = async (pagina = paginaActual) => {
    paginaActual = pagina;
    const q = sessionStorage.getItem('auditoria_q_usuarios') || '';
    const res = await api('usuarios', { q, pagina });
    $('#lista-usuarios').innerHTML = res.filas.length ? `<div class="tabla-marco"><table><thead><tr><th class="c-persona">Usuario</th><th class="c-con">Contacto</th><th class="c-res">Estado</th><th class="num">Wallets</th><th class="num">Actividades</th><th>Última actividad</th></tr></thead><tbody>
      ${res.filas.map((u) => `<tr class="fila" data-u="${esc(u.usuario_id)}" tabindex="0"><td><a class="vinculo" href="#/usuarios/${esc(u.usuario_id)}">${esc(u.nombre_completo)}</a><small>${esc(u.tipo_documento)} ${esc(u.numero_documento)}</small></td>
      <td>${esc(u.correo || '—')}<small>${esc(u.celular || '')}</small></td><td><span class="chip ${u.estado === 'activo' ? 'exitoso' : 'fallido'}">${esc(u.estado)}</span></td>
      <td class="num">${u.wallets}</td><td class="num">${fmtN(u.actividades)}</td><td>${esc(fFecha(u.ultima_actividad))}</td></tr>`).join('')}</tbody></table></div>${paginador(res)}` : '<div class="tarjeta vacio">No se encontraron usuarios.</div>';
    document.querySelectorAll('#lista-usuarios tr.fila').forEach((tr) => tr.addEventListener('click', () => { location.hash = '#/usuarios/' + tr.dataset.u; }));
    document.querySelectorAll('#lista-usuarios [data-pag]').forEach((b) => b.addEventListener('click', () => cargar(Number(b.dataset.pag))));
  };
  $('#busca-usuarios').addEventListener('submit', (e) => { e.preventDefault(); sessionStorage.setItem('auditoria_q_usuarios', new FormData(e.target).get('q') || ''); cargar(1); });
  recargarLista = () => cargar();
  cargar(1);
}

const estadoFicha = {};
async function vUsuario(id) {
  const d = await api('usuarios/' + id), u = d.usuario, e = d.estadisticas;
  const celulares = d.contactos.filter((c) => c.tipo === 'celular'), correos = d.contactos.filter((c) => c.tipo === 'correo');
  const lc = (l) => l.map((c) => `${esc(c.valor)} ${c.verificado ? '<span class="chip exitoso">verificado</span>' : '<span class="chip gris">sin verificar</span>'}`).join('<br>') || '—';
  return `<p><a class="vinculo" href="#/usuarios">← Usuarios</a></p>
  <section class="tarjeta"><div class="ficha"><div class="avatar" aria-hidden="true">${esc(iniciales(u.nombre_completo))}</div>
    <div><h3>${esc(u.nombre_completo)}</h3><span style="color:var(--gris)">${esc(u.tipo_documento)} ${esc(u.numero_documento)} · cliente desde ${esc(fDia(u.creado_en))}</span></div>
    <span class="espacio" style="flex:1"></span><span class="chip ${u.estado === 'activo' ? 'exitoso' : 'fallido'}">${esc(u.estado)}</span></div></section>
  <div class="rejilla k4" style="margin:16px 0">
    ${kpi('Actividades', fmtN(e.total), `desde ${esc(fDia(e.primera))}`, 'destacado')}${kpi('Inicios de sesión', fmtN(e.ingresos))}
    ${kpi('Credenciales recibidas', fmtN(e.credenciales_recibidas))}${kpi('Presentaciones', fmtN(e.presentaciones), `${fmtN(e.fallidas)} actividades fallidas`)}</div>
  <div class="rejilla dos">
    <section class="tarjeta"><h3>Información personal</h3><p class="sub">Datos validados con la cédula</p><dl class="datos">
      <div><dt>Nombres</dt><dd>${esc(u.nombres)}</dd></div><div><dt>Apellidos</dt><dd>${esc(u.apellidos)}</dd></div>
      <div><dt>Fecha de nacimiento</dt><dd>${esc(fDia(u.fecha_nacimiento))}</dd></div><div><dt>Lugar de nacimiento</dt><dd>${esc(u.lugar_nacimiento || '—')}</dd></div>
      <div><dt>Género</dt><dd>${esc(u.genero || '—')}</dd></div><div><dt>Grupo sanguíneo</dt><dd>${esc(u.grupo_sanguineo || '—')}</dd></div>
      <div><dt>Estatura</dt><dd>${u.estatura_cm ? u.estatura_cm + ' cm' : '—'}</dd></div><div><dt>Expedición</dt><dd>${esc(fDia(u.fecha_expedicion))} · ${esc(u.lugar_expedicion || '—')}</dd></div>
      <div><dt>Validación de identidad</dt><dd>${esc(u.validacion_decision || '—')}${u.validacion_puntaje_rostro ? ` · rostro ${u.validacion_puntaje_rostro}%` : ''}</dd></div></dl></section>
    <section class="tarjeta"><h3>Medios de contacto</h3><p class="sub">Los que registró al crear su usuario</p><dl class="datos">
      <div><dt>Celular</dt><dd>${lc(celulares)}</dd></div><div><dt>Correo electrónico</dt><dd>${lc(correos)}</dd></div></dl>
      <h3 style="margin-top:22px">Wallets</h3><p class="sub">Dispositivos asociados</p>
      ${d.wallets.map((w) => `<div style="padding:8px 0;border-top:1px solid #edf0f6"><a class="vinculo" href="#/wallets/${esc(w.wallet_id)}"><b>${esc([w.fabricante, w.modelo].filter(Boolean).join(' ') || 'Dispositivo')}</b></a> <span class="chip ${w.estado === 'activa' ? 'exitoso' : 'gris'}">${esc(w.estado)}</span>
        <small style="color:var(--gris);display:block">Android ${esc(w.version_android || '—')} · app ${esc(w.version_app || '—')} · última actividad ${esc(fFecha(w.ultima_actividad_en))}<br><code>${esc(w.wallet_id)}</code></small></div>`).join('') || '—'}</section>
    <section class="tarjeta"><h3>Credenciales</h3><p class="sub">Lo que tiene y ha tenido en su wallet</p>
      ${d.credenciales.map((c) => `<div style="padding:8px 0;border-top:1px solid #edf0f6"><b>${esc(c.nombre || c.tipo_credencial)}</b> <span class="chip ${c.estado === 'vigente' ? 'exitoso' : 'gris'}">${esc(c.estado)}</span>
        <small style="color:var(--gris);display:block">${esc(c.emisor || 'emisor desconocido')} · recibida ${esc(fFecha(c.recibida_en))}${c.eliminada_en ? ' · eliminada ' + esc(fFecha(c.eliminada_en)) : ''}</small></div>`).join('') || '<p class="vacio">Sin credenciales.</p>'}</section>
    <section class="tarjeta"><h3>Sesiones recientes</h3><p class="sub">Cuándo entró y salió de la wallet</p>
      ${d.sesiones.map((s) => `<div style="padding:8px 0;border-top:1px solid #edf0f6">${esc(fFecha(s.inicio_en))} <span class="chip azul">${esc(s.metodo_autenticacion || '—')}</span>
        <small style="color:var(--gris);display:block">${s.fin_en ? `salió ${esc(fFecha(s.fin_en))} (${Math.round(s.segundos / 60)} min) · ${esc(s.motivo_cierre || '')}` : 'sin cierre registrado'}</small></div>`).join('') || '<p class="vacio">Sin sesiones.</p>'}</section>
  </div>
  <h3 style="margin:26px 0 12px">Extracto de movimientos</h3><div id="ext"><div class="lista"></div></div>`;
}
function postUsuario(id) { montarExtracto($('#ext'), { usuario: id }, estadoFicha, { sinPersona: true, conFiltros: false }); }

const SI_NO = (v) => v == null ? '—' : v ? 'Sí' : 'No';
async function vWallets() {
  const q = sessionStorage.getItem('auditoria_q_wallets') || '';
  return `<form class="barra" id="busca-wallets"><div class="buscador"><svg viewBox="0 0 24 24"><path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1014 15.5l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 110-9 4.5 4.5 0 010 9z"/></svg>
    <input type="search" name="q" value="${esc(q)}" placeholder="Usuario, cédula, modelo del teléfono, identificador de wallet o del dispositivo" aria-label="Buscar wallet"></div><button class="boton boton-primario">Buscar</button></form><div id="lista-wallets"></div>`;
}
async function postWallets() {
  let paginaActual = 1;
  const cargar = async (pagina = paginaActual) => {
    paginaActual = pagina;
    const res = await api('wallets', { q: sessionStorage.getItem('auditoria_q_wallets') || '', pagina });
    $('#lista-wallets').innerHTML = res.filas.length ? `<div class="tabla-marco"><table><thead><tr><th class="c-wallet">Wallet</th><th class="c-persona">Usuario</th><th class="c-actividad">Teléfono</th><th class="c-con">Android / app</th><th class="c-res">Estado</th><th class="num">Actividades</th><th class="num">Fallidas</th><th>Última actividad</th></tr></thead><tbody>
      ${res.filas.map((w) => `<tr class="fila" data-w="${esc(w.wallet_id)}" tabindex="0"><td><code>${esc(w.wallet_id.slice(0, 8))}…</code><small>${w.alta_completa ? 'alta completa' : 'sin terminar registro'}</small></td>
      <td>${w.usuario_id ? `<a class="vinculo" href="#/usuarios/${esc(w.usuario_id)}">${esc(w.nombre_completo)}</a><small>CC ${esc(w.numero_documento)}</small>` : '<small>sin usuario aún</small>'}</td>
      <td><b>${esc([w.fabricante, w.modelo].filter(Boolean).join(' ') || '—')}</b><small>${esc(w.pantalla || '')}</small></td>
      <td>Android ${esc(w.version_android || '—')}<small>app ${esc(w.version_app || '—')}</small></td>
      <td><span class="chip ${w.estado === 'activa' ? 'exitoso' : 'gris'}">${esc(w.estado)}</span></td>
      <td class="num">${fmtN(w.actividades)}</td><td class="num" style="color:${w.fallidas ? 'var(--mal)' : 'inherit'}">${fmtN(w.fallidas)}</td><td>${esc(fFecha(w.ultima_actividad_en))}</td></tr>`).join('')}</tbody></table></div>${paginador(res)}` : '<div class="tarjeta vacio">No se encontraron wallets.</div>';
    document.querySelectorAll('#lista-wallets tr.fila').forEach((tr) => tr.addEventListener('click', (e) => { if (!e.target.closest('a')) location.hash = '#/wallets/' + tr.dataset.w; }));
    document.querySelectorAll('#lista-wallets [data-pag]').forEach((b) => b.addEventListener('click', () => cargar(Number(b.dataset.pag))));
  };
  $('#busca-wallets').addEventListener('submit', (e) => { e.preventDefault(); sessionStorage.setItem('auditoria_q_wallets', new FormData(e.target).get('q') || ''); cargar(1); });
  recargarLista = () => cargar();
  cargar(1);
}
const estadoWallet = {};
async function vWallet(id) {
  const d = await api('wallets/' + id), w = d.wallet;
  const dato = (t, v) => `<div><dt>${t}</dt><dd>${esc(v ?? '—')}</dd></div>`;
  return `<p><a class="vinculo" href="#/wallets">← Wallets</a></p>
  <section class="tarjeta"><div class="ficha"><div class="avatar" aria-hidden="true">📱</div>
    <div><h3>${esc([w.fabricante, w.modelo].filter(Boolean).join(' ') || 'Dispositivo sin datos')}</h3><span style="color:var(--gris)">Wallet <code>${esc(w.wallet_id)}</code></span></div>
    <span class="espacio" style="flex:1"></span><span class="chip ${w.estado === 'activa' ? 'exitoso' : 'gris'}">${esc(w.estado)}</span></div></section>
  <div class="rejilla k4" style="margin:16px 0">${kpi('Actividades', fmtN(w.actividades), `${fmtN(w.fallidas)} fallidas`, 'destacado')}${kpi('Credenciales vigentes', fmtN(w.credenciales))}
    ${kpi('Primer contacto', esc(fDia(w.primer_registro_en)))}${kpi('Alta completa', w.alta_completa ? 'Sí' : 'No', w.alta_en ? esc(fFecha(w.alta_en)) : 'aún sin terminar el registro')}</div>
  <div class="rejilla dos">
    <section class="tarjeta"><h3>Datos del teléfono</h3><p class="sub">Lo que reporta la wallet al registrarse y al ingresar</p><dl class="datos">
      ${dato('Fabricante', w.fabricante)}${dato('Marca', w.marca)}${dato('Modelo', w.modelo)}${dato('Producto', w.producto)}${dato('Hardware', w.hardware)}
      ${dato('Versión de Android', w.version_android ? `${w.version_android} (API ${w.nivel_api ?? '—'})` : null)}${dato('Parche de seguridad', w.parche_seguridad)}
      ${dato('Versión de la wallet', w.version_app ? `${w.version_app} (${w.codigo_version_app ?? '—'})` : null)}${dato('Arquitectura', w.arquitectura)}
      ${dato('Memoria RAM', w.memoria_ram_mb ? w.memoria_ram_mb + ' MB' : null)}${dato('Pantalla', w.pantalla)}${dato('Idioma', w.idioma)}${dato('Zona horaria', w.zona_horaria)}
      ${dato('Biometría disponible', SI_NO(w.biometria_disponible))}${dato('Bloqueo de pantalla', SI_NO(w.bloqueo_pantalla))}${dato('ID del dispositivo', w.identificador_android)}</dl></section>
    <section class="tarjeta"><h3>Titular</h3><p class="sub">Usuario enlazado a esta wallet</p>
      ${w.usuario_id ? `<p style="margin:0 0 4px"><a class="vinculo" href="#/usuarios/${esc(w.usuario_id)}" style="font-size:17px">${esc(w.nombre_completo)}</a></p><span style="color:var(--gris)">CC ${esc(w.numero_documento)}</span>` : '<p class="vacio">Aún no hay usuario enlazado.</p>'}
      <h3 style="margin-top:22px">Credenciales</h3><p class="sub">En esta wallet</p>
      ${d.credenciales.map((c) => `<div style="padding:8px 0;border-top:1px solid #edf0f6"><b>${esc(c.nombre || c.tipo_credencial)}</b> <span class="chip ${c.estado === 'vigente' ? 'exitoso' : 'gris'}">${esc(c.estado)}</span><small style="color:var(--gris);display:block">${esc(c.emisor || '—')} · ${esc(fFecha(c.recibida_en))}</small></div>`).join('') || '<p class="vacio">Sin credenciales.</p>'}</section>
  </div>
  <h3 style="margin:26px 0 12px">Historial de esta wallet</h3><div id="ext"><div class="lista"></div></div>`;
}
function postWallet(id) { montarExtracto($('#ext'), { wallet: id }, estadoWallet, { conFiltros: false }); }

async function vContrapartes() {
  const lista = await api('contrapartes');
  const bloque = (tipo, titulo) => {
    const f = lista.filter((c) => c.tipo === tipo);
    return `<h3 style="margin:8px 0 12px">${titulo}</h3><div class="tabla-marco" style="margin-bottom:24px"><table><thead><tr><th>Nombre</th><th class="num">Actividades</th><th class="num">Usuarios</th><th class="num">Éxito</th><th class="num">Fallidas</th><th>Última</th></tr></thead><tbody>
    ${f.map((c) => `<tr class="fila" data-n="${esc(c.nombre)}" tabindex="0"><td><b>${esc(c.nombre || c.identificador)}</b><small>${esc(c.identificador)}</small></td><td class="num">${fmtN(c.total)}</td><td class="num">${fmtN(c.usuarios)}</td>
      <td class="num">${pct(c.exitosas, c.total)}%</td><td class="num" style="color:${c.fallidas ? 'var(--mal)' : 'inherit'}">${fmtN(c.fallidas)}</td><td>${esc(fFecha(c.ultima))}</td></tr>`).join('') || '<tr><td colspan="6" class="vacio">Sin registros.</td></tr>'}</tbody></table></div>`;
  };
  return bloque('emisor', 'Emisores (de dónde vienen las credenciales)') + bloque('verificador', 'Verificadores (quién las pide)');
}
function postContrapartes() {
  document.querySelectorAll('tr.fila').forEach((tr) => tr.addEventListener('click', () => { Object.keys(estadoExtracto).forEach((k) => delete estadoExtracto[k]); estadoExtracto.contraparte = tr.dataset.n; location.hash = '#/extracto'; }));
}

// ───────── detalle de una actividad (cajón) ─────────
async function abrirDetalle(id) {
  const cajon = $('#cajon'); cajon.hidden = false; cajon.innerHTML = '<div class="cajon-panel"><div class="cargando">Cargando…</div></div>';
  try {
    const a = await api('actividades/' + id);
    const comp = a.elementos.filter((e) => e.sentido === 'compartido'), rec = a.elementos.filter((e) => e.sentido === 'recibido');
    const lista = (l) => l.map((e) => `<span class="chip azul">${esc(e.elemento)}</span>`).join(' ');
    cajon.innerHTML = `<div class="cajon-panel" role="dialog" aria-modal="true" aria-label="Detalle de la actividad"><header><div><h3>${esc(a.actividad)}</h3><span style="color:var(--gris)">${esc(fFecha(a.ocurrido_en))}</span></div><button class="cerrar" aria-label="Cerrar">×</button></header>
    <p>${chip(a.resultado)} ${a.duracion_ms != null ? `<span class="chip gris">${esc(fDur(a.duracion_ms))}</span>` : ''}</p>
    <dl class="datos" style="grid-template-columns:1fr 1fr">
      <div><dt>Persona</dt><dd>${a.usuario_id ? `<a class="vinculo" href="#/usuarios/${esc(a.usuario_id)}">${esc(a.nombre_completo)}</a>` : '—'}</dd></div>
      <div><dt>Cédula</dt><dd>${esc(a.numero_documento || '—')}</dd></div>
      <div><dt>${a.contraparte_tipo === 'emisor' ? 'Emisor' : a.contraparte_tipo === 'verificador' ? 'Verificador' : 'Contraparte'}</dt><dd>${esc(a.contraparte || '—')}</dd></div>
      <div><dt>Canal</dt><dd>${esc(a.canal || '—')}</dd></div>
      <div style="grid-column:1/-1"><dt>Credencial</dt><dd>${a.credencial_nombre ? `<b>${esc(a.credencial_nombre)}</b><br>` : ''}${esc(a.credencial_tipo || '—')}</dd></div>
      <div style="grid-column:1/-1"><dt>Wallet</dt><dd><code>${esc(a.wallet_id)}</code></dd></div>
      <div><dt>Registrada en el centro</dt><dd>${esc(fFecha(a.registrado_en))}</dd></div><div><dt>Sesión</dt><dd><code>${esc((a.sesion_id || '—').slice(0, 8))}</code></dd></div></dl>
    ${a.resultado !== 'exitoso' ? `<div class="error-caja" style="margin-top:16px"><b>${esc(TIPO_ERROR[a.tipo_error] || 'Error')}</b>${a.codigo_error ? ` · <code>${esc(a.codigo_error)}</code>` : ''}<br>${esc(a.mensaje_error || 'Sin mensaje')}</div>` : ''}
    ${comp.length ? `<h3 style="margin-top:20px;font-size:15px">Datos compartidos</h3><p class="tira" style="margin-top:8px">${lista(comp)}</p><small style="color:var(--gris)">Solo se guardan los nombres de los datos, no sus valores.</small>` : ''}
    ${rec.length ? `<h3 style="margin-top:20px;font-size:15px">Datos recibidos</h3><p class="tira">${lista(rec)}</p>` : ''}
    ${Object.keys(a.detalle || {}).length ? `<h3 style="margin-top:20px;font-size:15px">Información adicional</h3><pre>${esc(JSON.stringify(a.detalle, null, 2))}</pre>` : ''}</div>`;
    cajon.querySelector('.cerrar').focus();
    cajon.querySelector('a.vinculo')?.addEventListener('click', () => { cajon.hidden = true; });
  } catch (e) { cajon.innerHTML = `<div class="cajon-panel"><div class="error-caja">${esc(e.message)}</div></div>`; }
}
$('#cajon').addEventListener('click', (e) => { if (e.target.id === 'cajon' || e.target.closest('.cerrar')) $('#cajon').hidden = true; });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') $('#cajon').hidden = true; });

// ───────── Actividad de EMISORES y de VERIFICADORES ─────────
// Lo reportan los propios servicios (portales, issuers, verifiers): cada oferta, autorización, emisión y verificación,
// exitosa o con error. La misma vista sirve a los dos roles; solo cambian los textos y las cifras de cabecera.
const SRV = {
  emisor: {
    ruta: 'emisores', singular: 'emisor', plural: 'Emisores', titulo: 'Actividad Emisores',
    intro: 'Cada vez que un emisor ofrece, autoriza o entrega una credencial a una wallet, con su resultado y, si falló, el motivo.',
    columnaCred: 'Credencial', persona: 'Persona atendida', abre: 'Ofertas', cierra: 'Emitidas', cierraFallida: 'Fallidas',
    embudo: 'Recorrido de una emisión', embudoSub: 'Oferta → autorización de la wallet → entrega de la credencial',
    credTitulo: 'Credenciales emitidas', credSub: 'Por tipo de credencial', elemTitulo: 'Datos emitidos', elemSub: 'Atributos que llevan las credenciales (solo nombres)',
  },
  verificador: {
    ruta: 'verificadores', singular: 'verificador', plural: 'Verificadores', titulo: 'Actividad Verificadores',
    intro: 'Cada vez que un verificador pide datos a una wallet y valida lo que se le presenta, con su resultado y, si falló, el motivo.',
    columnaCred: 'Qué se pidió', persona: 'Persona verificada', abre: 'Solicitudes', cierra: 'Verificadas', cierraFallida: 'Rechazadas',
    embudo: 'Recorrido de una verificación', embudoSub: 'Solicitud al usuario → resultado de la validación',
    credTitulo: 'Credenciales verificadas', credSub: 'Por tipo de credencial presentada', elemTitulo: 'Datos más solicitados', elemSub: 'Atributos que piden los verificadores (solo nombres)',
  },
};
const estadoServ = { emisor: {}, verificador: {} };
const diasServ = () => Number(sessionStorage.getItem('auditoria_dias') || 30);

function filtrosServicio(rol, f) {
  return `<form class="barra" id="filtros-srv">
    <div class="buscador"><svg viewBox="0 0 24 24"><path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1014 15.5l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 110-9 4.5 4.5 0 010 9z"/></svg>
      <input type="search" name="q" value="${esc(f.q || '')}" placeholder="Buscar por ${SRV[rol].singular}, persona, cédula, credencial, error o referencia…" aria-label="Buscar"></div>
    <select name="servicio" id="s-servicio" aria-label="${SRV[rol].singular}"><option value="">Todo ${SRV[rol].singular}</option></select>
    <select name="tipo" id="s-tipo" aria-label="Evento"><option value="">Todo evento</option></select>
    <select name="resultado" aria-label="Resultado"><option value="">Todo resultado</option>${Object.entries(RESULT).map(([v, t]) => `<option value="${v}" ${f.resultado === v ? 'selected' : ''}>${t}</option>`).join('')}</select>
    <select name="tipo_error" id="s-error" aria-label="Tipo de error"><option value="">Todo error</option></select>
    <select name="credencial" id="s-cred" aria-label="Credencial"><option value="">Toda credencial</option></select>
    <input type="date" name="desde" value="${esc(f.desde || '')}" aria-label="Desde"><input type="date" name="hasta" value="${esc(f.hasta || '')}" aria-label="Hasta">
    <button class="boton boton-primario" type="submit">Buscar</button><button class="boton" type="button" id="limpiar-srv">Limpiar</button>
    <a class="boton" id="csv-srv" href="#" role="button">Exportar CSV</a></form>`;
}

function tablaServicio(res, rol, marcarDesde = Infinity) {
  const c = SRV[rol];
  if (!res.filas.length) return `<div class="tarjeta vacio">No hay actividad de ${c.plural.toLowerCase()} con esos filtros.</div>`;
  return `<div class="tabla-marco"><table class="t-ext t-srv"><thead><tr><th class="c-fecha">Fecha y hora</th><th class="c-con">${esc(c.plural.slice(0, -2))}</th><th class="c-actividad">Evento</th>
    <th class="c-actividad">${c.columnaCred}</th><th class="c-persona">${c.persona}</th><th class="c-res">Resultado</th><th class="c-error">Detalle del error</th><th class="num c-dur">Duración</th></tr></thead><tbody>
  ${res.filas.map((a) => `<tr class="fila${Number(a.actividad_servicio_id) > marcarDesde ? ' nueva' : ''}" data-id="${a.actividad_servicio_id}" tabindex="0">
    <td class="c-fecha">${esc(fFecha(a.ocurrido_en))}</td>
    <td class="c-con"><b>${esc(a.servicio || a.servicio_identificador)}</b>${a.canal ? `<small>${esc(a.canal)}</small>` : ''}</td>
    <td class="c-actividad">${esc(a.evento)}</td>
    <td class="c-actividad">${a.credencial_nombre ? `${esc(a.credencial_nombre)}<small>${esc(a.credencial_tipo || '')}</small>` : esc(a.credencial_tipo || '—')}</td>
    <td class="c-persona">${a.sujeto_nombre || a.sujeto_documento ? `${a.usuario_id ? `<a class="vinculo" href="#/usuarios/${esc(a.usuario_id)}">${esc(a.sujeto_nombre || a.sujeto_documento)}</a>` : esc(a.sujeto_nombre || '')}${a.sujeto_documento ? `<small>CC ${esc(a.sujeto_documento)}</small>` : ''}` : '—'}</td>
    <td class="c-res">${chip(a.resultado)}</td>
    <td class="c-error">${a.resultado === 'exitoso' || a.resultado === 'en_proceso' && !a.mensaje_error ? '—' : `${a.codigo_error ? `<code>${esc(a.codigo_error)}</code>` : ''}<small>${esc([TIPO_ERROR[a.tipo_error], a.mensaje_error].filter(Boolean).join(' · '))}</small>`}</td>
    <td class="num c-dur">${fDur(a.duracion_ms)}</td></tr>`).join('')}</tbody></table></div>${paginador(res)}`;
}

function tablaQuienEsQuien(lista, rol) {
  const c = SRV[rol];
  return `<div class="tabla-marco"><table><thead><tr><th>${esc(c.plural.slice(0, -2))}</th><th class="num">${c.abre}</th><th class="num">${c.cierra}</th><th class="num">${c.cierraFallida}</th><th class="num">Éxito</th><th class="num">Personas</th><th>Última actividad</th></tr></thead><tbody>
  ${lista.map((s) => { const cerr = s.exitosas + s.fallidas; return `<tr class="fila" data-n="${esc(s.nombre)}" data-rol="${rol}" tabindex="0"><td><b>${esc(s.nombre || s.identificador)}</b><small>${esc(s.proyecto || s.identificador)}</small></td>
    <td class="num">${fmtN(s.aperturas)}</td><td class="num">${fmtN(s.exitosas)}</td><td class="num" style="color:${s.fallidas ? 'var(--mal)' : 'inherit'}">${fmtN(s.fallidas)}</td>
    <td class="num">${cerr ? pct(s.exitosas, cerr) + '%' : '—'}</td><td class="num">${fmtN(s.personas)}</td><td>${esc(fFecha(s.ultima || s.ultima_vez_en))}</td></tr>`; }).join('') || `<tr><td colspan="7" class="vacio">Ningún ${c.singular} ha reportado actividad todavía.</td></tr>`}
  </tbody></table></div>`;
}

/** Bloque de cifras y gráficos de un rol (emisores o verificadores): va en el Panorama, junto al de las wallets. */
async function htmlResumenServicio(rol, dias) {
  const c = SRV[rol];
  try {
    const [r, s, d, lista] = await Promise.all([api(`${c.ruta}/resumen`, { dias }), api(`${c.ruta}/series`, { dias }), api(`${c.ruta}/distribucion`, { dias }), api(`${c.ruta}/servicios`, { dias })]);
    const p = r.periodo; const cerr = p.cierres_exitosos + p.cierres_fallidos;
    const kpis = rol === 'emisor' ? [
      kpi('Credenciales emitidas', fmtN(p.cierres_exitosos), `${cerr ? pct(p.cierres_exitosos, cerr) + '% de éxito' : 'sin emisiones aún'}`, 'destacado'),
      kpi('Emisiones fallidas', fmtN(p.cierres_fallidos), 'no se pudo entregar la credencial', p.cierres_fallidos ? 'alerta' : ''),
      kpi('Ofertas generadas', fmtN(p.aperturas), `${fmtN(r.sin_respuesta)} sin canjear tras ${r.espera_min} min`),
      kpi('Autorizaciones rechazadas', fmtN(p.autorizaciones_fallidas), 'PIN, código o wallet no válida'),
      kpi('Personas atendidas', fmtN(p.personas), 'distintas, por cédula'),
      kpi('Emisores activos', fmtN(r.activos_24h), `${fmtN(r.servicios)} han reportado alguna vez`),
      kpi('Duración media de emisión', fDur(p.duracion_media_ms || null), 'solo emisiones con medición'),
    ] : [
      kpi('Verificaciones exitosas', fmtN(p.cierres_exitosos), `${cerr ? pct(p.cierres_exitosos, cerr) + '% de éxito' : 'sin verificaciones aún'}`, 'destacado'),
      kpi('Verificaciones rechazadas', fmtN(p.cierres_fallidos), 'firma, confianza, estado o el usuario no aceptó', p.cierres_fallidos ? 'alerta' : ''),
      kpi('Solicitudes realizadas', fmtN(p.aperturas), `${fmtN(r.sin_respuesta)} sin respuesta tras ${r.espera_min} min`),
      kpi('Personas verificadas', fmtN(p.personas), 'distintas, por cédula'),
      kpi('Verificadores activos', fmtN(r.activos_24h), `${fmtN(r.servicios)} han reportado alguna vez`),
      kpi('Duración media', fDur(p.duracion_media_ms || null), 'de solicitud a resultado'),
    ];
    return `<h3 class="sec-panorama">${esc(c.plural)}</h3><p class="sub" style="margin:-4px 0 12px">${esc(c.intro)}</p>
    <div class="rejilla k4">${kpis.join('')}</div>
    <div class="rejilla dos" style="margin-top:16px">
      <section class="tarjeta" style="grid-column:1/-1"><h3>Actividad diaria de ${esc(c.plural.toLowerCase())}</h3><p class="sub">Cada barra es un día; la parte roja son eventos fallidos.</p>${leyendaOkMal}${barrasDiarias(s)}</section>
      <section class="tarjeta"><h3>${c.embudo}</h3><p class="sub">${c.embudoSub}</p>${barrasH(d.eventos)}</section>
      <section class="tarjeta"><h3>${c.plural} más activos</h3><p class="sub">Eventos reportados en el período</p>${barrasH(d.servicios)}</section>
      <section class="tarjeta"><h3>${c.credTitulo}</h3><p class="sub">${c.credSub}</p>${barrasH(d.credenciales)}</section>
      <section class="tarjeta"><h3>${c.elemTitulo}</h3><p class="sub">${c.elemSub}</p>${barrasH(d.elementos.map((e) => ({ ...e, fallidas: 0 })))}</section>
      <section class="tarjeta"><h3>Por qué fallan (${esc(c.plural.toLowerCase())})</h3><p class="sub">Tipo de error en eventos fallidos</p>${d.errores.length ? dona(d.errores.map((e) => ({ nombre: TIPO_ERROR[e.tipo_error] || e.tipo_error, total: e.total }))) : '<p class="vacio">Sin errores en el período.</p>'}</section>
      <section class="tarjeta"><h3>Errores más frecuentes</h3><p class="sub">Códigos de error</p>
        <div class="tabla-marco"><table><thead><tr><th>Código</th><th>${esc(c.plural.slice(0, -2))}</th><th>Mensaje de ejemplo</th><th class="num">Veces</th></tr></thead><tbody>
        ${d.codigos.map((x) => `<tr><td><code>${esc(x.codigo_error)}</code></td><td>${esc(x.servicio || '—')}</td><td>${esc(x.ejemplo || '—')}</td><td class="num">${fmtN(x.total)}</td></tr>`).join('') || '<tr><td colspan="4" class="vacio">Sin errores.</td></tr>'}</tbody></table></div></section>
      <section class="tarjeta" style="grid-column:1/-1"><h3>${c.plural}</h3><p class="sub">Quién es quién: haz clic en uno para ver su registro de actividad.</p>${tablaQuienEsQuien(lista, rol)}</section>
    </div>`;
  } catch (e) { return `<h3 class="sec-panorama">${esc(c.plural)}</h3><div class="error-caja">${esc(e.message)}</div>`; }
}
/** Clic en un emisor/verificador del Panorama: abre su menú con el registro ya filtrado. */
function activarServiciosPanorama() {
  document.querySelectorAll('#vista tr.fila[data-n]').forEach((tr) => tr.addEventListener('click', () => {
    const rol = tr.dataset.rol; estadoServ[rol] = { servicio: tr.dataset.n, pagina: 1 }; location.hash = '#/' + SRV[rol].ruta;
  }));
}

/** El menú de emisores / verificadores muestra SOLO el registro de actividad (las cifras y gráficos están en el Panorama). */
async function vServicios(rol) {
  return `<h3 style="margin:0 0 12px">Registro de actividad</h3><div id="srv-ext">${filtrosServicio(rol, estadoServ[rol])}<div class="lista"></div></div>`;
}

async function postServicios(rol) {
  const c = SRV[rol]; const estado = estadoServ[rol];
  let maxVisto = null;
  const cargarTabla = async () => {
    const lista = $('#srv-ext .lista'); if (!lista) return;
    if (maxVisto === null) lista.innerHTML = '<div class="cargando">Cargando…</div>';
    try {
      const res = await api(`${c.ruta}/actividades`, { ...estado, tamano: 25 });
      const previo = maxVisto; maxVisto = Math.max(maxVisto ?? 0, ...res.filas.map((f) => Number(f.actividad_servicio_id)));
      lista.innerHTML = tablaServicio(res, rol, previo ?? Infinity);
      lista.querySelectorAll('[data-pag]').forEach((b) => b.addEventListener('click', () => { estado.pagina = Number(b.dataset.pag); cargarTabla(); }));
      lista.querySelectorAll('tr.fila').forEach((tr) => {
        const abrir = (e) => { if (e.target.closest('a')) return; abrirDetalleServicio(rol, tr.dataset.id); };
        tr.addEventListener('click', abrir); tr.addEventListener('keydown', (e) => { if (e.key === 'Enter') abrir(e); });
      });
    } catch (e) { lista.innerHTML = `<div class="error-caja">${esc(e.message)}</div>`; }
  };

  const form = $('#filtros-srv');
  form.addEventListener('submit', (e) => { e.preventDefault(); Object.keys(estado).forEach((k) => delete estado[k]); new FormData(form).forEach((v, k) => { if (v) estado[k] = v; }); estado.pagina = 1; cargarTabla(); });
  $('#limpiar-srv').addEventListener('click', () => { form.reset(); Object.keys(estado).forEach((k) => delete estado[k]); cargarTabla(); });
  $('#csv-srv').addEventListener('click', async (e) => {
    e.preventDefault();
    const q = new URLSearchParams(estado); q.delete('pagina');
    const r = await fetch(`${BASE}api/panel/${c.ruta}/actividades.csv?${q}`, { headers: { authorization: 'Bearer ' + token } });
    const a = document.createElement('a'); a.href = URL.createObjectURL(await r.blob()); a.download = `actividad-${c.ruta}.csv`; a.click();
  });
  api(`${c.ruta}/catalogos`).then((cat) => {
    const llenar = (sel, items, actual) => { const el = $(sel); if (el) items.forEach(([v, t]) => el.insertAdjacentHTML('beforeend', `<option value="${esc(v)}" ${actual === v ? 'selected' : ''}>${esc(t)}</option>`)); };
    llenar('#s-servicio', cat.servicios.map((x) => [x, x]), estado.servicio);
    llenar('#s-tipo', cat.tipos.map((x) => [x.codigo, x.nombre]), estado.tipo);
    llenar('#s-error', cat.tipos_error.map((x) => [x, TIPO_ERROR[x] || x]), estado.tipo_error);
    llenar('#s-cred', cat.credenciales.map((x) => [x, x]), estado.credencial);
  }).catch(() => {});

  recargarLista = cargarTabla;   // el aviso en vivo refresca el registro sin rehacer los filtros
  cargarTabla();
}

async function abrirDetalleServicio(rol, id) {
  const cajon = $('#cajon'); cajon.hidden = false; cajon.innerHTML = '<div class="cajon-panel"><div class="cargando">Cargando…</div></div>';
  const c = SRV[rol];
  try {
    const a = await api(`${c.ruta}/actividad/${id}`);
    const por = (s) => a.elementos.filter((e) => e.sentido === s);
    const chips = (l) => l.map((e) => `<span class="chip azul">${esc(e.elemento)}</span>`).join(' ');
    const pasos = a.pasos.length > 1 ? `<h3 style="margin-top:20px;font-size:15px">Recorrido del trámite</h3><ol class="pasos">${a.pasos.map((p) =>
      `<li class="${String(p.actividad_servicio_id) === String(id) ? 'actual' : ''}">${chip(p.resultado)} <b>${esc(p.evento)}</b> <span style="color:var(--gris)">${esc(fFecha(p.ocurrido_en))}${p.duracion_ms != null ? ' · ' + esc(fDur(p.duracion_ms)) : ''}</span>${p.mensaje_error ? `<br><small>${esc(p.mensaje_error)}</small>` : ''}</li>`).join('')}</ol>` : '';
    cajon.innerHTML = `<div class="cajon-panel" role="dialog" aria-modal="true" aria-label="Detalle del evento"><header><div><h3>${esc(a.evento)}</h3><span style="color:var(--gris)">${esc(fFecha(a.ocurrido_en))}</span></div><button class="cerrar" aria-label="Cerrar">✕</button></header>
    <p>${chip(a.resultado)} ${a.duracion_ms != null ? `<span class="chip gris">${esc(fDur(a.duracion_ms))}</span>` : ''}</p>
    <dl class="datos" style="grid-template-columns:1fr 1fr">
      <div><dt>${esc(c.plural.slice(0, -2))}</dt><dd>${esc(a.servicio || '—')}</dd></div><div><dt>Canal</dt><dd>${esc(a.canal || '—')}</dd></div>
      <div style="grid-column:1/-1"><dt>Servicio (URL)</dt><dd><code>${esc(a.servicio_identificador)}</code>${a.proyecto ? `<br><small>${esc(a.proyecto)}</small>` : ''}</dd></div>
      <div><dt>${c.persona}</dt><dd>${a.usuario_id ? `<a class="vinculo" href="#/usuarios/${esc(a.usuario_id)}">${esc(a.sujeto_nombre || a.sujeto_documento)}</a>` : esc(a.sujeto_nombre || '—')}</dd></div><div><dt>Cédula</dt><dd>${esc(a.sujeto_documento || '—')}</dd></div>
      <div style="grid-column:1/-1"><dt>${c.columnaCred}</dt><dd>${a.credencial_nombre ? `<b>${esc(a.credencial_nombre)}</b><br>` : ''}${esc(a.credencial_tipo || '—')}</dd></div>
      <div style="grid-column:1/-1"><dt>Referencia del trámite</dt><dd><code>${esc(a.referencia || '—')}</code></dd></div>
      <div><dt>Registrado en el centro</dt><dd>${esc(fFecha(a.registrado_en))}</dd></div>${a.wallet_id ? `<div><dt>Wallet</dt><dd><a class="vinculo" href="#/wallets/${esc(a.wallet_id)}"><code>${esc(a.wallet_id.slice(0, 8))}</code></a></dd></div>` : ''}</dl>
    ${a.resultado === 'fallido' ? `<div class="error-caja" style="margin-top:16px"><b>${esc(TIPO_ERROR[a.tipo_error] || 'Error')}</b>${a.codigo_error ? ` · <code>${esc(a.codigo_error)}</code>` : ''}<br>${esc(a.mensaje_error || 'Sin mensaje')}</div>` : ''}
    ${por('solicitado').length ? `<h3 style="margin-top:20px;font-size:15px">Datos solicitados</h3><p class="tira">${chips(por('solicitado'))}</p>` : ''}
    ${por('recibido').length ? `<h3 style="margin-top:20px;font-size:15px">Datos recibidos</h3><p class="tira">${chips(por('recibido'))}</p>` : ''}
    ${por('emitido').length ? `<h3 style="margin-top:20px;font-size:15px">Datos emitidos</h3><p class="tira">${chips(por('emitido'))}</p>` : ''}
    ${a.elementos.length ? '<small style="color:var(--gris)">Solo se guardan los nombres de los datos, no sus valores.</small>' : ''}
    ${pasos}
    ${Object.keys(a.detalle || {}).length ? `<h3 style="margin-top:20px;font-size:15px">Información adicional</h3><pre>${esc(JSON.stringify(a.detalle, null, 2))}</pre>` : ''}</div>`;
    cajon.querySelector('.cerrar').focus();
    cajon.querySelector('a.vinculo')?.addEventListener('click', () => { cajon.hidden = true; });
  } catch (e) { cajon.innerHTML = `<div class="cajon-panel"><div class="error-caja">${esc(e.message)}</div></div>`; }
}

// ───────── enrutador ─────────
let vivoModo = 'render';   // cómo reaccionar a un cambio: 'render' (rehacer la vista), 'extracto' o 'lista'
const RUTAS = {
  panorama: { titulo: 'Panorama del ecosistema', vista: vPanorama, post: (m) => { activarCopiar($('#vista')); activarServiciosPanorama(); document.querySelectorAll('#seg-dias button').forEach((b) => b.addEventListener('click', () => { sessionStorage.setItem('auditoria_dias', b.dataset.dias); render(); })); } },
  extracto: { titulo: 'Actividad Wallet', vista: vExtracto, post: postExtracto, vivo: 'extracto' },
  emisores: { titulo: SRV.emisor.titulo, vista: () => vServicios('emisor'), post: () => postServicios('emisor'), vivo: 'lista' },
  verificadores: { titulo: SRV.verificador.titulo, vista: () => vServicios('verificador'), post: () => postServicios('verificador'), vivo: 'lista' },
  usuarios: { titulo: 'Usuarios y wallets', vista: vUsuarios, post: postUsuarios, vivo: 'lista' },
  wallets: { titulo: 'Wallets y dispositivos', vista: vWallets, post: postWallets, vivo: 'lista' },
  contrapartes: { titulo: 'Emisores y verificadores', vista: vContrapartes, post: postContrapartes },
};
async function render(suave = false) {
  const [, ruta = 'panorama', id] = location.hash.split('/');
  if (!suave) { $('#cajon').hidden = true; $('#lateral').classList.remove('abierto'); recargarExtracto = null; recargarLista = null; }
  const def = id && ruta === 'wallets' ? { titulo: 'Wallet y dispositivo', vista: () => vWallet(id), post: () => postWallet(id), vivo: 'render' } : id && ruta === 'usuarios' ? { titulo: 'Ficha del usuario', vista: () => vUsuario(id), post: () => postUsuario(id) } : RUTAS[ruta] || RUTAS.panorama;
  document.querySelectorAll('.lateral a').forEach((a) => a.classList.toggle('activo', a.dataset.ruta === (RUTAS[ruta] ? ruta : 'panorama')));
  $('#titulo').textContent = def.titulo; document.title = def.titulo + ' · Auditoría Avance';
  if (!suave) $('#vista').innerHTML = '<div class="cargando">Cargando…</div>';
  vivoModo = def.vivo || 'render';
  try { const html = await def.vista(); if (suave) { const y = window.scrollY; $('#vista').innerHTML = html; window.scrollTo(0, y); } else $('#vista').innerHTML = html; def.post?.(); }
  catch (e) { if (!suave) $('#vista').innerHTML = `<div class="error-caja">${esc(e.message)}</div>`; }
  if (!suave) window.scrollTo(0, 0);
}

// ───────── tiempo real (SSE sobre fetch, para poder enviar la cabecera Authorization) ─────────
let vivoCtl = null, vivoTimer = null, sondeo = null, esperaFlujo = null;
// Respaldo: si un proxy o red retiene el flujo en vivo, el panel consulta cada 10 s y lo indica.
function sondeoVivo(on) {
  if (on && !sondeo) { sondeo = setInterval(() => { if (token) refrescarVivo(); }, 10000); indicadorVivo(false, 'Actualiza cada 10 s'); }
  else if (!on && sondeo) { clearInterval(sondeo); sondeo = null; }
}
function indicadorVivo(on, texto) { const el = $('#vivo'); if (!el) return; el.classList.toggle('on', on); el.querySelector('b').textContent = texto; }
function avisoVivo(titulo, detalle, mal) {
  const caja = $('#aviso-vivo'); const d = document.createElement('div'); if (mal) d.className = 'mal';
  d.innerHTML = `<b>${esc(titulo)}</b>${esc(detalle)}`; caja.appendChild(d);
  while (caja.children.length > 4) caja.firstChild.remove();
  setTimeout(() => d.remove(), 6000);
}
function alCambiar(msg) {
  if (msg.tabla === 'actividad') {
    avisoVivo('Nueva actividad', `${msg.actividad || msg.tipo} · ${RESULT[msg.resultado] || msg.resultado}`, msg.resultado === 'fallido');
  } else if (msg.tabla === 'actividad_servicio') {
    avisoVivo(msg.rol === 'emisor' ? 'Actividad de emisor' : 'Actividad de verificador', `${msg.servicio ? msg.servicio + ' · ' : ''}${msg.actividad || msg.tipo} · ${RESULT[msg.resultado] || msg.resultado}`, msg.resultado === 'fallido');
  } else if (msg.tabla === 'usuario' && msg.op === 'INSERT') avisoVivo('Nuevo usuario registrado', '');
  else if (msg.tabla === 'wallet' && msg.op === 'INSERT') avisoVivo('Nueva wallet conectada', '');
  clearTimeout(alCambiar.t);
  alCambiar.t = setTimeout(refrescarVivo, 600);   // agrupa ráfagas de cambios en un solo refresco
}
function refrescarVivo() {
  if (!token || document.hidden) { refrescarVivo.pendiente = true; return; }
  if (vivoModo === 'lista') return recargarLista?.();
  if (vivoModo === 'extracto') return recargarExtracto?.();
  render(true);   // panorama, fichas y emisores/verificadores: se rehace la vista sin saltar ni parpadear
}
document.addEventListener('visibilitychange', () => { if (!document.hidden && refrescarVivo.pendiente) { refrescarVivo.pendiente = false; refrescarVivo(); } });
async function conectarVivo() {
  desconectarVivo();
  if (!token) return;
  const ctl = new AbortController(); vivoCtl = ctl; indicadorVivo(false, 'Conectando…');
  clearTimeout(esperaFlujo); esperaFlujo = setTimeout(() => sondeoVivo(true), 6000);
  try {
    const r = await fetch(BASE + 'api/panel/eventos', { headers: { authorization: 'Bearer ' + token }, signal: ctl.signal });
    if (!r.ok) throw new Error('estado ' + r.status);
    const lector = r.body.getReader(), dec = new TextDecoder(); let buf = '';
    for (;;) {
      const { value, done } = await lector.read(); if (done) break;
      if (esperaFlujo) { clearTimeout(esperaFlujo); esperaFlujo = null; sondeoVivo(false); indicadorVivo(true, 'En vivo'); }
      buf += dec.decode(value, { stream: true }); let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const bloque = buf.slice(0, i); buf = buf.slice(i + 2);
        if (!bloque.includes('event: cambio')) continue;
        const dato = bloque.split('\n').find((l) => l.startsWith('data: '));
        try { alCambiar(JSON.parse(dato.slice(6))); } catch { /* latido o dato incompleto */ }
      }
    }
  } catch { /* se reintenta abajo */ }
  if (ctl.signal.aborted) return;
  sondeoVivo(true);
  vivoTimer = setTimeout(conectarVivo, 3000);
}
function desconectarVivo() { clearTimeout(vivoTimer); clearTimeout(esperaFlujo); esperaFlujo = null; sondeoVivo(false); vivoCtl?.abort(); vivoCtl = null; }

function mostrarApp(si) {
  $('#app').hidden = !si; $('#login').hidden = si;
  if (!si) tarjetaDescarga().then((h) => { $('#descarga-login').innerHTML = h; activarCopiar($('#descarga-login')); });
}
function cerrarSesion() { desconectarVivo(); token = ''; sessionStorage.removeItem('auditoria_token'); mostrarApp(false); }
$('#salir').addEventListener('click', cerrarSesion);
$('#menu').addEventListener('click', () => $('#lateral').classList.toggle('abierto'));
$('#form-login').addEventListener('submit', async (e) => {
  e.preventDefault(); token = $('#token').value.trim(); $('#login-error').textContent = '';
  try { await api('resumen', { dias: 7 }); sessionStorage.setItem('auditoria_token', token); mostrarApp(true); render(); conectarVivo(); }
  catch (err) { $('#login-error').textContent = /^Sesión no autorizada/.test(err.message) ? 'Clave incorrecta.' : err.message; token = ''; }
});
window.addEventListener('hashchange', () => token && render());
setInterval(() => { $('#reloj').textContent = new Date().toLocaleString('es-CO', { timeZone: TZ, dateStyle: 'medium', timeStyle: 'short' }); }, 1000);
if (token) { mostrarApp(true); render(); conectarVivo(); } else mostrarApp(false);

// Marco común de las tres páginas (cabecera, menú y pie). Va antes del script de cada página:
// lee <body data-base data-gym data-seccion data-titulo data-slot> y deja el hueco de la persona
// con el id data-slot (persona o der), que rellena la propia página.
(() => {
  const L = (d) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${d}</svg>`
  const ICONOS = {
    tarjeta: L('<rect x="3" y="5" width="18" height="14" rx="2.5"/><circle cx="9" cy="11" r="2.2"/><path d="M5.5 16c.6-1.7 2-2.5 3.5-2.5s2.9.8 3.5 2.5M14.5 10h4M14.5 13.5h3"/>'),
    pesa: L('<path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"/>'),
    puerta: L('<path d="M5 21V4a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v17M3 21h18"/><circle cx="12" cy="12" r=".9" fill="currentColor"/><path d="M18 9l3 3-3 3M21 12h-5"/>'),
    entrada: L('<path d="M10 17l5-5-5-5M15 12H3M14 4h5a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-5"/>'),
    salida: L('<path d="M14 7l-5 5 5 5M9 12h12M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5"/>'),
    admin: L('<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4M7 12l3-3 3 2 4-4"/>'),
    candado: L('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>'),
    reloj: L('<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2M9.5 3h5"/>'),
    escudo: L('<path d="M12 3l7 3v5.5c0 4.2-2.9 7.6-7 9.5-4.1-1.9-7-5.3-7-9.5V6z"/><path d="M8.8 12l2.3 2.3 4.2-4.6"/>'),
    gente: L('<circle cx="9" cy="8" r="3.2"/><path d="M3 20c.7-3.4 3.2-5.2 6-5.2s5.3 1.8 6 5.2"/><circle cx="17" cy="9" r="2.4"/><path d="M16.5 14.2c2.4.2 4 1.8 4.5 4.8"/>'),
  }
  window.ICONO = (n) => ICONOS[n] || ''

  const d = document.body.dataset
  const BASE = d.base, GYM = d.gym, SECCION = d.seccion || 'inicio'
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
  // Marca propia (mancuerna dentro de un cuadrado inclinado): no reproduce la de ningún gimnasio real.
  const logo = `<svg viewBox="0 0 64 64" aria-hidden="true"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF8A3D"/><stop offset="1" stop-color="#C2410C"/></linearGradient></defs><rect x="4" y="4" width="56" height="56" rx="14" fill="url(#lg)"/><g fill="#fff"><rect x="11" y="23" width="6" height="18" rx="2"/><rect x="47" y="23" width="6" height="18" rx="2"/><rect x="17" y="19" width="6" height="26" rx="2"/><rect x="41" y="19" width="6" height="26" rx="2"/><rect x="23" y="29.5" width="18" height="5" rx="1.5"/></g><path d="M10 52h44" stroke="#C6F135" stroke-width="3.5" stroke-linecap="round"/></svg>`
  const enlace = (clave, href, texto) => `<a href="${href}"${SECCION === clave ? ' class="activo" aria-current="page"' : ''} data-clave="${clave}">${texto}</a>`

  document.body.insertAdjacentHTML('afterbegin', `
<a class="saltar" href="#vista">Saltar al contenido</a>
<div class="utilidad"><div class="ancho"><span>${ICONOS.candado} Tu tarjeta de socio vive en tu wallet: sin plástico, sin contraseñas</span><a href="${BASE}/acceso">Control de acceso</a><a href="${BASE}/admin">Administración</a><a href="${BASE}/auditoria/" id="nav-historial" hidden>Historial</a></div></div>
<header class="cabecera"><div class="ancho">
  <a class="marca" href="${BASE}/">${logo}<span>${esc(GYM)}<small>Entrena sin filas</small></span></a>
  <nav class="accesos" aria-label="Accesos rápidos">
    <a href="${BASE}/#mi-gym">Mi membresía</a>
    <a href="${BASE}/acceso">Entrada y salida</a>
    <a href="${BASE}/admin">Administración</a>
  </nav>
  <div class="persona" id="${d.slot || 'persona'}"${(d.slot || 'persona') === 'persona' ? ' hidden' : ''}></div>
</div></header>
<nav class="menu" aria-label="Principal"><div class="ancho">
  ${enlace('inicio', `${BASE}/`, 'Inicio')}${enlace('mi-gym', `${BASE}/#mi-gym`, 'Mi membresía')}${enlace('acceso', `${BASE}/acceso`, 'Control de acceso')}${enlace('admin', `${BASE}/admin`, 'Administración')}
</div></nav>
${d.titulo ? `<div class="cinta"><div class="ancho">${ICONOS[d.icono] || ICONOS.pesa}<h1>${esc(d.titulo)}</h1></div></div>` : ''}`)

  document.body.insertAdjacentHTML('beforeend', `
<footer class="pie"><div class="ancho">
  <div><h3>${esc(GYM)}</h3><p>Un gimnasio de ejemplo para probar el ecosistema de identidad digital: hazte socio con tu documento digital, lleva tu tarjeta en la wallet y entra y sal de la sede sin mostrar nada más.</p></div>
  <div><h3>Para ti</h3><ul><li><a href="${BASE}/#mi-gym">Mi membresía</a></li><li><a href="${BASE}/#planes">Planes</a></li><li><a href="${BASE}/acceso">Control de acceso</a></li></ul></div>
  <div><h3>Privacidad</h3><ul><li>La entrada solo ve tu nombre, plan, vigencia y foto</li><li>El registro guarda tu número de socio, no tu documento</li><li>Puedes revocar tu tarjeta cuando quieras</li></ul></div>
  <div class="legal">Gimnasio de ejemplo para probar el ecosistema EUDI Wallet. No es un gimnasio real; los pagos son simulados y los datos de los socios de prueba son ficticios.</div>
</div></footer>`)

  // Enlace «Historial»: solo se muestra a quien puede abrirlo (el proxy responde 403 fuera de las IPs de administración).
  try { fetch(`${BASE}/auditoria/api/ping`, { cache: 'no-store' }).then((r) => { if (r.ok) { const a = document.getElementById('nav-historial'); if (a) a.hidden = false } }).catch(() => {}) } catch (e) {}

  // Con el portal en una sola página, el resaltado del menú sigue al #hash.
  if (SECCION === 'inicio' || SECCION === 'mi-gym') {
    const marcar = () => {
      const clave = decodeURIComponent(location.hash.slice(1)) === 'mi-gym' ? 'mi-gym' : 'inicio'
      document.querySelectorAll('.menu a[data-clave]').forEach((a) => { const on = a.dataset.clave === clave; a.classList.toggle('activo', on); on ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current') })
    }
    addEventListener('hashchange', marcar); marcar()
  }
})()

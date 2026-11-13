// Marco común de las tres páginas (cabecera, menú y pie). Va antes del script de cada página:
// lee <body data-base data-eps data-seccion data-titulo data-slot> y deja el hueco de la persona
// con el id data-slot (persona, yo o der), que rellena la propia página.
(() => {
  const L = (d, extra = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${extra}>${d}</svg>`
  const ICONOS = {
    carne: L('<rect x="3" y="5" width="18" height="14" rx="2.500"/><circle cx="9" cy="11" r="2.200"/><path d="M5.500 16c.6-1.700 2-2.500 3.500-2.500s2.900.8 3.500 2.500M14.500 10h4M14.500 13.500h3"/>'),
    historia: L('<path d="M9 4h6l1 2h2a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h2z"/><path d="M12 17s-3.500-2.200-3.500-4.600A1.900 1.900 0 0 1 12 11.600a1.900 1.900 0 0 1 3.500.8C15.500 14.800 12 17 12 17z"/>'),
    verificar: L('<path d="M12 3l7 3v5.500c0 4.200-2.900 7.600-7 9.500-4.100-1.900-7-5.300-7-9.500V6z"/><path d="M8.800 12l2.300 2.300 4.200-4.600"/>'),
    medico: L('<path d="M6 3v6a4 4 0 0 0 8 0V3"/><path d="M10 13v1.500a4.500 4.500 0 0 0 9 0V12"/><circle cx="19" cy="10" r="2"/>'),
    admin: L('<circle cx="12" cy="12" r="3"/><path d="M19.400 15a1.700 1.700 0 0 0 .3 1.800l.1.100a2 2 0 1 1-2.800 2.800l-.1-.1a1.700 1.700 0 0 0-1.800-.3 1.700 1.700 0 0 0-1 1.500V21a2 2 0 1 1-4 0v-.1a1.700 1.700 0 0 0-1.100-1.500 1.700 1.700 0 0 0-1.800.3l-.1.100a2 2 0 1 1-2.800-2.800l.1-.1a1.700 1.700 0 0 0 .3-1.800 1.700 1.700 0 0 0-1.500-1H3a2 2 0 1 1 0-4h.1a1.700 1.700 0 0 0 1.500-1.100 1.700 1.700 0 0 0-.3-1.800l-.1-.1a2 2 0 1 1 2.800-2.800l.1.100a1.700 1.700 0 0 0 1.800.3H9a1.700 1.700 0 0 0 1-1.500V3a2 2 0 1 1 4 0v.1a1.700 1.700 0 0 0 1 1.500 1.700 1.700 0 0 0 1.800-.3l.1-.1a2 2 0 1 1 2.800 2.800l-.1.100a1.700 1.700 0 0 0-.3 1.800V9a1.700 1.700 0 0 0 1.500 1H21a2 2 0 1 1 0 4h-.1a1.700 1.700 0 0 0-1.500 1z"/>'),
    estetoscopio: L('<path d="M5 3v6a4 4 0 0 0 8 0V3M9 13v2a5 5 0 0 0 10 0v-2"/><circle cx="19" cy="11" r="2"/>'),
    telefono: L('<rect x="7" y="2.500" width="10" height="19" rx="2.500"/><path d="M11 18.500h2"/>'),
    candado: L('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>'),
    llamada: L('<path d="M5 4h4l2 5-2.500 1.500a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"/>'),
    ubicacion: L('<path d="M12 21s-6-5.400-6-10.500a6 6 0 0 1 12 0C18 15.600 12 21 12 21z"/><circle cx="12" cy="10.500" r="2.200"/>'),
  }
  window.ICONO = (n) => ICONOS[n] || ''

  const d = document.body.dataset
  const BASE = d.base, EPS = d.eps, SECCION = d.seccion || 'inicio'
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
  // Marca propia (cruz sobre un círculo con un trazo de onda): no reproduce la de ninguna EPS real.
  const logo = `<svg viewBox="0 0 64 64" aria-hidden="true"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#37B6EE"/><stop offset="1" stop-color="#0B5A7A"/></linearGradient></defs><circle cx="32" cy="32" r="30" fill="url(#lg)"/><rect x="27" y="14" width="10" height="36" rx="3.500" fill="#fff"/><rect x="14" y="27" width="36" height="10" rx="3.500" fill="#fff"/><path d="M9 46c9 5 18 5 24-1 6 6 15 6 22 1" fill="none" stroke="#FF7A70" stroke-width="3.500" stroke-linecap="round"/></svg>`
  const enlace = (clave, href, texto) => `<a href="${href}"${SECCION === clave ? ' class="activo" aria-current="page"' : ''} data-clave="${clave}">${texto}</a>`

  document.body.insertAdjacentHTML('afterbegin', `
<a class="saltar" href="#vista">Saltar al contenido</a>
<div class="utilidad"><div class="ancho"><span>${ICONOS.candado} Tus datos viajan cifrados y los compartes tú, con tu wallet</span><a href="${BASE}/#verificar">Verificar a un paciente</a><a href="${BASE}/medico">Portal médico</a></div></div>
<header class="cabecera"><div class="ancho">
  <a class="marca" href="${BASE}/">${logo}<span>${esc(EPS)}<small>Salud con tu wallet</small></span></a>
  <nav class="accesos" aria-label="Accesos rápidos">
    <a href="${BASE}/#mi-eps">Oficina virtual<br>Afiliados</a>
    <a href="${BASE}/medico">Oficina virtual<br>Médicos</a>
    <a href="${BASE}/admin">Administración</a>
  </nav>
  <div class="persona" id="${d.slot || 'persona'}"${(d.slot || 'persona') === 'persona' ? ' hidden' : ''}></div>
</div></header>
<nav class="menu" aria-label="Principal"><div class="ancho">
  ${enlace('inicio', `${BASE}/`, 'Inicio')}${enlace('mi-eps', `${BASE}/#mi-eps`, 'Mi EPS')}${enlace('verificar', `${BASE}/#verificar`, 'Verificar')}${enlace('medico', `${BASE}/medico`, 'Portal médico')}${enlace('admin', `${BASE}/admin`, 'Administración')}<a id="enlace-historial" href="${BASE}/auditoria/" hidden>Historial</a>
</div></nav>
${d.titulo ? `<div class="cinta"><div class="ancho">${ICONOS[d.icono] || ICONOS.estetoscopio}<h1>${esc(d.titulo)}</h1></div></div>` : ''}`)

  document.body.insertAdjacentHTML('beforeend', `
<footer class="pie"><div class="ancho">
  <div><h3>${esc(EPS)}</h3><p>Una EPS de ejemplo para probar el ecosistema de identidad digital: afíliate, lleva tu carné y tu historia clínica en la wallet y compártelos solo cuando tú quieras.</p></div>
  <div><h3>Para ti</h3><ul><li><a href="${BASE}/#mi-eps">Mi EPS</a></li><li><a href="${BASE}/#verificar">Verificar a un paciente</a></li><li><a href="${BASE}/medico">Portal médico</a></li></ul></div>
  <div><h3>Privacidad</h3><ul><li>Tú decides qué compartir</li><li>Nada se guarda al verificar</li><li>Puedes revocar lo que tienes en la wallet</li></ul></div>
  <div class="legal">EPS de ejemplo para probar el ecosistema EUDI Wallet. No es una EPS real; los datos clínicos de los afiliados de prueba son ficticios.</div>
</div></footer>`)

  // Historial: solo para administración (el proxy responde 403 fuera de sus IPs); el enlace se muestra si el ping responde.
  try { fetch(`${BASE}/auditoria/api/ping`).then((r) => { if (r.ok) document.getElementById('enlace-historial').hidden = false }).catch(() => {}) } catch (e) {}

  // Con el portal en una sola página, el resaltado del menú sigue al #hash.
  if (SECCION === 'inicio' || SECCION === 'mi-eps' || SECCION === 'verificar') {
    const marcar = () => {
      const h = decodeURIComponent(location.hash.slice(1))
      const clave = h === 'mi-eps' || h === 'verificar' ? h : (h.startsWith('verificado=') ? 'verificar' : 'inicio')
      document.querySelectorAll('.menu a[data-clave]').forEach((a) => { const on = a.dataset.clave === clave; a.classList.toggle('activo', on); on ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current') })
    }
    addEventListener('hashchange', marcar); marcar()
  }
})()

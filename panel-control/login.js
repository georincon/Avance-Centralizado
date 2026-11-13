// Página de ingreso del Centro de control: valida la clave contra el servidor y la guarda en este navegador.
// La descarga de la wallet es pública (el teléfono no tiene la clave).
(() => {
  const $ = (s) => document.querySelector(s)
  const CLAVE = 'avance-control-token'
  const url = new URL(location.href)
  const pedido = url.searchParams.get('siguiente') || ''
  // Solo rutas locales: una sola barra inicial (// o /\ llevarían a otro sitio).
  const siguiente = /^\/(?![\/\\])/.test(pedido) ? pedido : '/'

  // Ya hay una clave guardada y es válida: directo al panel.
  const guardada = (() => { try { return localStorage.getItem(CLAVE) } catch { return null } })()
  if (guardada) validar(guardada).then((ok) => { if (ok === true) location.replace(siguiente) })

  async function validar(clave) {
    try {
      const r = await fetch('/api/session', { headers: { authorization: 'Bearer ' + clave }, cache: 'no-store' })
      if (r.status === 429) return 'demasiados'
      if (r.status === 404) return 'restringido'   // el borde oculta la API del panel fuera de las redes autorizadas
      return r.ok
    } catch { return 'red' }
  }

  $('#ver').addEventListener('click', () => {
    const i = $('#clave'); const ver = i.type === 'password'
    i.type = ver ? 'text' : 'password'; $('#ver').textContent = ver ? 'Ocultar' : 'Mostrar'
  })

  $('#form').addEventListener('submit', async (e) => {
    e.preventDefault()
    const clave = $('#clave').value.trim(); const err = $('#error'); const boton = $('#entrar')
    err.hidden = true; boton.disabled = true; boton.textContent = 'Verificando…'
    const ok = await validar(clave)
    if (ok === true) { try { localStorage.setItem(CLAVE, clave) } catch {} ; location.replace(siguiente); return }
    err.textContent = ok === 'restringido' ? 'Desde esta red no se puede administrar el panel. Pide que autoricen tu IP pública (srv-nw-nginx/ips-admin-permitidas.conf).'
      : ok === 'demasiados' ? 'Demasiados intentos fallidos. Espera un minuto e inténtalo de nuevo.'
      : ok === 'red' ? 'No hay conexión con el panel.' : 'La clave no es correcta.'
    err.hidden = false; boton.disabled = false; boton.textContent = 'Ingresar'; $('#clave').select()
  })

  // Descarga de la wallet: siempre la última versión compilada.
  $('#url').value = location.origin + '/descargas/wallet.apk'
  $('#copiar').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText($('#url').value); $('#copiar').textContent = 'Copiado' } catch { $('#url').select() }
    setTimeout(() => { $('#copiar').textContent = 'Copiar' }, 1800)
  })
  fetch('/descargas/apk.json', { cache: 'no-store' }).then((r) => r.ok ? r.json() : Promise.reject()).then((a) => {
    $('#subido').textContent = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(a.subido))
    $('#tamano').textContent = (a.tamano / 1048576).toFixed(1).replace('.', ',') + ' MB'
    $('#sha').textContent = a.sha256.slice(0, 16) + '…'; $('#sha').title = a.sha256
    $('#qr').src = '/descargas/qr.svg?v=' + a.sha256.slice(0, 12)
    $('#url').value = a.url
  }).catch(() => {
    $('#apk').textContent = 'Aún no hay un APK compilado'; $('#apk').removeAttribute('href'); $('#apk').style.opacity = '.6'; $('#qr').style.opacity = '.25'
  })
})()

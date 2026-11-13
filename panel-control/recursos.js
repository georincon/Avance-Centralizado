(() => {
  const $ = (id) => document.getElementById(id)
  const token = localStorage.getItem('avance-control-token') || ''
  if (!token) { location.replace('/login?siguiente=%2Frecursos'); return }

  $('salir')?.addEventListener('click', () => { try { localStorage.removeItem('avance-control-token') } catch {} location.replace('/login') })

  let foto = null, tab = 'contenedores', desfase = 0
  const abiertos = new Set()

  // Nodos sin HTML: los nombres vienen de Docker y se muestran siempre como texto.
  function h(tag, props = {}, ...hijos) {
    const n = document.createElement(tag)
    for (const [k, v] of Object.entries(props)) {
      if (k === 'class') n.className = v
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v)
      else if (v !== undefined && v !== null && v !== false) n.setAttribute(k, v === true ? '' : v)
    }
    for (const hijo of hijos.flat(Infinity)) if (hijo !== null && hijo !== undefined && hijo !== false) n.append(hijo instanceof Node ? hijo : String(hijo))
    return n
  }
  const toast = (msg) => { const t = $('toast'); t.textContent = msg; t.className = 'toast show'; setTimeout(() => (t.className = 'toast'), 2200) }
  const copiar = (texto) => navigator.clipboard?.writeText(texto).then(() => toast(`Copiado: ${texto}`), () => {})

  // --- Tiempos -------------------------------------------------------------------------------------
  const ahora = () => Date.now() + desfase
  function duracion(ms) {
    const s = Math.max(0, Math.floor(ms / 1000))
    const d = Math.floor(s / 86400), hh = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60), seg = s % 60
    if (d) return `${d} d ${hh} h`
    if (hh) return `${hh} h ${m} min`
    if (m) return `${m} min ${seg} s`
    return `${seg} s`
  }
  const valida = (f) => f && !f.startsWith('0001-')
  function actividad(c) {
    if (c.estado === 'running' || c.estado === 'restarting' || c.estado === 'paused') return { texto: duracion(ahora() - Date.parse(c.inicio)), ref: c.inicio, activo: true }
    if (valida(c.fin)) return { texto: `Detenido hace ${duracion(ahora() - Date.parse(c.fin))}`, ref: c.fin, activo: false }
    return { texto: 'Creado, sin iniciar', ref: '', activo: false }
  }

  // --- Estado --------------------------------------------------------------------------------------
  function estadoDe(c) {
    if (c.estado === 'running') {
      if (c.salud === 'unhealthy') return { clase: 'malo', texto: 'Activo · no saludable', problema: true, activo: true }
      if (c.salud === 'starting') return { clase: 'aviso', texto: 'Iniciando…', problema: true, activo: true }
      return { clase: 'activo', texto: c.salud === 'healthy' ? 'Activo · saludable' : 'Activo', problema: false, activo: true }
    }
    if (c.estado === 'restarting') return { clase: 'aviso', texto: 'Reiniciando', problema: true, activo: false }
    if (c.estado === 'paused') return { clase: 'aviso', texto: 'En pausa', problema: true, activo: false }
    if (c.estado === 'exited') return c.codigo && ![130, 137, 143].includes(c.codigo) ? { clase: 'malo', texto: `Terminó con error (${c.codigo})`, problema: true, activo: false } : { clase: '', texto: c.codigo ? `Detenido (señal ${c.codigo})` : 'Detenido', problema: false, activo: false }
    if (c.estado === 'created') return { clase: '', texto: 'Creado', problema: false, activo: false }
    return { clase: 'malo', texto: c.estado, problema: true, activo: false }
  }
  const chip = (texto, clase = '', titulo) => h('span', { class: `chip ${clase}`, title: titulo }, texto)

  // --- Contenedores --------------------------------------------------------------------------------
  const TIPOS = { 'Base de datos': 'bd', Issuer: 'issuer', Verifier: 'verifier', Portal: 'portal', Wallet: 'wallet', Onboarding: 'wallet', Confianza: 'confianza', Gateway: 'gateway', Panel: 'gateway' }
  const etiquetaTipo = (tipo) => h('span', { class: `tipo tipo-${TIPOS[tipo] || 'otro'}` }, tipo)
  const destino = (p) => `${p.ip || '0.0.0.0'}:${p.host}`
  const copiable = (texto) => (ev) => { ev.stopPropagation(); copiar(texto) }

  function celdaPuertos(c) {
    const partes = []
    for (const p of c.publicados) {
      partes.push(h('div', { class: 'puerto' },
        h('span', { class: 'chip conexion', title: `Clic para copiar ${destino(p)}`, onclick: copiable(destino(p)) }, `${p.ip || '*'}:${p.host}`),
        h('span', { class: 'flecha' }, `→ ${p.interno}${p.protocolo === 'udp' ? '/udp' : ''}`)))
    }
    if (c.base) {
      partes.push(h('span', { class: 'tenue' }, `${c.base.motor} · base ${c.base.base || '—'} · usuario ${c.base.usuario || '—'}`))
      if (!c.base.puertoLocal) partes.push(h('div', {}, chip('sin puerto publicado', 'sin', 'Solo se alcanza desde otros contenedores'), h('span', { class: 'tenue' }, `interno ${c.base.puertoInterno}`)))
    }
    const internos = c.internos.filter((p) => !c.base || p !== c.base.puertoInterno || c.base.puertoLocal)
    if (internos.length) partes.push(h('div', {}, internos.map((p) => chip(p, 'interno', 'Solo dentro de la red de Docker')), h('span', { class: 'tenue' }, 'interno (red Docker)')))
    if (!partes.length) partes.push(h('span', { class: 'tenue' }, 'ninguno'))
    return h('td', {}, partes)
  }

  function celdaEnlaces(c, activo) {
    const partes = c.enlaces.map((e) => h('a', { class: 'enlace' + (activo ? '' : ' apagado'), href: e.url, target: '_blank', rel: 'noopener', title: `${e.url}${activo ? '' : ' (el servicio está detenido)'}`, onclick: (ev) => ev.stopPropagation() }, e.texto, ' ↗'))
    if (c.base?.jdbc) partes.push(h('span', { class: 'chip conexion', title: c.base.jdbc, onclick: copiable(c.base.jdbc) }, 'Copiar JDBC'))
    return h('td', {}, partes.length ? partes : h('span', { class: 'tenue' }, '—'))
  }

  function celdasContenedor(c) {
    const e = estadoDe(c), act = actividad(c)
    const t = c.tarjeta
    const red = c.redes.find((r) => r.red === 'avance-eudi') || c.redes[0]
    return [
      h('td', {}, h('span', { class: `est ${e.clase}` }, h('i'), e.texto), c.reinicios ? h('span', { class: 'tenue' }, `${c.reinicios} reinicio${c.reinicios === 1 ? '' : 's'}`) : ''),
      h('td', {}, h('span', { class: 'fuerte' }, c.proyecto),
        t?.url && t.url !== '/' && e.activo ? h('a', { class: 'abrir', href: t.url, target: '_blank', rel: 'noopener', onclick: (ev) => ev.stopPropagation() }, 'Abrir ↗') : '',
        h('span', { class: 'tenue' }, etiquetaTipo(c.tipo), t ? h('span', { class: 'grupo-txt' }, t.grupo) : chip('Sin tarjeta en el panel', 'sin'))),
      h('td', {}, h('span', { class: 'mono fuerte' }, c.servicio || c.nombre), h('span', { class: 'tenue mono' }, c.stack || '—')),
      h('td', {}, h('span', { class: 'mono fuerte' }, c.nombre), h('span', { class: 'tenue mono' }, c.id)),
      h('td', {}, c.hosts.length ? c.hosts.map((x) => h('span', { class: 'chip host', title: 'Clic para copiar', onclick: copiable(x) }, x)) : h('span', { class: 'mono' }, c.nombre),
        h('span', { class: 'tenue mono' }, red ? `${red.red}${red.ip ? ' · ' + red.ip : ''}` : 'sin red')),
      celdaPuertos(c),
      h('td', {}, h('span', { class: 'fuerte act', 'data-ref': act.ref, 'data-activo': act.activo ? '1' : '0' }, act.texto),
        act.activo ? h('span', { class: 'tenue' }, `desde ${new Date(c.inicio).toLocaleString('es-CO', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })}`) : ''),
      h('td', { title: c.imagen }, h('span', { class: 'mono' }, c.imagen)),
      celdaEnlaces(c, e.activo),
    ]
  }
  function detalle(c) {
    const bloque = (titulo, ...contenido) => h('div', {}, h('h4', {}, titulo), ...contenido)
    const b = c.base
    const conexion = b ? bloque('Conexión a la base de datos',
      h('div', { class: 'mono' }, `Motor: ${b.motor}`), h('div', { class: 'mono' }, `Base: ${b.base || '—'} · usuario: ${b.usuario || '—'}`),
      b.puertoLocal
        ? [h('div', { class: 'mono' }, `Desde tu equipo: ${b.ipLocal}:${b.puertoLocal}`), b.jdbc ? h('div', {}, h('span', { class: 'chip conexion', title: 'Clic para copiar', onclick: copiable(b.jdbc) }, b.jdbc)) : '']
        : h('div', { class: 'mono' }, `Sin puerto publicado: solo por la red Docker (${c.hosts[0] || c.nombre}:${b.puertoInterno})`),
      h('div', { class: 'tenue' }, 'La contraseña está en el compose del stack.')) : ''
    return h('tr', { class: 'detalle' }, h('td', { colspan: '9' }, h('div', { class: 'det-grid' },
      conexion,
      bloque('Imagen', h('span', { class: 'mono' }, c.imagen)),
      bloque('Creado', new Date(c.creado).toLocaleString('es-CO')),
      bloque('Redes', c.redes.length ? c.redes.map((r) => h('div', { class: 'mono' }, `${r.red} · ${r.ip || 'sin IP'}${r.alias.length ? ' · alias: ' + r.alias.join(', ') : ''}`)) : 'Ninguna'),
      bloque('Volúmenes', c.volumenes.length ? c.volumenes.map((v) => h('div', { class: 'mono' }, v)) : 'Ninguno'),
    )))
  }
  function pasaFiltros(c) {
    const q = $('buscar').value.trim().toLowerCase(), fe = $('filtro-estado').value, fg = $('filtro-grupo').value, ft = $('filtro-tipo').value
    const e = estadoDe(c)
    if (fe === 'activos' && !e.activo) return false
    if (fe === 'detenidos' && e.activo) return false
    if (fe === 'problemas' && !e.problema) return false
    if (ft !== 'todos' && c.tipo !== ft) return false
    const grupo = c.tarjeta?.grupo || 'Sin tarjeta en el panel'
    if (fg !== 'todos' && grupo !== fg) return false
    if (!q) return true
    const puertosTxt = c.publicados.flatMap((p) => [p.host, destino(p), `${p.host}→${p.interno}`])
    return [c.nombre, c.servicio, c.stack, c.imagen, c.proyecto, c.tipo, c.tarjeta?.grupo, ...c.hosts, ...puertosTxt, ...c.internos,
      c.base && 'base de datos', c.base?.motor, c.base?.base, c.base?.usuario].some((x) => String(x || '').toLowerCase().includes(q))
  }
  function tablaContenedores() {
    const lista = foto.contenedores.filter(pasaFiltros)
    if (!lista.length) return h('div', { class: 'r-vacio' }, 'No hay contenedores que coincidan con el filtro.')
    const cuerpo = h('tbody')
    for (const c of lista) {
      cuerpo.append(h('tr', { class: 'fila', title: 'Clic para ver el detalle', onclick: () => { abiertos.has(c.nombre) ? abiertos.delete(c.nombre) : abiertos.add(c.nombre); pintar() } }, celdasContenedor(c)))
      if (abiertos.has(c.nombre)) cuerpo.append(detalle(c))
    }
    // Reparto proporcional (suma 100 %): cada columna recibe un porcentaje del ancho y el texto largo salta de línea.
    const ANCHOS = [8, 13, 10, 13, 12, 14, 8, 10, 12]
    return h('table', { class: 't-contenedores' }, h('colgroup', {}, ...ANCHOS.map((a) => h('col', { style: `width:${a}%` }))),
      h('thead', {}, h('tr', {}, ...['Estado', 'Proyecto', 'Servicio / stack', 'Contenedor (Docker)', 'Host en la red', 'Puertos de conexión', 'Tiempo de actividad', 'Imagen', 'Enlaces'].map((t) => h('th', {}, t)))), cuerpo)
  }

  // --- Redes y volúmenes ---------------------------------------------------------------------------
  function tablaRedes() {
    const q = $('buscar').value.trim().toLowerCase()
    const lista = foto.redes.filter((r) => !q || [r.nombre, r.subred, ...r.contenedores].some((x) => String(x).toLowerCase().includes(q)))
    if (!lista.length) return h('div', { class: 'r-vacio' }, 'No hay redes que coincidan.')
    return h('table', {}, h('thead', {}, h('tr', {}, ...['Red', 'Controlador', 'Subred', 'Contenedores conectados'].map((t) => h('th', {}, t)))),
      h('tbody', {}, ...lista.map((r) => h('tr', {}, h('td', {}, h('span', { class: 'mono fuerte' }, r.nombre)), h('td', {}, r.controlador), h('td', { class: 'mono' }, r.subred || '—'),
        h('td', {}, r.contenedores.length ? [h('span', { class: 'fuerte' }, String(r.contenedores.length)), ' ', r.contenedores.map((n) => chip(n, 'interno'))] : h('span', { class: 'tenue' }, 'ninguno'))))))
  }
  function tablaVolumenes() {
    const q = $('buscar').value.trim().toLowerCase()
    const lista = foto.volumenes.filter((v) => !q || [v.nombre, ...v.usadoPor].some((x) => x.toLowerCase().includes(q)))
    if (!lista.length) return h('div', { class: 'r-vacio' }, 'No hay volúmenes que coincidan.')
    return h('table', {}, h('thead', {}, h('tr', {}, ...['Volumen', 'En uso por'].map((t) => h('th', {}, t)))),
      h('tbody', {}, ...lista.map((v) => h('tr', {}, h('td', {}, h('span', { class: 'mono fuerte' }, v.nombre)),
        h('td', {}, v.usadoPor.length ? v.usadoPor.map((n) => chip(n)) : h('span', { class: 'tenue' }, 'sin contenedor (datos guardados)'))))))
  }

  // --- Página --------------------------------------------------------------------------------------
  function kpis() {
    const cs = foto.contenedores, est = cs.map(estadoDe)
    const datos = [
      ['Activos', est.filter((e) => e.activo).length, '▶', 'background:#e7f6ef;color:#20a273'],
      ['Detenidos', est.filter((e) => !e.activo && !e.problema).length, 'Ⅱ', 'background:#f0f2f4;color:#798797'],
      ['Con problemas', est.filter((e) => e.problema).length, '!', 'background:#fff0ee;color:#c05a4c'],
      ['Sin tarjeta en el panel', cs.filter((c) => !c.tarjeta).length, '◫', 'background:#fff4e5;color:#b27625'],
    ]
    $('kpis').replaceChildren(...datos.map(([t, n, i, estilo]) => h('div', { class: 'r-kpi' }, h('div', {}, h('small', {}, t), h('b', {}, String(n))), h('span', { style: estilo }, i))))
  }
  function grupos() {
    const sel = $('filtro-grupo'), actual = sel.value
    const nombres = [...new Set(foto.contenedores.map((c) => c.tarjeta?.grupo || 'Sin tarjeta en el panel'))].sort()
    sel.replaceChildren(h('option', { value: 'todos' }, 'Todos los grupos'), ...nombres.map((n) => h('option', { value: n }, n)))
    sel.value = nombres.includes(actual) ? actual : 'todos'
  }
  function tipos() {
    const sel = $('filtro-tipo'), actual = sel.value
    const nombres = [...new Set(foto.contenedores.map((c) => c.tipo))].sort()
    sel.replaceChildren(h('option', { value: 'todos' }, 'Todos los tipos'), ...nombres.map((n) => h('option', { value: n }, n === 'Base de datos' ? 'Bases de datos' : n)))
    sel.value = nombres.includes(actual) ? actual : 'todos'
  }
  function pintar() {
    if (!foto) return
    if (foto.error) { $('tabla').replaceChildren(h('div', { class: 'r-vacio' }, `No se pudo leer Docker: ${foto.error}`)); return }
    const scroll = $('tabla').scrollLeft
    $('n-contenedores').textContent = foto.contenedores.length
    $('n-redes').textContent = foto.redes.length
    $('n-volumenes').textContent = foto.volumenes.length
    kpis()
    $('tabla').replaceChildren(tab === 'contenedores' ? tablaContenedores() : tab === 'redes' ? tablaRedes() : tablaVolumenes())
    $('tabla').scrollLeft = scroll
    $('filtro-estado').hidden = $('filtro-grupo').hidden = $('filtro-tipo').hidden = tab !== 'contenedores'
    $('pie').textContent = `Última actualización ${new Date().toLocaleTimeString('es-CO')} · se refresca sola con cada cambio de Docker.`
  }

  // Los tiempos avanzan cada segundo sin volver a pintar la tabla.
  setInterval(() => {
    document.querySelectorAll('.act').forEach((n) => {
      if (!n.dataset.ref) return
      const ms = ahora() - Date.parse(n.dataset.ref)
      n.textContent = n.dataset.activo === '1' ? duracion(ms) : `Detenido hace ${duracion(ms)}`
    })
  }, 1000)

  // --- Canal en vivo -------------------------------------------------------------------------------
  const vivo = (clase, texto) => { const v = $('vivo'); v.className = `r-vivo ${clase}`; v.querySelector('span').textContent = texto }
  async function conectar(espera = 1000) {
    const control = new AbortController()
    let guardian
    const rearmar = () => { clearTimeout(guardian); guardian = setTimeout(() => control.abort(), 45_000) }
    try {
      const r = await fetch('/api/recursos/stream', { headers: { authorization: 'Bearer ' + token }, signal: control.signal })
      if (r.status === 401) { localStorage.removeItem('avance-control-token'); location.replace('/login?siguiente=%2Frecursos'); return }
      if (!r.ok || !r.body) throw new Error('HTTP ' + r.status)
      vivo('en-vivo', 'En vivo')
      rearmar()
      const lector = r.body.getReader(), decodificador = new TextDecoder()
      let resto = ''
      for (;;) {
        const { done, value } = await lector.read()
        if (done) break
        rearmar()
        resto += decodificador.decode(value, { stream: true })
        const partes = resto.split('\n\n'); resto = partes.pop()
        for (const parte of partes) {
          const linea = parte.split('\n').find((l) => l.startsWith('data: '))
          if (!linea) continue
          foto = JSON.parse(linea.slice(6))
          if (foto.ahora) desfase = Date.parse(foto.ahora) - Date.now()
          if (foto.contenedores) { grupos(); tipos() }
          pintar()
        }
        espera = 1000
      }
    } catch { /* se reintenta abajo */ }
    clearTimeout(guardian)
    vivo('caido', 'Reconectando…')
    setTimeout(() => conectar(Math.min(espera * 2, 10_000)), espera)
  }

  document.querySelectorAll('.r-tabs button').forEach((b) => b.addEventListener('click', () => {
    tab = b.dataset.tab
    document.querySelectorAll('.r-tabs button').forEach((x) => x.setAttribute('aria-selected', String(x === b)))
    pintar()
  }))
  for (const id of ['buscar', 'filtro-estado', 'filtro-grupo', 'filtro-tipo']) $(id).addEventListener('input', pintar)
  conectar()
})()

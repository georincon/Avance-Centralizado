// El protocolo de la aerolínea: un archivo JSON con cada trámite, solo para el ejemplo.
//
// Cada trámite tiene un número consecutivo (como en el protocolo de una aerolínea
// real), la persona que lo hizo (verificada con su PID), los datos que escribió
// y su índice en la status list. Anular un trámite pone su bit a 1.

import { leerAlmacen, guardarAlmacen } from './almacen.js'

export function crearTramites(archivo) {
  const leer = () => leerAlmacen(archivo)
  const guardar = (p) => guardarAlmacen(archivo, p)

  return {
    crear({ tipo, persona, datos }) {
      const p = leer()
      const n = p.siguiente++
      const tramite = {
        numero: `${new Date().getFullYear()}-${String(n).padStart(5, '0')}`,
        tipo,
        fecha: new Date().toISOString(),
        persona,
        datos,
        // El índice en la status list es el consecutivo: nunca se reutiliza.
        indice_estado: n,
        revocados: [],   // índices de la status list de copias anteriores (reexpedición): quedan revocados para siempre
        estado: 'vigente',   // vigente | usado (abordó) | anulado (cancelado)
        entregas: 0,
      }
      p.tramites.push(tramite)
      guardar(p)
      return tramite
    },

    buscar: (numero) => leer().tramites.find((t) => t.numero === numero) ?? null,

    /** Guarda los cambios de datos de una reserva (p. ej. el número de tiquete, que sale del consecutivo). */
    actualizar(tramite) {
      const p = leer()
      const t = p.tramites.find((x) => x.numero === tramite.numero)
      if (t) {
        t.datos = tramite.datos
        guardar(p)
      }
      return t ?? null
    },

    dePersona: (documento) => leer().tramites.filter((t) => t.persona.documento === documento).reverse(),

    anular(numero, motivo) {
      const p = leer()
      const t = p.tramites.find((x) => x.numero === numero)
      if (!t || t.estado === 'anulado') return t ?? null
      t.estado = 'anulado'
      t.anulado = { fecha: new Date().toISOString(), motivo }
      guardar(p)
      return t
    },

    registrarEntrega(numero) {
      const p = leer()
      const t = p.tramites.find((x) => x.numero === numero)
      if (t) {
        t.entregas = (t.entregas ?? 0) + 1
        guardar(p)
      }
    },

    /** Los índices de la status list que valen 1: tiquetes cancelados o ya usados, y copias reexpedidas. */
    anulados: () => leer().tramites.flatMap((t) => [...(t.estado === 'vigente' ? [] : [t.indice_estado]), ...(t.revocados ?? [])]),

    /** El pasajero abordó: el tiquete se usó y deja de valer (como un cupón de vuelo usado). */
    usar(numero) {
      const p = leer()
      const t = p.tramites.find((x) => x.numero === numero)
      if (!t || t.estado !== 'vigente') return t ?? null
      t.estado = 'usado'
      t.usado = { fecha: new Date().toISOString() }
      guardar(p)
      return t
    },

    /** Reexpedición (el pasajero perdió su tiquete): la copia anterior queda revocada y la siguiente usa otro índice. */
    reexpedir(numero) {
      const p = leer()
      const t = p.tramites.find((x) => x.numero === numero)
      if (!t || t.estado !== 'vigente') return null
      t.revocados = [...(t.revocados ?? []), t.indice_estado]
      t.indice_estado = p.siguiente++
      t.entregas = 0
      t.reexpediciones = (t.reexpediciones ?? 0) + 1
      guardar(p)
      return t
    },

    buscarPorTiquete: (numeroTiquete) => leer().tramites.find((t) => t.datos?.numero_tiquete === numeroTiquete) ?? null,

    deReserva: (codigo) => leer().tramites.filter((t) => t.datos?.reserva === codigo),

    /** Los tiquetes de un vuelo y fecha (el manifiesto). */
    deVuelo: (vuelo, fecha) => leer().tramites.filter((t) => t.datos?.vuelo === vuelo && t.datos?.fecha === fecha),

    /** Los vuelos que tienen tiquetes emitidos desde `desde` (YYYY-MM-DD). */
    vuelosConTiquetes: (desde) => {
      const m = new Map()
      for (const t of leer().tramites) {
        const d = t.datos
        if (!d?.vuelo || d.fecha < desde) continue
        const k = `${d.vuelo}|${d.fecha}`
        const v = m.get(k) ?? { vuelo: d.vuelo, fecha: d.fecha, salida: d.salida, origen: d.origen_texto, destino: d.destino_texto, tiquetes: 0 }
        v.tiquetes += 1
        m.set(k, v)
      }
      return [...m.values()].sort((a, b) => `${a.fecha}${a.salida}`.localeCompare(`${b.fecha}${b.salida}`))
    },
  }
}

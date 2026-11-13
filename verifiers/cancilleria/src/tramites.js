// El protocolo de la cancillería: un archivo JSON con cada trámite, solo para el ejemplo.
//
// Cada trámite tiene un número consecutivo (como en el protocolo de una cancillería
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
        estado: 'vigente',
        entregas: 0,
      }
      p.tramites.push(tramite)
      guardar(p)
      return tramite
    },

    buscar: (numero) => leer().tramites.find((t) => t.numero === numero) ?? null,

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

    /** Índices de la status list que valen 1: documentos anulados y copias reexpedidas. */
    anulados: () => leer().tramites.flatMap((t) => [...(t.estado === 'anulado' ? [t.indice_estado] : []), ...(t.revocados ?? [])]),

    /** Reexpedición (la persona perdió su documento): la copia anterior queda revocada y la siguiente usa otro índice. */
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
  }
}

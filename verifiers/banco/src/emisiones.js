// Credenciales de cuenta entregadas: una fila por copia, con su índice en la status list.
//
//   { siguiente_indice, emisiones: [{ indice, cedula, cuenta, estado: 'vigente' | 'anulada', motivo?, fecha, anulada? }] }
//
// El índice es la posición del bit en la status list (estado.js): 0 = vigente, 1 = anulada. Al cerrar una cuenta se
// anulan todas sus copias; al volver a pedir la credencial de una cuenta, las anteriores quedan reemplazadas.

import { leerAlmacen, guardarAlmacen } from './almacen.js'

export function crearEmisiones(archivo) {
  const leer = () => leerAlmacen(archivo)
  const guardar = (p) => guardarAlmacen(archivo, p)
  const anular = (p, filtro, motivo) => {
    let n = 0
    for (const e of p.emisiones) {
      if (e.estado === 'vigente' && filtro(e)) { e.estado = 'anulada'; e.motivo = motivo; e.anulada = new Date().toISOString(); n++ }
    }
    return n
  }

  return {
    /** Registra una copia recién entregada y devuelve su índice. */
    registrar(cedula, cuenta) {
      const p = leer()
      const e = { indice: p.siguiente_indice++, cedula, cuenta, estado: 'vigente', fecha: new Date().toISOString() }
      p.emisiones.push(e)
      guardar(p)
      return e
    },
    /** La cuenta se cerró: todas sus copias dejan de valer. Devuelve cuántas se anularon. */
    anularCuenta(cuenta, motivo = 'cuenta cerrada') {
      const p = leer()
      const n = anular(p, (e) => e.cuenta === cuenta, motivo)
      if (n) guardar(p)
      return n
    },
    /** Se entregó una copia nueva de la cuenta: las anteriores quedan reemplazadas. */
    reemplazar(cuenta, exceptoIndice) {
      const p = leer()
      const n = anular(p, (e) => e.cuenta === cuenta && e.indice !== exceptoIndice, 'reemplazada por una nueva')
      if (n) guardar(p)
      return n
    },
    deCuenta: (cuenta) => leer().emisiones.filter((e) => e.cuenta === cuenta),
    /** Índices anulados: lo que pinta la status list. */
    anulados: () => leer().emisiones.filter((e) => e.estado !== 'vigente').map((e) => e.indice),
  }
}

// Los socios del gimnasio, sus membresías y el registro de ingresos y egresos: un archivo JSON, solo para el
// ejemplo (un gimnasio real lo tiene en su sistema de control de acceso).
//
// También lleva el registro de cada tarjeta entregada a una wallet («emisión»): su índice en la status list y si
// sigue vigente. Al renovar, suspender o reactivar la membresía, las tarjetas entregadas antes quedan
// «reemplazadas» o «revocadas» (su bit pasa a 1): en la entrada solo vale la última.
//
// El registro de accesos guarda solo el número de socio, el tipo (ingreso, egreso…), la sede y la hora. El nombre
// no se copia al registro: se une con la lista de socios cuando el administrador lo consulta.

import { randomInt } from 'node:crypto'
import { leerAlmacen, guardarAlmacen } from './almacen.js'
import { hoy, plan as buscarPlan, sumarDias } from './planes.js'

const MAX_REGISTRO = 5000
// Quien ingresó y no marcó salida hace más de esto se da por salido (se fue sin pasar por el torniquete).
const PERMANENCIA_MAXIMA_MS = 8 * 60 * 60 * 1000

export function crearSocios(archivo) {
  const leer = () => leerAlmacen(archivo)
  const guardar = (p) => guardarAlmacen(archivo, p)
  // Las emisiones vigentes de un tipo pasan a «reemplazada» (o al estado dado).
  const retirar = (p, documento, tipo, estado = 'reemplazada') => {
    for (const e of p.emisiones) if (e.documento === documento && e.tipo === tipo && e.estado === 'vigente') e.estado = estado
  }
  const numeroNuevo = (p) => {
    for (;;) {
      const n = `GYM-${randomInt(100000, 999999)}`
      if (!p.socios.some((s) => s.membresia.numero === n)) return n
    }
  }

  /** Quién está dentro: el último ingreso de cada socio sin egreso posterior, y no más viejo que la permanencia máxima. */
  function dentroDe(p) {
    const dentro = new Map()
    for (const a of p.accesos) {
      if (a.tipo === 'ingreso') dentro.set(a.numero, a)
      else if (a.tipo === 'egreso' || a.tipo === 'salida_forzada') dentro.delete(a.numero)
    }
    const limite = Date.now() - PERMANENCIA_MAXIMA_MS
    return [...dentro.values()].filter((a) => Date.parse(a.ts) >= limite)
  }

  return {
    buscar: (documento) => leer().socios.find((s) => s.documento === documento) ?? null,
    porNumero: (numero) => leer().socios.find((s) => s.membresia.numero === numero) ?? null,
    todos: () => leer().socios,

    /** Afiliar a alguien identificado con su PID, con el plan elegido (el pago es simulado). */
    afiliar({ documento, nombres, apellidos, fecha_nacimiento, foto, plan, sede }) {
      const p = leer()
      if (p.socios.some((s) => s.documento === documento)) throw new Error('ya es socio')
      const pl = buscarPlan(plan)
      if (!pl) throw new Error('plan desconocido')
      const desde = hoy()
      const s = {
        documento, nombres, apellidos, fecha_nacimiento, foto: foto ?? null,
        membresia: { numero: numeroNuevo(p), plan: pl.nombre, plan_id: pl.id, sede, estado: 'Activa', socio_desde: desde, desde, hasta: sumarDias(desde, pl.dias - 1) },
      }
      p.socios.push(s)
      guardar(p)
      return s
    },

    /** Guarda la foto de un socio que se afilió sin ella (se toma del PID cuando se vuelve a identificar). */
    ponerFoto(documento, foto) {
      const p = leer()
      const s = p.socios.find((x) => x.documento === documento)
      if (!s || s.foto) return false
      s.foto = foto
      guardar(p)
      return true
    },

    /** Renueva: la membresía continúa donde termina (o desde hoy si ya venció). Las tarjetas anteriores dejan de valer. */
    renovar(documento, planId, sede) {
      const p = leer()
      const s = p.socios.find((x) => x.documento === documento)
      const pl = buscarPlan(planId)
      if (!s || !pl) return null
      const m = s.membresia
      const base = m.hasta >= hoy() ? sumarDias(m.hasta, 1) : hoy()
      Object.assign(m, { plan: pl.nombre, plan_id: pl.id, sede: sede ?? m.sede, estado: 'Activa', desde: base, hasta: sumarDias(base, pl.dias - 1) })
      retirar(p, documento, 'tarjeta')
      guardar(p)
      return s
    },

    /** Suspende o reactiva. La tarjeta entregada antes deja de valer: al reactivar, el socio pide la nueva. */
    cambiarEstado(documento, estado) {
      const p = leer()
      const s = p.socios.find((x) => x.documento === documento)
      if (!s || !['Activa', 'Suspendida'].includes(estado)) return null
      s.membresia.estado = estado
      retirar(p, documento, 'tarjeta', estado === 'Suspendida' ? 'revocada' : 'reemplazada')
      guardar(p)
      return s
    },

    /** Registra una tarjeta entregada y devuelve su índice en la status list. */
    registrarEmision(documento, tipo) {
      const p = leer()
      const e = { indice: p.siguiente_indice++, documento, tipo, estado: 'vigente', fecha: new Date().toISOString() }
      p.emisiones.push(e)
      guardar(p)
      return e
    },

    emisionesDe: (documento) => leer().emisiones.filter((e) => e.documento === documento).reverse(),

    /** Reexpedición: las tarjetas vigentes quedan reemplazadas (el socio recibe una nueva y la anterior deja de valer). */
    reexpedir(documento, tipo) {
      const p = leer()
      const n = p.emisiones.filter((e) => e.documento === documento && e.tipo === tipo && e.estado === 'vigente').length
      retirar(p, documento, tipo)
      guardar(p)
      return n
    },

    /** El socio retira una tarjeta suya (perdió el teléfono, ya no quiere que circule). */
    revocar(documento, indice) {
      const p = leer()
      const e = p.emisiones.find((x) => x.indice === indice && x.documento === documento)
      if (e && e.estado === 'vigente') e.estado = 'revocada'
      guardar(p)
      return e ?? null
    },

    anulados: () => leer().emisiones.filter((e) => e.estado !== 'vigente').map((e) => e.indice),

    // --- Accesos ----------------------------------------------------------------------------------
    /** Anota un ingreso, egreso, salida forzada o intento denegado. */
    registrarAcceso({ numero, tipo, sede, motivo }) {
      const p = leer()
      const a = { ts: new Date().toISOString(), numero: numero ?? null, tipo, sede, ...(motivo ? { motivo } : {}) }
      p.accesos.push(a)
      if (p.accesos.length > MAX_REGISTRO) p.accesos.splice(0, p.accesos.length - MAX_REGISTRO)
      guardar(p)
      return a
    },

    /** Los que están dentro ahora (de una sede, o de todas), con su hora de ingreso. */
    dentro: (sede) => dentroDe(leer()).filter((a) => !sede || a.sede === sede),

    /** Lo último que pasó con un socio: su ingreso abierto, si lo hay. */
    ingresoAbierto: (numero) => dentroDe(leer()).find((a) => a.numero === numero) ?? null,

    /** El administrador (o el cierre del día) marca como salidos a los que siguen dentro. */
    cerrarSede(sede) {
      const p = leer()
      const quedan = dentroDe(p).filter((a) => !sede || a.sede === sede)
      for (const a of quedan) p.accesos.push({ ts: new Date().toISOString(), numero: a.numero, tipo: 'salida_forzada', sede: a.sede, motivo: 'cierre' })
      guardar(p)
      return quedan.length
    },

    historial: (limite = 100) => leer().accesos.slice(-limite).reverse(),

    /** Visitas de un socio: pares ingreso → egreso, de la más reciente a la más antigua. */
    visitasDe(numero, limite = 10) {
      const eventos = leer().accesos.filter((a) => a.numero === numero && a.tipo !== 'denegado')
      const visitas = []
      let abierta = null
      for (const a of eventos) {
        if (a.tipo === 'ingreso') {
          if (abierta) visitas.push(abierta)
          abierta = { sede: a.sede, ingreso: a.ts, egreso: null }
        } else if (abierta) {
          abierta.egreso = a.ts
          visitas.push(abierta)
          abierta = null
        }
      }
      if (abierta) visitas.push(abierta)
      return visitas.reverse().slice(0, limite)
    },
  }
}

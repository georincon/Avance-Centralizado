// Los afiliados de la EPS y su historia clínica: un archivo JSON, solo para el
// ejemplo (una EPS real los tiene en su sistema de información y la historia en
// las IPS que atienden).
//
// También lleva el registro de cada credencial entregada a una wallet
// («emisión»): su índice en la status list y si sigue vigente. Cuando el médico
// actualiza la historia, las historias entregadas antes quedan «reemplazadas»
// (su bit pasa a 1): un médico que reciba una vieja verá que ya no vale.
//
// La semilla (afiliados de prueba) está en semilla.json y solo se copia la
// primera vez.

import { randomInt } from 'node:crypto'
import { leerAlmacen, guardarAlmacen } from './almacen.js'

const HISTORIA_VACIA = {
  grupo_sanguineo: '', alergias: [], condiciones: [], medicamentos: [], vacunas: [],
  ultima_consulta: null, observaciones: '', version: 1, actualizada: null, actualizada_por: null,
}

export function crearAfiliados(archivo, semilla) {
  const leer = () => leerAlmacen(archivo)
  const guardar = (p) => guardarAlmacen(archivo, p)
  // Las emisiones vigentes de un tipo pasan a «reemplazada» (o al estado dado).
  const retirar = (p, documento, tipo, estado = 'reemplazada') => {
    for (const e of p.emisiones) if (e.documento === documento && e.tipo === tipo && e.estado === 'vigente') e.estado = estado
  }

  return {
    buscar: (documento) => leer().afiliados.find((a) => a.documento === documento) ?? null,

    todos: () => leer().afiliados,

    /** Afiliar a alguien identificado con su PID. */
    afiliar({ documento, nombres, apellidos, fecha_nacimiento, regimen, tipo, ips }) {
      const p = leer()
      if (p.afiliados.some((a) => a.documento === documento)) throw new Error('ya afiliado')
      const a = {
        documento, nombres, apellidos, fecha_nacimiento,
        afiliacion: {
          numero: `EPS-${randomInt(100000, 999999)}`, regimen, tipo, plan: 'Plan de Beneficios en Salud (PBS)',
          ips, estado: 'Activo', desde: new Date().toISOString().slice(0, 10),
        },
        historia: { ...HISTORIA_VACIA },
      }
      p.afiliados.push(a)
      guardar(p)
      return a
    },

    /** Añade un beneficiario (compañero(a) permanente) al afiliado, con la declaración que lo respalda. */
    agregarBeneficiario(documento, b) {
      const p = leer()
      const a = p.afiliados.find((x) => x.documento === documento)
      if (!a) return null
      a.beneficiarios ??= []
      if (a.beneficiarios.some((x) => x.documento === b.documento)) return { ya: true }
      a.beneficiarios.push({ ...b, desde: new Date().toISOString().slice(0, 10) })
      guardar(p)
      return a
    },

    /** El médico actualiza la historia: sube la versión y las entregadas antes quedan reemplazadas. */
    actualizarHistoria(documento, cambios, medico) {
      const p = leer()
      const a = p.afiliados.find((x) => x.documento === documento)
      if (!a) return null
      a.historia = { ...a.historia, ...cambios, version: (a.historia.version ?? 1) + 1, actualizada: new Date().toISOString(), actualizada_por: medico }
      retirar(p, documento, 'historia')
      guardar(p)
      return a
    },

    /** Cambia la afiliación (estado, IPS…). Los carnés entregados antes dejan de valer. */
    actualizarAfiliacion(documento, cambios) {
      const p = leer()
      const a = p.afiliados.find((x) => x.documento === documento)
      if (!a) return null
      a.afiliacion = { ...a.afiliacion, ...cambios }
      retirar(p, documento, 'carne', cambios.estado && cambios.estado !== 'Activo' ? 'revocada' : 'reemplazada')
      guardar(p)
      return a
    },

    /** Registra una credencial entregada y devuelve su índice en la status list. */
    registrarEmision(documento, tipo) {
      const p = leer()
      const a = p.afiliados.find((x) => x.documento === documento)
      const e = {
        indice: p.siguiente_indice++, documento, tipo,
        version: tipo === 'historia' ? a?.historia.version : null, estado: 'vigente', fecha: new Date().toISOString(),
      }
      p.emisiones.push(e)
      guardar(p)
      return e
    },

    emisionesDe: (documento) => leer().emisiones.filter((e) => e.documento === documento).reverse(),

    /** Reexpedición: las copias vigentes de un tipo quedan reemplazadas (la persona recibe una nueva y la anterior deja de valer). */
    reexpedir(documento, tipo) {
      const p = leer()
      const n = p.emisiones.filter((e) => e.documento === documento && e.tipo === tipo && e.estado === 'vigente').length
      retirar(p, documento, tipo)
      guardar(p)
      return n
    },

    /** El paciente retira una credencial suya (perdió el teléfono, ya no quiere que circule). */
    revocar(documento, indice) {
      const p = leer()
      const e = p.emisiones.find((x) => x.indice === indice && x.documento === documento)
      if (e && e.estado === 'vigente') e.estado = 'revocada'
      guardar(p)
      return e ?? null
    },

    anulados: () => leer().emisiones.filter((e) => e.estado !== 'vigente').map((e) => e.indice),
  }
}

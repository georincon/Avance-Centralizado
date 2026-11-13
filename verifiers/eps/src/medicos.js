// Los médicos que pueden entrar al portal médico: un archivo JSON, solo para el
// ejemplo. Llegan de dos formas:
//   - el administrador los registra (/eps/admin, con contraseña): activos ya;
//   - el médico se registra él mismo con su PID (/eps/medico): queda
//     «pendiente» hasta que el administrador lo apruebe. Sin esa aprobación,
//     cualquiera con un PID podría editar historias clínicas.
//
// Un médico no tiene contraseña: entra presentando su PID con la wallet, y la
// EPS lo deja pasar si su número de documento está aquí y activo. Su nombre en
// la historia («actualizada por») sale del PID, ya verificado, y el registro
// médico (RETHUS) de aquí.

import { leerAlmacen, guardarAlmacen } from './almacen.js'

export function crearMedicos(archivo) {
  const leer = () => leerAlmacen(archivo)
  const guardar = (lista) => guardarAlmacen(archivo, lista)

  return {
    todos: leer,
    buscar: (documento) => leer().find((m) => m.documento === documento) ?? null,

    registrar({ documento, nombre, registro, especialidad, solicitud = false }) {
      const lista = leer()
      if (lista.some((m) => m.documento === documento)) throw new Error('ya registrado')
      const m = {
        documento, nombre, registro, especialidad,
        activo: !solicitud, pendiente: solicitud, origen: solicitud ? 'autorregistro con PID' : 'administrador',
        registrado: new Date().toISOString(), ultimo_ingreso: null,
      }
      lista.push(m)
      guardar(lista)
      return m
    },

    eliminar(documento) {
      const lista = leer()
      const queda = lista.filter((m) => m.documento !== documento)
      if (queda.length === lista.length) return false
      guardar(queda)
      return true
    },

    cambiar(documento, cambios) {
      const lista = leer()
      const m = lista.find((x) => x.documento === documento)
      if (!m) return null
      Object.assign(m, cambios)
      guardar(lista)
      return m
    },
  }
}

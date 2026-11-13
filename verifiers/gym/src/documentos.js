// El documento que emite el gimnasio a la wallet: la tarjeta de socio.
//
//   Tarjeta de socio   quién es el socio, su número, su plan, hasta cuándo vale la membresía, su estado y su FOTO
//                      (la del PID con el que se afilió). La pide la pantalla de entrada y la de salida de cada sede.
//
// Es un SD-JWT VC: cada dato se divulga por separado. La entrada no necesita el documento del socio: le basta
// el número, el nombre, el plan, el estado, la fecha y la foto (para que quien atiende la compare con la persona);
// el resto lo consulta el gimnasio en su sistema. La foto va en un dato llamado `picture`, que es el nombre que la
// wallet reconoce como imagen (lo mismo que hace con la foto del PID).

import { diasRestantes, hoy } from './planes.js'

export const DOCUMENTOS = {
  tarjeta: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'gym-tarjeta.jpg',
    configuracion: 'co.gym.tarjeta_socio',
    vct: 'urn:co:gym:tarjeta-socio:1',
    nombre: 'Tarjeta de socio',
    descripcion: 'Membresía del gimnasio: sirve para entrar y salir de las sedes',
    color: '#C2410C',
    datos: [
      ['gimnasio', 'Gimnasio'],
      ['socio_nombre', 'Socio'],
      ['socio_documento', 'Documento'],
      ['numero_socio', 'Número de socio'],
      ['plan', 'Plan'],
      ['sede', 'Sede principal'],
      ['estado', 'Estado de la membresía'],
      ['socio_desde', 'Socio desde'],
      ['vigente_hasta', 'Vigente hasta'],
      ['picture', 'Foto'],
    ],
  },
}

export const porConfiguracion = (id) => Object.entries(DOCUMENTOS).find(([, d]) => d.configuracion === id)?.[0]
export const porVct = (vct) => Object.entries(DOCUMENTOS).find(([, d]) => d.vct === vct)?.[0]

/** Los datos del documento `tipo` para un socio, en el orden de la definición. */
export function datosDelDocumento(tipo, s, gimnasio) {
  const m = s.membresia
  return {
    gimnasio,
    socio_nombre: `${s.nombres} ${s.apellidos}`,
    socio_documento: s.documento,
    numero_socio: m.numero,
    plan: m.plan,
    sede: m.sede,
    estado: m.estado,
    socio_desde: m.socio_desde,
    vigente_hasta: m.hasta,
    picture: s.foto ?? undefined,
  }
}

/** Días de validez de la tarjeta: hasta el final de la membresía (al menos 1; la tarjeta no sobrevive a su plan). */
export const validezDias = (socio) => Math.max(1, diasRestantes(socio.membresia.hasta) + 1)

/** El estado real de una membresía hoy: Activa, Suspendida o Vencida. */
export function estadoDe(socio) {
  const m = socio.membresia
  if (m.estado === 'Suspendida') return 'Suspendida'
  return m.hasta < hoy() ? 'Vencida' : 'Activa'
}

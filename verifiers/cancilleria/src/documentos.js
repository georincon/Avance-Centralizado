// El documento que emite la Cancillería: el pasaporte digital.
//
// Es una credencial SD-JWT VC (`vct` urn:co:cancilleria:pasaporte:1). La wallet muestra el nombre,
// los colores y el nombre de cada dato tal como están aquí, y quien lo recibe (una aerolínea, el
// control migratorio) puede pedir solo algunos datos: cada uno se divulga por separado.
//
// Los datos de la persona (nombre, documento, nacimiento, retrato) no los escribe nadie: salen del
// PID que presentó al identificarse, ya verificado. Solo se escriben el lugar de nacimiento y el sexo.

const DATOS_AUTORIDAD = [
  ['numero', 'Solicitud'],
  ['fecha', 'Fecha de expedición'],
  ['autoridad', 'Autoridad expedidora'],
]

export const DOCUMENTOS = {
  pasaporte: {
    fondo: 'cancilleria-pasaporte.jpg',
    configuracion: 'co.cancilleria.pasaporte',
    vct: 'urn:co:cancilleria:pasaporte:1',
    nombre: 'Pasaporte',
    descripcion: 'Pasaporte digital ordinario de Cívica, expedido por su Cancillería',
    color: '#7a1f2b',
    datos: [
      ...DATOS_AUTORIDAD,
      ['pasaporte_numero', 'Número de pasaporte'],
      ['tipo', 'Tipo'],
      ['pais_emisor', 'País emisor'],
      ['apellidos', 'Apellidos'],
      ['nombres', 'Nombres'],
      ['documento', 'Documento de identidad'],
      ['fecha_nacimiento', 'Fecha de nacimiento'],
      ['lugar_nacimiento', 'Lugar de nacimiento'],
      ['sexo', 'Sexo'],
      ['retrato', 'Retrato'],
      ['fecha_vencimiento', 'Válido hasta'],
    ],
  },
}

export const porConfiguracion = (id) => Object.values(DOCUMENTOS).find((d) => d.configuracion === id)
export const porVct = (vct) => Object.entries(DOCUMENTOS).find(([, d]) => d.vct === vct)?.[0]

/** Años de vigencia del pasaporte. Simplificación del simulador (conocimiento general, sin verificar): 10 años, 5 si es menor de edad. */
export const vigenciaAnios = (menor) => (menor ? 5 : 10)

/** Días de validez de la credencial: hasta el vencimiento del pasaporte. */
export function validezDias(tramite) {
  const dias = Math.ceil((new Date(`${tramite.datos.fecha_vencimiento}T23:59:59Z`) - Date.now()) / 86400000)
  return Math.max(1, dias)
}

/** Los datos del documento de un trámite, en el orden de la definición. */
export function datosDelDocumento(tramite, cancilleria) {
  const p = tramite.persona
  const d = tramite.datos
  return {
    numero: tramite.numero, fecha: tramite.fecha.slice(0, 10), autoridad: cancilleria.nombre,
    pasaporte_numero: d.pasaporte_numero, tipo: 'Ordinario (P)', pais_emisor: 'COL',
    apellidos: p.apellidos, nombres: p.nombres, documento: p.documento,
    fecha_nacimiento: p.fecha_nacimiento, lugar_nacimiento: d.lugar_nacimiento, sexo: d.sexo,
    retrato: p.foto, fecha_vencimiento: d.fecha_vencimiento,
  }
}

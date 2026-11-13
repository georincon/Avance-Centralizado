// Los documentos que emite la notaría: uno por trámite.
//
// Cada uno es una credencial SD-JWT VC con su `vct`. La wallet muestra el nombre,
// los colores y el nombre de cada dato tal como están aquí (salen en la metadata
// OpenID4VCI), y quien lo recibe puede pedir solo algunos datos: cada uno se
// divulga por separado.
//
// Los datos de la persona (nombre y documento) no los escribe nadie: salen del
// PID que presentó al identificarse, ya verificado por el verifier.

const DATOS_NOTARIA = [
  ['numero', 'Número'],
  ['fecha', 'Fecha'],
  ['notaria', 'Notaría'],
  ['notario', 'Notario'],
]

export const DOCUMENTOS = {
  declaracion: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'notaria-declaracion.jpg',
    configuracion: 'co.notaria.declaracion_extraproceso',
    vct: 'urn:co:notaria:declaracion-extraproceso:1',
    nombre: 'Declaración extraproceso',
    descripcion: 'Declaración bajo juramento ante notario (Decreto 1557 de 1989)',
    color: '#374d95',
    datos: [
      ...DATOS_NOTARIA,
      ['declarante_nombre', 'Declarante'],
      ['declarante_documento', 'Documento del declarante'],
      ['finalidad', 'Con destino a'],
      ['declaracion', 'Declaración'],
    ],
  },
  poder: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'notaria-poder.jpg',
    configuracion: 'co.notaria.poder_especial',
    vct: 'urn:co:notaria:poder-especial:1',
    nombre: 'Poder especial',
    descripcion: 'Poder especial con presentación personal ante notario',
    color: '#1f5f9e',
    datos: [
      ...DATOS_NOTARIA,
      ['poderdante_nombre', 'Poderdante'],
      ['poderdante_documento', 'Documento del poderdante'],
      ['apoderado_nombre', 'Apoderado'],
      ['apoderado_documento', 'Documento del apoderado'],
      ['facultades', 'Facultades'],
      ['vigente_hasta', 'Vigente hasta'],
    ],
  },
  reconocimiento: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'notaria-reconocimiento.jpg',
    configuracion: 'co.notaria.reconocimiento_documento',
    vct: 'urn:co:notaria:reconocimiento-documento:1',
    nombre: 'Reconocimiento de documento',
    descripcion: 'Reconocimiento de firma y contenido de un documento privado',
    color: '#0146a0',
    datos: [
      ...DATOS_NOTARIA,
      ['firmante_nombre', 'Firmante'],
      ['firmante_documento', 'Documento del firmante'],
      ['documento_nombre', 'Documento'],
      ['documento_sha256', 'Huella SHA-256 del documento'],
      ['documento_bytes', 'Tamaño (bytes)'],
    ],
  },
  fe_vida: {
    fondo: 'notaria-fe-vida.jpg',
    configuracion: 'co.notaria.fe_de_vida',
    vct: 'urn:co:notaria:fe-de-vida:1',
    nombre: 'Fe de vida',
    descripcion: 'Constancia ante notario de que la persona vive (Decreto-Ley 960 de 1970, art. 78)',
    color: '#1a6f8f',
    validezDias: 30,
    datos: [
      ...DATOS_NOTARIA,
      ['titular_nombre', 'Titular'],
      ['titular_documento', 'Documento del titular'],
      ['medio_identificacion', 'Medio de identificación'],
      ['entidad_destino', 'Con destino a'],
      ['vigente_hasta', 'Válida hasta'],
    ],
  },
  autenticacion: {
    fondo: 'notaria-autenticacion.jpg',
    configuracion: 'co.notaria.autenticacion_firma',
    vct: 'urn:co:notaria:autenticacion-firma:1',
    nombre: 'Autenticación de firma',
    descripcion: 'Testimonio de que la firma de un documento es de quien lo firma (Decreto-Ley 960 de 1970, art. 73)',
    color: '#4a3f9e',
    datos: [
      ...DATOS_NOTARIA,
      ['firmante_nombre', 'Firmante'],
      ['firmante_documento', 'Documento del firmante'],
      ['documento_nombre', 'Documento'],
      ['documento_sha256', 'Huella SHA-256 del documento'],
      ['documento_bytes', 'Tamaño (bytes)'],
      ['alcance', 'Alcance'],
    ],
  },
  copia_escritura: {
    fondo: 'notaria-copia-escritura.jpg',
    configuracion: 'co.notaria.constancia_copia_escritura',
    vct: 'urn:co:notaria:copia-escritura:1',
    nombre: 'Constancia de copia de escritura',
    descripcion: 'Constancia de que una copia coincide con la escritura pública que reposa en el protocolo (no es la escritura)',
    color: '#5a4a2a',
    datos: [
      ...DATOS_NOTARIA,
      ['solicitante_nombre', 'Solicitante'],
      ['solicitante_documento', 'Documento del solicitante'],
      ['escritura_numero', 'Escritura pública n.º'],
      ['escritura_fecha', 'Fecha de la escritura'],
      ['notaria_origen', 'Notaría de origen'],
      ['documento_nombre', 'Copia'],
      ['documento_sha256', 'Huella SHA-256 de la copia'],
      ['documento_bytes', 'Tamaño (bytes)'],
      ['alcance', 'Alcance'],
    ],
  },
  registro_civil: {
    fondo: 'notaria-registro-civil.jpg',
    configuracion: 'co.notaria.registro_civil_nacimiento',
    vct: 'urn:co:notaria:registro-civil:1',
    nombre: 'Registro civil de nacimiento',
    descripcion: 'Inscripción del nacimiento de un hijo o hija, declarada por su padre o madre',
    color: '#2f6b4f',
    datos: [
      ...DATOS_NOTARIA,
      ['inscrito_nombre', 'Inscrito(a)'],
      ['inscrito_documento', 'Serial / NUIP del registro'],
      ['fecha_nacimiento', 'Fecha de nacimiento'],
      ['lugar_nacimiento', 'Lugar de nacimiento'],
      ['sexo', 'Sexo'],
      ['progenitor_nombre', 'Declarante'],
      ['progenitor_documento', 'Documento del declarante'],
      ['calidad', 'En calidad de'],
      ['otro_progenitor', 'Otro progenitor'],
    ],
  },
  convivencia: {
    fondo: 'notaria-convivencia.jpg',
    configuracion: 'co.notaria.declaracion_convivencia',
    vct: 'urn:co:notaria:convivencia:1',
    nombre: 'Declaración de convivencia',
    descripcion: 'Declaración extraproceso de que el declarante convive con una persona (para afiliarla como beneficiaria en una EPS)',
    color: '#7a3b4f',
    validezDias: 90,
    datos: [
      ...DATOS_NOTARIA,
      ['declarante_nombre', 'Declarante'],
      ['declarante_documento', 'Documento del declarante'],
      ['companero_nombre', 'Compañero(a) permanente'],
      ['companero_documento', 'Documento del compañero(a)'],
      ['convivencia_desde', 'Convive desde'],
      ['vigente_hasta', 'Válida hasta'],
    ],
  },
  permiso_menor: {
    fondo: 'notaria-permiso-menor.jpg',
    configuracion: 'co.notaria.permiso_salida_menor',
    vct: 'urn:co:notaria:permiso-salida-menor:1',
    nombre: 'Permiso de salida de menor',
    descripcion: 'Autorización de un progenitor para que un menor viaje fuera del país',
    color: '#2d447c',
    // Dura hasta el regreso del viaje (ver `validezDias`).
    datos: [
      ...DATOS_NOTARIA,
      ['otorgante_nombre', 'Otorgante'],
      ['otorgante_documento', 'Documento del otorgante'],
      ['calidad', 'En calidad de'],
      ['menor_nombre', 'Menor'],
      ['menor_documento', 'Documento del menor'],
      ['destino', 'Destino'],
      ['fecha_salida', 'Fecha de salida'],
      ['fecha_regreso', 'Fecha de regreso'],
      ['acompanante', 'Viaja con'],
      ['otro_progenitor', 'Otro progenitor'],
    ],
  },
}

export const porConfiguracion = (id) => Object.values(DOCUMENTOS).find((d) => d.configuracion === id)
export const porVct = (vct) => Object.entries(DOCUMENTOS).find(([, d]) => d.vct === vct)?.[0]

/** Días de validez del documento: el de cada tipo, o hasta el regreso del viaje en el permiso de menor. */
export function validezDias(tramite) {
  if (tramite.tipo === 'permiso_menor') {
    const dias = Math.ceil((new Date(`${tramite.datos.fecha_regreso}T23:59:59Z`) - Date.now()) / 86400000)
    return Math.max(1, Math.min(dias, 365))
  }
  return DOCUMENTOS[tramite.tipo]?.validezDias
}

/** Los datos del documento de un trámite, en el orden de la definición. */
export function datosDelDocumento(tramite, notaria) {
  const p = tramite.persona
  const nombre = `${p.nombres} ${p.apellidos}`
  const base = { numero: tramite.numero, fecha: tramite.fecha.slice(0, 10), notaria: notaria.nombre, notario: notaria.notario }
  const d = tramite.datos
  switch (tramite.tipo) {
    case 'declaracion':
      return { ...base, declarante_nombre: nombre, declarante_documento: p.documento, finalidad: d.finalidad, declaracion: d.declaracion }
    case 'poder':
      return {
        ...base, poderdante_nombre: nombre, poderdante_documento: p.documento,
        apoderado_nombre: d.apoderado_nombre, apoderado_documento: d.apoderado_documento,
        facultades: d.facultades, vigente_hasta: d.vigente_hasta,
      }
    case 'reconocimiento':
      return {
        ...base, firmante_nombre: nombre, firmante_documento: p.documento,
        documento_nombre: d.documento_nombre, documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes,
      }
    case 'fe_vida': {
      const hasta = new Date(Date.now() + DOCUMENTOS.fe_vida.validezDias * 86400000).toISOString().slice(0, 10)
      return {
        ...base, titular_nombre: nombre, titular_documento: p.documento,
        medio_identificacion: `Documento de identidad digital (PID) presentado con la wallet${p.emisor_pid ? `, emitido por ${p.emisor_pid}` : ''}`,
        entidad_destino: d.entidad_destino || 'Quien lo requiera', vigente_hasta: hasta,
      }
    }
    case 'autenticacion':
      return {
        ...base, firmante_nombre: nombre, firmante_documento: p.documento,
        documento_nombre: d.documento_nombre, documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes,
        alcance: 'Testimonio de que la firma corresponde a quien firma. No equivale a reconocimiento ni da mayor fuerza al documento (Decreto-Ley 960 de 1970, art. 77).',
      }
    case 'copia_escritura':
      return {
        ...base, solicitante_nombre: nombre, solicitante_documento: p.documento,
        escritura_numero: d.escritura_numero, escritura_fecha: d.escritura_fecha, notaria_origen: d.notaria_origen,
        documento_nombre: d.documento_nombre, documento_sha256: d.documento_sha256, documento_bytes: d.documento_bytes,
        alcance: 'Constancia sobre una copia. No es la escritura pública ni la reemplaza: la escritura digital no está habilitada (Sentencia C-159 de 2021).',
      }
    case 'registro_civil':
      return {
        ...base, inscrito_nombre: d.inscrito_nombre, inscrito_documento: d.inscrito_documento,
        fecha_nacimiento: d.fecha_nacimiento, lugar_nacimiento: d.lugar_nacimiento, sexo: d.sexo,
        progenitor_nombre: nombre, progenitor_documento: p.documento, calidad: d.calidad,
        otro_progenitor: d.otro_progenitor || 'No se declara',
      }
    case 'convivencia': {
      const hasta = new Date(Date.now() + DOCUMENTOS.convivencia.validezDias * 86400000).toISOString().slice(0, 10)
      return {
        ...base, declarante_nombre: nombre, declarante_documento: p.documento,
        companero_nombre: d.companero_nombre, companero_documento: d.companero_documento,
        convivencia_desde: d.convivencia_desde, vigente_hasta: hasta,
      }
    }
    case 'permiso_menor':
      return {
        ...base, otorgante_nombre: nombre, otorgante_documento: p.documento, calidad: d.calidad,
        menor_nombre: d.menor_nombre, menor_documento: `${d.menor_tipo_documento} ${d.menor_documento}`,
        destino: d.destino, fecha_salida: d.fecha_salida, fecha_regreso: d.fecha_regreso,
        acompanante: d.acompanante || 'Viaja solo(a)', otro_progenitor: d.otro_progenitor,
      }
    default:
      throw new Error(`tipo de trámite desconocido: ${tramite.tipo}`)
  }
}

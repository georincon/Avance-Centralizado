// Los documentos de este emisor: los de la notaría de ejemplo (../verifiers/notaria) más los campos que la
// revisión jurídica (30-oct-2026) encontró ausentes. Todo es SIMULACIÓN: lo que no sale de la persona se
// emula con un valor fijo (`emulado`), que el emisor pone solo; lo demás lo pide el formulario (`usuario`).
// Valores con llave se resuelven al emitir: {hoy}, {ahora}, {consecutivo}.
import { DOCUMENTOS as BASE } from '../../verifiers/notaria/src/documentos.js'

const CC = 'Cédula de ciudadanía'
const MEDIO = 'Virtual, con verificación biométrica contra la Registraduría (simulada)'
const SIMULACION = 'Documento emulado por una notaría simulada del ecosistema de pruebas: no tiene validez legal.'

// [clave, nombre, valor emulado | null si lo escribe la persona, obligatorio]
const comunes = [
  ['fecha_hora_comparecencia', 'Fecha y hora de la comparecencia', '{ahora}'],
  ['medio_comparecencia', 'Medio de comparecencia', MEDIO],
]
const EXTRAS = {
  declaracion: {
    campos: [
      ['declarante_tipo_documento', 'Tipo de documento del declarante', CC],
      ['declarante_estado_civil', 'Estado civil', null],
      ['declarante_domicilio', 'Domicilio', null],
      ['juramento_prestado', 'Juramento', 'Sí: declarado bajo la gravedad del juramento'],
      ['testigos', 'Testigos', null, false],
    ],
    advertencia: 'Declaración rendida bajo juramento con fines extraprocesales (Decreto 1557 de 1989). No es escritura pública ni prueba plena de los hechos declarados; la falsedad tiene consecuencias penales.',
  },
  poder: {
    campos: [
      ['poderdante_tipo_documento', 'Tipo de documento del poderdante', CC],
      ['apoderado_tipo_documento', 'Tipo de documento del apoderado', CC],
      ['tipo_poder', 'Tipo de poder', 'Poder especial'],
      ['asunto', 'Asunto o proceso', null],
      ['lugar_otorgamiento', 'Lugar de otorgamiento', 'Bucaramanga, Santander'],
    ],
    advertencia: 'Poder especial. Para actos que exigen escritura pública (por ejemplo, sobre inmuebles) este poder no basta: se requiere otorgarlo por escritura. Se puede revocar en cualquier momento.',
  },
  reconocimiento: {
    campos: [
      ['firmante_tipo_documento', 'Tipo de documento del firmante', CC],
      ['numero_folios', 'Número de folios', null],
      ['algoritmo_hash', 'Algoritmo de la huella', 'SHA-256'],
    ],
    advertencia: 'Constancia de que el firmante reconoció como suyos la firma y el contenido. No acredita que el contenido sea veraz o legal ni lo convierte en escritura pública.',
  },
  fe_vida: {
    campos: [
      ['titular_tipo_documento', 'Tipo de documento del titular', CC],
      ['metodo_verificacion_biometrica', 'Verificación biométrica', 'Reconocimiento facial contra la base biométrica de la Registraduría (simulada)'],
    ],
    advertencia: 'Constancia de supervivencia a la fecha de la comparecencia. No acredita residencia ni capacidad. Las entidades del Estado no pueden exigir certificado de supervivencia (Decreto 019 de 2012, art. 21).',
  },
  autenticacion: {
    quitar: ['alcance'],
    campos: [
      ['alcance', 'Alcance', 'Testimonio de que la firma corresponde a quien firma. No equivale a reconocimiento ni da mayor fuerza al documento.'],
      ['firmante_tipo_documento', 'Tipo de documento del firmante', CC],
      ['forma_identificacion', 'Forma de identificación', MEDIO],
    ],
    advertencia: 'Testimonio de que la firma corresponde a quien la estampa. No certifica el contenido del documento.',
  },
  copia_escritura: {
    quitar: ['alcance'],
    campos: [
      ['alcance', 'Alcance', 'Constancia sobre una copia. No es la escritura pública ni la reemplaza: la escritura digital no está habilitada (Sentencia C-159 de 2021).'],
      ['numero_copia', 'Número de copia', null],
      ['acto_o_contrato', 'Acto o contrato', null],
      ['folios', 'Folios', null],
    ],
    advertencia: 'Constancia sobre una copia. No es la escritura pública ni la reemplaza, y no acredita que siga vigente o sin notas marginales.',
  },
  registro_civil: {
    quitar: ['inscrito_documento'],
    campos: [
      ['nuip', 'NUIP', null],
      ['indicativo_serial', 'Indicativo serial', null],
      ['hora_nacimiento', 'Hora de nacimiento', null],
      ['nacionalidad_inscrito', 'Nacionalidad', 'Colombiana'],
      ['certificado_nacido_vivo', 'Certificado de nacido vivo n.º', null],
      ['madre_nombre', 'Madre', null],
      ['madre_documento', 'Documento de la madre', null],
      ['padre_nombre', 'Padre', null, false],
      ['padre_documento', 'Documento del padre', null, false],
      ['oficina_registro', 'Oficina de registro', 'Notaría Avance, Bucaramanga (simulada)'],
      ['notas_marginales', 'Notas marginales', 'Sin notas marginales'],
    ],
    advertencia: 'Inscripción emulada. El registro civil oficial lo inscribe la Registraduría o una notaría en el sistema oficial; este documento no lo reemplaza ni genera NUIP real.',
  },
  convivencia: {
    campos: [
      ['declarante_tipo_documento', 'Tipo de documento del declarante', CC],
      ['companero_tipo_documento', 'Tipo de documento del compañero(a)', CC],
      ['estado_civil_declarante', 'Estado civil del declarante', null],
      ['estado_civil_companero', 'Estado civil del compañero(a)', null],
      ['domicilio_comun', 'Domicilio común', null],
      ['entidad_destino', 'Con destino a', null],
      ['testigos', 'Testigos', null, false],
      ['tipo_declaracion', 'Naturaleza', 'Declaración extrajuicio de un hecho de convivencia'],
    ],
    advertencia: 'Constancia de un hecho para afiliación. NO declara la unión marital de hecho ni constituye sociedad patrimonial: eso se hace por escritura pública o acta de conciliación (Ley 54 de 1990, art. 4, modificado por la Ley 979 de 2005).',
  },
  permiso_menor: {
    campos: [
      ['otorgante_tipo_documento', 'Tipo de documento del otorgante', CC],
      ['proposito_viaje', 'Propósito del viaje', null],
      ['acompanante_documento', 'Documento de quien viaja con el menor', null, false],
      ['menor_nacionalidad', 'Nacionalidad del menor', 'Colombiana'],
    ],
    advertencia: 'Autorización de un progenitor para la salida de un menor (Ley 1098 de 2006, art. 110). Migración Colombia puede exigir el documento físico autenticado en el control migratorio.',
  },
}

// Qué campos de cada documento salen del PID con el que la persona se identifica (inicio de sesión con la wallet).
// Tokens: nombre_completo, nombres, apellidos, documento, emisor_pid, medio_identificacion_pid.
// El resto de datos personales (estado civil, domicilio…) no están en el PID y los escribe la persona.
const PERSONA = {
  declaracion: { declarante_nombre: 'nombre_completo', declarante_documento: 'documento' },
  poder: { poderdante_nombre: 'nombre_completo', poderdante_documento: 'documento' },
  reconocimiento: { firmante_nombre: 'nombre_completo', firmante_documento: 'documento' },
  fe_vida: { titular_nombre: 'nombre_completo', titular_documento: 'documento', medio_identificacion: 'medio_identificacion_pid' },
  autenticacion: { firmante_nombre: 'nombre_completo', firmante_documento: 'documento' },
  copia_escritura: { solicitante_nombre: 'nombre_completo', solicitante_documento: 'documento' },
  registro_civil: { progenitor_nombre: 'nombre_completo', progenitor_documento: 'documento' },
  convivencia: { declarante_nombre: 'nombre_completo', declarante_documento: 'documento' },
  permiso_menor: { otorgante_nombre: 'nombre_completo', otorgante_documento: 'documento' },
}

export const DOCUMENTOS = Object.fromEntries(Object.entries(BASE).map(([tipo, d]) => {
  const x = EXTRAS[tipo] ?? { campos: [], advertencia: '' }
  const datos = d.datos.filter(([k]) => !(x.quitar ?? []).includes(k))
  const extra = [...x.campos, ...comunes, ['advertencia_legal', 'Advertencia legal', x.advertencia], ['simulacion', 'Aviso', SIMULACION]]
  const yaEsta = new Set(datos.map(([k]) => k))
  const nuevos = extra.filter(([k]) => !yaEsta.has(k))
  return [tipo, {
    ...d,
    datos: [...datos, ...nuevos.map(([k, n]) => [k, n])],
    persona: PERSONA[tipo] ?? {},
    // clave → { emulado, obligatorio } para los campos nuevos
    reglas: Object.fromEntries(nuevos.map(([k, , v, obl]) => [k, { emulado: v, obligatorio: obl !== false }])),
  }]
}))

// El documento que emite el banco a la wallet: la credencial de cuenta bancaria.
//
//   Cuenta bancaria   quién es el titular, el número y el tipo de la cuenta, su estado y desde cuándo está abierta.
//                     NO lleva el saldo ni los movimientos: eso lo consulta el banco en su sistema.
//
// Es un SD-JWT VC: cada dato se divulga por separado, así que a un tercero (p. ej. quien pide «prueba de que tengo
// una cuenta») se le puede mostrar solo lo necesario. Se emite una por cuenta y se ANULA (status list) cuando la
// cuenta se cierra: la wallet la marca como revocada y deja de ser válida ante cualquier verifier.

export const DOCUMENTOS = {
  cuenta: {
    configuracion: 'co.banco.cuenta_bancaria',
    vct: 'urn:co:banco:cuenta-bancaria:1',
    nombre: 'Cuenta Bancaria',
    descripcion: 'Certifica que eres titular de una cuenta activa en el banco',
    color: '#820AD1',
    datos: [
      ['banco', 'Banco'],
      ['titular_nombre', 'Titular'],
      ['titular_documento', 'Documento'],
      ['numero_cuenta', 'Número de cuenta'],
      ['tipo_cuenta', 'Tipo de cuenta'],
      ['estado', 'Estado'],
      ['abierta_desde', 'Abierta desde'],
    ],
  },
}

export const porConfiguracion = (id) => Object.entries(DOCUMENTOS).find(([, d]) => d.configuracion === id)?.[0]

/** Los datos de la credencial de una cuenta, en el orden de la definición. */
export function datosDelDocumento(cliente, cuenta, banco, tipos) {
  return {
    banco,
    titular_nombre: `${cliente.nombres} ${cliente.apellidos}`,
    titular_documento: cliente.cedula,
    numero_cuenta: cuenta.numero,
    tipo_cuenta: tipos[cuenta.tipo] ?? cuenta.tipo,
    estado: 'Activa',
    abierta_desde: String(cuenta.abierta).slice(0, 10),
  }
}

/** Un año: la credencial no sobrevive a la cuenta (al cerrarla se anula), y se puede volver a pedir. */
export const VALIDEZ_DIAS = 365

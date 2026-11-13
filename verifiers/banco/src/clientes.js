// Los clientes del banco: un archivo JSON, solo para el ejemplo.
//
// Cada cliente se crea al abrir su primera cuenta con los datos que su wallet presentó
// (PID verificado por el verifier). La cédula (personal_administrative_number del
// PID) es la llave: con ella se le reconoce al ingresar.
//
// Un cliente puede tener varias cuentas (`cuentas`). Una cuenta cerrada no se borra: queda con
// estado "cerrada" y sus movimientos, como haría un banco que debe conservar el historial.

import { randomInt } from 'node:crypto'
import { leerAlmacen, guardarAlmacen } from './almacen.js'

export const TIPOS = {
  ahorros: 'Cuenta de ahorros',
  corriente: 'Cuenta corriente',
  nomina: 'Cuenta de nómina',
  infantil: 'Cuenta de ahorro programado',
}
export const MAX_ACTIVAS = 5

const ahora = () => new Date().toISOString()
const numeroCuenta = (lista) => {
  for (;;) {
    const n = `${randomInt(100, 999)}-${randomInt(100000, 999999)}-${randomInt(10, 99)}`
    if (!lista.some((c) => c.cuentas.some((x) => x.numero === n))) return n
  }
}

// Los clientes guardados antes de existir varias cuentas tenían la cuenta pegada al cliente
// (cuenta, saldo, movimientos): se pasan a la lista sin perder nada.
function normalizar(c) {
  if (Array.isArray(c.cuentas)) return c
  const { cuenta, saldo, movimientos, ...resto } = c
  return {
    ...resto,
    cuentas: cuenta
      ? [{ numero: cuenta, tipo: 'ahorros', estado: 'activa', saldo: saldo ?? 0, movimientos: movimientos ?? [], abierta: c.creado ?? ahora() }]
      : [],
  }
}

export function crearClientes(archivo) {
  const leer = () => leerAlmacen(archivo).map(normalizar)
  const guardar = (lista) => guardarAlmacen(archivo, lista)
  const nuevaCuenta = (lista, tipo, conBono) => ({
    numero: numeroCuenta(lista),
    tipo,
    estado: 'activa',
    saldo: conBono ? 50000 : 0,
    movimientos: conBono ? [{ fecha: ahora(), concepto: 'Bono de bienvenida', valor: 50000 }] : [],
    abierta: ahora(),
  })
  const activas = (c) => c.cuentas.filter((x) => x.estado === 'activa')

  return {
    buscar: (cedula) => leer().find((c) => c.cedula === cedula) ?? null,

    crear(datos, tipo = 'ahorros') {
      const lista = leer()
      if (lista.some((c) => c.cedula === datos.cedula)) throw new Error('ya existe')
      const cliente = { ...datos, cuentas: [nuevaCuenta(lista, tipo, true)], creado: ahora() }
      lista.push(cliente)
      guardar(lista)
      return cliente
    },

    registrarIngreso(cedula) {
      const lista = leer()
      const cliente = lista.find((c) => c.cedula === cedula)
      if (cliente) {
        cliente.ultimo_ingreso = ahora()
        guardar(lista)
      }
      return cliente
    },

    /** Abre otra cuenta (sin bono: el bono es de la primera). Devuelve { cuenta } o { error }. */
    abrirCuenta(cedula, tipo) {
      if (!TIPOS[tipo]) return { error: 'Ese tipo de cuenta no existe.' }
      const lista = leer()
      const cliente = lista.find((c) => c.cedula === cedula)
      if (!cliente) return { error: 'No encontramos al cliente.' }
      if (activas(cliente).length >= MAX_ACTIVAS) return { error: `Ya tienes ${MAX_ACTIVAS} cuentas activas: cierra alguna para abrir otra.` }
      const cuenta = nuevaCuenta(lista, tipo, false)
      cliente.cuentas.push(cuenta)
      guardar(lista)
      return { cuenta }
    },

    /**
     * Cierra una cuenta activa. Si tiene saldo, pasa a otra cuenta activa del mismo cliente o, si
     * no hay otra, se entrega como retiro. La cuenta cerrada se conserva con sus movimientos.
     */
    cerrarCuenta(cedula, numero) {
      const lista = leer()
      const cliente = lista.find((c) => c.cedula === cedula)
      const cuenta = cliente?.cuentas.find((x) => x.numero === numero)
      if (!cuenta) return { error: 'No encontramos esa cuenta.' }
      if (cuenta.estado !== 'activa') return { error: 'Esa cuenta ya está cerrada.' }
      const destino = activas(cliente).find((x) => x.numero !== numero)
      let detalle
      if (cuenta.saldo > 0) {
        if (destino) {
          cuenta.movimientos.push({ fecha: ahora(), concepto: `Traslado del saldo a la cuenta ${destino.numero}`, valor: -cuenta.saldo })
          destino.movimientos.push({ fecha: ahora(), concepto: `Saldo recibido por cierre de la cuenta ${numero}`, valor: cuenta.saldo })
          destino.saldo += cuenta.saldo
          detalle = { trasladado: cuenta.saldo, a: destino.numero }
        } else {
          cuenta.movimientos.push({ fecha: ahora(), concepto: 'Retiro por cierre de cuenta', valor: -cuenta.saldo })
          detalle = { retirado: cuenta.saldo }
        }
        cuenta.saldo = 0
      }
      cuenta.estado = 'cerrada'
      cuenta.cerrada = ahora()
      guardar(lista)
      return { cuenta, ...detalle }
    },
  }
}

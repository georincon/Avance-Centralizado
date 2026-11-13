// Los planes y las sedes del gimnasio (ficticias). El pago es simulado: el portal no cobra nada.

export const PLANES = [
  { id: 'dia', nombre: 'Pase de día', dias: 1, precio: 15000, detalle: 'Un día de acceso a todas las sedes' },
  { id: 'mensual', nombre: 'Mensual', dias: 30, precio: 120000, detalle: '30 días, todas las sedes' },
  { id: 'trimestral', nombre: 'Trimestral', dias: 90, precio: 300000, detalle: '90 días, todas las sedes', destacado: true },
  { id: 'anual', nombre: 'Anual', dias: 365, precio: 960000, detalle: '365 días, todas las sedes y un mes de regalo en tu cumpleaños' },
]

export const SEDES = ['Cumbre Centro', 'Cumbre Norte', 'Cumbre Sur']

export const plan = (id) => PLANES.find((p) => p.id === id) ?? null

/** Fecha de hoy (AAAA-MM-DD) en la hora de Colombia: el día de una membresía no cambia a las 7 p. m. UTC. */
export const hoy = () => new Date().toLocaleDateString('en-CA', { timeZone: 'America/Bogota' })

/** Suma días a una fecha AAAA-MM-DD. */
export function sumarDias(fecha, dias) {
  const d = new Date(`${fecha}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + dias)
  return d.toISOString().slice(0, 10)
}

/** Días que faltan para el final de la membresía (0 el último día; negativo si ya venció). */
export const diasRestantes = (hasta) => Math.round((Date.parse(`${hasta}T12:00:00Z`) - Date.parse(`${hoy()}T12:00:00Z`)) / 86400000)

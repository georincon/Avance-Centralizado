// El catálogo de vuelos de la aerolínea (ficticio): rutas, horarios, tarifas y precios. No hay base de datos de
// vuelos: cada ruta sale a unas horas fijas todos los días y el número de vuelo se deriva de la ruta y la hora.

export const CIUDADES = {
  BGA: { nombre: 'Bucaramanga', pais: 'CO' },
  BOG: { nombre: 'Bogotá', pais: 'CO' },
  MDE: { nombre: 'Medellín', pais: 'CO' },
  CLO: { nombre: 'Cali', pais: 'CO' },
  CTG: { nombre: 'Cartagena', pais: 'CO' },
  SMR: { nombre: 'Santa Marta', pais: 'CO' },
  BAQ: { nombre: 'Barranquilla', pais: 'CO' },
  // Internacionales cercanos. Qué documento exige cada país hay que confirmarlo antes de viajar.
  UIO: { nombre: 'Quito', pais: 'EC' },
  LIM: { nombre: 'Lima', pais: 'PE' },
}

// Rutas (en los dos sentidos): duración en minutos, precio base de un adulto en COP y horas de salida.
const RUTAS = [
  ['BGA', 'BOG', 55, 189000, ['06:10', '12:40', '18:55']],
  ['BGA', 'MDE', 60, 205000, ['07:30', '16:20']],
  ['BGA', 'CTG', 75, 259000, ['09:15']],
  ['BOG', 'MDE', 55, 149000, ['06:00', '09:30', '13:15', '17:45', '21:00']],
  ['BOG', 'CLO', 65, 159000, ['06:40', '11:20', '15:50', '20:10']],
  ['BOG', 'CTG', 95, 229000, ['07:10', '12:00', '18:30']],
  ['BOG', 'SMR', 100, 239000, ['08:20', '16:40']],
  ['BOG', 'BAQ', 100, 219000, ['06:50', '14:10', '19:40']],
  ['MDE', 'CTG', 75, 209000, ['10:00', '17:15']],
  ['CLO', 'MDE', 55, 169000, ['08:45']],
  ['BOG', 'UIO', 130, 489000, ['08:00', '19:20']],
  ['BOG', 'LIM', 200, 689000, ['10:30']],
]

export const INTERNACIONAL = (o, d) => CIUDADES[o]?.pais !== CIUDADES[d]?.pais

// Tarifas: factor de precio y condiciones.
export const TARIFAS = {
  basica: { nombre: 'Básica', factor: 1.0, equipaje: 'Un artículo personal. Sin equipaje de bodega.', cambios: 'No permite cambios ni reembolso.' },
  flexible: { nombre: 'Flexible', factor: 1.35, equipaje: 'Equipaje de mano de 10 kg y una maleta de bodega de 23 kg.', cambios: 'Cambios con penalidad menor. Reembolso parcial.' },
  plus: { nombre: 'Plus', factor: 1.75, equipaje: 'Equipaje de mano de 10 kg, dos maletas de bodega de 23 kg y selección de silla.', cambios: 'Cambios sin penalidad. Reembolso total hasta 24 h antes.' },
}

// Categoría del pasajero a la fecha del vuelo (como las clasifican las aerolíneas): adulto desde 12 años, niño de 2 a 11, infante menor de 2.
export function categoria(nacimiento, fechaVuelo) {
  const n = new Date(`${nacimiento}T00:00:00Z`)
  const v = new Date(`${fechaVuelo}T00:00:00Z`)
  if (Number.isNaN(n.getTime())) return null
  let edad = v.getUTCFullYear() - n.getUTCFullYear()
  if (v < new Date(Date.UTC(v.getUTCFullYear(), n.getUTCMonth(), n.getUTCDate()))) edad -= 1
  if (edad < 0) return null
  return { edad, nombre: edad >= 12 ? 'Adulto' : edad >= 2 ? 'Niño' : 'Infante', factor: edad >= 12 ? 1 : edad >= 2 ? 0.75 : 0.1 }
}

const hora = (hhmm, minutos) => {
  const [h, m] = hhmm.split(':').map(Number)
  const t = (h * 60 + m + minutos) % (24 * 60)
  return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`
}

const numeroDeVuelo = (codigo, o, d, salida) => {
  let h = 7
  for (const c of `${o}${d}${salida}`) h = (h * 31 + c.charCodeAt(0)) % 9000
  return `${codigo} ${1000 + h}`
}

/** Los vuelos de un día entre dos ciudades (en cualquiera de los dos sentidos de la ruta). */
export function buscar({ origen, destino, fecha, codigo }) {
  const salidas = []
  for (const [a, b, minutos, base, horas] of RUTAS) {
    if (!((a === origen && b === destino) || (a === destino && b === origen))) continue
    for (const h of horas) {
      const ida = a === origen
      // La vuelta sale a otras horas, para no repetir el mismo horario.
      const salida = ida ? h : hora(h, 4 * 60 + 25)
      salidas.push({
        vuelo: numeroDeVuelo(codigo, origen, destino, salida), origen, destino, fecha, salida, llegada: hora(salida, minutos),
        duracion_min: minutos, base, internacional: INTERNACIONAL(origen, destino),
      })
    }
  }
  return salidas.sort((x, y) => x.salida.localeCompare(y.salida))
}

export const precio = ({ base, internacional }, tarifa, cat) => {
  const tasas = internacional ? 98000 : 46000
  const tarifaCop = Math.round((base * TARIFAS[tarifa].factor * cat.factor) / 1000) * 1000
  return { tarifa: tarifaCop, tasas, total: tarifaCop + tasas }
}

export const cop = (n) => `$ ${n.toLocaleString('es-CO')} COP`

/** Rutas con el precio «desde» de un adulto en tarifa básica (con tasas), para la portada. */
export const OFERTAS = (ciudades) => RUTAS.map(([a, b, , base]) => ({
  origen: a, destino: b, origen_nombre: ciudades[a].nombre, destino_nombre: ciudades[b].nombre,
  desde: base + (INTERNACIONAL(a, b) ? 98000 : 46000), internacional: INTERNACIONAL(a, b),
}))

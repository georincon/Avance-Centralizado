// Almacén de datos del portal en SU base de datos (Postgres, HISTORIAL_DB_URL): reemplaza a los archivos JSON (clientes, socios,
// afiliados, trámites, emisiones…). Cada archivo pasa a ser una fila de la tabla `almacen` (nombre, contenido jsonb).
//
//   - Al arrancar: si la base ya tiene el documento, manda la base. Si hay un archivo JSON (datos antiguos, o escrito mientras la
//     base estaba caída) se importa a la base y el archivo queda renombrado como `.importado-<fecha>`. Si no hay nada, se crea con el valor inicial.
//   - leer/guardar siguen siendo síncronos para el código del portal: se lee de memoria y se guarda en la base en segundo plano
//     (en orden, con reintento). Si la base no responde, el documento se escribe en el archivo y se importa cuando vuelva.
//   - Sin HISTORIAL_DB_URL el portal sigue funcionando solo con archivos, como antes.
import pg from 'pg'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { basename, dirname } from 'node:path'

const URL_DB = process.env.HISTORIAL_DB_URL || ''
const docs = new Map()          // archivo -> valor en memoria
let pool = null
let cola = Promise.resolve()
const pendientes = new Set()    // archivos con un guardado en la base sin confirmar

const nombreDe = (archivo) => basename(archivo).replace(/\.json$/, '')
const estampa = () => new Date().toISOString().replace(/\D/g, '').slice(0, 14)
const leerArchivo = (archivo) => (existsSync(archivo) ? JSON.parse(readFileSync(archivo, 'utf8')) : undefined)
const escribirArchivo = (archivo, valor) => {
  mkdirSync(dirname(archivo), { recursive: true })
  writeFileSync(`${archivo}.tmp`, `${JSON.stringify(valor, null, 2)}\n`)
  renameSync(`${archivo}.tmp`, archivo)
}
const retirarArchivo = (archivo) => { if (existsSync(archivo)) renameSync(archivo, `${archivo}.importado-${estampa()}`) }

const upsert = (archivo, valor) =>
  pool.query(
    `INSERT INTO almacen (nombre, contenido, actualizado) VALUES ($1, $2::jsonb, now())
     ON CONFLICT (nombre) DO UPDATE SET contenido = EXCLUDED.contenido, actualizado = now()`,
    [nombreDe(archivo), JSON.stringify(valor)])

/** Carga (o crea) el documento. Llamar con await al arrancar, antes de crear el almacén del portal. */
export async function iniciarAlmacen(archivo, inicial) {
  const enArchivo = leerArchivo(archivo)
  if (URL_DB) {
    try {
      pool ??= new pg.Pool({ connectionString: URL_DB, max: 3, connectionTimeoutMillis: 4000 })
      await pool.query(`CREATE TABLE IF NOT EXISTS almacen (nombre text PRIMARY KEY, contenido jsonb NOT NULL, actualizado timestamptz NOT NULL DEFAULT now())`)
      const fila = (await pool.query('SELECT contenido FROM almacen WHERE nombre = $1', [nombreDe(archivo)])).rows[0]
      if (enArchivo !== undefined) {                       // datos antiguos o escritos con la base caída: se importan
        await upsert(archivo, enArchivo); retirarArchivo(archivo)
        docs.set(archivo, enArchivo)
        console.log(`[almacen] ${nombreDe(archivo)}: importado a la base desde ${basename(archivo)}`)
      } else if (fila) {
        docs.set(archivo, fila.contenido)
      } else {
        await upsert(archivo, inicial)
        docs.set(archivo, inicial)
      }
      return
    } catch (e) {
      console.error(`[almacen] ${nombreDe(archivo)}: base no disponible (${e.message}); se usa el archivo`)
    }
  }
  const v = enArchivo ?? inicial
  if (enArchivo === undefined) escribirArchivo(archivo, v)
  docs.set(archivo, v)
}

/** Copia del documento (el código del portal lo modifica y lo devuelve con guardarAlmacen). */
export function leerAlmacen(archivo) {
  if (!docs.has(archivo)) throw new Error(`almacén no iniciado: ${archivo}`)
  return structuredClone(docs.get(archivo))
}

export function guardarAlmacen(archivo, valor) {
  docs.set(archivo, structuredClone(valor))
  if (!pool) { escribirArchivo(archivo, valor); return }
  pendientes.add(archivo)
  const intentar = () => {
    cola = cola.then(async () => {
      const v = docs.get(archivo)
      try {
        await upsert(archivo, v)
        pendientes.delete(archivo)
        retirarArchivo(archivo)                              // si quedó uno de respaldo, ya está en la base
      } catch (e) {
        try { escribirArchivo(archivo, v) } catch { /* nada más que hacer */ }
        console.error(`[almacen] ${nombreDe(archivo)}: no se pudo guardar en la base (${e.message}); respaldo en archivo, se reintenta`)
        setTimeout(intentar, 15000).unref?.()
      }
    })
  }
  intentar()
}

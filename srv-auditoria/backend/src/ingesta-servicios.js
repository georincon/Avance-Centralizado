// Ingesta de EMISORES y VERIFICADORES: lo que reportan los servicios del ecosistema (portales, issuers, verifiers).
// Idempotente por `evento_id`. Solo monitoreo: nada de lo que se reciba aquí cambia el comportamiento de un servicio.

import { ErrorValidacion } from './ingesta.js';

const ROLES = ['emisor', 'verificador'];
const RESULTADOS = ['exitoso', 'fallido', 'cancelado', 'en_proceso'];
const SENTIDOS = ['solicitado', 'recibido', 'emitido'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const texto = (v, max = 500) => (v == null || v === '' ? null : String(v).slice(0, max));
const entero = (v) => (Number.isFinite(Number(v)) && v !== null && v !== '' ? Math.trunc(Number(v)) : null);

export function validarServicio(e) {
  if (!e || typeof e !== 'object') throw new ErrorValidacion('cuerpo vacío');
  if (!UUID.test(e.evento_id || '')) throw new ErrorValidacion('evento_id debe ser un UUID');
  if (!ROLES.includes(e.servicio?.rol)) throw new ErrorValidacion(`servicio.rol debe ser uno de ${ROLES.join(', ')}`);
  if (!e.servicio?.identificador) throw new ErrorValidacion('servicio.identificador es obligatorio');
  const v = e.evento;
  if (!v || !v.tipo) throw new ErrorValidacion('evento.tipo es obligatorio');
  if (!RESULTADOS.includes(v.resultado)) throw new ErrorValidacion(`evento.resultado debe ser uno de ${RESULTADOS.join(', ')}`);
  if (Number.isNaN(Date.parse(v.ocurrido_en))) throw new ErrorValidacion('evento.ocurrido_en debe ser una fecha ISO-8601');
  if (v.wallet_id && !UUID.test(v.wallet_id)) throw new ErrorValidacion('evento.wallet_id debe ser un UUID');
}

/** Registra un evento de servicio. Devuelve { duplicado, actividad_servicio_id }. */
export async function registrarEventoServicio(pool, e, ip) {
  validarServicio(e);
  const s = e.servicio; const v = e.evento;
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const sv = await c.query(
      `INSERT INTO servicio (rol, identificador, nombre, proyecto) VALUES ($1,$2,$3,$4)
       ON CONFLICT (rol, identificador) DO UPDATE SET
          nombre = COALESCE(EXCLUDED.nombre, servicio.nombre), proyecto = COALESCE(EXCLUDED.proyecto, servicio.proyecto), ultima_vez_en = now()
       RETURNING servicio_id`,
      [s.rol, texto(s.identificador, 300), texto(s.nombre, 200), texto(s.proyecto, 200)]);

    // Si la persona ya existe en el centro (por su cédula), se enlaza: así se ve todo lo suyo junto.
    let doc = texto(v.sujeto?.numero_documento, 30);
    let nombreSujeto = texto(v.sujeto?.nombre, 200);
    // Un trámite pasa por varios procesos (oferta, token, credencial) y no todos conocen a la persona: el evento que no la trae
    // hereda la del mismo trámite (misma referencia y mismo servicio) si otro paso ya la reportó.
    if (!doc && v.referencia) {
      const previo = (await c.query(
        `SELECT sujeto_documento, sujeto_nombre FROM actividad_servicio
          WHERE servicio_id = $1 AND referencia = $2 AND sujeto_documento IS NOT NULL ORDER BY actividad_servicio_id LIMIT 1`,
        [sv.rows[0].servicio_id, texto(v.referencia, 200)])).rows[0];
      if (previo) { doc = previo.sujeto_documento; nombreSujeto = nombreSujeto ?? previo.sujeto_nombre; }
    }
    const usuario = doc ? (await c.query('SELECT usuario_id FROM usuario WHERE numero_documento = $1 LIMIT 1', [doc])).rows[0] : null;

    const ins = await c.query(
      `INSERT INTO actividad_servicio (evento_id, servicio_id, rol, tipo_evento, ocurrido_en, resultado, tipo_error, codigo_error,
          mensaje_error, referencia, credencial_tipo, credencial_nombre, sujeto_documento, sujeto_nombre, usuario_id, wallet_id,
          canal, duracion_ms, direccion_ip, detalle)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)
       ON CONFLICT (evento_id) DO NOTHING RETURNING actividad_servicio_id`,
      [e.evento_id, sv.rows[0].servicio_id, s.rol, texto(v.tipo, 60), v.ocurrido_en, v.resultado,
       v.resultado === 'exitoso' ? null : texto(v.tipo_error, 40), texto(v.codigo_error, 80), texto(v.mensaje_error, 1000),
       texto(v.referencia, 200), texto(v.credencial_tipo, 200), texto(v.credencial_nombre, 200), doc, nombreSujeto,
       usuario?.usuario_id ?? null, v.wallet_id || null, texto(v.canal, 30), entero(v.duracion_ms), ip,
       JSON.stringify(v.detalle && typeof v.detalle === 'object' ? v.detalle : {})]);
    if (!ins.rowCount) { await c.query('ROLLBACK'); return { duplicado: true }; }
    const id = ins.rows[0].actividad_servicio_id;

    for (const el of Array.isArray(v.elementos) ? v.elementos.slice(0, 200) : []) {
      if (!SENTIDOS.includes(el?.sentido) || !el.elemento) continue;
      await c.query(
        `INSERT INTO actividad_servicio_elemento (actividad_servicio_id, sentido, tipo_credencial, elemento) VALUES ($1,$2,$3,$4)
         ON CONFLICT DO NOTHING`, [id, el.sentido, texto(el.tipo_credencial, 200) || 'desconocida', texto(el.elemento, 200)]);
    }
    await c.query('COMMIT');
    return { duplicado: false, actividad_servicio_id: Number(id) };
  } catch (err) {
    await c.query('ROLLBACK').catch(() => {});
    if (err.code === '23503') throw new ErrorValidacion('tipo de evento desconocido: ' + texto(v.tipo, 60));
    throw err;
  } finally {
    c.release();
  }
}

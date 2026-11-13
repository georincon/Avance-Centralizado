// Consultas de solo lectura sobre la actividad de EMISORES y VERIFICADORES (para el dashboard).
// `rol` es siempre 'emisor' o 'verificador': lo fija la ruta, nunca el usuario.

const lim = (v, def, max) => Math.min(Math.max(parseInt(v, 10) || def, 1), max);
const dias = (v) => lim(v, 30, 365);
// Evento que cierra un trámite y el que lo abre, por rol.
const CIERRE = { emisor: 'emision_credencial', verificador: 'verificacion_credencial' };
const APERTURA = { emisor: 'oferta_credencial', verificador: 'solicitud_verificacion' };
const ESPERA_MIN = { emisor: 30, verificador: 10 };   // pasado este tiempo sin cierre, el trámite se considera «sin respuesta»

export function filtros(rol, q) {
  const p = [rol]; const w = ['rol = $1'];
  const add = (sql, v) => { p.push(v); w.push(sql.replace('$#', '$' + p.length)); };
  if (q.q) {
    p.push('%' + String(q.q).trim() + '%'); const n = '$' + p.length;
    w.push(`(servicio ILIKE ${n} OR evento ILIKE ${n} OR credencial_tipo ILIKE ${n} OR credencial_nombre ILIKE ${n}
             OR sujeto_nombre ILIKE ${n} OR sujeto_documento ILIKE ${n} OR mensaje_error ILIKE ${n} OR codigo_error ILIKE ${n} OR referencia ILIKE ${n})`);
  }
  if (q.servicio) add('servicio = $#', q.servicio);
  if (q.tipo) add('tipo_evento = $#', q.tipo);
  if (q.resultado) add('resultado = $#', q.resultado);
  if (q.tipo_error) add('tipo_error = $#', q.tipo_error);
  if (q.credencial) add('credencial_tipo = $#', q.credencial);
  if (q.desde) add('ocurrido_en >= $#::timestamptz', q.desde);
  if (q.hasta) add('ocurrido_en < ($#::date + 1)', q.hasta);
  return { where: 'WHERE ' + w.join(' AND '), params: p };
}

export async function resumen(db, rol, d) {
  const n = dias(d); const ap = APERTURA[rol]; const ci = CIERRE[rol];
  const [per, srv, sin] = await Promise.all([
    db.query(`SELECT count(*)::int total,
                     count(*) FILTER (WHERE tipo_evento = $3)::int aperturas,
                     count(*) FILTER (WHERE tipo_evento = $2 AND resultado = 'exitoso')::int cierres_exitosos,
                     count(*) FILTER (WHERE tipo_evento = $2 AND resultado = 'fallido')::int cierres_fallidos,
                     count(*) FILTER (WHERE tipo_evento = 'autorizacion_emision' AND resultado = 'fallido')::int autorizaciones_fallidas,
                     count(*) FILTER (WHERE resultado = 'fallido')::int fallidos,
                     count(DISTINCT sujeto_documento)::int personas,
                     coalesce(round(avg(duracion_ms) FILTER (WHERE duracion_ms IS NOT NULL AND tipo_evento = $2))::int, 0) duracion_media_ms
              FROM actividad_servicio WHERE rol = $1 AND ocurrido_en > now() - make_interval(days => $4)`, [rol, ci, ap, n]),
    db.query(`SELECT count(*)::int servicios, count(*) FILTER (WHERE ultima_vez_en > now() - interval '24 hours')::int activos_24h
              FROM servicio WHERE rol = $1`, [rol]),
    // Trámites abiertos hace más de X minutos y que nunca llegaron a su cierre.
    db.query(`SELECT count(*)::int n FROM actividad_servicio a
              WHERE a.rol = $1 AND a.tipo_evento = $2 AND a.ocurrido_en > now() - make_interval(days => $4)
                AND a.ocurrido_en < now() - make_interval(mins => $5) AND a.referencia IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM actividad_servicio b WHERE b.referencia = a.referencia AND b.tipo_evento = $3)`,
      [rol, ap, ci, n, ESPERA_MIN[rol]])]);
  return { dias: n, rol, ...srv.rows[0], periodo: per.rows[0], sin_respuesta: sin.rows[0].n, espera_min: ESPERA_MIN[rol] };
}

export async function series(db, rol, d) {
  const n = dias(d);
  const r = await db.query(
    `SELECT to_char(g.dia, 'YYYY-MM-DD') dia, count(a.*)::int total,
            count(a.*) FILTER (WHERE a.resultado='exitoso')::int exitosas,
            count(a.*) FILTER (WHERE a.resultado='fallido')::int fallidas
     FROM generate_series(current_date - ($1::int - 1), current_date, interval '1 day') g(dia)
     LEFT JOIN actividad_servicio a ON a.rol = $2 AND a.ocurrido_en >= g.dia AND a.ocurrido_en < g.dia + interval '1 day'
     GROUP BY g.dia ORDER BY g.dia`, [n, rol]);
  return r.rows;
}

export async function distribucion(db, rol, d) {
  const n = dias(d); const ci = CIERRE[rol];
  const base = `FROM v_actividad_servicio WHERE rol = $1 AND ocurrido_en > now() - make_interval(days => $2)`;
  const [servicios, eventos, credenciales, errores, codigos, elementos] = await Promise.all([
    db.query(`SELECT servicio nombre, count(*)::int total, count(*) FILTER (WHERE resultado='fallido')::int fallidas ${base} GROUP BY 1 ORDER BY total DESC LIMIT 12`, [rol, n]),
    db.query(`SELECT evento nombre, tipo_evento codigo, count(*)::int total, count(*) FILTER (WHERE resultado='fallido')::int fallidas ${base} GROUP BY 1,2, fase ORDER BY fase, 1`, [rol, n]),
    db.query(`SELECT coalesce(credencial_nombre, credencial_tipo, '(sin dato)') nombre, count(*)::int total, count(*) FILTER (WHERE resultado='fallido')::int fallidas
              ${base} AND tipo_evento = $3 GROUP BY 1 ORDER BY total DESC LIMIT 10`, [rol, n, ci]),
    db.query(`SELECT coalesce(tipo_error,'sin_clasificar') tipo_error, count(*)::int total ${base} AND resultado = 'fallido' GROUP BY 1 ORDER BY total DESC`, [rol, n]),
    db.query(`SELECT coalesce(codigo_error,'(sin código)') codigo_error, tipo_error, count(*)::int total, max(mensaje_error) ejemplo, max(servicio) servicio
              ${base} AND resultado = 'fallido' GROUP BY 1,2 ORDER BY total DESC LIMIT 8`, [rol, n]),
    db.query(`SELECT e.elemento nombre, count(*)::int total
              FROM actividad_servicio_elemento e JOIN actividad_servicio a USING (actividad_servicio_id)
              WHERE a.rol = $1 AND a.ocurrido_en > now() - make_interval(days => $2) AND e.sentido = $3
              GROUP BY 1 ORDER BY total DESC LIMIT 10`, [rol, n, rol === 'verificador' ? 'solicitado' : 'emitido'])]);
  return { servicios: servicios.rows, eventos: eventos.rows, credenciales: credenciales.rows, errores: errores.rows, codigos: codigos.rows, elementos: elementos.rows };
}

/** Un renglón por emisor/verificador, con sus cifras (la tabla «quién es quién»). */
export async function servicios(db, rol, d) {
  const n = dias(d); const ap = APERTURA[rol]; const ci = CIERRE[rol];
  const r = await db.query(
    `SELECT s.servicio_id, s.nombre, s.identificador, s.proyecto, s.primera_vez_en, s.ultima_vez_en,
            count(a.*)::int total,
            count(a.*) FILTER (WHERE a.tipo_evento = $3)::int aperturas,
            count(a.*) FILTER (WHERE a.tipo_evento = $2 AND a.resultado = 'exitoso')::int exitosas,
            count(a.*) FILTER (WHERE a.tipo_evento = $2 AND a.resultado = 'fallido')::int fallidas,
            count(a.*) FILTER (WHERE a.resultado = 'fallido')::int errores,
            count(DISTINCT a.sujeto_documento)::int personas, max(a.ocurrido_en) ultima
     FROM servicio s LEFT JOIN actividad_servicio a ON a.servicio_id = s.servicio_id AND a.ocurrido_en > now() - make_interval(days => $4)
     WHERE s.rol = $1 GROUP BY s.servicio_id ORDER BY total DESC, s.nombre`, [rol, ci, ap, n]);
  return r.rows;
}

const COLS = `actividad_servicio_id, evento_id, ocurrido_en, servicio, servicio_identificador, tipo_evento, evento, resultado, tipo_error, codigo_error,
              mensaje_error, referencia, credencial_tipo, credencial_nombre, sujeto_documento, sujeto_nombre, usuario_id, canal, duracion_ms`;

export async function actividades(db, rol, q, { limite } = {}) {
  const { where, params } = filtros(rol, q);
  const tam = limite ?? lim(q.tamano, 25, 200);
  const pag = Math.max(parseInt(q.pagina, 10) || 1, 1);
  const total = await db.query(`SELECT count(*)::int n FROM v_actividad_servicio ${where}`, params);
  const filas = await db.query(
    `SELECT ${COLS} FROM v_actividad_servicio ${where} ORDER BY ocurrido_en DESC, actividad_servicio_id DESC LIMIT ${tam} OFFSET ${(pag - 1) * tam}`, params);
  return { total: total.rows[0].n, pagina: pag, tamano: tam, filas: filas.rows };
}

/** Un evento, sus datos (solo nombres) y los demás pasos del mismo trámite (misma referencia). */
export async function actividad(db, rol, id) {
  const a = await db.query('SELECT * FROM v_actividad_servicio WHERE actividad_servicio_id = $1 AND rol = $2', [id, rol]);
  if (!a.rowCount) return null;
  const [el, pasos] = await Promise.all([
    db.query('SELECT sentido, tipo_credencial, elemento FROM actividad_servicio_elemento WHERE actividad_servicio_id = $1 ORDER BY 1,2,3', [id]),
    a.rows[0].referencia
      ? db.query(`SELECT actividad_servicio_id, ocurrido_en, evento, tipo_evento, resultado, mensaje_error, duracion_ms
                  FROM v_actividad_servicio WHERE referencia = $1 AND servicio_identificador = $2 ORDER BY ocurrido_en, fase`, [a.rows[0].referencia, a.rows[0].servicio_identificador])
      : { rows: [] }]);
  return { ...a.rows[0], elementos: el.rows, pasos: pasos.rows };
}

export async function catalogos(db, rol) {
  const [srv, tipos, errores, cred] = await Promise.all([
    db.query('SELECT DISTINCT nombre FROM servicio WHERE rol = $1 AND nombre IS NOT NULL ORDER BY 1', [rol]),
    db.query('SELECT codigo, nombre FROM tipo_evento_servicio WHERE rol = $1 ORDER BY fase', [rol]),
    db.query('SELECT DISTINCT tipo_error FROM actividad_servicio WHERE rol = $1 AND tipo_error IS NOT NULL ORDER BY 1', [rol]),
    db.query('SELECT DISTINCT credencial_tipo FROM actividad_servicio WHERE rol = $1 AND credencial_tipo IS NOT NULL ORDER BY 1', [rol])]);
  return { servicios: srv.rows.map((x) => x.nombre), tipos: tipos.rows, tipos_error: errores.rows.map((x) => x.tipo_error), credenciales: cred.rows.map((x) => x.credencial_tipo) };
}

const esc = (v) => (v == null ? '' : /[",\n]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : String(v));
export async function actividadesCsv(db, rol, q) {
  const { filas } = await actividades(db, rol, q, { limite: 5000 });
  const cols = ['ocurrido_en', 'servicio', 'evento', 'resultado', 'credencial_tipo', 'credencial_nombre', 'sujeto_nombre', 'sujeto_documento', 'tipo_error', 'codigo_error', 'mensaje_error', 'canal', 'duracion_ms', 'referencia'];
  return [cols.join(','), ...filas.map((f) => cols.map((c) => esc(f[c] instanceof Date ? f[c].toISOString() : f[c])).join(','))].join('\n');
}

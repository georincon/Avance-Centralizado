// Consultas de solo lectura para el dashboard.

const lim = (v, def, max) => Math.min(Math.max(parseInt(v, 10) || def, 1), max);
const dias = (v) => lim(v, 30, 365);

/** Construye el WHERE de la búsqueda de actividades a partir de los filtros. */
export function filtrosActividad(q) {
  const w = []; const p = [];
  const add = (sql, v) => { p.push(v); w.push(sql.replace('$#', '$' + p.length)); };
  if (q.q) {
    p.push('%' + String(q.q).trim() + '%');
    const n = '$' + p.length;
    w.push(`(nombre_completo ILIKE ${n} OR numero_documento ILIKE ${n} OR contraparte ILIKE ${n} OR contraparte_identificador ILIKE ${n}
             OR actividad ILIKE ${n} OR credencial_tipo ILIKE ${n} OR mensaje_error ILIKE ${n} OR codigo_error ILIKE ${n}
             OR wallet_id::text ILIKE ${n})`);
  }
  if (q.tipo) add('tipo_actividad = $#', q.tipo);
  if (q.categoria) add('categoria = $#', q.categoria);
  if (q.resultado) add('resultado = $#', q.resultado);
  if (q.tipo_error) add('tipo_error = $#', q.tipo_error);
  if (q.contraparte) add('contraparte = $#', q.contraparte);
  if (q.usuario) add('usuario_id = $#', q.usuario);
  if (q.wallet) add('wallet_id = $#', q.wallet);
  if (q.desde) add('ocurrido_en >= $#::timestamptz', q.desde);
  if (q.hasta) add("ocurrido_en < ($#::date + 1)", q.hasta);
  return { where: w.length ? 'WHERE ' + w.join(' AND ') : '', params: p };
}

export async function resumen(db, d) {
  const n = dias(d);
  const [tot, per, hoy] = await Promise.all([
    db.query(`SELECT (SELECT count(*) FROM usuario)::int usuarios,
                     (SELECT count(*) FROM wallet)::int wallets,
                     (SELECT count(*) FROM wallet WHERE ultima_actividad_en > now() - interval '24 hours')::int wallets_activas_24h,
                     (SELECT count(*) FROM credencial WHERE estado = 'vigente')::int credenciales_vigentes,
                     (SELECT count(*) FROM contraparte WHERE tipo = 'emisor')::int emisores,
                     (SELECT count(*) FROM contraparte WHERE tipo = 'verificador')::int verificadores,
                     (SELECT count(*) FROM sesion WHERE fin_en IS NULL AND inicio_en > now() - interval '12 hours')::int sesiones_abiertas`),
    db.query(`SELECT count(*)::int total,
                     count(*) FILTER (WHERE resultado='exitoso')::int exitosas,
                     count(*) FILTER (WHERE resultado='fallido')::int fallidas,
                     count(*) FILTER (WHERE resultado='cancelado')::int canceladas,
                     count(*) FILTER (WHERE tipo_actividad='inicio_sesion' AND resultado='exitoso')::int ingresos,
                     count(*) FILTER (WHERE tipo_actividad='emision_credencial' AND resultado='exitoso')::int credenciales_emitidas,
                     count(*) FILTER (WHERE tipo_actividad='presentacion_credencial' AND resultado='exitoso')::int presentaciones,
                     coalesce(round(avg(duracion_ms) FILTER (WHERE duracion_ms IS NOT NULL))::int, 0) duracion_media_ms
              FROM actividad WHERE ocurrido_en > now() - make_interval(days => $1)`, [n]),
    db.query(`SELECT count(*)::int total FROM actividad WHERE ocurrido_en > now() - interval '24 hours'`)]);
  return { dias: n, ...tot.rows[0], periodo: per.rows[0], actividades_24h: hoy.rows[0].total };
}

export async function series(db, d) {
  const n = dias(d);
  const r = await db.query(
    `SELECT to_char(g.dia, 'YYYY-MM-DD') dia,
            count(a.*)::int total,
            count(a.*) FILTER (WHERE a.resultado='exitoso')::int exitosas,
            count(a.*) FILTER (WHERE a.resultado='fallido')::int fallidas
     FROM generate_series(current_date - ($1::int - 1), current_date, interval '1 day') g(dia)
     LEFT JOIN actividad a ON a.ocurrido_en >= g.dia AND a.ocurrido_en < g.dia + interval '1 day'
     GROUP BY g.dia ORDER BY g.dia`, [n]);
  return r.rows;
}

export async function distribucion(db, d) {
  const n = dias(d);
  const base = `FROM v_extracto WHERE ocurrido_en > now() - make_interval(days => $1)`;
  const [tipos, errores, codigos, contrapartes, horas] = await Promise.all([
    db.query(`SELECT tipo_actividad codigo, actividad nombre, categoria, count(*)::int total,
                     count(*) FILTER (WHERE resultado='fallido')::int fallidas ${base} GROUP BY 1,2,3 ORDER BY total DESC`, [n]),
    db.query(`SELECT coalesce(tipo_error,'sin_clasificar') tipo_error, count(*)::int total ${base} AND resultado <> 'exitoso' GROUP BY 1 ORDER BY total DESC`, [n]),
    db.query(`SELECT coalesce(codigo_error,'(sin código)') codigo_error, tipo_error, count(*)::int total, max(mensaje_error) ejemplo
              ${base} AND resultado = 'fallido' GROUP BY 1,2 ORDER BY total DESC LIMIT 8`, [n]),
    db.query(`SELECT contraparte nombre, contraparte_tipo tipo, count(*)::int total,
                     count(*) FILTER (WHERE resultado='fallido')::int fallidas ${base} AND contraparte IS NOT NULL
              GROUP BY 1,2 ORDER BY total DESC LIMIT 10`, [n]),
    db.query(`SELECT h::int hora, count(a.*)::int total
              FROM generate_series(0,23) h
              LEFT JOIN actividad a ON extract(hour FROM a.ocurrido_en AT TIME ZONE 'America/Bogota') = h
                   AND a.ocurrido_en > now() - make_interval(days => $1)
              GROUP BY h ORDER BY h`, [n])]);
  return { tipos: tipos.rows, errores: errores.rows, codigos: codigos.rows, contrapartes: contrapartes.rows, horas: horas.rows };
}

// Nombre legible de la credencial de una actividad: el que reportó la wallet o, si no, el de la credencial
// guardada o el de otra actividad con el mismo tipo (la solicitud solo trae el identificador).
const NOMBRE_CRED = `COALESCE(NULLIF(v_extracto.detalle->>'nombre', ''),
  (SELECT c.nombre FROM credencial c WHERE c.wallet_id = v_extracto.wallet_id AND c.tipo_credencial = v_extracto.credencial_tipo AND c.nombre <> '' ORDER BY c.recibida_en DESC LIMIT 1),
  (SELECT x.detalle->>'nombre' FROM actividad x WHERE x.credencial_tipo = v_extracto.credencial_tipo AND x.detalle->>'nombre' <> '' ORDER BY x.actividad_id DESC LIMIT 1))`;

export async function actividades(db, q, { limite } = {}) {
  const { where, params } = filtrosActividad(q);
  const tam = limite ?? lim(q.tamano, 25, 200);
  const pag = Math.max(parseInt(q.pagina, 10) || 1, 1);
  const total = await db.query(`SELECT count(*)::int n FROM v_extracto ${where}`, params);
  const filas = await db.query(
    `SELECT actividad_id, evento_id, ocurrido_en, wallet_id, usuario_id, nombre_completo, numero_documento, tipo_actividad,
            actividad, categoria, contraparte_tipo, contraparte, credencial_tipo, resultado, tipo_error, codigo_error,
            mensaje_error, duracion_ms, canal, ${NOMBRE_CRED} AS credencial_nombre
     FROM v_extracto ${where} ORDER BY ocurrido_en DESC, actividad_id DESC LIMIT ${tam} OFFSET ${(pag - 1) * tam}`, params);
  return { total: total.rows[0].n, pagina: pag, tamano: tam, filas: filas.rows };
}

export async function actividad(db, id) {
  const a = await db.query(`SELECT *, ${NOMBRE_CRED} AS credencial_nombre FROM v_extracto WHERE actividad_id = $1`, [id]);
  if (!a.rowCount) return null;
  const el = await db.query(`SELECT sentido, tipo_credencial, elemento FROM actividad_elemento WHERE actividad_id = $1 ORDER BY 1,2,3`, [id]);
  return { ...a.rows[0], elementos: el.rows };
}

export async function usuarios(db, q) {
  const tam = lim(q.tamano, 25, 100); const pag = Math.max(parseInt(q.pagina, 10) || 1, 1);
  const p = []; let w = '';
  if (q.q) {
    p.push('%' + String(q.q).trim() + '%');
    w = `WHERE u.nombre_completo ILIKE $1 OR u.numero_documento ILIKE $1
         OR EXISTS (SELECT 1 FROM medio_contacto m WHERE m.usuario_id = u.usuario_id AND m.valor ILIKE $1)`;
  }
  const total = await db.query(`SELECT count(*)::int n FROM usuario u ${w}`, p);
  const filas = await db.query(
    `SELECT u.usuario_id, u.tipo_documento, u.numero_documento, u.nombre_completo, u.estado, u.creado_en,
            (SELECT valor FROM medio_contacto m WHERE m.usuario_id=u.usuario_id AND m.tipo='correo' ORDER BY principal DESC, registrado_en LIMIT 1) correo,
            (SELECT valor FROM medio_contacto m WHERE m.usuario_id=u.usuario_id AND m.tipo='celular' ORDER BY principal DESC, registrado_en LIMIT 1) celular,
            (SELECT count(*) FROM wallet x WHERE x.usuario_id=u.usuario_id)::int wallets,
            (SELECT count(*) FROM actividad a WHERE a.usuario_id=u.usuario_id)::int actividades,
            (SELECT max(ocurrido_en) FROM actividad a WHERE a.usuario_id=u.usuario_id) ultima_actividad
     FROM usuario u ${w} ORDER BY ultima_actividad DESC NULLS LAST, u.creado_en DESC LIMIT ${tam} OFFSET ${(pag - 1) * tam}`, p);
  return { total: total.rows[0].n, pagina: pag, tamano: tam, filas: filas.rows };
}

export async function usuario(db, id) {
  const u = await db.query('SELECT * FROM usuario WHERE usuario_id = $1', [id]);
  if (!u.rowCount) return null;
  const [contactos, wallets, creds, stats, sesiones, tipos] = await Promise.all([
    db.query('SELECT tipo, valor, verificado, principal, registrado_en FROM medio_contacto WHERE usuario_id=$1 ORDER BY tipo, principal DESC', [id]),
    db.query(`SELECT wallet_id, fabricante, modelo, version_android, version_app, estado, primer_registro_en, ultima_actividad_en
              FROM v_wallet WHERE usuario_id=$1 ORDER BY primer_registro_en`, [id]),
    db.query(`SELECT c.credencial_id, c.tipo_credencial, c.nombre, c.estado, c.recibida_en, c.eliminada_en, p.nombre emisor
              FROM credencial c JOIN wallet w USING (wallet_id) LEFT JOIN contraparte p ON p.contraparte_id = c.emisor_id
              WHERE w.usuario_id=$1 ORDER BY c.recibida_en DESC`, [id]),
    db.query(`SELECT count(*)::int total, count(*) FILTER (WHERE resultado='exitoso')::int exitosas,
                     count(*) FILTER (WHERE resultado='fallido')::int fallidas,
                     count(*) FILTER (WHERE tipo_actividad='inicio_sesion' AND resultado='exitoso')::int ingresos,
                     count(*) FILTER (WHERE tipo_actividad='presentacion_credencial' AND resultado='exitoso')::int presentaciones,
                     count(*) FILTER (WHERE tipo_actividad='emision_credencial' AND resultado='exitoso')::int credenciales_recibidas,
                     min(ocurrido_en) primera, max(ocurrido_en) ultima FROM actividad WHERE usuario_id=$1`, [id]),
    db.query(`SELECT sesion_id, wallet_id, metodo_autenticacion, inicio_en, fin_en, motivo_cierre,
                     extract(epoch FROM (coalesce(fin_en, now()) - inicio_en))::int segundos
              FROM sesion WHERE usuario_id=$1 ORDER BY inicio_en DESC LIMIT 15`, [id]),
    db.query(`SELECT actividad nombre, count(*)::int total FROM v_extracto WHERE usuario_id=$1 GROUP BY 1 ORDER BY 2 DESC`, [id])]);
  return { usuario: u.rows[0], contactos: contactos.rows, wallets: wallets.rows, credenciales: creds.rows,
           estadisticas: stats.rows[0], sesiones: sesiones.rows, por_tipo: tipos.rows };
}

export async function wallets(db, q) {
  const tam = lim(q.tamano, 25, 100); const pag = Math.max(parseInt(q.pagina, 10) || 1, 1);
  const p = []; const w = [];
  if (q.q) { p.push('%' + String(q.q).trim() + '%'); w.push(`(nombre_completo ILIKE $1 OR numero_documento ILIKE $1 OR modelo ILIKE $1 OR fabricante ILIKE $1 OR wallet_id::text ILIKE $1 OR identificador_android ILIKE $1)`); }
  if (q.estado) { p.push(q.estado); w.push(`estado = $${p.length}`); }
  const where = w.length ? 'WHERE ' + w.join(' AND ') : '';
  const total = await db.query(`SELECT count(*)::int n FROM v_wallet ${where}`, p);
  const filas = await db.query(`SELECT * FROM v_wallet ${where} ORDER BY ultima_actividad_en DESC LIMIT ${tam} OFFSET ${(pag - 1) * tam}`, p);
  return { total: total.rows[0].n, pagina: pag, tamano: tam, filas: filas.rows };
}

export async function wallet(db, id) {
  const w = await db.query('SELECT * FROM v_wallet WHERE wallet_id = $1', [id]);
  if (!w.rowCount) return null;
  const [creds, ses] = await Promise.all([
    db.query(`SELECT c.tipo_credencial, c.nombre, c.estado, c.recibida_en, c.eliminada_en, p.nombre emisor
              FROM credencial c LEFT JOIN contraparte p ON p.contraparte_id = c.emisor_id WHERE c.wallet_id=$1 ORDER BY c.recibida_en DESC`, [id]),
    db.query(`SELECT metodo_autenticacion, inicio_en, fin_en, motivo_cierre, extract(epoch FROM (coalesce(fin_en, now()) - inicio_en))::int segundos
              FROM sesion WHERE wallet_id=$1 ORDER BY inicio_en DESC LIMIT 10`, [id])]);
  return { wallet: w.rows[0], credenciales: creds.rows, sesiones: ses.rows };
}

export async function contrapartes(db) {
  const r = await db.query(
    `SELECT c.contraparte_id, c.tipo, c.nombre, c.identificador, c.primera_vez_en,
            count(a.*)::int total, count(a.*) FILTER (WHERE a.resultado='exitoso')::int exitosas,
            count(a.*) FILTER (WHERE a.resultado='fallido')::int fallidas,
            count(DISTINCT a.usuario_id)::int usuarios, max(a.ocurrido_en) ultima
     FROM contraparte c LEFT JOIN actividad a USING (contraparte_id)
     GROUP BY c.contraparte_id ORDER BY total DESC`);
  return r.rows;
}

export async function catalogos(db) {
  const [tipos, contra, errores] = await Promise.all([
    db.query('SELECT codigo, nombre, categoria FROM tipo_actividad ORDER BY categoria, nombre'),
    db.query('SELECT DISTINCT nombre, tipo FROM contraparte WHERE nombre IS NOT NULL ORDER BY nombre'),
    db.query('SELECT DISTINCT tipo_error FROM actividad WHERE tipo_error IS NOT NULL ORDER BY 1')]);
  return { tipos: tipos.rows, contrapartes: contra.rows, tipos_error: errores.rows.map((x) => x.tipo_error) };
}

const esc = (v) => (v == null ? '' : /[",\n]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : String(v));
export async function actividadesCsv(db, q) {
  const { filas } = await actividades(db, q, { limite: 5000 });
  const cols = ['ocurrido_en', 'nombre_completo', 'numero_documento', 'actividad', 'contraparte', 'credencial_tipo', 'resultado', 'tipo_error', 'codigo_error', 'mensaje_error', 'duracion_ms', 'canal', 'wallet_id'];
  return [cols.join(','), ...filas.map((f) => cols.map((c) => esc(f[c] instanceof Date ? f[c].toISOString() : f[c])).join(','))].join('\n');
}

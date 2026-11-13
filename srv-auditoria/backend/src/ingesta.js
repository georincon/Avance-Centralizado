// Ingesta: recibe lo que reporta cada wallet y lo deja en las tablas del esquema `auditoria`.
// La actividad es idempotente por `evento_id`: la app puede reintentar sin duplicar.

const RESULTADOS = ['exitoso', 'fallido', 'cancelado', 'en_proceso'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export class ErrorValidacion extends Error {}

const texto = (v, max = 500) => (v == null || v === '' ? null : String(v).slice(0, max));
const fecha = (v) => (v && /^\d{4}-\d{2}-\d{2}$/.test(String(v)) ? String(v) : null);
const entero = (v) => (Number.isFinite(Number(v)) && v !== null && v !== '' ? Math.trunc(Number(v)) : null);

export function validar(e) {
  if (!e || typeof e !== 'object') throw new ErrorValidacion('cuerpo vacío');
  if (!UUID.test(e.evento_id || '')) throw new ErrorValidacion('evento_id debe ser un UUID');
  if (!UUID.test(e.wallet?.wallet_id || '')) throw new ErrorValidacion('wallet.wallet_id debe ser un UUID');
  const a = e.actividad;
  if (!a || !a.tipo) throw new ErrorValidacion('actividad.tipo es obligatorio');
  if (!RESULTADOS.includes(a.resultado)) throw new ErrorValidacion(`actividad.resultado debe ser uno de ${RESULTADOS.join(', ')}`);
  const t = Date.parse(a.ocurrido_en);
  if (Number.isNaN(t)) throw new ErrorValidacion('actividad.ocurrido_en debe ser una fecha ISO-8601');
  if (e.sesion?.sesion_id && !UUID.test(e.sesion.sesion_id)) throw new ErrorValidacion('sesion.sesion_id debe ser un UUID');
}

async function upsertUsuario(c, u) {
  if (!u?.numero_documento || !u?.nombres) return null;
  const r = await c.query(
    `INSERT INTO usuario (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, lugar_nacimiento,
        genero, grupo_sanguineo, estatura_cm, fecha_expedicion, lugar_expedicion, validacion_decision, validacion_puntaje_rostro)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
     ON CONFLICT (tipo_documento, numero_documento) DO UPDATE SET
        nombres = EXCLUDED.nombres, apellidos = EXCLUDED.apellidos,
        fecha_nacimiento = COALESCE(EXCLUDED.fecha_nacimiento, usuario.fecha_nacimiento),
        lugar_nacimiento = COALESCE(EXCLUDED.lugar_nacimiento, usuario.lugar_nacimiento),
        genero = COALESCE(EXCLUDED.genero, usuario.genero),
        grupo_sanguineo = COALESCE(EXCLUDED.grupo_sanguineo, usuario.grupo_sanguineo),
        estatura_cm = COALESCE(EXCLUDED.estatura_cm, usuario.estatura_cm),
        fecha_expedicion = COALESCE(EXCLUDED.fecha_expedicion, usuario.fecha_expedicion),
        lugar_expedicion = COALESCE(EXCLUDED.lugar_expedicion, usuario.lugar_expedicion),
        validacion_decision = COALESCE(EXCLUDED.validacion_decision, usuario.validacion_decision),
        validacion_puntaje_rostro = COALESCE(EXCLUDED.validacion_puntaje_rostro, usuario.validacion_puntaje_rostro),
        actualizado_en = now()
     RETURNING usuario_id`,
    [texto(u.tipo_documento, 10) || 'CC', texto(u.numero_documento, 30), texto(u.nombres, 120), texto(u.apellidos, 120) || '',
     fecha(u.fecha_nacimiento), texto(u.lugar_nacimiento, 120), texto(u.genero, 20), texto(u.grupo_sanguineo, 10),
     entero(u.estatura_cm), fecha(u.fecha_expedicion), texto(u.lugar_expedicion, 120),
     texto(u.validacion_decision, 30), u.validacion_puntaje_rostro != null ? Number(u.validacion_puntaje_rostro) : null]);
  return r.rows[0].usuario_id;
}

async function upsertDispositivo(c, w) {
  const d = w.dispositivo || w;   // tolera el formato antiguo (campos sueltos en wallet)
  if (!d.modelo && !d.fabricante && !d.identificador_android) return;
  await c.query(
    `INSERT INTO dispositivo (wallet_id, identificador_android, fabricante, marca, modelo, producto, hardware, version_android, nivel_api,
        parche_seguridad, version_app, codigo_version_app, arquitectura, memoria_ram_mb, pantalla, idioma, zona_horaria, biometria_disponible, bloqueo_pantalla)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19)
     ON CONFLICT (wallet_id) DO UPDATE SET
        identificador_android = COALESCE(EXCLUDED.identificador_android, dispositivo.identificador_android),
        fabricante = COALESCE(EXCLUDED.fabricante, dispositivo.fabricante), marca = COALESCE(EXCLUDED.marca, dispositivo.marca),
        modelo = COALESCE(EXCLUDED.modelo, dispositivo.modelo), producto = COALESCE(EXCLUDED.producto, dispositivo.producto),
        hardware = COALESCE(EXCLUDED.hardware, dispositivo.hardware),
        version_android = COALESCE(EXCLUDED.version_android, dispositivo.version_android), nivel_api = COALESCE(EXCLUDED.nivel_api, dispositivo.nivel_api),
        parche_seguridad = COALESCE(EXCLUDED.parche_seguridad, dispositivo.parche_seguridad),
        version_app = COALESCE(EXCLUDED.version_app, dispositivo.version_app),
        codigo_version_app = COALESCE(EXCLUDED.codigo_version_app, dispositivo.codigo_version_app),
        arquitectura = COALESCE(EXCLUDED.arquitectura, dispositivo.arquitectura), memoria_ram_mb = COALESCE(EXCLUDED.memoria_ram_mb, dispositivo.memoria_ram_mb),
        pantalla = COALESCE(EXCLUDED.pantalla, dispositivo.pantalla), idioma = COALESCE(EXCLUDED.idioma, dispositivo.idioma),
        zona_horaria = COALESCE(EXCLUDED.zona_horaria, dispositivo.zona_horaria),
        biometria_disponible = COALESCE(EXCLUDED.biometria_disponible, dispositivo.biometria_disponible),
        bloqueo_pantalla = COALESCE(EXCLUDED.bloqueo_pantalla, dispositivo.bloqueo_pantalla),
        actualizado_en = now()`,
    [w.wallet_id, texto(d.identificador_android, 64), texto(d.fabricante, 60), texto(d.marca, 60), texto(d.modelo, 80), texto(d.producto, 80),
     texto(d.hardware, 60), texto(d.version_android, 20), entero(d.nivel_api), texto(d.parche_seguridad, 20), texto(d.version_app, 40),
     entero(d.codigo_version_app), texto(d.arquitectura, 40), entero(d.memoria_ram_mb), texto(d.pantalla, 40), texto(d.idioma, 20),
     texto(d.zona_horaria, 60), typeof d.biometria_disponible === 'boolean' ? d.biometria_disponible : null,
     typeof d.bloqueo_pantalla === 'boolean' ? d.bloqueo_pantalla : null]);
}

async function upsertContraparte(c, p) {
  if (!p?.identificador) return null;
  const tipo = ['emisor', 'verificador'].includes(p.tipo) ? p.tipo : 'desconocida';
  const r = await c.query(
    `INSERT INTO contraparte (tipo, identificador, nombre) VALUES ($1,$2,$3)
     ON CONFLICT (tipo, identificador) DO UPDATE SET nombre = COALESCE(EXCLUDED.nombre, contraparte.nombre)
     RETURNING contraparte_id`, [tipo, texto(p.identificador, 300), texto(p.nombre, 200)]);
  return r.rows[0].contraparte_id;
}

/** Procesa un evento completo dentro de una transacción. Devuelve { duplicado }. */
export async function registrarEvento(pool, e, ip) {
  validar(e);
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const a = e.actividad;
    const w = e.wallet;

    const usuarioId = await upsertUsuario(c, e.usuario);
    await c.query(
      `INSERT INTO wallet (wallet_id, usuario_id) VALUES ($1,$2)
       ON CONFLICT (wallet_id) DO UPDATE SET usuario_id = COALESCE(EXCLUDED.usuario_id, wallet.usuario_id), ultima_actividad_en = now()`,
      [w.wallet_id, usuarioId]);
    await upsertDispositivo(c, w);
    if (usuarioId && e.actividad.tipo === 'registro_usuario' && e.actividad.resultado === 'exitoso') {
      // Alta automática: la wallet queda enlazada a su usuario desde que termina el onboarding.
      await c.query(`UPDATE wallet SET alta_completa = true, alta_en = COALESCE(alta_en, $2) WHERE wallet_id = $1`, [w.wallet_id, e.actividad.ocurrido_en]);
    }
    const fila = (await c.query('SELECT usuario_id FROM wallet WHERE wallet_id = $1', [w.wallet_id])).rows[0];
    const usuarioEfectivo = usuarioId || fila.usuario_id;

    for (const k of Array.isArray(e.contactos) ? e.contactos : []) {
      if (!['celular', 'correo'].includes(k?.tipo) || !k.valor || !usuarioEfectivo) continue;
      await c.query(
        `INSERT INTO medio_contacto (usuario_id, tipo, valor, verificado) VALUES ($1,$2,$3,$4)
         ON CONFLICT (usuario_id, tipo, valor) DO UPDATE SET verificado = medio_contacto.verificado OR EXCLUDED.verificado`,
        [usuarioEfectivo, k.tipo, texto(k.valor, 200), !!k.verificado]);
    }

    let sesionId = null;
    if (e.sesion?.sesion_id) {
      sesionId = e.sesion.sesion_id;
      await c.query(
        `INSERT INTO sesion (sesion_id, wallet_id, usuario_id, metodo_autenticacion, inicio_en, direccion_ip)
         VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (sesion_id) DO NOTHING`,
        [sesionId, w.wallet_id, usuarioEfectivo, texto(e.sesion.metodo_autenticacion, 30), a.ocurrido_en, ip]);
      if (a.tipo === 'cierre_sesion') {
        await c.query(`UPDATE sesion SET fin_en = $2, motivo_cierre = $3 WHERE sesion_id = $1 AND fin_en IS NULL`,
          [sesionId, a.ocurrido_en, texto(a.detalle?.motivo, 40) || 'usuario']);
      }
    }

    const contraparteId = await upsertContraparte(c, a.contraparte);
    const ins = await c.query(
      `INSERT INTO actividad (evento_id, wallet_id, usuario_id, sesion_id, tipo_actividad, contraparte_id, credencial_tipo,
          ocurrido_en, resultado, tipo_error, codigo_error, mensaje_error, duracion_ms, canal, direccion_ip, detalle)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)
       ON CONFLICT (evento_id) DO NOTHING RETURNING actividad_id`,
      [e.evento_id, w.wallet_id, usuarioEfectivo, sesionId, texto(a.tipo, 60), contraparteId, texto(a.credencial_tipo, 200),
       a.ocurrido_en, a.resultado, a.resultado === 'exitoso' ? null : texto(a.tipo_error, 40),
       texto(a.codigo_error, 80), texto(a.mensaje_error, 1000), entero(a.duracion_ms), texto(a.canal, 20), ip,
       JSON.stringify(a.detalle && typeof a.detalle === 'object' ? a.detalle : {})]);

    if (!ins.rowCount) { await c.query('ROLLBACK'); return { duplicado: true }; }
    const actividadId = ins.rows[0].actividad_id;

    for (const el of Array.isArray(a.elementos) ? a.elementos : []) {
      if (!['compartido', 'recibido'].includes(el?.sentido) || !el.elemento) continue;
      await c.query(
        `INSERT INTO actividad_elemento (actividad_id, sentido, tipo_credencial, elemento) VALUES ($1,$2,$3,$4)
         ON CONFLICT DO NOTHING`, [actividadId, el.sentido, texto(el.tipo_credencial, 200) || 'desconocida', texto(el.elemento, 200)]);
    }

    if (a.tipo === 'emision_credencial' && a.resultado === 'exitoso' && a.credencial_tipo) {
      await c.query(`INSERT INTO credencial (wallet_id, tipo_credencial, nombre, emisor_id, recibida_en) VALUES ($1,$2,$3,$4,$5)`,
        [w.wallet_id, a.credencial_tipo, texto(a.detalle?.nombre, 200), contraparteId, a.ocurrido_en]);
    } else if (a.tipo === 'eliminacion_credencial' && a.resultado === 'exitoso' && a.credencial_tipo) {
      await c.query(
        `UPDATE credencial SET estado = 'eliminada', eliminada_en = $3
          WHERE credencial_id = (SELECT credencial_id FROM credencial WHERE wallet_id = $1 AND tipo_credencial = $2
                                  AND estado = 'vigente' ORDER BY recibida_en LIMIT 1)`,
        [w.wallet_id, a.credencial_tipo, a.ocurrido_en]);
    }
    await c.query('COMMIT');
    return { duplicado: false, actividad_id: Number(actividadId) };
  } catch (err) {
    await c.query('ROLLBACK').catch(() => {});
    throw err;
  } finally {
    c.release();
  }
}

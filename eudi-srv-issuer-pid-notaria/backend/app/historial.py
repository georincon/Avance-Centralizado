"""Historial de emisiones: consola de SOLO LECTURA sobre las tablas locales del emisor de la Notaría.

Pública (tras el haproxy, que quita /notaria-issuer/api): /notaria-issuer/api/historial/  ·  /historial/orden/<id>
Restringida por IP en el proxy único (nw-nginx, mapa $es_admin). Solo ejecuta SELECT, cada uno dentro de una
transacción READ ONLY. Muestra datos personales (cédula/sujeto) porque es una consola de administración.
No muestra tx_code ni la URI de oferta (son secretos vivos).
"""
import json
import logging
import uuid
from html import escape

from flask import Blueprint, request, Response

from app import db_status_persistence as db

historial = Blueprint("historial", __name__, url_prefix="/historial")
logger = logging.getLogger(__name__)


@historial.get("/ping")
def ping():
    """Respuesta mínima: el menú del sitio la usa para mostrar «Historial» solo a quien puede abrirlo (el proxy responde 403 fuera de las IPs de administración)."""
    return Response(status=204, headers={"Cache-Control": "no-store"})

TAM_PAGINA = 25
# Estado informado: el guardado ajustado por vencimientos (misma regla que ordenes._estado, sin consultar sesiones).
ESTADO_SQL = ("CASE WHEN estado IN ('EMITIDA','CANCELADA') THEN estado WHEN now() >= expira_en THEN 'EXPIRADA' "
              "WHEN estado='OFERTA_GENERADA' AND oferta_expira_en <= now() THEN 'OFERTA_EXPIRADA' ELSE estado END")
CON_ERROR_SQL = ("EXISTS (SELECT 1 FROM notaria_orden_evento ev WHERE ev.orden_id = o.id "
                 "AND (ev.evento ILIKE '%%ERROR%%' OR ev.evento ILIKE '%%FALL%%'))")

CSS = """
:root{--azul:#0b2545;--azul2:#13315c;--gris:#eef2f7;--borde:#cdd6e2;--txt:#1b2733;--mal:#9b1c1c}
*{box-sizing:border-box}body{margin:0;font:14px/1.45 system-ui,Segoe UI,Arial,sans-serif;color:var(--txt);background:#f7f9fc}
header{background:var(--azul);color:#fff;padding:14px 24px}header h1{margin:0;font-size:20px}header small{opacity:.75}
main{padding:20px 24px;max-width:1280px;margin:auto}
.tarjetas{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:16px}
.t{background:#fff;border:1px solid var(--borde);border-left:4px solid var(--azul2);border-radius:4px;padding:8px 14px;min-width:120px}
.t b{display:block;font-size:22px;color:var(--azul)}.t span{font-size:12px;color:#51607a}.t.mal{border-left-color:var(--mal)}.t.mal b{color:var(--mal)}
h2{font-size:15px;color:var(--azul);margin:18px 0 6px}
form.f{background:#fff;border:1px solid var(--borde);border-radius:4px;padding:10px;display:flex;flex-wrap:wrap;gap:10px;align-items:end;margin-bottom:14px}
form.f label{display:flex;flex-direction:column;font-size:12px;color:#51607a;gap:2px}
input,select{padding:5px 7px;border:1px solid var(--borde);border-radius:3px;font:inherit}
button,.btn{background:var(--azul);color:#fff;border:0;border-radius:3px;padding:7px 14px;cursor:pointer;text-decoration:none;font:inherit}
table{border-collapse:collapse;width:100%;background:#fff;border:1px solid var(--borde)}
th{background:var(--azul2);color:#fff;text-align:left;padding:6px 8px;font-weight:600}
td{padding:6px 8px;border-top:1px solid var(--borde);vertical-align:top}tr:nth-child(even) td{background:var(--gris)}
a{color:var(--azul2)}code,pre{font:12px ui-monospace,Menlo,monospace}pre{background:var(--gris);padding:8px;overflow:auto;border-radius:3px;margin:0;max-height:320px}
.e{display:inline-block;padding:1px 8px;border-radius:10px;background:var(--gris);border:1px solid var(--borde);font-size:12px}
.e.EMITIDA{background:#dff3e4}.e.CANCELADA,.e.EXPIRADA,.e.OFERTA_EXPIRADA{background:#f6e3e3}
.pie{display:flex;gap:12px;align-items:center;margin:12px 0}.vacio{padding:20px;text-align:center;color:#51607a}
dl{display:grid;grid-template-columns:200px 1fr;gap:4px 12px;background:#fff;border:1px solid var(--borde);padding:12px;margin:0}dt{color:#51607a}dd{margin:0;word-break:break-all}
"""


def _base():
    """Prefijo público de esta app (lo pone el haproxy en X-Forwarded-Prefix) + ruta propia."""
    return request.headers.get("X-Forwarded-Prefix", "").rstrip("/") + "/historial"


def _consulta(sql, params=(), uno=False):
    """Un SELECT en transacción de solo lectura; nunca confirma cambios."""
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute("SET TRANSACTION READ ONLY")
        cur.execute(sql, params)
        filas = cur.fetchone() if uno else cur.fetchall()
        conn.rollback()
        return filas


def _pagina(titulo, cuerpo, estado=200):
    html = (f"<!doctype html><html lang='es'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            f"<meta name='robots' content='noindex'><title>{escape(titulo)}</title><style>{CSS}</style></head><body>"
            f"<header><h1>Historial de emisiones</h1><small>Emisor de la Notaría &middot; consola de administración &middot; solo lectura</small></header>"
            f"<main>{cuerpo}</main></body></html>")
    return Response(html, status=estado, mimetype="text/html", headers={"Cache-Control": "no-store", "X-Robots-Tag": "noindex"})


def _fecha(t):
    return t.strftime("%Y-%m-%d %H:%M:%S UTC") if t else "—"


def _json(v):
    return escape(json.dumps(v, ensure_ascii=False, indent=2, default=str))


def _sel(nombre, etiqueta, opciones, actual):
    o = "<option value=''>Todos</option>" + "".join(
        f"<option value='{escape(x)}'{' selected' if x == actual else ''}>{escape(x)}</option>" for x in opciones)
    return f"<label>{etiqueta}<select name='{nombre}'>{o}</select></label>"


@historial.get("/")
def lista():
    a = request.args
    estado, doc, q = a.get("estado", "").strip(), a.get("documento", "").strip(), a.get("q", "").strip()
    desde, hasta = a.get("desde", "").strip(), a.get("hasta", "").strip()
    solo_error = a.get("error") == "1"
    try:
        pag = max(1, int(a.get("p", "1")))
    except ValueError:
        pag = 1

    donde, par = [], []
    if estado:
        donde.append("est = %s"); par.append(estado)
    if doc:
        donde.append("documento = %s"); par.append(doc)
    if desde:
        donde.append("creada_en >= %s::date"); par.append(desde)
    if hasta:
        donde.append("creada_en < (%s::date + 1)"); par.append(hasta)
    if q:
        donde.append("(referencia ILIKE %s OR cliente ILIKE %s OR id::text ILIKE %s OR datos::text ILIKE %s OR session_id ILIKE %s)")
        par += [f"%{q}%"] * 5
    if solo_error:
        donde.append("con_error")
    where = ("WHERE " + " AND ".join(donde)) if donde else ""
    base = (f"(SELECT o.id, o.cliente, o.referencia, o.documento, o.datos, o.session_id, o.creada_en, o.emitida_en, "
            f"o.expira_en, {ESTADO_SQL} AS est, {CON_ERROR_SQL} AS con_error FROM notaria_orden_emision o) x")
    try:
        total_f = _consulta(f"SELECT count(*) FROM {base} {where}", par, uno=True)[0]
        filas = _consulta(
            f"SELECT id, cliente, referencia, documento, est, creada_en, emitida_en, datos, con_error FROM {base} {where} "
            f"ORDER BY creada_en DESC LIMIT %s OFFSET %s", par + [TAM_PAGINA, (pag - 1) * TAM_PAGINA])
        tot, = _consulta("SELECT count(*) FROM notaria_orden_emision", uno=True)
        por_estado = _consulta(f"SELECT est, count(*) FROM {base} GROUP BY est ORDER BY 2 DESC")
        por_tipo = _consulta("SELECT documento, count(*) FROM notaria_orden_emision GROUP BY 1 ORDER BY 2 DESC")
        n_err, = _consulta(f"SELECT count(*) FROM {base} WHERE con_error", uno=True)
        claves, = _consulta("SELECT count(*) FROM issued_key_status", uno=True)
    except Exception:
        logger.exception("historial: error consultando")
        return _pagina("Historial", "<p class='vacio'>No se pudo consultar la base de datos.</p>", 500)

    try:
        ev_ofertas, ev_ok, ev_mal = _consulta(
            "SELECT count(*) FILTER (WHERE tipo='oferta_credencial'), "
            "count(*) FILTER (WHERE tipo='emision_credencial' AND resultado='exitoso'), "
            "count(*) FILTER (WHERE tipo='emision_credencial' AND resultado<>'exitoso') FROM notaria_auditoria_evento", uno=True)
        eventos = _consulta(
            "SELECT ocurrido_en, tipo, resultado, credencial_tipo, sujeto_documento, sujeto_nombre, detalle, codigo_error, mensaje_error, referencia "
            "FROM notaria_auditoria_evento WHERE tipo IN ('oferta_credencial','emision_credencial') ORDER BY id DESC LIMIT 50")
    except Exception:
        ev_ofertas = ev_ok = ev_mal = 0
        eventos = []
    NOMBRES_EV = {"oferta_credencial": "Oferta de credencial", "emision_credencial": "Emisión de credencial"}
    trs_ev = ""
    for t0, tipo, res, cred, doc_, nom, det, cod, msg, ref in eventos:
        rel = (det or {}).get("relacion") if isinstance(det, dict) else None
        persona = (escape(nom) + "<br>" if nom else "") + (f"CC {escape(doc_)}" if doc_ else "—")
        trs_ev += (f"<tr><td>{_fecha(t0)}</td><td>{NOMBRES_EV.get(tipo, escape(tipo))}</td><td>{escape(cred or '—')}</td><td>{persona}</td>"
                   f"<td>{escape(rel) if rel else '—'}</td><td><span class='e {escape(res)}'>{escape(res)}</span></td>"
                   f"<td>{escape((cod or '') + ' ' + (msg or '')).strip() or '—'}</td><td><code>{escape((ref or '')[:8])}</code></td></tr>")
    tabla_ev = ("<table><tr><th>Fecha</th><th>Evento</th><th>Credencial</th><th>Persona</th><th>Relación</th><th>Resultado</th><th>Error</th><th>Trámite</th></tr>"
                + trs_ev + "</table>") if trs_ev else "<p class='vacio'>Todavía no hay emisiones registradas.</p>"
    seccion_ev = ("<h2>Emisiones registradas (sitio web y API de órdenes)</h2>"
                  "<p class='vacio' style='text-align:left'>Cada oferta y cada emisión de credencial, con la persona atendida. "
                  "Los pasos de autorización de la wallet se ven en el panel de auditoría central.</p>"
                  f"<div class='tarjetas'><div class='t'><b>{ev_ofertas}</b><span>Ofertas generadas</span></div>"
                  f"<div class='t'><b>{ev_ok}</b><span>Credenciales emitidas</span></div>"
                  f"<div class='t mal'><b>{ev_mal}</b><span>Emisiones fallidas</span></div></div>" + tabla_ev)

    tarjetas = [f"<div class='t'><b>{tot}</b><span>Total de órdenes (API)</span></div>"]
    tarjetas += [f"<div class='t'><b>{n}</b><span>{escape(e)}</span></div>" for e, n in por_estado]
    tarjetas += [f"<div class='t mal'><b>{n_err}</b><span>Con error (evento ERROR/FALLA)</span></div>",
                 f"<div class='t'><b>{claves}</b><span>Claves emitidas (issued_key_status)</span></div>"]
    tipos = [f"<div class='t'><b>{n}</b><span>{escape(d)}</span></div>" for d, n in por_tipo]

    estados = ["CREADA", "OFERTA_GENERADA", "OFERTA_EXPIRADA", "EMITIDA", "CANCELADA", "EXPIRADA"]
    form = ("<form class='f' method='get'>" + _sel("estado", "Estado", estados, estado)
            + _sel("documento", "Tipo de documento", [d for d, _ in por_tipo], doc)
            + f"<label>Desde<input type='date' name='desde' value='{escape(desde)}'></label>"
              f"<label>Hasta<input type='date' name='hasta' value='{escape(hasta)}'></label>"
              f"<label>Búsqueda (cédula, referencia, cliente, id…)<input name='q' size='28' value='{escape(q)}'></label>"
              f"<label><span>&nbsp;</span><span><input type='checkbox' name='error' value='1'{' checked' if solo_error else ''}> Solo con error</span></label>"
              f"<button>Filtrar</button> <a class='btn' href='{_base()}/'>Limpiar</a></form>")

    if filas:
        trs = ""
        for id_, cli, ref, d, est, creada, emitida, datos, err in filas:
            trs += (f"<tr><td>{_fecha(creada)}</td><td><a href='{_base()}/orden/{id_}'>{escape(ref)}</a></td><td>{escape(cli)}</td>"
                    f"<td>{escape(d)}</td><td><span class='e {est}'>{est}</span>{' <b style=color:#9b1c1c>error</b>' if err else ''}</td>"
                    f"<td>{_fecha(emitida)}</td><td><code>{escape(json.dumps(datos, ensure_ascii=False)[:90])}</code></td></tr>")
        tabla = ("<table><tr><th>Creada</th><th>Referencia</th><th>Cliente</th><th>Documento</th><th>Estado</th><th>Emitida</th><th>Datos</th></tr>"
                 + trs + "</table>")
    else:
        tabla = "<p class='vacio'>Sin resultados para los filtros indicados.</p>"

    paginas = max(1, -(-total_f // TAM_PAGINA))
    qs = {k: v for k, v in a.items() if k != "p" and v}
    from urllib.parse import urlencode
    def enlace(n, txt):
        return f"<a class='btn' href='{_base()}/?{urlencode({**qs, 'p': n})}'>{txt}</a>"
    pie = (f"<div class='pie'>{enlace(pag - 1, '&laquo; Anterior') if pag > 1 else ''}"
           f"<span>Página {pag} de {paginas} &middot; {total_f} resultado(s)</span>"
           f"{enlace(pag + 1, 'Siguiente &raquo;') if pag < paginas else ''}</div>")
    cuerpo = (seccion_ev + f"<div class='tarjetas'>{''.join(tarjetas)}</div><h2>Por tipo de documento</h2><div class='tarjetas'>{''.join(tipos) or '—'}</div>"
              f"<h2>Órdenes</h2>{form}{tabla}{pie}")
    return _pagina("Historial de emisiones", cuerpo)


@historial.get("/orden/<orden_id>")
def detalle(orden_id):
    try:
        uuid.UUID(orden_id)
    except ValueError:
        return _pagina("No encontrada", f"<p class='vacio'>Identificador no válido. <a href='{_base()}/'>Volver</a></p>", 404)
    try:
        o = _consulta(
            "SELECT id, cliente, referencia, documento, datos, estado, session_id, oferta_generada_en, oferta_expira_en, "
            f"emitida_en, creada_en, expira_en, actualizada_en, {ESTADO_SQL} FROM notaria_orden_emision o WHERE id=%s", (orden_id,), uno=True)
        if not o:
            return _pagina("No encontrada", f"<p class='vacio'>Orden no encontrada. <a href='{_base()}/'>Volver</a></p>", 404)
        eventos = _consulta("SELECT id, evento, detalle, creado_en FROM notaria_orden_evento WHERE orden_id=%s ORDER BY id", (orden_id,))
        claves = _consulta("SELECT created_at, device_key IS NOT NULL FROM issued_key_status WHERE session_id::text=%s ORDER BY id", (o[6],)) if o[6] else []
    except Exception:
        logger.exception("historial: error consultando detalle")
        return _pagina("Historial", "<p class='vacio'>No se pudo consultar la base de datos.</p>", 500)

    campos = [("Id", o[0]), ("Referencia", o[2]), ("Cliente", o[1]), ("Documento", o[3]), ("Estado informado", o[13]),
              ("Estado guardado", o[5]), ("Sesión del emisor", o[6] or "—"), ("Creada", _fecha(o[10])),
              ("Oferta generada", _fecha(o[7])), ("Oferta expira", _fecha(o[8])), ("Emitida", _fecha(o[9])),
              ("Orden expira", _fecha(o[11])), ("Actualizada", _fecha(o[12])), ("Claves emitidas (sesión)", len(claves))]
    dl = "".join(f"<dt>{escape(k)}</dt><dd>{escape(str(v))}</dd>" for k, v in campos)
    evs = "".join(f"<tr><td>{e_id}</td><td>{_fecha(t)}</td><td>{escape(e)}</td><td><pre>{_json(d)}</pre></td></tr>" for e_id, e, d, t in eventos) \
        or "<tr><td colspan='4' class='vacio'>Sin eventos</td></tr>"
    cuerpo = (f"<p><a href='{_base()}/'>&laquo; Volver al listado</a></p><h2>Orden</h2><dl>{dl}</dl>"
              f"<h2>Datos del documento (sujeto)</h2><pre>{_json(o[4])}</pre>"
              f"<h2>Eventos</h2><table><tr><th>#</th><th>Fecha</th><th>Evento</th><th>Detalle</th></tr>{evs}</table>"
              "<p style='color:#51607a'>El código de transacción y la URI de la oferta no se muestran por ser secretos vigentes.</p>")
    return _pagina(f"Orden {o[2]}", cuerpo)

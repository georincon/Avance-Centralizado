/*
 * Código propio de este fork. No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import org.springframework.web.reactive.function.server.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * «Historial de emisiones»: consulta de SOLO LECTURA de la tabla `issued_credential` (lo que este emisor ha entregado).
 * Solo ejecuta SELECT. Cuelga de [BASE] (`/registraduria/admin`), que la cadena de seguridad del panel admin ya protege
 * (login ADMIN) y que el proxy restringe por IP. Muestra el sujeto (cédula) en claro: es una consola de administración.
 */
class HistorialEmisionesUi(
    private val db: DatabaseClient,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(BASE) { handleList(it) }
            GET("$BASE/{id}") { handleDetail(it) }
        }

    private data class Fila(
        val id: Long,
        val tipo: String,
        val formato: String,
        val emitida: OffsetDateTime,
        val vence: OffsetDateTime,
        val sujeto: String?,
        val revocada: OffsetDateTime?,
        val motivo: String?,
        val uuid: String,
        val notificacion: String?,
        val statusUri: String?,
        val statusIdx: Long?,
        val clientUri: String,
        val clientIdx: Long,
        val keyUri: String?,
        val keyIdx: Long?,
    ) {
        fun estado(ahora: OffsetDateTime): String =
            when {
                revocada != null -> "revocada"
                !vence.isAfter(ahora) -> "vencida"
                else -> "vigente"
            }
    }

    private val columnas =
        "id, credential_format, credential_type, issued_at, expires_at, subject, revoked_at, revocation_reason, " +
            "credential_identifier::text AS uuid, notification_id, status_list_uri, status_list_index, " +
            "client_status_list_uri, client_status_list_index, key_storage_status_list_uri, key_storage_status_list_index"

    private fun io.r2dbc.spi.Row.fila(): Fila =
        Fila(
            id = get("id", java.lang.Long::class.java)!!.toLong(),
            formato = get("credential_format", String::class.java)!!,
            tipo = get("credential_type", String::class.java)!!,
            emitida = get("issued_at", OffsetDateTime::class.java)!!,
            vence = get("expires_at", OffsetDateTime::class.java)!!,
            sujeto = get("subject", String::class.java),
            revocada = get("revoked_at", OffsetDateTime::class.java),
            motivo = get("revocation_reason", String::class.java),
            uuid = get("uuid", String::class.java)!!,
            notificacion = get("notification_id", String::class.java),
            statusUri = get("status_list_uri", String::class.java),
            statusIdx = get("status_list_index", java.lang.Long::class.java)?.toLong(),
            clientUri = get("client_status_list_uri", String::class.java)!!,
            clientIdx = get("client_status_list_index", java.lang.Long::class.java)!!.toLong(),
            keyUri = get("key_storage_status_list_uri", String::class.java),
            keyIdx = get("key_storage_status_list_index", java.lang.Long::class.java)?.toLong(),
        )

    private suspend fun handleList(request: ServerRequest): ServerResponse {
        val ahora = OffsetDateTime.now(ZoneOffset.UTC)
        val tipo = request.queryParam("tipo").orElse("").trim()
        val estado = request.queryParam("estado").orElse("").trim().takeIf { it in ESTADOS } ?: ""
        val q = request.queryParam("q").orElse("").trim().take(100)
        val desde = request.queryParam("desde").orElse("").trim().toLocalDateOrNull()
        val hasta = request.queryParam("hasta").orElse("").trim().toLocalDateOrNull()
        val pagina = (request.queryParam("pagina").orElse("1").toIntOrNull() ?: 1).coerceIn(1, 100_000)

        val condiciones = mutableListOf<String>()
        val params = linkedMapOf<String, Any>()
        if (tipo.isNotEmpty()) { condiciones += "credential_type = :tipo"; params["tipo"] = tipo }
        when (estado) {
            "vigente" -> { condiciones += "revoked_at IS NULL AND expires_at > :ahora"; params["ahora"] = ahora }
            "revocada" -> condiciones += "revoked_at IS NOT NULL"
            "vencida" -> { condiciones += "revoked_at IS NULL AND expires_at <= :ahora"; params["ahora"] = ahora }
        }
        if (q.isNotEmpty()) {
            condiciones += "(subject ILIKE :q OR credential_identifier::text ILIKE :q OR notification_id ILIKE :q)"
            params["q"] = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        if (desde != null) { condiciones += "issued_at >= :desde"; params["desde"] = desde.atStartOfDay().atOffset(BOGOTA) }
        if (hasta != null) { condiciones += "issued_at < :hasta"; params["hasta"] = hasta.plusDays(1).atStartOfDay().atOffset(BOGOTA) }
        val where = if (condiciones.isEmpty()) "" else " WHERE " + condiciones.joinToString(" AND ")

        fun DatabaseClient.GenericExecuteSpec.bindAll(extra: Map<String, Any> = emptyMap()): DatabaseClient.GenericExecuteSpec {
            var s = this
            (params + extra).forEach { (k, v) -> s = s.bind(k, v) }
            return s
        }

        val contadores = contadores(ahora)
        val filtrados =
            db.sql("SELECT COUNT(*) AS n FROM issued_credential$where").bindAll()
                .map { row, _ -> (row.get(0) as Number).toLong() }.one().awaitSingleOrNull() ?: 0L
        val paginas = maxOf(1L, (filtrados + PAGINA - 1) / PAGINA)
        val pag = minOf(pagina.toLong(), paginas)
        val filas =
            db.sql("SELECT $columnas FROM issued_credential$where ORDER BY issued_at DESC, id DESC LIMIT :lim OFFSET :off")
                .bindAll(mapOf("lim" to PAGINA, "off" to (pag - 1) * PAGINA))
                .map { row, _ -> row.fila() }.flow().toList()

        val base = request.requestPath().contextPath().value()
        fun enlace(p: Long): String {
            val partes = buildList {
                if (tipo.isNotEmpty()) add("tipo=${enc(tipo)}")
                if (estado.isNotEmpty()) add("estado=$estado")
                if (q.isNotEmpty()) add("q=${enc(q)}")
                if (desde != null) add("desde=$desde")
                if (hasta != null) add("hasta=$hasta")
                add("pagina=$p")
            }
            return "$base$BASE?" + partes.joinToString("&")
        }

        val html = StringBuilder()
        cabecera(html, base, "Historial de emisiones")
        html.append("""<div class="mb-3"><h2 class="rne-titulo mb-1">Historial de emisiones</h2>
            <p class="text-muted mb-0">Credenciales que la Registraduría (emulador) ha entregado a las wallets · consulta de solo lectura</p></div>""")
        html.append("""<div class="row g-3 mb-3">""")
        for ((etq, v, clase) in listOf(
            Triple("Total emitidas", contadores.total, "total"), Triple("Vigentes", contadores.vigentes, "vigente"),
            Triple("Revocadas", contadores.revocadas, "revocada"), Triple("Vencidas", contadores.vencidas, "vencida"),
        )) {
            html.append("""<div class="col-6 col-md-3"><div class="rne-kpi rne-kpi-$clase"><div class="etq">$etq</div><div class="n">$v</div></div></div>""")
        }
        html.append("</div>")
        if (contadores.porTipo.isNotEmpty()) {
            html.append("""<div class="rne-card"><div class="rne-card-titulo">Por tipo de credencial</div><table class="table table-sm rne-tabla w-100 mb-0"><thead><tr><th>Tipo de credencial</th><th>Total</th><th>Vigentes</th><th>Revocadas</th><th>Vencidas</th></tr></thead><tbody>""")
            contadores.porTipo.forEach { c ->
                html.append("<tr><td><a href=\"$base$BASE?tipo=${enc(c.tipo)}\">${esc(c.tipo)}</a></td><td>${c.total}</td><td>${c.vigentes}</td><td>${c.revocadas}</td><td>${c.vencidas}</td></tr>")
            }
            html.append("</tbody></table></div>")
        }
        html.append("""<form method="get" action="$base$BASE" class="rne-card row g-2 mb-3 align-items-end mx-0">
            <div class="col-md-3"><label class="form-label small mb-0">Tipo</label><select name="tipo" class="form-select form-select-sm"><option value="">Todos</option>""")
        contadores.porTipo.forEach { c -> html.append("<option value=\"${esc(c.tipo)}\"${if (c.tipo == tipo) " selected" else ""}>${esc(c.tipo)}</option>") }
        html.append("""</select></div><div class="col-md-2"><label class="form-label small mb-0">Estado</label><select name="estado" class="form-select form-select-sm"><option value="">Todos</option>""")
        ESTADOS.forEach { html.append("<option value=\"$it\"${if (it == estado) " selected" else ""}>${it.replaceFirstChar(Char::uppercase)}</option>") }
        html.append("""</select></div>
            <div class="col-md-2"><label class="form-label small mb-0">Emitida desde</label><input type="date" name="desde" value="${desde ?: ""}" class="form-control form-control-sm"></div>
            <div class="col-md-2"><label class="form-label small mb-0">Hasta</label><input type="date" name="hasta" value="${hasta ?: ""}" class="form-control form-control-sm"></div>
            <div class="col-md-3"><label class="form-label small mb-0">Sujeto / id / notificación</label><input type="text" name="q" value="${esc(q)}" class="form-control form-control-sm" placeholder="Cédula, UUID…"></div>
            <div class="col-12"><button type="submit" class="btn btn-primary btn-sm">Filtrar</button> <a class="btn btn-outline-secondary btn-sm" href="$base$BASE">Limpiar</a>
            <span class="ms-2 text-muted small">$filtrados resultado(s) · página $pag de $paginas (horas en Bogotá)</span></div></form>""")
        html.append("""<div class="rne-card p-0 rne-tabla-marco"><table class="table table-hover rne-tabla mb-0"><thead><tr><th>#</th><th>Emitida</th><th>Tipo</th><th>Sujeto</th><th>Vence</th><th>Estado</th><th></th></tr></thead><tbody>""")
        if (filas.isEmpty()) html.append("""<tr><td colspan="7" class="text-center text-muted">Sin resultados.</td></tr>""")
        filas.forEach { f ->
            val e = f.estado(ahora)
            html.append("<tr><td>${f.id}</td><td>${hora(f.emitida)}</td><td>${esc(f.tipo)}</td><td>${esc(f.sujeto ?: "—")}</td><td>${hora(f.vence)}</td><td>${badge(e)}</td>" +
                "<td><a class=\"btn btn-outline-secondary btn-sm\" href=\"$base$BASE/${f.id}\">Detalle</a></td></tr>")
        }
        html.append("</tbody></table></div>")
        html.append("""<nav class="d-flex gap-2">""")
        if (pag > 1) html.append("<a class=\"btn btn-outline-secondary btn-sm\" href=\"${enlace(pag - 1)}\">« Anterior</a>")
        if (pag < paginas) html.append("<a class=\"btn btn-outline-secondary btn-sm\" href=\"${enlace(pag + 1)}\">Siguiente »</a>")
        html.append("</nav>")
        pie(html)
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValueAndAwait(html.toString())
    }

    private suspend fun handleDetail(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLongOrNull() ?: return ServerResponse.notFound().buildAndAwait()
        val f =
            db.sql("SELECT $columnas FROM issued_credential WHERE id = :id").bind("id", id)
                .map { row, _ -> row.fila() }.one().awaitSingleOrNull()
                ?: return ServerResponse.notFound().buildAndAwait()
        val base = request.requestPath().contextPath().value()
        val html = StringBuilder()
        cabecera(html, base, "Emisión #${f.id}")
        html.append("""<p><a href="$base$BASE">« Volver al historial</a></p><h2>Emisión #${f.id} ${badge(f.estado(OffsetDateTime.now(ZoneOffset.UTC)))}</h2><div class="rne-card p-0 rne-tabla-marco"><table class="table rne-detalle mb-0"><tbody>""")
        fun fila(k: String, v: String?) = html.append("<tr><th class=\"w-25\">${esc(k)}</th><td>${esc(v ?: "—")}</td></tr>")
        fila("Tipo de credencial", f.tipo); fila("Formato", f.formato)
        fila("Sujeto", f.sujeto); fila("Identificador de la credencial", f.uuid); fila("Id de notificación", f.notificacion)
        fila("Emitida", hora(f.emitida)); fila("Vence", hora(f.vence))
        fila("Revocada", f.revocada?.let { hora(it) }); fila("Motivo de revocación", f.motivo)
        fila("Lista de estado (token)", f.statusUri?.let { "$it  [índice ${f.statusIdx}]" })
        fila("Lista de estado (cliente/wallet)", "${f.clientUri}  [índice ${f.clientIdx}]")
        fila("Lista de estado (almacén de claves)", f.keyUri?.let { "$it  [índice ${f.keyIdx}]" })
        html.append("</tbody></table></div>")
        pie(html)
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValueAndAwait(html.toString())
    }

    private data class Cont(val tipo: String, val total: Long, val vigentes: Long, val revocadas: Long, val vencidas: Long)
    private data class Contadores(val total: Long, val vigentes: Long, val revocadas: Long, val vencidas: Long, val porTipo: List<Cont>)

    private suspend fun contadores(ahora: OffsetDateTime): Contadores {
        val filas =
            db.sql(
                "SELECT credential_type AS t, COUNT(*) AS total, " +
                    "COUNT(*) FILTER (WHERE revoked_at IS NULL AND expires_at > :ahora) AS vig, " +
                    "COUNT(*) FILTER (WHERE revoked_at IS NOT NULL) AS rev, " +
                    "COUNT(*) FILTER (WHERE revoked_at IS NULL AND expires_at <= :ahora) AS ven " +
                    "FROM issued_credential GROUP BY credential_type ORDER BY credential_type",
            ).bind("ahora", ahora)
                .map { row, _ ->
                    Cont(
                        row.get("t", String::class.java)!!,
                        (row.get("total") as Number).toLong(), (row.get("vig") as Number).toLong(),
                        (row.get("rev") as Number).toLong(), (row.get("ven") as Number).toLong(),
                    )
                }.flow().toList()
        return Contadores(filas.sumOf { it.total }, filas.sumOf { it.vigentes }, filas.sumOf { it.revocadas }, filas.sumOf { it.vencidas }, filas)
    }

    private fun cabecera(sb: StringBuilder, base: String, titulo: String) {
        sb.append("""<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="$base/webjars/bootstrap/css/bootstrap.min.css"><link rel="stylesheet" href="$base/public/css/main.css">
            <link rel="stylesheet" href="$base/public/css/registraduria-theme.css"><link rel="icon" type="image/png" href="$base/public/img/registraduria/escudo-favicon.png">
            <title>${esc(titulo)} :: Registraduría (emulador)</title><style>$ESTILO</style></head><body>
            <div class="rne-header"><img src="$base/public/img/registraduria/logo-blanco.svg" alt="Escudo Registraduría Nacional del Estado Civil">
              <div><h1>Registraduría Nacional del Estado Civil</h1><p class="rne-subtitle mb-0">Emulador · Historial de emisiones de credenciales</p></div></div>
            <div class="rne-franja"></div>
            <nav class="rne-nav"><a href="$base/registraduria/admin/ciudadanos">Ciudadanos</a><a class="activo" href="$base$BASE">Historial de emisiones</a>
              <a href="$base/registraduria/admin/avisos">Avisos de revocación</a><span class="espacio"></span>
              <form method="post" action="$base$LOGOUT" class="m-0"><button type="submit" class="btn btn-outline-secondary btn-sm">Salir</button></form></nav>
            <main class="container p-4">""")
    }

    private fun pie(sb: StringBuilder) {
        sb.append("</main></body></html>")
    }

    private fun badge(estado: String): String =
        when (estado) {
            "vigente" -> """<span class="rne-badge rne-badge-vigente">Vigente</span>"""
            "revocada" -> """<span class="rne-badge rne-badge-revocada">Revocada</span>"""
            else -> """<span class="rne-badge rne-badge-vencida">Vencida</span>"""
        }

    private fun hora(t: OffsetDateTime): String = FORMATO.format(t.withOffsetSameInstant(BOGOTA))

    private fun String.toLocalDateOrNull(): LocalDate? = if (isEmpty()) null else runCatching { LocalDate.parse(this) }.getOrNull()

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8)

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    companion object {
        const val BASE: String = "/registraduria/admin/historial"
        const val LOGOUT: String = "/registraduria/admin/logout"
        private const val PAGINA: Long = 25
        private val ESTADOS = listOf("vigente", "revocada", "vencida")
        private val BOGOTA: ZoneOffset = ZoneOffset.ofHours(-5)
        private val FORMATO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // Identidad de la Registraduría (paleta de registraduria-theme.css): azul institucional, turquesa, amarillo y rojo.
        private const val ESTILO: String =
            "body{background:#eef3f8}" +
            ".rne-franja{height:6px;background:linear-gradient(90deg,#fcd116 0 50%,#003087 50% 75%,#ce1126 75% 100%)}" +
            ".rne-nav{background:#fff;border-bottom:1px solid #dbe4ee;padding:0 1.5rem;display:flex;gap:.25rem;align-items:center;flex-wrap:wrap}" +
            ".rne-nav a{padding:.85rem 1rem;color:#002d55;text-decoration:none;font-weight:600;font-size:.92rem;border-bottom:3px solid transparent}" +
            ".rne-nav a:hover{background:#f3f7fb}.rne-nav a.activo{border-bottom-color:#fcd116;background:#f3f7fb}.rne-nav .espacio{flex:1}" +
            ".rne-titulo{font-family:Georgia,'Times New Roman',serif;color:#002d55;font-weight:700}" +
            ".rne-card{background:#fff;border:1px solid #dbe4ee;border-radius:12px;padding:1rem 1.25rem;box-shadow:0 1px 3px rgba(0,45,85,.07);margin-bottom:1rem}" +
            ".rne-card-titulo{font-weight:700;color:#002d55;margin-bottom:.6rem;font-size:.95rem}" +
            ".rne-kpi{background:#fff;border:1px solid #dbe4ee;border-left:6px solid var(--c);border-radius:12px;padding:.9rem 1.1rem;box-shadow:0 1px 3px rgba(0,45,85,.07)}" +
            ".rne-kpi .etq{color:#5b6b7c;font-size:.82rem;text-transform:uppercase;letter-spacing:.04em;font-weight:600}" +
            ".rne-kpi .n{font-size:2.1rem;font-weight:700;line-height:1.15;color:var(--t);font-family:Georgia,'Times New Roman',serif}" +
            ".rne-kpi-total{--c:#002d55;--t:#002d55}.rne-kpi-vigente{--c:#01cac4;--t:#00867f}.rne-kpi-revocada{--c:#ce1126;--t:#ce1126}.rne-kpi-vencida{--c:#fcd116;--t:#8a6d00}" +
            ".rne-tabla-marco{overflow:hidden}.rne-tabla thead th{background:#002d55;color:#fff;border:0;font-size:.78rem;text-transform:uppercase;letter-spacing:.05em;font-weight:600;padding:.7rem .75rem}" +
            ".rne-tabla td{vertical-align:middle;padding:.6rem .75rem;font-size:.9rem}.rne-tabla tbody tr:nth-child(even){background:#f7fafd}.rne-tabla tbody tr:hover{background:#e3f6f6}" +
            ".rne-detalle th{background:#f3f7fb;color:#002d55;width:28%;font-weight:600;padding:.7rem .9rem}.rne-detalle td{padding:.7rem .9rem;word-break:break-all}" +
            ".rne-badge{display:inline-block;padding:.22rem .7rem;border-radius:99px;font-size:.76rem;font-weight:700;letter-spacing:.02em}" +
            ".rne-badge-vigente{background:#d9f7f6;color:#00676a}.rne-badge-revocada{background:#fde3e6;color:#ce1126}.rne-badge-vencida{background:#fff4c2;color:#7a5f00}" +
            ".btn-primary{background:#002d55;border-color:#002d55}.btn-primary:hover{background:#1b3853;border-color:#1b3853}" +
            ".form-select:focus,.form-control:focus{border-color:#01cac4;box-shadow:0 0 0 .2rem rgba(1,202,196,.2)}"
    }
}

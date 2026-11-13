/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.uis.ESTADOS_ACADEMICOS
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.Estudiante
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.HistorialEntrada
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.SyncEstudianteToKeycloak
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.TIPOS_IDENTIFICACION
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.TIPOS_SIN_PID
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.UisAcademicStore
import eu.europa.ec.eudi.pidissuer.port.input.RevocationNoticeText
import eu.europa.ec.eudi.pidissuer.port.input.SubjectControls
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.qr.Dimensions
import eu.europa.ec.eudi.pidissuer.port.out.qr.Format
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.qr.Pixels
import eu.europa.ec.eudi.pidissuer.port.out.uis.ActivationCodes
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import kotlin.time.Clock

/**
 * Panel de la UIS para dar de alta o modificar estudiantes y su historial por programa (`datos_estudiantes` y
 * `historial_estudiantes` de `uis_academic`). Al guardar un estudiante se crea o actualiza su usuario en Keycloak
 * (`username` = número de identificación, sin contraseña). Para quien no tiene PID (CE, PAS, PPT, PEP) permite generar un
 * código de activación de un solo uso.
 */
class UisAdminUi(
    private val store: UisAcademicStore,
    private val keycloak: SyncEstudianteToKeycloak,
    private val activationCodes: ActivationCodes,
    private val generateQrCode: GenerateQqCode,
    private val controls: SubjectControls,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(LOGIN) { page("uis-admin-login", mapOf("error" to it.queryParam("error").isPresent)) }
            GET(ESTUDIANTES) { handleList(it) }
            GET(NUEVO) { form(null, null, null) }
            GET(QR) { handleQr(it) }
            GET(EDITAR) { handleEdit(it) }
            POST(ESTUDIANTES, ::handleSave)
            POST(BORRAR, ::handleDelete)
            POST(HISTORIAL, ::handleHistory)
            POST(HISTORIAL_BORRAR, ::handleHistoryDelete)
            POST(CODIGO, ::handleActivationCode)
            GET(AVISOS) { handleNotices() }
            POST(AVISOS_NOTIFICADO) { handleNoticeDone(it) }
        }

    /** Avisos de revocación de las credenciales de la UIS: hay que informar al titular dentro de las 24 h (CIR 2024/2977, art. 5(3)). */
    private suspend fun handleNotices(): ServerResponse {
        val now = Clock.System.now()
        val avisos =
            controls.listNotices(false).map {
                AvisoView(
                    id = it.id,
                    subject = it.subject,
                    credentials = it.credentialTypes.joinToString(", ") { type -> RevocationNoticeText.credentialName(type) },
                    reason = RevocationNoticeText.reasonLabel(it.reason),
                    message = it.message,
                    revokedAt = RevocationNoticeText.formatDate(it.revokedAt),
                    deadlineAt = RevocationNoticeText.formatDate(it.deadlineAt),
                    status = it.status.name,
                    open = it.status.isOpen,
                    overdue = it.status.isOpen && now > it.deadlineAt,
                    detail = it.detail,
                )
            }
        return page("uis-admin-avisos", mapOf("avisos" to avisos, "abiertos" to avisos.count { it.open }))
    }

    private suspend fun handleNoticeDone(request: ServerRequest): ServerResponse {
        request.pathVariable("id").toLongOrNull()?.let {
            controls.updateNotice(it, NoticeStatus.MANUAL_DONE, "manual", "confirmado por el administrador")
        }
        return redirect(request, AVISOS)
    }

    private suspend fun page(view: String, model: Map<String, Any?>) =
        ServerResponse.ok().contentType(MediaType.TEXT_HTML).renderAndAwait(view, model)

    /** PNG con el QR de una solicitud OpenID4VP; lo pide el formulario al leer el PID desde la wallet. */
    private suspend fun handleQr(request: ServerRequest): ServerResponse {
        val data = request.queryParam("data").orElse(null)?.takeIf { it.isNotBlank() && it.length <= 4096 }
            ?: return ServerResponse.badRequest().buildAndAwait()
        val png = generateQrCode(com.eygraber.uri.Uri.parse(data), Format.PNG, Dimensions(Pixels(320u), Pixels(320u)))
        return ServerResponse.ok().contentType(MediaType.IMAGE_PNG).bodyValueAndAwait(png)
    }

    private suspend fun handleList(request: ServerRequest): ServerResponse {
        val q = request.queryParam("q").orElse(null)
        val estudiantes = store.listar(q)
        return page("uis-admin-list", mapOf("estudiantes" to estudiantes, "query" to (q ?: ""), "total" to estudiantes.size, "sinPid" to TIPOS_SIN_PID))
    }

    private suspend fun handleEdit(request: ServerRequest): ServerResponse {
        val numero = request.pathVariable("numero")
        val e = store.buscar(numero) ?: return redirect(request, ESTUDIANTES)
        return form(e, null, null)
    }

    private suspend fun form(
        e: Estudiante?,
        error: String?,
        code: String?,
        historialEnEdicion: HistorialEntrada? = null,
    ): ServerResponse {
        val numero = e?.numeroIdentificacion
        return page(
            "uis-admin-estudiante",
            mapOf(
                "e" to e,
                "isEdit" to (e != null),
                "tipos" to TIPOS_IDENTIFICACION,
                "estados" to ESTADOS_ACADEMICOS,
                "programas" to store.programas(),
                "historial" to (numero?.let { store.historial(it) } ?: emptyList()),
                "sinPid" to (e?.tipoIdentificacion in TIPOS_SIN_PID),
                "error" to error,
                "codigo" to code,
                "h" to historialEnEdicion,
            ),
        )
    }

    private suspend fun handleSave(request: ServerRequest): ServerResponse {
        val f = request.awaitFormData()
        fun v(k: String) = f.getFirst(k)?.trim().orEmpty()
        val e = Estudiante(v("numeroIdentificacion"), v("tipoIdentificacion"), v("nombres"), v("apellidos"), v("numeroContacto").ifBlank { null }, v("correoElectronico").ifBlank { null })
        val ya = f.getFirst("edit") == "1"
        if (e.numeroIdentificacion.isBlank() || e.nombres.isBlank() || e.apellidos.isBlank() || e.tipoIdentificacion !in TIPOS_IDENTIFICACION) {
            return form(if (ya) e else null, "Completa el número de identificación, el tipo, los nombres y los apellidos.", null)
        }
        if (!ya && store.buscar(e.numeroIdentificacion) != null) {
            return form(null, "Ya existe un estudiante con ese número de identificación; búscalo en la lista y edítalo.", null)
        }
        store.guardar(e)
        // Si Keycloak falla, el estudiante queda guardado y se avisa: el panel no depende de él para guardar.
        val aviso = runCatching { keycloak.sync(e) }.exceptionOrNull()?.also { log.error("No se pudo sincronizar con Keycloak", it) }
        return if (aviso == null) redirect(request, "$ESTUDIANTES/${e.numeroIdentificacion}/editar") else form(e, "Guardado, pero no se pudo crear el usuario en Keycloak (${aviso.message}). Vuelve a guardar para reintentar.", null)
    }

    private suspend fun handleDelete(request: ServerRequest): ServerResponse {
        val numero = request.pathVariable("numero")
        store.borrar(numero)
        runCatching { keycloak.delete(numero) }.onFailure { log.error("No se pudo borrar el usuario de Keycloak", it) }
        return redirect(request, ESTUDIANTES)
    }

    private suspend fun handleHistory(request: ServerRequest): ServerResponse {
        val numero = request.pathVariable("numero")
        val e = store.buscar(numero) ?: return redirect(request, ESTUDIANTES)
        val f = request.awaitFormData()
        fun v(k: String) = f.getFirst(k)?.trim().orEmpty()
        val entrada =
            HistorialEntrada(
                id = v("id").toLongOrNull(),
                programaId = v("programaId").toLongOrNull() ?: return form(e, "Elige el programa.", null),
                fechaIngreso = v("fechaIngreso").ifBlank { null },
                fechaDeGrado = v("fechaDeGrado").ifBlank { null },
                promedioAcumulado = v("promedioAcumulado").ifBlank { null },
                creditosAprobados = v("creditosAprobados").toIntOrNull(),
                semestresCursados = v("semestresCursados").toIntOrNull(),
                estadoAcademico = v("estadoAcademico"),
            )
        if (entrada.estadoAcademico !in ESTADOS_ACADEMICOS) return form(e, "Elige un estado académico válido.", null, entrada)
        return runCatching { store.guardarHistorial(numero, entrada) }.fold(
            onSuccess = { redirect(request, "$ESTUDIANTES/$numero/editar") },
            onFailure = { form(e, motivo(it), null, entrada) },
        )
    }

    private suspend fun handleHistoryDelete(request: ServerRequest): ServerResponse {
        val numero = request.pathVariable("numero")
        request.pathVariable("id").toLongOrNull()?.let { store.borrarHistorial(numero, it) }
        return redirect(request, "$ESTUDIANTES/$numero/editar")
    }

    private suspend fun handleActivationCode(request: ServerRequest): ServerResponse {
        val numero = request.pathVariable("numero")
        val e = store.buscar(numero) ?: return redirect(request, ESTUDIANTES)
        val issued = activationCodes.issue(numero)
        log.info("Código de activación generado para el estudiante {}", numero.take(3) + "***")
        val txt = issued.code.substring(0, 4) + "-" + issued.code.substring(4) + " (vence " + issued.expiresAt + ")"
        return form(e, null, txt)
    }

    /** La regla que rechazó el guardado (la valida un trigger de la base): el mensaje de la causa más profunda. */
    private fun motivo(e: Throwable): String =
        generateSequence(e) { it.cause }.mapNotNull { it.message }.lastOrNull { it.contains("Para estado") || it.contains("debe") }
            ?.lineSequence()?.firstOrNull()?.removePrefix("ERROR:")?.trim()
            ?: generateSequence(e) { it.cause }.last().message?.lineSequence()?.firstOrNull() ?: "No se pudo guardar el historial."

    /** El prefijo (`/uis-issuer`, el base-path) se saca de la propia petición: no se repite en la configuración. */
    private suspend fun redirect(request: ServerRequest, path: String): ServerResponse =
        ServerResponse.status(303).location(java.net.URI.create(request.exchange().request.path.contextPath().value() + path)).buildAndAwait()

    companion object {
        private val log = LoggerFactory.getLogger(UisAdminUi::class.java)
        const val BASE: String = "/uis/admin"
        const val LOGIN: String = "$BASE/login"
        const val LOGOUT: String = "$BASE/logout"
        const val ESTUDIANTES: String = "$BASE/estudiantes"
        const val NUEVO: String = "$ESTUDIANTES/nuevo"
        const val EDITAR: String = "$ESTUDIANTES/{numero}/editar"
        const val BORRAR: String = "$ESTUDIANTES/{numero}/borrar"
        const val HISTORIAL: String = "$ESTUDIANTES/{numero}/historial"
        const val HISTORIAL_BORRAR: String = "$ESTUDIANTES/{numero}/historial/{id}/borrar"
        const val QR: String = "$ESTUDIANTES/qr"
        const val CODIGO: String = "$ESTUDIANTES/{numero}/codigo-activacion"
        const val AVISOS: String = "$BASE/avisos"
        const val AVISOS_NOTIFICADO: String = "$AVISOS/{id}/notificado"
    }
}

/** Un aviso de revocación ya preparado para mostrarse (textos en español, fechas formateadas). */
private data class AvisoView(
    val id: Long?,
    val subject: String,
    val credentials: String,
    val reason: String,
    val message: String,
    val revokedAt: String,
    val deadlineAt: String,
    val status: String,
    val open: Boolean,
    val overdue: Boolean,
    val detail: String?,
)

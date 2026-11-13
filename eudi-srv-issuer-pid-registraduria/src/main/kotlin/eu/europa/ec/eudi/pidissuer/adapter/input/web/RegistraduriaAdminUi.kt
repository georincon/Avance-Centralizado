/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 5 y 9.5.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.FotoReferencia
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.GrupoSanguineo
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.port.input.RevocationNoticeText
import eu.europa.ec.eudi.pidissuer.port.input.RevokeCredentialsOfSubject
import eu.europa.ec.eudi.pidissuer.port.input.SubjectControls
import eu.europa.ec.eudi.pidissuer.port.out.persistence.NoticeStatus
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.DeleteCedula
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.ListCedulas
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.SaveCedula
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.server.*
import kotlin.time.Clock

/**
 * Panel admin de la Registraduría: login propio (ver
 * [configureRegistraduriaAdminSecurity]) + alta, edición, consulta y borrado de ciudadanos +
 * subida de la foto de referencia + revocación de sus credenciales. Deliberadamente
 * sin firma, huella ni código de barras (ver CONTEXTO-REGISTRADURIA.md, sección 3).
 *
 * Borrar un ciudadano y revocar sus credenciales son operaciones independientes:
 * revocar NUNCA borra datos del ciudadano.
 */
class RegistraduriaAdminUi(
    private val listCedulas: ListCedulas,
    private val findCedulaByDocumentNumber: FindCedulaByDocumentNumber,
    private val saveCedula: SaveCedula,
    private val deleteCedula: DeleteCedula,
    private val revokeCredentialsOfSubject: RevokeCredentialsOfSubject,
    private val controls: SubjectControls,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(ADMIN_LOGIN) { handleLogin(it) }

            GET(ADMIN_CIUDADANOS) { handleList(it) }
            GET(ADMIN_CIUDADANOS_NEW) { handleNewForm() }
            GET(ADMIN_CIUDADANOS_EDIT) { handleEditForm(it) }
            GET(ADMIN_CIUDADANOS_FOTO) { handlePhoto(it) }

            POST(
                ADMIN_CIUDADANOS,
                contentType(MediaType.MULTIPART_FORM_DATA),
                ::handleSave,
            )
            POST(ADMIN_CIUDADANOS_DELETE) { handleDelete(it) }
            POST(ADMIN_CIUDADANOS_REVOCAR) { handleRevoke(it) }
            POST(ADMIN_CIUDADANOS_DESBLOQUEAR) { handleUnblock(it) }
            GET(ADMIN_AVISOS) { handleNotices() }
            POST(ADMIN_AVISOS_NOTIFICADO) { handleNoticeDone(it) }
        }

    private suspend fun handleLogin(request: ServerRequest): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "registraduria-admin-login",
                mapOf(
                    "error" to request.queryParam("error").isPresent,
                    "loggedOut" to request.queryParam("logout").isPresent,
                ),
            )

    private suspend fun handleList(request: ServerRequest): ServerResponse {
        val query = request.queryParam("q").orElse(null)
        val cedulas = listCedulas(query)
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "registraduria-admin-list",
                mapOf(
                    "cedulas" to cedulas,
                    "query" to (query ?: ""),
                    "total" to cedulas.size,
                    // Resultado de una revocación (se pasa por la URL tras redirigir)
                    "revocadas" to request.queryParam("revocadas").orElse(null),
                    "fallidas" to request.queryParam("fallidas").orElse(null),
                    "sinEstado" to request.queryParam("sinEstado").orElse(null),
                    "bloqueado" to request.queryParam("bloqueado").isPresent,
                    "desbloqueado" to request.queryParam("desbloqueado").isPresent,
                    // Titulares con la emisión bloqueada y avisos de revocación sin entregar
                    // HashSet (clase pública): la plantilla no puede invocar métodos del EmptySet privado de Kotlin
                    "bloqueados" to HashSet(controls.listBlocked()),
                    "avisosAbiertos" to controls.listNotices(true).size,
                ),
            )
    }

    private suspend fun handleNewForm(): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("registraduria-admin-form", formModel(cedula = null))

    private suspend fun handleEditForm(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        val cedula = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))
        return if (cedula == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait("registraduria-admin-form", formModel(cedula))
        }
    }

    private suspend fun handlePhoto(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        val foto = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))?.fotoReferencia
        return if (foto == null) {
            ServerResponse.notFound().buildAndAwait()
        } else {
            ServerResponse
                .ok()
                .contentType(MediaType.parseMediaType(foto.contentType))
                .bodyValueAndAwait(foto.bytes)
        }
    }

    /**
     * URL de redireccion CON el prefijo de la aplicacion.
     *
     * `ServerResponse.location()` escribe tal cual lo que se le da en la cabecera
     * `Location`. Si se le pasa la ruta de la pantalla (`/registraduria/admin/ciudadanos`)
     * sin el `base-path` de WebFlux (`/pid-registraduria`), el navegador aterriza en
     * una ruta que no existe: el issuer vive bajo `/pid-registraduria` y el proxy
     * unico solo enruta ese prefijo. De ahi el 404 al guardar.
     *
     * El prefijo se saca de la propia peticion, que es donde vive el `base-path`:
     * no hay que configurarlo dos veces ni hardcodearlo.
     */
    private fun ServerRequest.redirectTo(path: String): java.net.URI =
        java.net.URI.create(exchange().request.path.contextPath().value() + path)

    private suspend fun handleSave(request: ServerRequest): ServerResponse {
        val parts = request.awaitMultipartData()
        return try {
            val form = parts.toCedulaForm()
            val existing = findCedulaByDocumentNumber(CedulaDocumentNumber(form.documentNumber))
            val fotoReferencia = form.foto ?: existing?.fotoReferencia
            val cedula =
                Cedula(
                    id = existing?.id,
                    documentNumber = CedulaDocumentNumber(form.documentNumber),
                    apellidos = form.apellidos,
                    nombres = form.nombres,
                    fechaNacimiento = form.fechaNacimiento,
                    lugarNacimientoCiudad = form.lugarNacimientoCiudad,
                    lugarNacimientoDepartamento = form.lugarNacimientoDepartamento,
                    sexo = form.sexo,
                    estaturaCm = form.estaturaCm,
                    grupoSanguineo = form.grupoSanguineo,
                    fechaExpedicion = form.fechaExpedicion,
                    lugarExpedicion = form.lugarExpedicion,
                    nacionalidad = form.nacionalidad,
                    fotoReferencia = fotoReferencia,
                    correoElectronico = form.correoElectronico,
                    celular = form.celular,
                )
            saveCedula(cedula)
            ServerResponse
                .status(303)
                .location(request.redirectTo(ADMIN_CIUDADANOS))
                .buildAndAwait()
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid citizen form submission: {}", e.message)
            ServerResponse
                .badRequest()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "registraduria-admin-form",
                    formModel(cedula = null) +
                        mapOf("validationError" to (e.message ?: "Datos inválidos")),
                )
        }
    }

    /**
     * Borrar es una decisión exclusiva del administrador y es independiente de la revocación: solo elimina el
     * registro del ciudadano. No toca sus credenciales emitidas (para eso está "Revocar credenciales").
     */
    private suspend fun handleDelete(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        deleteCedula(CedulaDocumentNumber(documentNumber))
        return ServerResponse
            .status(303)
            .location(request.redirectTo(ADMIN_CIUDADANOS))
            .buildAndAwait()
    }

    private suspend fun handleRevoke(request: ServerRequest): ServerResponse {
        val documentNumber = request.pathVariable("documentNumber")
        val form = request.awaitFormData()
        // El motivo se le informa al titular (CIR 2024/2977 art. 5(3)); un código desconocido se trata como "otro"
        val reason = form.getFirst("reason")?.takeIf { it in RevocationNoticeText.REASON_CODES } ?: "other"
        val block = form.getFirst("block") == "true"
        // Primero el bloqueo: así no se puede pedir otra credencial entre la revocación y el bloqueo.
        if (block) controls.block(documentNumber, reason)
        val result = revokeCredentialsOfSubject(documentNumber, reason)
        val extra = if (block) "&bloqueado=1" else ""
        return ServerResponse
            .status(303)
            .location(request.redirectTo("$ADMIN_CIUDADANOS?${result.toQuery()}$extra"))
            .buildAndAwait()
    }

    /** Levanta el bloqueo de emisión del titular. Sus datos y credenciales no cambian. */
    private suspend fun handleUnblock(request: ServerRequest): ServerResponse {
        controls.unblock(request.pathVariable("documentNumber"))
        return ServerResponse
            .status(303)
            .location(request.redirectTo("$ADMIN_CIUDADANOS?desbloqueado=1"))
            .buildAndAwait()
    }

    private suspend fun handleNotices(): ServerResponse {
        val now = Clock.System.now()
        val avisos =
            controls.listNotices(false).map {
                NoticeView(
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
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("registraduria-admin-avisos", mapOf("avisos" to avisos, "abiertos" to avisos.count { it.open }))
    }

    /** El administrador confirma que avisó al titular por su cuenta (correo, llamada, en persona...). */
    private suspend fun handleNoticeDone(request: ServerRequest): ServerResponse {
        request.pathVariable("id").toLongOrNull()?.let {
            controls.updateNotice(it, NoticeStatus.MANUAL_DONE, "manual", "confirmado por el administrador")
        }
        return ServerResponse
            .status(303)
            .location(request.redirectTo(ADMIN_AVISOS))
            .buildAndAwait()
    }

    private fun RevokeCredentialsOfSubject.Result.toQuery(): String =
        "revocadas=$revoked&fallidas=$failed&sinEstado=$withoutStatus"

    private fun formModel(cedula: Cedula?): Map<String, Any?> =
        mapOf(
            "cedula" to cedula,
            "isEdit" to (cedula != null),
            "gruposSanguineos" to GrupoSanguineo.entries.map { it.etiqueta },
        )

    companion object {
        const val ADMIN_BASE_PATH: String = "/registraduria/admin"
        const val ADMIN_LOGIN: String = "$ADMIN_BASE_PATH/login"
        const val ADMIN_LOGOUT: String = "$ADMIN_BASE_PATH/logout"
        const val ADMIN_CIUDADANOS: String = "$ADMIN_BASE_PATH/ciudadanos"
        const val ADMIN_CIUDADANOS_NEW: String = "$ADMIN_CIUDADANOS/nuevo"
        const val ADMIN_CIUDADANOS_EDIT: String = "$ADMIN_CIUDADANOS/{documentNumber}/editar"
        const val ADMIN_CIUDADANOS_FOTO: String = "$ADMIN_CIUDADANOS/{documentNumber}/foto"
        const val ADMIN_CIUDADANOS_DELETE: String = "$ADMIN_CIUDADANOS/{documentNumber}/borrar"
        const val ADMIN_CIUDADANOS_DESBLOQUEAR: String = "$ADMIN_CIUDADANOS/{documentNumber}/desbloquear"
        const val ADMIN_AVISOS: String = "$ADMIN_BASE_PATH/avisos"
        const val ADMIN_AVISOS_NOTIFICADO: String = "$ADMIN_AVISOS/{id}/notificado"
        const val ADMIN_CIUDADANOS_REVOCAR: String = "$ADMIN_CIUDADANOS/{documentNumber}/revocar"
        private val log = LoggerFactory.getLogger(RegistraduriaAdminUi::class.java)
    }
}

private data class NoticeView(
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

private data class CedulaForm(
    val documentNumber: String,
    val apellidos: String,
    val nombres: String,
    val fechaNacimiento: LocalDate,
    val lugarNacimientoCiudad: String,
    val lugarNacimientoDepartamento: String?,
    val sexo: Sexo,
    val estaturaCm: Int?,
    val grupoSanguineo: GrupoSanguineo?,
    val fechaExpedicion: LocalDate?,
    val lugarExpedicion: String?,
    val nacionalidad: String,
    val foto: FotoReferencia?,
    val correoElectronico: String?,
    val celular: String?,
)

private suspend fun MultiValueMap<String, Part>.toCedulaForm(): CedulaForm {
    fun field(name: String): String? = (getFirst(name) as? FormFieldPart)?.value()?.takeIf { it.isNotBlank() }

    val documentNumber =
        requireNotNull(field("documentNumber")) { "El número de documento es obligatorio" }
    val apellidos = requireNotNull(field("apellidos")) { "Los apellidos son obligatorios" }
    val nombres = requireNotNull(field("nombres")) { "Los nombres son obligatorios" }
    val fechaNacimiento =
        requireNotNull(field("fechaNacimiento")) { "La fecha de nacimiento es obligatoria" }.let(LocalDate::parse)
    val lugarNacimientoCiudad =
        requireNotNull(field("lugarNacimientoCiudad")) { "La ciudad de nacimiento es obligatoria" }
    val sexo =
        requireNotNull(field("sexo")?.let { runCatching { Sexo.valueOf(it) }.getOrNull() }) {
            "El sexo debe ser M o F"
        }

    val fotoPart = get("foto")?.firstOrNull() as? FilePart
    val foto =
        if (fotoPart != null && fotoPart.filename().isNotBlank()) {
            val bytes = fotoPart.readAllBytes()
            val contentType = fotoPart.headers().contentType?.toString() ?: "image/jpeg"
            FotoReferencia(bytes, contentType)
        } else {
            null
        }

    return CedulaForm(
        documentNumber = documentNumber,
        apellidos = apellidos,
        nombres = nombres,
        fechaNacimiento = fechaNacimiento,
        lugarNacimientoCiudad = lugarNacimientoCiudad,
        lugarNacimientoDepartamento = field("lugarNacimientoDepartamento"),
        sexo = sexo,
        estaturaCm = field("estaturaCm")?.toIntOrNull(),
        grupoSanguineo = field("grupoSanguineo")?.let { GrupoSanguineo.fromEtiqueta(it) },
        fechaExpedicion = field("fechaExpedicion")?.let(LocalDate::parse),
        lugarExpedicion = field("lugarExpedicion"),
        nacionalidad = field("nacionalidad") ?: "CO",
        foto = foto,
        correoElectronico = field("correoElectronico")?.trim()?.lowercase(),
        // Acepta «304 373 0066», «+57 304…» o «57304…»: se guardan solo los 10 dígitos.
        celular = field("celular")?.filter { it.isDigit() }?.let { if (it.length == 12 && it.startsWith("57")) it.drop(2) else it },
    )
}

private suspend fun FilePart.readAllBytes(): ByteArray {
    val buffer = DataBufferUtils.join(content()).awaitSingle()
    return try {
        ByteArray(buffer.readableByteCount()).also { buffer.read(it) }
    } finally {
        DataBufferUtils.release(buffer)
    }
}

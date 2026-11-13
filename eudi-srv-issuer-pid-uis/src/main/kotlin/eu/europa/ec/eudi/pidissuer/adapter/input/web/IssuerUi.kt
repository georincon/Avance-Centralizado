/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.StudentInfo
import arrow.core.raise.effect
import arrow.core.raise.fold
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.AcademicCredentialScope
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.CarnetCredentialScope
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.GetAcademicDataFromDatabase
import eu.europa.ec.eudi.pidissuer.appendPath
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.qr.Dimensions
import eu.europa.ec.eudi.pidissuer.port.out.qr.Format
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.qr.Pixels
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidIngresos
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidResult
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidVerifierClient
import eu.europa.ec.eudi.pidissuer.port.out.uis.ActivationCodes
import eu.europa.ec.eudi.pidissuer.port.out.uis.OfferTickets
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.WebSession
import kotlin.io.encoding.Base64

class IssuerUi(
    private val metadata: CredentialIssuerMetaData,
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val generateQrCode: GenerateQqCode,
    private val getAcademicDataFromDatabase: GetAcademicDataFromDatabase,
    private val loginRateLimit: LoginRateLimit = LoginRateLimit.disabled(),
    private val tickets: OfferTickets,
    private val activationCodes: ActivationCodes,
    private val pidVerifier: PidVerifierClient,
    private val pidIngresos: PidIngresos,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            // Redirect / to 'generate credentials offer' form
            (GET("") or GET("/")) {
                log.info("Redirecting to {}", GENERATE_CREDENTIALS_OFFER)
                ServerResponse
                    .status(HttpStatus.SEE_OTHER)
                    .renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
            }

            // Display student login form
            GET(
                LOGIN,
                contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML),
                ::handleDisplayLogin,
            )

            // Validate student credentials against datos_estudiantes
            POST(
                LOGIN,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handleLogin,
            )

            // Carnet Estudiantil de un programa ACTIVO: misma sesión, mismos datos y misma pantalla de oferta (QR + volver)
            POST(
                LOGIN_PID,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handlePidStart,
            )
            GET(LOGIN_PID_ESTADO, ::handlePidStatus)

            GET(CARNET, ::handleCarnet)

            // End the student session
            (GET(LOGOUT) or POST(LOGOUT)) { handleLogout(it) }

            // Display 'generate credentials offer' form (Step 1: enter document number)
            GET(
                GENERATE_CREDENTIALS_OFFER,
                contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML),
                ::handleDisplayDocumentForm,
            )

            // Step 2: Look up student by document number, show credential type selection
            POST(
                GENERATE_CREDENTIALS_OFFER,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handleLookupStudent,
            )

            // Step 3: Select credential type and show programs
            POST(
                SELECT_CREDENTIAL_TYPE,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handleSelectCredentialType,
            )

            // Step 3b: Go back from program selection to credential type selection
            GET(
                SELECT_CREDENTIAL_TYPE_BACK,
                contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML),
                ::handleSelectCredentialTypeBack,
            )

            // Step 4b: Go back from the QR page to program selection (same student/credential type)
            GET(
                SELECT_PROGRAM_BACK,
                contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML),
                ::handleSelectProgramBack,
            )

            // Step 4: Save selection and generate credential offer
            POST(
                SELECT_PROGRAM,
                contentType(MediaType.APPLICATION_FORM_URLENCODED) and accept(MediaType.TEXT_HTML),
                ::handleSelectProgram,
            )
        }

    private suspend fun handleDisplayLogin(request: ServerRequest): ServerResponse {
        log.info("Displaying student login form")
        val session = request.session().awaitSingle()
        if (session.attributes[SESSION_STUDENT_USERNAME] != null) {
            return ServerResponse
                .status(HttpStatus.SEE_OTHER)
                .renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
        }
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("login", emptyMap<String, Any>())
    }

    /**
     * Ingreso del estudiante sin contraseña. Esta pantalla atiende a quien NO puede probar su identidad con un PID
     * (extranjeros: CE, PAS, PPT, PEP): número de identificación + código de activación que entrega el registro de la UIS.
     * Quien tiene PID entra presentándolo desde su wallet.
     */
    private suspend fun handleLogin(request: ServerRequest): ServerResponse {
        val formData = request.awaitFormData()
        val documento = formData["documento"]?.firstOrNull()?.trim().orEmpty()
        val codigo = formData["codigo"]?.firstOrNull().orEmpty()
        log.info("Activation-code login attempt for document {}", documento.take(3) + "***")

        if (documento.isBlank() || codigo.isBlank()) {
            return ServerResponse.badRequest().contentType(MediaType.TEXT_HTML)
                .renderAndAwait("login", mapOf("error" to "Escribe tu número de identificación y el código de activación", "documento" to documento))
        }
        loginRateLimit.blocked(request, documento)?.let { blocked ->
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", blocked.retryAfterSeconds.toString())
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "login",
                    mapOf("error" to "Demasiados intentos fallidos. Intenta de nuevo en ${(blocked.retryAfterSeconds + 59) / 60} minuto(s).", "documento" to documento),
                )
        }

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(documento)
        // Misma respuesta si el estudiante no existe o si el código no sirve: no se revela cuál de las dos cosas falló.
        if (student == null || !activationCodes.verifyAndConsume(student.numeroIdentificacion, codigo)) {
            log.warn("Failed activation-code login for document {}", documento.take(3) + "***")
            loginRateLimit.recordFailure(request, documento)
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.TEXT_HTML)
                .renderAndAwait("login", mapOf("error" to "Número de identificación o código de activación incorrectos", "documento" to documento))
        }

        loginRateLimit.recordSuccess(documento)
        startStudentSession(request, student)
        return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
    }

    /** Ingreso con la cédula digital: muestra el QR de la solicitud de PID (cualquier wallet EUDI) y espera la verificación. */
    private suspend fun handlePidStart(request: ServerRequest): ServerResponse {
        val started = runCatching { pidVerifier.start() }.getOrElse {
            log.error("No se pudo crear la solicitud de PID", it)
            return ServerResponse.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                .renderAndAwait("login", mapOf("error" to "El servicio de verificación de la cédula digital no está disponible. Intenta más tarde."))
        }
        val ing = pidIngresos.crear(started.transactionId, started.requestUri)
        val qr = generateQrCode(Uri.parse(started.requestUri), Format.PNG, Dimensions(Pixels(480u), Pixels(480u)))
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).renderAndAwait(
            "login-pid",
            mapOf(
                "requestUri" to started.requestUri,
                "estadoUrl" to "$LOGIN_PID_ESTADO_BASE/${ing.id}",
                "qrCode" to Base64.encode(qr),
                "qrCodeMediaType" to "image/png",
            ),
        )
    }

    /** La página consulta aquí: cuando el verifier validó el PID y su cédula es la de un estudiante, abre la sesión. */
    private suspend fun handlePidStatus(request: ServerRequest): ServerResponse {
        suspend fun json(estado: String, motivo: String? = null, siguiente: String? = null) =
            ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .bodyValueAndAwait(PidEstadoTO(estado, motivo, siguiente))
        val ing = pidIngresos.obtener(request.pathVariable("id")) ?: return json("error", "La solicitud venció. Vuelve a intentarlo.")
        when (val r = pidVerifier.result(ing.transactionId)) {
            PidResult.Pending -> return json("esperando")
            is PidResult.Failed -> { pidIngresos.cerrar(ing.id); return json("error", r.reason) }
            is PidResult.Ok -> {
                val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(r.documentNumber)
                pidIngresos.cerrar(ing.id)
                if (student == null) return json("error", "No hay un estudiante de la UIS con esa cédula.")
                startStudentSession(request, student)
                return json("ok", siguiente = "${request.exchange().request.path.contextPath().value()}$GENERATE_CREDENTIALS_OFFER")
            }
        }
    }

    private suspend fun startStudentSession(request: ServerRequest, student: StudentInfo) {
        val session = request.session().awaitSingle()
        session.attributes[SESSION_STUDENT_USERNAME] = student.usuarioAutenticacion
        session.attributes[SESSION_STUDENT_FULLNAME] = "${student.nombres} ${student.apellidos}"
        log.info("Student {} logged in", student.usuarioAutenticacion.take(3) + "***")
    }

    /** Ticket (issuer_state) de un solo uso, ligado al estudiante de esta sesión: Keycloak lo canjea y entra sin formulario. */
    private fun ticketFor(session: WebSession): String =
        tickets.issue(session.attributes[SESSION_STUDENT_USERNAME] as String, TICKET_TTL)

    private suspend fun handleLogout(request: ServerRequest): ServerResponse {
        val session = request.session().awaitSingle()
        log.info("Logging out student: {}", session.attributes[SESSION_STUDENT_USERNAME])
        session.invalidate().awaitSingleOrNull()
        return ServerResponse
            .status(HttpStatus.SEE_OTHER)
            .renderAndAwait("redirect:$LOGIN")
    }

    private suspend fun ServerRequest.requireStudentSession(): WebSession? {
        val session = session().awaitSingle()
        return if (session.attributes[SESSION_STUDENT_USERNAME] != null) session else null
    }

    private suspend fun handleDisplayDocumentForm(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")

        log.info("Displaying document number lookup form")
        val usefulLinks = createUsefulLinks(metadata.id, metadata.authorizationServers[0])
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "generate-credentials-offer-form",
                mapOf(
                    "credentialsOfferUri" to createCredentialsOffer.defaultCredentialOfferUri.toString(),
                    "openid4VciVersion" to OpenId4VciSpec.VERSION,
                    "usefulLinks" to usefulLinks,
                    "studentFullName" to session.attributes[SESSION_STUDENT_FULLNAME],
                ),
            )
    }

    private suspend fun handleLookupStudent(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]

        log.info("Looking up student by document number")
        val formData = request.awaitFormData()
        val documentoIdentidad = formData["documentoIdentidad"]?.firstOrNull()?.trim().orEmpty()
        val credentialsOfferUri = formData["credentialsOfferUri"]?.firstOrNull { it.isNotBlank() }

        if (documentoIdentidad.isBlank()) {
            return ServerResponse
                .badRequest()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "generate-credentials-offer-form",
                    mapOf(
                        "error" to "Debe ingresar un número de documento válido",
                        "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                        "openid4VciVersion" to OpenId4VciSpec.VERSION,
                        "studentFullName" to studentFullName,
                    ),
                )
        }

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(documentoIdentidad)
        if (student == null) {
            return ServerResponse
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "generate-credentials-offer-form",
                    mapOf(
                        "error" to "No se encontró estudiante con documento: $documentoIdentidad",
                        "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                        "openid4VciVersion" to OpenId4VciSpec.VERSION,
                        "studentFullName" to studentFullName,
                    ),
                )
        }

        val usefulLinks = createUsefulLinks(metadata.id, metadata.authorizationServers[0])
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "select-credential-type",
                mapOf(
                    "student" to student,
                    "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                    "openid4VciVersion" to OpenId4VciSpec.VERSION,
                    "usefulLinks" to usefulLinks,
                    "studentFullName" to studentFullName,
                ),
            )
    }

    private suspend fun handleSelectCredentialType(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]

        log.info("Selecting credential type and loading programs")
        val formData = request.awaitFormData()
        val usuarioAutenticacion = formData["usuarioAutenticacion"]?.firstOrNull().orEmpty()
        val numeroIdentificacion = formData["numeroIdentificacion"]?.firstOrNull().orEmpty()
        val credentialType = formData["credentialType"]?.firstOrNull().orEmpty()
        val credentialsOfferUri = formData["credentialsOfferUri"]?.firstOrNull()

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(numeroIdentificacion)
        if (student == null) {
            return ServerResponse
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "generate-credentials-offer-form",
                    mapOf(
                        "error" to "No se encontró estudiante con documento: $numeroIdentificacion",
                        "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                        "openid4VciVersion" to OpenId4VciSpec.VERSION,
                        "studentFullName" to studentFullName,
                    ),
                )
        }

        val programs = getAcademicDataFromDatabase.findStudentPrograms(student.usuarioAutenticacion)
        if (programs.isEmpty()) {
            return ServerResponse
                .ok()
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "generate-credentials-offer-form",
                    mapOf(
                        "error" to "No se encontraron registros académicos para el estudiante",
                        "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                        "openid4VciVersion" to OpenId4VciSpec.VERSION,
                        "studentFullName" to studentFullName,
                    ),
                )
        }

        val usefulLinks = createUsefulLinks(metadata.id, metadata.authorizationServers[0])
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "select-academic-program",
                mapOf(
                    "student" to student,
                    "programs" to programs,
                    "credentialType" to credentialType,
                    "credentialsOfferUri" to (credentialsOfferUri ?: createCredentialsOffer.defaultCredentialOfferUri.toString()),
                    "openid4VciVersion" to OpenId4VciSpec.VERSION,
                    "usefulLinks" to usefulLinks,
                    "studentFullName" to studentFullName,
                ),
            )
    }

    private suspend fun handleSelectCredentialTypeBack(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]

        log.info("Going back to credential type selection")
        val usuarioAutenticacion = request.queryParam("usuarioAutenticacion").orElse("").orEmpty()
        val numeroIdentificacion = request.queryParam("numeroIdentificacion").orElse("").orEmpty()
        val tipoDocumento = request.queryParam("tipoDocumento").orElse("").orEmpty()
        val credentialsOfferUri = request.queryParam("credentialsOfferUri").orElse("")

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(numeroIdentificacion)
        if (student == null) {
            return ServerResponse
                .status(HttpStatus.SEE_OTHER)
                .renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
        }

        val usefulLinks = createUsefulLinks(metadata.id, metadata.authorizationServers[0])
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "select-credential-type",
                mapOf(
                    "student" to student,
                    "credentialsOfferUri" to (credentialsOfferUri.ifBlank { createCredentialsOffer.defaultCredentialOfferUri.toString() }),
                    "openid4VciVersion" to OpenId4VciSpec.VERSION,
                    "usefulLinks" to usefulLinks,
                    "studentFullName" to studentFullName,
                ),
            )
    }

    private suspend fun handleSelectProgramBack(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]

        log.info("Going back to program selection")
        val numeroIdentificacion = request.queryParam("numeroIdentificacion").orElse("").orEmpty()
        val credentialType = request.queryParam("credentialType").orElse("").orEmpty()
        val credentialsOfferUri = request.queryParam("credentialsOfferUri").orElse("")

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(numeroIdentificacion)
        if (student == null) {
            return ServerResponse
                .status(HttpStatus.SEE_OTHER)
                .renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
        }

        val programs = getAcademicDataFromDatabase.findStudentPrograms(student.usuarioAutenticacion)
        val usefulLinks = createUsefulLinks(metadata.id, metadata.authorizationServers[0])
        return ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "select-academic-program",
                mapOf(
                    "student" to student,
                    "programs" to programs,
                    "credentialType" to credentialType,
                    "credentialsOfferUri" to (credentialsOfferUri.ifBlank { createCredentialsOffer.defaultCredentialOfferUri.toString() }),
                    "openid4VciVersion" to OpenId4VciSpec.VERSION,
                    "usefulLinks" to usefulLinks,
                    "studentFullName" to studentFullName,
                ),
            )
    }

    /**
     * Botón «CARNET» de un programa ACTIVO. Es otra credencial del mismo issuer: guarda la misma selección que la
     * credencial académica y crea la oferta del carnet; no toca la generación de la credencial académica.
     */
    private suspend fun handleCarnet(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]
        val numeroIdentificacion = request.queryParam("numeroIdentificacion").orElse("").orEmpty()
        val credentialType = request.queryParam("credentialType").orElse("").orEmpty()
        val credentialsOfferUri = request.queryParam("credentialsOfferUri").orElse("")
        val historialId = request.queryParam("historialId").orElse("").toLongOrNull() ?: 0L

        val student = getAcademicDataFromDatabase.findStudentByDocumentNumber(numeroIdentificacion)
        // Solo el propio estudiante con la sesión abierta, y solo un programa suyo que siga ACTIVO.
        val program = student
            ?.takeIf { it.usuarioAutenticacion == session.attributes[SESSION_STUDENT_USERNAME] }
            ?.let { getAcademicDataFromDatabase.findStudentPrograms(it.usuarioAutenticacion) }
            ?.firstOrNull { it.historialId == historialId && it.estadoAcademico == "ACTIVO" }
        if (student == null || program == null) {
            return ServerResponse
                .status(HttpStatus.SEE_OTHER)
                .renderAndAwait("redirect:$GENERATE_CREDENTIALS_OFFER")
        }

        getAcademicDataFromDatabase.saveSelection(student.usuarioAutenticacion, numeroIdentificacion, program.programaId, program.historialId)

        return effect<CreateCredentialsOffer.Error, Uri> {
            val credentialIds = setOf(CredentialConfigurationId(CarnetCredentialScope.value))
            createCredentialsOffer(CreateCredentialsOffer.Request(credentialIds, credentialsOfferUri.ifBlank { null }, issuerState = ticketFor(session)))
        }.fold(
            transform = { generatedOfferUri ->
                context(generateQrCode) {
                    generatedOfferUri.credentialOfferSuccessResponse(
                        studentFullName,
                        numeroIdentificacion,
                        credentialType,
                        credentialsOfferUri,
                        esCarnet = true,
                    )
                }
            },
            recover = { error ->
                log.warn("Unable to generate Carnet offer. Error: {}", error)
                error.credentialOfferErrorResponse(studentFullName)
            },
        )
    }

    private suspend fun handleSelectProgram(request: ServerRequest): ServerResponse {
        val session = request.requireStudentSession()
            ?: return ServerResponse.status(HttpStatus.SEE_OTHER).renderAndAwait("redirect:$LOGIN")
        val studentFullName = session.attributes[SESSION_STUDENT_FULLNAME]

        log.debug("Saving selection and generating credential offer")
        val formData = request.awaitFormData()
        val usuarioAutenticacion = formData["usuarioAutenticacion"]?.firstOrNull().orEmpty()
        val numeroIdentificacion = formData["numeroIdentificacion"]?.firstOrNull().orEmpty()
        val credentialType = formData["credentialType"]?.firstOrNull().orEmpty()
        val programmaId = formData["programaId"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val historialId = formData["historialId"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val credentialsOfferUri = formData["credentialsOfferUri"]?.firstOrNull()

        getAcademicDataFromDatabase.saveSelection(
            usuarioAutenticacion,
            numeroIdentificacion,
            programmaId,
            historialId,
        )

        return effect<CreateCredentialsOffer.Error, Uri> {
            val credentialIds = setOf(CredentialConfigurationId(AcademicCredentialScope.value))
            val createCredentialOfferRequest = CreateCredentialsOffer.Request(credentialIds, credentialsOfferUri, issuerState = ticketFor(session))
            createCredentialsOffer(createCredentialOfferRequest)
        }.fold(
            transform = { generatedOfferUri ->
                context(generateQrCode) {
                    generatedOfferUri.credentialOfferSuccessResponse(
                        studentFullName,
                        numeroIdentificacion,
                        credentialType,
                        credentialsOfferUri.orEmpty(),
                    )
                }
            },
            recover = { error ->
                log.warn("Unable to generate Credentials Offer. Error: {}", error)
                error.credentialOfferErrorResponse(studentFullName)
            },
        )
    }

    private fun createUsefulLinks(
        credentialIssuer: CredentialIssuerId,
        authorizationServer: HttpsUrl,
    ): Map<String, String> {
        fun HttpsUrl.wellKnown(path: String): HttpsUrl =
            HttpsUrl.unsafe(
                value
                    .buildUpon()
                    .path(null)
                    .appendPath(".well-known")
                    .appendPath(path)
                    .apply {
                        value.pathSegments
                            .filterNot { it.isBlank() }
                            .forEach { appendPath(it) }
                    }.build()
                    .toString(),
            )

        val credentialIssuerMetadata = credentialIssuer.wellKnown("openid-credential-issuer")
        val protectedResourceMetadata = credentialIssuer.wellKnown("oauth-protected-resource")
        val authorizationServerMetadata = authorizationServer.wellKnown("oauth-authorization-server")
        val sdJwtVcIssuerMetadata = credentialIssuer.wellKnown("jwt-vc-issuer")

        return mapOf(
            "credential_issuer_metadata" to credentialIssuerMetadata.externalForm,
            "protected_resource_metadata" to protectedResourceMetadata.externalForm,
            "authorization_server_metadata" to authorizationServerMetadata.externalForm,
            "sdjwt_vc_issuer_metadata" to sdJwtVcIssuerMetadata.externalForm,
        )
    }

    companion object {
        /** Vigencia del ticket de la oferta: lo justo para escanear el QR y abrir la wallet. */
        val TICKET_TTL = 10.minutes
        const val LOGIN: String = "/issuer/login"
        const val LOGIN_PID: String = "/issuer/login/pid"
        const val LOGIN_PID_ESTADO_BASE: String = "/issuer/login/pid/estado"
        const val LOGIN_PID_ESTADO: String = "/issuer/login/pid/estado/{id}"
        const val LOGOUT: String = "/issuer/logout"
        const val GENERATE_CREDENTIALS_OFFER: String = "/issuer/credentialsOffer/generate"
        const val SELECT_CREDENTIAL_TYPE: String = "/issuer/credentialsOffer/selectCredentialType"
        const val SELECT_CREDENTIAL_TYPE_BACK: String = "/issuer/credentialsOffer/selectCredentialTypeBack"
        const val SELECT_PROGRAM: String = "/issuer/credentialsOffer/selectProgram"
        const val SELECT_PROGRAM_BACK: String = "/issuer/credentialsOffer/selectProgramBack"
        const val CARNET: String = "/issuer/credentialsOffer/carnet"
        private const val SESSION_STUDENT_USERNAME: String = "studentUsername"
        private const val SESSION_STUDENT_FULLNAME: String = "studentFullName"
        private val log = LoggerFactory.getLogger(IssuerUi::class.java)
    }
}

context(generateQrCode: GenerateQqCode)
private suspend fun Uri.credentialOfferSuccessResponse(
    studentFullName: Any?,
    numeroIdentificacion: String,
    credentialType: String,
    credentialsOfferUri: String,
    esCarnet: Boolean = false,
): ServerResponse {
    val uri = this@credentialOfferSuccessResponse
    val qrCode = generateQrCode(uri, Format.PNG, Dimensions(Pixels(640u), Pixels(640u)))
    return ServerResponse
        .ok()
        .contentType(MediaType.TEXT_HTML)
        .renderAndAwait(
            "display-credentials-offer",
            mapOf(
                "uri" to uri.toString(),
                "qrCode" to Base64.encode(qrCode),
                "qrCodeMediaType" to "image/png",
                "studentFullName" to studentFullName,
                "numeroIdentificacion" to numeroIdentificacion,
                "credentialType" to credentialType,
                "credentialsOfferUri" to credentialsOfferUri,
                "esCarnet" to esCarnet,
            ),
        )
}

private suspend fun CreateCredentialsOffer.Error.credentialOfferErrorResponse(studentFullName: Any?): ServerResponse =
    ServerResponse
        .badRequest()
        .contentType(MediaType.TEXT_HTML)
        .renderAndAwait(
            "generate-credentials-offer-error",
            mapOf(
                "error" to this::class.java.canonicalName,
                "openid4VciVersion" to OpenId4VciSpec.VERSION,
                "studentFullName" to studentFullName,
            ),
        )

@kotlinx.serialization.Serializable
private data class PidEstadoTO(
    @kotlinx.serialization.SerialName("estado") val estado: String,
    @kotlinx.serialization.SerialName("motivo") val motivo: String? = null,
    @kotlinx.serialization.SerialName("siguiente") val siguiente: String? = null,
)

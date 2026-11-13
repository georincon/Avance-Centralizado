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
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import eu.europa.ec.eudi.pidissuer.domain.HttpsUrl
import eu.europa.ec.eudi.pidissuer.domain.NonBlankString
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.port.out.notification.SubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.flow

private val log = LoggerFactory.getLogger(GetAcademicDataFromDatabase::class.java)

data class AcademicDatabaseConfig(
    val institutionName: String,
    val institutionCountry: String,
    val institutionUri: String,
    val institutionNit: String? = null,
)

/**
 * Public data class for returning student info to the UI
 */
data class StudentInfo(
    val usuarioAutenticacion: String,
    val nombres: String,
    val apellidos: String,
    val tipoIdentificacion: String,
    val numeroIdentificacion: String,
    val correoElectronico: String? = null,
    val numeroContacto: String? = null,
)

private data class StudentRecord(
    val usuarioAutenticacion: String,
    val nombres: String,
    val apellidos: String,
    val tipoIdentificacion: String,
    val numeroIdentificacion: String,
    val correoElectronico: String? = null,
    val numeroContacto: String? = null,
) {
    fun toStudentInfo() =
        StudentInfo(
            usuarioAutenticacion = usuarioAutenticacion,
            nombres = nombres,
            apellidos = apellidos,
            tipoIdentificacion = tipoIdentificacion,
            numeroIdentificacion = numeroIdentificacion,
            correoElectronico = correoElectronico,
            numeroContacto = numeroContacto,
        )
}

private data class StoredPassword(
    val hash: String?,
    val legacyPlain: String?,
)

private data class ProgramRecord(
    val codigo: String,
    val nombre: String,
    val nivelAcademico: String,
    val modalidad: String,
    val facultad: String? = null,
    val unidadAcademica: String? = null,
    val tituloOtorgado: String,
    val duracionSemestres: Int,
    val creditos: Int,
)

private data class HistoryRecord(
    val idHistorial: Long,
    val programaId: Long,
    val estudianteId: Long,
    val fechaIngreso: String? = null,
    val fechaDeGrado: String? = null,
    val promedioAcumulado: String? = null,
    val creditosAprobados: Int,
    val semestresCursados: Int,
    val estadoAcademico: String,
)

/**
 * Info about a student's academic program (for selection UI)
 */
data class StudentProgramInfo(
    val historialId: Long,
    val programaId: Long,
    val codigo: String,
    val nombrePrograma: String,
    val nivelAcademico: String,
    val modalidad: String,
    val tituloOtorgado: String,
    val facultad: String?,
    val unidadAcademica: String?,
    val estadoAcademico: String,
    val fechaIngreso: String?,
    val fechaDeGrado: String?,
    val promedioAcumulado: String?,
    val creditosAprobados: Int,
    val semestresCursados: Int,
    val duracionSemestres: Int,
    val totalCredits: Int,
)

class GetAcademicDataFromDatabase(
    private val databaseClient: DatabaseClient,
    private val databaseConfig: AcademicDatabaseConfig,
    private val passwordVerifier: StudentPasswordVerifier = StudentPasswordVerifier(),
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
) : GetAttestationAttributes<AcademicCredential> {
    context(_: Raise<IssueCredentialError.AttestationDatasetNotFound>, authorizationContext: AuthorizationContext)
    override suspend fun invoke(): AcademicCredential {
        log.info("Fetching academic data for user: {}", authorizationContext.username)
        val username = authorizationContext.username

        // Estudiante con la emisión bloqueada: no se le emite nada (sus datos no se tocan)
        ensure(!isSubjectBlocked(username)) {
            log.warn("Issuance refused for a blocked student: {}", username)
            IssueCredentialError.AttestationDatasetNotFound
        }

        val student = fetchStudentByUsername(username)
        ensureNotNull(student) {
            log.warn("Student not found for username: {}", username)
            IssueCredentialError.AttestationDatasetNotFound
        }

        val selection = fetchSelection(student.usuarioAutenticacion)
        if (selection != null) {
            log.info(
                "Using selected program for user {}: programa={}, historial={}",
                username,
                selection.programaId,
                selection.historialId,
            )
            return buildAcademicCredential(student, selection)
        }

        log.warn(
            "No stored program selection for user {}, falling back to the GRADUADO-first heuristic",
            username,
        )

        val historyRecords = fetchAcademicHistory(student.usuarioAutenticacion)
        ensure(historyRecords.isNotEmpty()) {
            log.warn("No academic records found for student: {}", username)
            IssueCredentialError.AttestationDatasetNotFound
        }

        val relevantRecord =
            historyRecords
                .sortedByDescending { it.estadoAcademico == "GRADUADO" }
                .first()

        val program = fetchProgramById(relevantRecord.programaId)
        ensureNotNull(program) {
            log.warn("Program not found for ID: {}", relevantRecord.programaId)
            IssueCredentialError.AttestationDatasetNotFound
        }

        return buildAcademicCredential(student, program, relevantRecord)
    }

    /**
     * Look up a student by their document number (numero_identificacion).
     */
    suspend fun findStudentByDocumentNumber(numeroIdentificacion: String): StudentInfo? {
        log.info("Looking up student by document number: {}", numeroIdentificacion)
        val query =
            """
            SELECT usuario_autenticacion, nombres, apellidos, tipo_identificacion,
                   numero_identificacion, correo_electronico, numero_contacto
            FROM datos_estudiantes
            WHERE numero_identificacion = :docNumber
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("docNumber", numeroIdentificacion)
            .map { row, _ ->
                StudentRecord(
                    usuarioAutenticacion = row.get("usuario_autenticacion") as String,
                    nombres = row.get("nombres") as String,
                    apellidos = row.get("apellidos") as String,
                    tipoIdentificacion = row.get("tipo_identificacion") as String,
                    numeroIdentificacion = row.get("numero_identificacion") as String,
                    correoElectronico = row.get("correo_electronico") as? String,
                    numeroContacto = row.get("numero_contacto") as? String,
                )
            }.awaitOneOrNull()
            ?.toStudentInfo()
    }

    /**
     * Datos de contacto del estudiante, SOLO para avisarle de una revocación. Es una lectura: no modifica nada.
     */
    suspend fun contactOf(username: String): SubjectContact? =
        databaseClient
            .sql("SELECT correo_electronico, numero_contacto FROM datos_estudiantes WHERE usuario_autenticacion = :username")
            .bind("username", username)
            .map { row, _ ->
                SubjectContact(
                    email = row.get("correo_electronico", String::class.java),
                    phone = row.get("numero_contacto", String::class.java),
                )
            }.awaitOneOrNull()

    /**
     * Validate a student's login credentials against datos_estudiantes.
     * Returns the student's info on success, null on invalid credentials.
     */
    suspend fun authenticateStudent(
        usuarioAutenticacion: String,
        claveAutenticacion: String,
    ): StudentInfo? {
        log.info("Authenticating student: {}", usuarioAutenticacion)
        // La clave nunca viaja en la consulta: se busca al estudiante por usuario y la clave se verifica en la aplicación.
        val query =
            """
            SELECT usuario_autenticacion, nombres, apellidos, tipo_identificacion,
                   numero_identificacion, correo_electronico, numero_contacto,
                   clave_hash, clave_autenticacion
            FROM datos_estudiantes
            WHERE usuario_autenticacion = :username
            """.trimIndent()

        val found =
            databaseClient
                .sql(query)
                .bind("username", usuarioAutenticacion)
                .map { row, _ ->
                    StudentRecord(
                        usuarioAutenticacion = row.get("usuario_autenticacion") as String,
                        nombres = row.get("nombres") as String,
                        apellidos = row.get("apellidos") as String,
                        tipoIdentificacion = row.get("tipo_identificacion") as String,
                        numeroIdentificacion = row.get("numero_identificacion") as String,
                        correoElectronico = row.get("correo_electronico") as? String,
                        numeroContacto = row.get("numero_contacto") as? String,
                    ) to StoredPassword(
                        hash = row.get("clave_hash") as? String,
                        legacyPlain = row.get("clave_autenticacion") as? String,
                    )
                }.awaitOneOrNull()

        // bcrypt es costoso a propósito: se ejecuta fuera del event loop.
        val check =
            withContext(Dispatchers.Default) {
                if (found == null) {
                    passwordVerifier.verifyUnknownUser(claveAutenticacion)
                } else {
                    val (_, stored) = found
                    passwordVerifier.verify(claveAutenticacion, stored.hash, stored.legacyPlain)
                }
            }

        return when (check) {
            PasswordCheck.VALID -> found?.first?.toStudentInfo()
            PasswordCheck.VALID_LEGACY -> {
                log.warn("Student {} authenticated with a legacy plaintext password: run the clave_hash migration", usuarioAutenticacion)
                found?.first?.toStudentInfo()
            }
            PasswordCheck.INVALID -> null
        }
    }

    /**
     * Fetch all academic programs for a student (by usuario_autenticacion).
     */
    suspend fun findStudentPrograms(usuarioAutenticacion: String): List<StudentProgramInfo> {
        log.info("Fetching programs for student: {}", usuarioAutenticacion)
        val query =
            """
            SELECT h.id_historial, h.programa_id, p.codigo, p.nombre, p.nivel_academico,
                   p.modalidad, p.titulo_otorgado, p.facultad, p.unidad_academica,
                   h.estado_academico, h.fecha_ingreso, h.fecha_de_grado, h.promedio_acumulado,
                   h.creditos_aprobados, h.semestres_cursados,
                   p.duracion_semestres, p.creditos
            FROM historial_estudiantes h
            JOIN datos_estudiantes e ON h.estudiante_id = e.id_estudiante
            JOIN programas_academicos p ON h.programa_id = p.id_programa
            WHERE e.usuario_autenticacion = :username
            ORDER BY h.estado_academico = 'GRADUADO' DESC, h.fecha_ingreso DESC
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("username", usuarioAutenticacion)
            .map { row, _ ->
                StudentProgramInfo(
                    historialId = row.get("id_historial") as Long,
                    programaId = row.get("programa_id") as Long,
                    codigo = row.get("codigo") as String,
                    nombrePrograma = row.get("nombre") as String,
                    nivelAcademico = row.get("nivel_academico") as String,
                    modalidad = row.get("modalidad") as String,
                    tituloOtorgado = row.get("titulo_otorgado") as String,
                    facultad = row.get("facultad") as? String,
                    unidadAcademica = row.get("unidad_academica") as? String,
                    estadoAcademico = row.get("estado_academico") as String,
                    fechaIngreso = row.get("fecha_ingreso")?.toString(),
                    fechaDeGrado = row.get("fecha_de_grado")?.toString(),
                    promedioAcumulado = row.get("promedio_acumulado") as? String,
                    creditosAprobados = row.get("creditos_aprobados") as Int,
                    semestresCursados = row.get("semestres_cursados") as Int,
                    duracionSemestres = row.get("duracion_semestres") as Int,
                    totalCredits = row.get("creditos") as Int,
                )
            }.flow()
            .toList()
    }

    /**
     * Save a user's program selection for credential issuance.
     */
    suspend fun saveSelection(
        usuarioAutenticacion: String,
        numeroIdentificacion: String,
        programaId: Long,
        historialId: Long,
    ) {
        log.info("Saving selection for user {}: programa={}, historial={}", usuarioAutenticacion, programaId, historialId)
        databaseClient
            .sql("DELETE FROM academic_selections WHERE usuario_autenticacion = :username")
            .bind("username", usuarioAutenticacion)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient
            .sql(
                """
                INSERT INTO academic_selections (usuario_autenticacion, numero_identificacion, programa_id, historial_id)
                VALUES (:username, :docNumber, :programaId, :historialId)
                """.trimIndent(),
            ).bind("username", usuarioAutenticacion)
            .bind("docNumber", numeroIdentificacion)
            .bind("programaId", programaId)
            .bind("historialId", historialId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Fetch a stored selection for a user.
     */
    suspend fun fetchSelection(usuarioAutenticacion: String): SelectionInfo? {
        log.info("Fetching selection for user: {}", usuarioAutenticacion)
        val query =
            """
            SELECT s.programa_id, s.historial_id, s.numero_identificacion,
                   p.codigo, p.nombre, p.nivel_academico, p.modalidad,
                   p.facultad, p.unidad_academica, p.titulo_otorgado,
                   p.duracion_semestres, p.creditos,
                   h.fecha_ingreso, h.fecha_de_grado, h.promedio_acumulado,
                   h.creditos_aprobados, h.semestres_cursados, h.estado_academico
            FROM academic_selections s
            JOIN programas_academicos p ON s.programa_id = p.id_programa
            JOIN historial_estudiantes h ON s.historial_id = h.id_historial
            WHERE s.usuario_autenticacion = :username
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("username", usuarioAutenticacion)
            .map { row, _ ->
                SelectionInfo(
                    usuarioAutenticacion = usuarioAutenticacion,
                    numeroIdentificacion = row.get("numero_identificacion") as String,
                    programaId = row.get("programa_id") as Long,
                    historialId = row.get("historial_id") as Long,
                    codigo = row.get("codigo") as String,
                    nombre = row.get("nombre") as String,
                    nivelAcademico = row.get("nivel_academico") as String,
                    modalidad = row.get("modalidad") as String,
                    facultad = row.get("facultad") as? String,
                    unidadAcademica = row.get("unidad_academica") as? String,
                    tituloOtorgado = row.get("titulo_otorgado") as String,
                    duracionSemestres = row.get("duracion_semestres") as Int,
                    creditos = row.get("creditos") as Int,
                    fechaIngreso = row.get("fecha_ingreso")?.toString(),
                    fechaDeGrado = row.get("fecha_de_grado")?.toString(),
                    promedioAcumulado = row.get("promedio_acumulado") as? String,
                    creditosAprobados = row.get("creditos_aprobados") as Int,
                    semestresCursados = row.get("semestres_cursados") as Int,
                    estadoAcademico = row.get("estado_academico") as String,
                )
            }.awaitOneOrNull()
    }

    private suspend fun fetchStudentByUsername(username: String): StudentRecord? {
        val query =
            """
            SELECT usuario_autenticacion, nombres, apellidos, tipo_identificacion,
                   numero_identificacion, correo_electronico, numero_contacto
            FROM datos_estudiantes
            WHERE usuario_autenticacion = :username
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("username", username)
            .map { row, _ ->
                StudentRecord(
                    usuarioAutenticacion = row.get("usuario_autenticacion") as String,
                    nombres = row.get("nombres") as String,
                    apellidos = row.get("apellidos") as String,
                    tipoIdentificacion = row.get("tipo_identificacion") as String,
                    numeroIdentificacion = row.get("numero_identificacion") as String,
                    correoElectronico = row.get("correo_electronico") as? String,
                    numeroContacto = row.get("numero_contacto") as? String,
                )
            }.awaitOneOrNull()
    }

    private suspend fun fetchAcademicHistory(username: String): List<HistoryRecord> {
        val query =
            """
            SELECT h.id_historial, h.programa_id, h.estudiante_id, h.fecha_ingreso, h.fecha_de_grado,
                   h.promedio_acumulado, h.creditos_aprobados, h.semestres_cursados, h.estado_academico
            FROM historial_estudiantes h
            JOIN datos_estudiantes e ON h.estudiante_id = e.id_estudiante
            WHERE e.usuario_autenticacion = :username
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("username", username)
            .map { row, _ ->
                HistoryRecord(
                    idHistorial = row.get("id_historial") as Long,
                    programaId = row.get("programa_id") as Long,
                    estudianteId = row.get("estudiante_id") as Long,
                    fechaIngreso = row.get("fecha_ingreso")?.toString(),
                    fechaDeGrado = row.get("fecha_de_grado")?.toString(),
                    promedioAcumulado = row.get("promedio_acumulado") as? String,
                    creditosAprobados = row.get("creditos_aprobados") as Int,
                    semestresCursados = row.get("semestres_cursados") as Int,
                    estadoAcademico = row.get("estado_academico") as String,
                )
            }.flow()
            .toList()
    }

    private suspend fun fetchProgramById(programId: Long): ProgramRecord? {
        val query =
            """
            SELECT codigo, nombre, nivel_academico, modalidad, facultad,
                   unidad_academica, titulo_otorgado, duracion_semestres, creditos
            FROM programas_academicos
            WHERE id_programa = :programId
            """.trimIndent()

        return databaseClient
            .sql(query)
            .bind("programId", programId)
            .map { row, _ ->
                ProgramRecord(
                    codigo = row.get("codigo") as String,
                    nombre = row.get("nombre") as String,
                    nivelAcademico = row.get("nivel_academico") as String,
                    modalidad = row.get("modalidad") as String,
                    facultad = row.get("facultad") as? String,
                    unidadAcademica = row.get("unidad_academica") as? String,
                    tituloOtorgado = row.get("titulo_otorgado") as String,
                    duracionSemestres = row.get("duracion_semestres") as Int,
                    creditos = row.get("creditos") as Int,
                )
            }.awaitOneOrNull()
    }

    private fun parseLocalDateOrNull(value: String?): LocalDate? =
        value?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Builds the credential straight from a user's stored program [selection], so the person
     * whose credentials-offer QR was generated for a specific program (e.g. a specialization)
     * actually receives that program's data instead of always the GRADUADO-first heuristic.
     */
    private fun buildAcademicCredential(
        student: StudentRecord,
        selection: SelectionInfo,
    ): AcademicCredential {
        val localDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        return AcademicCredential(
            institution =
                IssuingInstitution(
                    name = NonBlankString(databaseConfig.institutionName),
                    country = NonBlankString(databaseConfig.institutionCountry),
                    uri = HttpsUrl.unsafe(databaseConfig.institutionUri),
                    nit = databaseConfig.institutionNit,
                ),
            studentId =
                StudentIdentification(
                    type = IdentificationType.valueOf(student.tipoIdentificacion),
                    number = student.numeroIdentificacion,
                ),
            username = NonBlankString(student.usuarioAutenticacion),
            familyName = NonBlankString(student.apellidos),
            givenName = NonBlankString(student.nombres),
            email = student.correoElectronico,
            phoneNumber = student.numeroContacto,
            academicRecord =
                AcademicRecord(
                    program =
                        AcademicProgram(
                            code = NonBlankString(selection.codigo),
                            name = NonBlankString(selection.nombre),
                            level = AcademicLevel.valueOf(selection.nivelAcademico),
                            modality = ProgramModality.valueOf(selection.modalidad),
                            faculty = selection.facultad,
                            academicUnit = selection.unidadAcademica,
                            awardedTitle = selection.tituloOtorgado,
                            durationSemesters = selection.duracionSemestres,
                            totalCredits = selection.creditos,
                        ),
                    enrollmentDate = parseLocalDateOrNull(selection.fechaIngreso),
                    graduationDate = parseLocalDateOrNull(selection.fechaDeGrado),
                    accumulatedAverage = selection.promedioAcumulado,
                    approvedCredits = selection.creditosAprobados,
                    semestersCompleted = selection.semestresCursados,
                    status = AcademicStatus.valueOf(selection.estadoAcademico),
                ),
            dateOfIssuance = localDate,
            documentNumber = student.numeroIdentificacion,
        )
    }

    private fun buildAcademicCredential(
        student: StudentRecord,
        program: ProgramRecord,
        history: HistoryRecord,
    ): AcademicCredential {
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        val enrollmentDate = parseLocalDateOrNull(history.fechaIngreso)
        val graduationDate = parseLocalDateOrNull(history.fechaDeGrado)

        return AcademicCredential(
            institution =
                IssuingInstitution(
                    name = NonBlankString(databaseConfig.institutionName),
                    country = NonBlankString(databaseConfig.institutionCountry),
                    uri = HttpsUrl.unsafe(databaseConfig.institutionUri),
                    nit = databaseConfig.institutionNit,
                ),
            studentId =
                StudentIdentification(
                    type = IdentificationType.valueOf(student.tipoIdentificacion),
                    number = student.numeroIdentificacion,
                ),
            username = NonBlankString(student.usuarioAutenticacion),
            familyName = NonBlankString(student.apellidos),
            givenName = NonBlankString(student.nombres),
            email = student.correoElectronico,
            phoneNumber = student.numeroContacto,
            academicRecord =
                AcademicRecord(
                    program =
                        AcademicProgram(
                            code = NonBlankString(program.codigo),
                            name = NonBlankString(program.nombre),
                            level = AcademicLevel.valueOf(program.nivelAcademico),
                            modality = ProgramModality.valueOf(program.modalidad),
                            faculty = program.facultad,
                            academicUnit = program.unidadAcademica,
                            awardedTitle = program.tituloOtorgado,
                            durationSemesters = program.duracionSemestres,
                            totalCredits = program.creditos,
                        ),
                    enrollmentDate = enrollmentDate,
                    graduationDate = graduationDate,
                    accumulatedAverage = history.promedioAcumulado,
                    approvedCredits = history.creditosAprobados,
                    semestersCompleted = history.semestresCursados,
                    status = AcademicStatus.valueOf(history.estadoAcademico),
                ),
            dateOfIssuance = localDate,
            documentNumber = student.numeroIdentificacion,
        )
    }
}

/**
 * Data class representing a stored selection
 */
data class SelectionInfo(
    val usuarioAutenticacion: String,
    val numeroIdentificacion: String,
    val programaId: Long,
    val historialId: Long,
    val codigo: String,
    val nombre: String,
    val nivelAcademico: String,
    val modalidad: String,
    val facultad: String?,
    val unidadAcademica: String?,
    val tituloOtorgado: String,
    val duracionSemestres: Int,
    val creditos: Int,
    val fechaIngreso: String?,
    val fechaDeGrado: String?,
    val promedioAcumulado: String?,
    val creditosAprobados: Int,
    val semestresCursados: Int,
    val estadoAcademico: String,
)

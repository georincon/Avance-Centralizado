/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import java.time.LocalDate

private val log = LoggerFactory.getLogger(UisAcademicStore::class.java)

/** Conexión a la base académica (`uis_academic`), distinta de la base del propio issuer. */
class AcademicDb(val client: DatabaseClient)

val TIPOS_IDENTIFICACION = listOf("CC", "CE", "TI", "PAS", "PPT", "PEP")
val ESTADOS_ACADEMICOS = listOf("ACTIVO", "GRADUADO", "PAUSA", "RETIRADO")

/** Tipos de documento que NO tienen PID de la Registraduría: entran con código de activación del registro de la UIS. */
val TIPOS_SIN_PID = setOf("CE", "PAS", "PPT", "PEP")

data class Estudiante(
    val numeroIdentificacion: String,
    val tipoIdentificacion: String,
    val nombres: String,
    val apellidos: String,
    val numeroContacto: String? = null,
    val correoElectronico: String? = null,
)

data class ProgramaRef(val id: Long, val codigo: String, val nombre: String, val duracionSemestres: Int, val creditos: Int)

data class HistorialFila(
    val id: Long,
    val programaId: Long,
    val programaNombre: String,
    val fechaIngreso: String?,
    val fechaDeGrado: String?,
    val promedioAcumulado: String?,
    val creditosAprobados: Int?,
    val semestresCursados: Int?,
    val estadoAcademico: String,
)

data class HistorialEntrada(
    val id: Long?,
    val programaId: Long,
    val fechaIngreso: String?,
    val fechaDeGrado: String?,
    val promedioAcumulado: String?,
    val creditosAprobados: Int?,
    val semestresCursados: Int?,
    val estadoAcademico: String,
)

/** Alta, edición y consulta de estudiantes y de su historial por programa (`datos_estudiantes`, `historial_estudiantes`). */
class UisAcademicStore(private val db: AcademicDb) {
    private val c get() = db.client

    /** Orden alfabético en español: la base ordena por bytes (una «é» quedaría después de la «z»), así que se ordena aquí. */
    private val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.of("es", "CO"))

    suspend fun listar(q: String?): List<Estudiante> {
        val filtro = q?.trim()?.takeIf { it.isNotEmpty() }?.let { "%${it.lowercase()}%" }
        var spec = c.sql(
            """SELECT numero_identificacion, tipo_identificacion, nombres, apellidos, numero_contacto, correo_electronico
               FROM datos_estudiantes
               ${if (filtro != null) "WHERE lower(numero_identificacion) LIKE :q OR lower(nombres || ' ' || apellidos) LIKE :q" else ""}
               ORDER BY apellidos, nombres""",
        )
        if (filtro != null) spec = spec.bind("q", filtro)
        return spec.map { row, _ -> row.estudiante() }.all().collectList().awaitSingle()
            .sortedWith(compareBy(collator) { it: Estudiante -> it.apellidos }.thenBy(collator) { it.nombres })
    }

    suspend fun buscar(numero: String): Estudiante? =
        c.sql(
            """SELECT numero_identificacion, tipo_identificacion, nombres, apellidos, numero_contacto, correo_electronico
               FROM datos_estudiantes WHERE numero_identificacion = :n""",
        ).bind("n", numero).map { row, _ -> row.estudiante() }.one().awaitFirstOrNull()

    /** Crea o actualiza por número de identificación; el usuario de autenticación es siempre ese número. */
    suspend fun guardar(e: Estudiante) {
        val ya = buscar(e.numeroIdentificacion) != null
        val sql =
            if (ya) {
                """UPDATE datos_estudiantes SET tipo_identificacion = :t, nombres = :nom, apellidos = :ape,
                   numero_contacto = :tel, correo_electronico = :mail, usuario_autenticacion = :n WHERE numero_identificacion = :n"""
            } else {
                """INSERT INTO datos_estudiantes (usuario_autenticacion, nombres, apellidos, tipo_identificacion, numero_identificacion,
                   numero_contacto, correo_electronico) VALUES (:n, :nom, :ape, :t, :n, :tel, :mail)"""
            }
        var spec = c.sql(sql)
            .bind("n", e.numeroIdentificacion).bind("t", e.tipoIdentificacion).bind("nom", e.nombres).bind("ape", e.apellidos)
        spec = if (e.numeroContacto.isNullOrBlank()) spec.bindNull("tel", String::class.java) else spec.bind("tel", e.numeroContacto)
        spec = if (e.correoElectronico.isNullOrBlank()) spec.bindNull("mail", String::class.java) else spec.bind("mail", e.correoElectronico)
        spec.fetch().rowsUpdated().awaitSingle()
    }

    suspend fun borrar(numero: String): Boolean =
        c.sql("DELETE FROM datos_estudiantes WHERE numero_identificacion = :n").bind("n", numero).fetch().rowsUpdated().awaitSingle() > 0

    suspend fun programas(): List<ProgramaRef> =
        c.sql("SELECT id_programa, codigo, nombre, duracion_semestres, creditos FROM programas_academicos WHERE activo ORDER BY nombre")
            .map { row, _ ->
                ProgramaRef(
                    (row.get("id_programa") as Number).toLong(), row.get("codigo") as String, row.get("nombre") as String,
                    (row.get("duracion_semestres") as Number).toInt(), (row.get("creditos") as Number).toInt(),
                )
            }.all().collectList().awaitSingle().sortedWith(compareBy(collator) { it: ProgramaRef -> it.nombre })

    suspend fun historial(numero: String): List<HistorialFila> =
        c.sql(
            """SELECT h.id_historial, h.programa_id, p.nombre, h.fecha_ingreso, h.fecha_de_grado, h.promedio_acumulado,
                      h.creditos_aprobados, h.semestres_cursados, h.estado_academico
               FROM historial_estudiantes h
               JOIN datos_estudiantes e ON e.id_estudiante = h.estudiante_id
               JOIN programas_academicos p ON p.id_programa = h.programa_id
               WHERE e.numero_identificacion = :n ORDER BY h.fecha_ingreso NULLS LAST, h.id_historial""",
        ).bind("n", numero).map { row, _ ->
            HistorialFila(
                (row.get("id_historial") as Number).toLong(), (row.get("programa_id") as Number).toLong(), row.get("nombre") as String,
                row.get("fecha_ingreso")?.toString(), row.get("fecha_de_grado")?.toString(), row.get("promedio_acumulado") as? String,
                (row.get("creditos_aprobados") as? Number)?.toInt(), (row.get("semestres_cursados") as? Number)?.toInt(),
                row.get("estado_academico") as String,
            )
        }.all().collectList().awaitSingle()

    /** Inserta o actualiza una fila del historial. Las reglas de coherencia (estado, créditos, semestres) las valida un trigger de la BD. */
    suspend fun guardarHistorial(numero: String, h: HistorialEntrada) {
        val id = c.sql("SELECT id_estudiante FROM datos_estudiantes WHERE numero_identificacion = :n").bind("n", numero)
            .map { row, _ -> (row.get("id_estudiante") as Number).toLong() }.one().awaitFirstOrNull()
            ?: error("El estudiante no existe")
        val sql =
            if (h.id == null) {
                """INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado,
                   creditos_aprobados, semestres_cursados, estado_academico) VALUES (:p, :e, :fi, :fg, :prom, :cred, :sem, :est)"""
            } else {
                """UPDATE historial_estudiantes SET programa_id = :p, fecha_ingreso = :fi, fecha_de_grado = :fg, promedio_acumulado = :prom,
                   creditos_aprobados = :cred, semestres_cursados = :sem, estado_academico = :est
                   WHERE id_historial = :hid AND estudiante_id = :e"""
            }
        var spec = c.sql(sql).bind("p", h.programaId).bind("e", id).bind("est", h.estadoAcademico)
        if (h.id != null) spec = spec.bind("hid", h.id)
        spec = h.fechaIngreso.fecha().let { if (it == null) spec.bindNull("fi", LocalDate::class.java) else spec.bind("fi", it) }
        spec = h.fechaDeGrado.fecha().let { if (it == null) spec.bindNull("fg", LocalDate::class.java) else spec.bind("fg", it) }
        spec = if (h.promedioAcumulado.isNullOrBlank()) spec.bindNull("prom", String::class.java) else spec.bind("prom", h.promedioAcumulado)
        spec = if (h.creditosAprobados == null) spec.bindNull("cred", Integer::class.java) else spec.bind("cred", h.creditosAprobados)
        spec = if (h.semestresCursados == null) spec.bindNull("sem", Integer::class.java) else spec.bind("sem", h.semestresCursados)
        spec.fetch().rowsUpdated().awaitSingle()
    }

    suspend fun borrarHistorial(numero: String, historialId: Long): Boolean =
        c.sql(
            """DELETE FROM historial_estudiantes WHERE id_historial = :h
               AND estudiante_id = (SELECT id_estudiante FROM datos_estudiantes WHERE numero_identificacion = :n)""",
        ).bind("h", historialId).bind("n", numero).fetch().rowsUpdated().awaitSingle() > 0

    private fun io.r2dbc.spi.Readable.estudiante() =
        Estudiante(
            numeroIdentificacion = get("numero_identificacion") as String,
            tipoIdentificacion = get("tipo_identificacion") as String,
            nombres = get("nombres") as String,
            apellidos = get("apellidos") as String,
            numeroContacto = get("numero_contacto") as? String,
            correoElectronico = get("correo_electronico") as? String,
        )

    private fun String?.fecha(): LocalDate? = this?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
}

/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.periodUntil
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.plus

/**
 * Número de documento de identidad (cédula de ciudadanía) del titular. En Colombia el número de la
 * licencia de conducción es el mismo de la cédula, y es la clave de búsqueda tanto del panel admin
 * como del flujo de emisión sin login.
 */
@JvmInline
value class LicenciaDocumentNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "documentNumber cannot be blank" }
    }
}

enum class Sexo {
    M,
    F,
}

enum class GrupoSanguineo(
    val etiqueta: String,
) {
    O_POS("O+"),
    O_NEG("O-"),
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    ;

    companion object {
        fun fromEtiqueta(etiqueta: String): GrupoSanguineo =
            entries.find { it.etiqueta == etiqueta }
                ?: error("Unknown GrupoSanguineo label: $etiqueta")
    }
}

/** Estado de la licencia en el organismo de tránsito. Solo las ACTIVAS pueden emitirse a la wallet. */
enum class EstadoLicencia(
    val etiqueta: String,
) {
    ACTIVA("Activa"),
    SUSPENDIDA("Suspendida"),
    CANCELADA("Cancelada"),
}

enum class ServicioLicencia(
    val etiqueta: String,
) {
    PARTICULAR("Servicio particular"),
    PUBLICO("Servicio público"),
}

/**
 * Categorías de la licencia de conducción en Colombia (Ley 769 de 2002, art. 19, y Resolución 12379 de 2012
 * del Ministerio de Transporte). La edad mínima y la vigencia se mantienen en este único lugar para poder
 * ajustarlas si la norma cambia.
 */
enum class CategoriaLicencia(
    val codigo: String,
    val servicio: ServicioLicencia,
    val edadMinima: Int,
    val descripcion: String,
) {
    A1("A1", ServicioLicencia.PARTICULAR, 16, "Motocicletas, motociclos y mototriciclos de hasta 125 c.c. de cilindrada"),
    A2("A2", ServicioLicencia.PARTICULAR, 16, "Motocicletas, motociclos, mototriciclos, motocarros y cuatrimotos de más de 125 c.c."),
    B1("B1", ServicioLicencia.PARTICULAR, 16, "Automóviles, motocarros, cuatrimotos, camperos, camionetas y microbuses de servicio particular"),
    B2("B2", ServicioLicencia.PARTICULAR, 18, "Camiones rígidos, busetas y buses de servicio particular"),
    B3("B3", ServicioLicencia.PARTICULAR, 18, "Vehículos articulados de servicio particular"),
    C1("C1", ServicioLicencia.PUBLICO, 18, "Automóviles, motocarros, camperos, camionetas y microbuses de servicio público"),
    C2("C2", ServicioLicencia.PUBLICO, 18, "Camiones rígidos, busetas y buses de servicio público"),
    C3("C3", ServicioLicencia.PUBLICO, 18, "Vehículos articulados de servicio público"),
    ;

    /**
     * Vigencia desde la expedición o refrendación: servicio público, 3 años; servicio particular, 10 años si el
     * titular es menor de 60, 5 años entre 60 y 80, y 1 año si es mayor de 80.
     */
    fun vigenciaHasta(
        fechaNacimiento: LocalDate,
        fechaExpedicion: LocalDate,
    ): LocalDate {
        val edad = fechaNacimiento.periodUntil(fechaExpedicion).years
        val anios =
            when {
                servicio == ServicioLicencia.PUBLICO -> 3
                edad < 60 -> 10
                edad <= 80 -> 5
                else -> 1
            }
        return fechaExpedicion.plus(anios, DateTimeUnit.YEAR)
    }

    companion object {
        fun fromCodigo(codigo: String): CategoriaLicencia =
            entries.find { it.codigo.equals(codigo.trim(), ignoreCase = true) }
                ?: error("Categoría de licencia desconocida: $codigo")
    }
}

/** Una categoría habilitada al titular, con sus fechas propias (la refrendación puede ser por categoría). */
data class LicenciaCategoria(
    val categoria: CategoriaLicencia,
    val fechaExpedicion: LocalDate,
    val fechaVencimiento: LocalDate,
) {
    init {
        require(fechaVencimiento > fechaExpedicion) { "La fecha de vencimiento de ${categoria.codigo} debe ser posterior a su expedición" }
    }
}

data class FotoReferencia(
    val bytes: ByteArray,
    val contentType: String,
) {
    init {
        require(bytes.isNotEmpty()) { "FotoReferencia bytes cannot be empty" }
        require(contentType.isNotBlank()) { "FotoReferencia contentType cannot be blank" }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is FotoReferencia &&
                    bytes.contentEquals(other.bytes) &&
                    contentType == other.contentType
            )

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
}

data class Licencia(
    val id: Long? = null,
    val documentNumber: LicenciaDocumentNumber,
    val apellidos: String,
    val nombres: String,
    val fechaNacimiento: LocalDate,
    val lugarNacimientoCiudad: String,
    val lugarNacimientoDepartamento: String? = null,
    val sexo: Sexo,
    val estaturaCm: Int? = null,
    val grupoSanguineo: GrupoSanguineo? = null,
    val nacionalidad: String = "CO",
    val organismoTransito: String = ORGANISMO_POR_DEFECTO,
    val fechaExpedicion: LocalDate,
    val estado: EstadoLicencia = EstadoLicencia.ACTIVA,
    val usaLentes: Boolean = false,
    val fotoReferencia: FotoReferencia? = null,
    val categorias: List<LicenciaCategoria> = emptyList(),
) {
    init {
        require(apellidos.isNotBlank()) { "apellidos cannot be blank" }
        require(nombres.isNotBlank()) { "nombres cannot be blank" }
        require(lugarNacimientoCiudad.isNotBlank()) { "lugarNacimientoCiudad cannot be blank" }
        require(organismoTransito.isNotBlank()) { "organismoTransito cannot be blank" }
        estaturaCm?.let { require(it in 50..250) { "estaturaCm out of plausible range: $it" } }
        require(categorias.map { it.categoria }.toSet().size == categorias.size) { "Categoría repetida" }
        categorias.forEach {
            val edad = fechaNacimiento.periodUntil(it.fechaExpedicion).years
            require(edad >= it.categoria.edadMinima) {
                "La categoría ${it.categoria.codigo} exige al menos ${it.categoria.edadMinima} años a la fecha de expedición"
            }
        }
    }

    /** Categorías sin vencer a la fecha [hoy]: solo estas viajan en el mDL. */
    fun categoriasVigentes(hoy: java.time.LocalDate): List<LicenciaCategoria> =
        categorias.filter { it.fechaVencimiento.toJavaLocalDate() > hoy }

    /** Una licencia se emite a la wallet solo si está ACTIVA, tiene foto y al menos una categoría vigente. */
    fun esEmisible(hoy: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        estado == EstadoLicencia.ACTIVA && fotoReferencia != null && categoriasVigentes(hoy).isNotEmpty()

    /** Fecha de la primera expedición más antigua de sus categorías, o la de la licencia si no tiene. */
    val primeraExpedicion: LocalDate
        get() = categorias.minOfOrNull { it.fechaExpedicion } ?: fechaExpedicion

    companion object {
        const val ORGANISMO_POR_DEFECTO = "Dirección de Tránsito de Bucaramanga"
    }
}

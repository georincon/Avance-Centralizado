/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import kotlinx.datetime.LocalDate

/**
 * Número de documento de identidad (cédula de ciudadanía), usado como clave
 * de búsqueda tanto por el panel admin como por el flujo de emisión sin login.
 */
@JvmInline
value class CedulaDocumentNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "documentNumber cannot be blank" }
    }
}

/**
 * Sexo tal como viene impreso en la cédula física colombiana (solo M/F).
 * La conversión a ISO/IEC 5218 (el formato que usa el modelo [Pid][eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.Pid])
 * la hace el adapter que arma la credencial, no este módulo.
 */
enum class Sexo {
    M,
    F,
}

/**
 * Grupo sanguíneo y factor RH, tal como viene impreso en la cédula.
 * No tiene equivalente en el modelo [Pid][eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.Pid]
 * estándar — se conserva únicamente en esta base de datos (ver sección 3 y 6
 * de CONTEXTO-REGISTRADURIA.md).
 */
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

/**
 * Foto de referencia subida aparte por el admin (no la impresa en la cédula),
 * pensada para que la wallet la use en su comparación facial selfie-vs-foto.
 */
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

/**
 * Registro de un ciudadano tal como lo carga el panel admin de la Registraduría,
 * con todos los campos de la cédula física que sí están en alcance del MVP
 * (ver tabla de la sección 3 de CONTEXTO-REGISTRADURIA.md). Deliberadamente
 * fuera: firma, huella índice derecho, código de barras del reverso.
 *
 * @param id Identificador interno, asignado por la base de datos.
 * @param documentNumber Número de cédula, clave de búsqueda.
 * @param apellidos Apellidos tal como aparecen en el frente de la cédula.
 * @param nombres Nombres tal como aparecen en el frente de la cédula.
 * @param fechaNacimiento Fecha de nacimiento (reverso).
 * @param lugarNacimientoCiudad Ciudad de nacimiento (reverso).
 * @param lugarNacimientoDepartamento Departamento de nacimiento (reverso), si aplica.
 * @param sexo Sexo tal como aparece impreso (reverso).
 * @param estaturaCm Estatura en centímetros (reverso). Sin equivalente en el PID estándar.
 * @param grupoSanguineo Grupo sanguíneo y RH (reverso). Sin equivalente en el PID estándar.
 * @param fechaExpedicion Fecha de expedición del documento (reverso).
 * @param lugarExpedicion Lugar de expedición del documento (reverso).
 * @param nacionalidad Código ISO 3166-1 alpha-2, fijo en "CO" para este emulador.
 * @param fotoReferencia Foto de referencia para biometría, subida aparte.
 */
data class Cedula(
    val id: Long? = null,
    val documentNumber: CedulaDocumentNumber,
    val apellidos: String,
    val nombres: String,
    val fechaNacimiento: LocalDate,
    val lugarNacimientoCiudad: String,
    val lugarNacimientoDepartamento: String? = null,
    val sexo: Sexo,
    val estaturaCm: Int? = null,
    val grupoSanguineo: GrupoSanguineo? = null,
    val fechaExpedicion: LocalDate? = null,
    val lugarExpedicion: String? = null,
    val nacionalidad: String = "CO",
    val fotoReferencia: FotoReferencia? = null,
    /** Correo electrónico de contacto: se comparte en el PID como `email` (SD-JWT) / `email_address` (mdoc). */
    val correoElectronico: String? = null,
    /** Celular colombiano de 10 dígitos, sin indicativo: se comparte en el PID tal cual (p. ej. 3001234567). */
    val celular: String? = null,
) {
    init {
        require(apellidos.isNotBlank()) { "apellidos cannot be blank" }
        require(nombres.isNotBlank()) { "nombres cannot be blank" }
        require(lugarNacimientoCiudad.isNotBlank()) { "lugarNacimientoCiudad cannot be blank" }
        estaturaCm?.let { require(it in 50..250) { "estaturaCm out of plausible range: $it" } }
        correoElectronico?.let {
            require(it.length <= 255 && it.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) { "El correo electrónico no es válido" }
        }
        celular?.let { require(it.matches(Regex("^3[0-9]{9}$"))) { "El celular debe tener 10 dígitos y empezar por 3" } }
    }
}

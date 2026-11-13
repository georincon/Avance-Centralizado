/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate

/**
 * Entidad R2DBC para la tabla `cedulas` (ver V2.sql). Espejo 1:1 de las
 * columnas, con las conversiones a/desde [Cedula].
 */
@Table("cedulas")
data class CedulaEntity(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("documento_numero")
    val documentoNumero: String,
    @Column("apellidos")
    val apellidos: String,
    @Column("nombres")
    val nombres: String,
    @Column("fecha_nacimiento")
    val fechaNacimiento: LocalDate,
    @Column("lugar_nacimiento_ciudad")
    val lugarNacimientoCiudad: String,
    @Column("lugar_nacimiento_departamento")
    val lugarNacimientoDepartamento: String?,
    @Column("sexo")
    val sexo: String,
    @Column("estatura_cm")
    val estaturaCm: Int?,
    @Column("grupo_sanguineo")
    val grupoSanguineo: String?,
    @Column("fecha_expedicion")
    val fechaExpedicion: LocalDate?,
    @Column("lugar_expedicion")
    val lugarExpedicion: String?,
    @Column("nacionalidad")
    val nacionalidad: String,
    @Column("foto_referencia")
    val fotoReferencia: ByteArray?,
    @Column("foto_referencia_tipo")
    val fotoReferenciaTipo: String?,
    @Column("correo_electronico")
    val correoElectronico: String? = null,
    @Column("celular")
    val celular: String? = null,
) {
    fun toDomain(): Cedula =
        Cedula(
            id = id,
            documentNumber = CedulaDocumentNumber(documentoNumero),
            apellidos = apellidos,
            nombres = nombres,
            fechaNacimiento = fechaNacimiento.toKotlinLocalDate(),
            lugarNacimientoCiudad = lugarNacimientoCiudad,
            lugarNacimientoDepartamento = lugarNacimientoDepartamento,
            sexo = Sexo.valueOf(sexo),
            estaturaCm = estaturaCm,
            grupoSanguineo = grupoSanguineo?.let { GrupoSanguineo.fromEtiqueta(it) },
            fechaExpedicion = fechaExpedicion?.toKotlinLocalDate(),
            lugarExpedicion = lugarExpedicion,
            nacionalidad = nacionalidad,
            fotoReferencia =
                if (fotoReferencia != null && fotoReferenciaTipo != null) {
                    FotoReferencia(fotoReferencia, fotoReferenciaTipo)
                } else {
                    null
                },
            correoElectronico = correoElectronico,
            celular = celular,
        )

    companion object {
        fun fromDomain(cedula: Cedula): CedulaEntity =
            CedulaEntity(
                id = cedula.id,
                documentoNumero = cedula.documentNumber.value,
                apellidos = cedula.apellidos,
                nombres = cedula.nombres,
                fechaNacimiento = cedula.fechaNacimiento.toJavaLocalDate(),
                lugarNacimientoCiudad = cedula.lugarNacimientoCiudad,
                lugarNacimientoDepartamento = cedula.lugarNacimientoDepartamento,
                sexo = cedula.sexo.name,
                estaturaCm = cedula.estaturaCm,
                grupoSanguineo = cedula.grupoSanguineo?.etiqueta,
                fechaExpedicion = cedula.fechaExpedicion?.toJavaLocalDate(),
                lugarExpedicion = cedula.lugarExpedicion,
                nacionalidad = cedula.nacionalidad,
                fotoReferencia = cedula.fotoReferencia?.bytes,
                fotoReferenciaTipo = cedula.fotoReferencia?.contentType,
                correoElectronico = cedula.correoElectronico,
                celular = cedula.celular,
            )
    }
}

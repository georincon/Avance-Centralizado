/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate

@Table("licencias")
data class LicenciaEntity(
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
    @Column("nacionalidad")
    val nacionalidad: String,
    @Column("organismo_transito")
    val organismoTransito: String,
    @Column("fecha_expedicion")
    val fechaExpedicion: LocalDate,
    @Column("estado")
    val estado: String,
    @Column("usa_lentes")
    val usaLentes: Boolean,
    @Column("foto_referencia")
    val fotoReferencia: ByteArray?,
    @Column("foto_referencia_tipo")
    val fotoReferenciaTipo: String?,
) {
    fun toDomain(categorias: List<LicenciaCategoriaEntity>): Licencia =
        Licencia(
            id = id,
            documentNumber = LicenciaDocumentNumber(documentoNumero),
            apellidos = apellidos,
            nombres = nombres,
            fechaNacimiento = fechaNacimiento.toKotlinLocalDate(),
            lugarNacimientoCiudad = lugarNacimientoCiudad,
            lugarNacimientoDepartamento = lugarNacimientoDepartamento,
            sexo = Sexo.valueOf(sexo),
            estaturaCm = estaturaCm,
            grupoSanguineo = grupoSanguineo?.let { GrupoSanguineo.fromEtiqueta(it) },
            nacionalidad = nacionalidad,
            organismoTransito = organismoTransito,
            fechaExpedicion = fechaExpedicion.toKotlinLocalDate(),
            estado = EstadoLicencia.valueOf(estado),
            usaLentes = usaLentes,
            fotoReferencia =
                if (fotoReferencia != null && fotoReferenciaTipo != null) {
                    FotoReferencia(fotoReferencia, fotoReferenciaTipo)
                } else {
                    null
                },
            categorias = categorias.map { it.toDomain() }.sortedBy { it.categoria },
        )

    companion object {
        fun fromDomain(licencia: Licencia): LicenciaEntity =
            LicenciaEntity(
                id = licencia.id,
                documentoNumero = licencia.documentNumber.value,
                apellidos = licencia.apellidos,
                nombres = licencia.nombres,
                fechaNacimiento = licencia.fechaNacimiento.toJavaLocalDate(),
                lugarNacimientoCiudad = licencia.lugarNacimientoCiudad,
                lugarNacimientoDepartamento = licencia.lugarNacimientoDepartamento,
                sexo = licencia.sexo.name,
                estaturaCm = licencia.estaturaCm,
                grupoSanguineo = licencia.grupoSanguineo?.etiqueta,
                nacionalidad = licencia.nacionalidad,
                organismoTransito = licencia.organismoTransito,
                fechaExpedicion = licencia.fechaExpedicion.toJavaLocalDate(),
                estado = licencia.estado.name,
                usaLentes = licencia.usaLentes,
                fotoReferencia = licencia.fotoReferencia?.bytes,
                fotoReferenciaTipo = licencia.fotoReferencia?.contentType,
            )
    }
}

@Table("licencia_categorias")
data class LicenciaCategoriaEntity(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("licencia_id")
    val licenciaId: Long,
    @Column("categoria")
    val categoria: String,
    @Column("fecha_expedicion")
    val fechaExpedicion: LocalDate,
    @Column("fecha_vencimiento")
    val fechaVencimiento: LocalDate,
) {
    fun toDomain(): LicenciaCategoria =
        LicenciaCategoria(
            categoria = CategoriaLicencia.fromCodigo(categoria),
            fechaExpedicion = fechaExpedicion.toKotlinLocalDate(),
            fechaVencimiento = fechaVencimiento.toKotlinLocalDate(),
        )

    companion object {
        fun fromDomain(
            licenciaId: Long,
            categoria: LicenciaCategoria,
        ): LicenciaCategoriaEntity =
            LicenciaCategoriaEntity(
                licenciaId = licenciaId,
                categoria = categoria.categoria.codigo,
                fechaExpedicion = categoria.fechaExpedicion.toJavaLocalDate(),
                fechaVencimiento = categoria.fechaVencimiento.toJavaLocalDate(),
            )
    }
}

/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md para el contexto completo.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.toNonEmptySetOrNull
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.DrivingPrivilege.Restriction
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.DrivingPrivilege.Restriction.GenericRestriction
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.DrivingPrivilege.Restriction.ParameterizedRestriction
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.CategoriaLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.EstadoLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaCategoria
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Sexo
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.domain.CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.transito.FindLicenciaByDocumentNumber
import kotlinx.datetime.toJavaLocalDate
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.Period

/** Id de la configuración del mDL de una categoría en el metadata del issuer (`org.iso.18013.5.1.mDL.<CAT>`): una credencial por categoría. */
fun mdlConfigurationId(categoria: CategoriaLicencia): CredentialConfigurationId =
    CredentialConfigurationId("${mdlDocType(1u)}.${categoria.codigo}")

private val log = LoggerFactory.getLogger(GetMobileDrivingLicenceFromTransito::class.java)

/**
 * Datos del mDL tomados de la tabla `licencias` del organismo de tránsito (clave: la cédula del titular,
 * que llega como `username`). El mDL es una atestación propia (`org.iso.18013.5.1.mDL`), distinta del PID.
 *
 * Solo se emite si la licencia está ACTIVA, el titular no está bloqueado, hay foto y al menos una categoría vigente.
 * Las categorías vencidas no se incluyen entre los privilegios de conducción.
 */
class GetMobileDrivingLicenceFromTransito(
    private val issuingJurisdiction: Latin150?,
    private val findLicenciaByDocumentNumber: FindLicenciaByDocumentNumber,
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
    private val hoy: () -> LocalDate = { LocalDate.now() },
    /** Si se indica, el mDL lleva solo esa categoría (una credencial por categoría). */
    private val categoria: CategoriaLicencia? = null,
) : GetAttestationAttributes<MobileDrivingLicence> {
    context(_: Raise<IssueCredentialError.AttestationDatasetNotFound>, authorizationContext: AuthorizationContext)
    override suspend fun invoke(): MobileDrivingLicence {
        val documentNumber = authorizationContext.username
        log.info("Trying to get mDL data from Tránsito for document {} ...", documentNumber.maskDocumentNumber())

        ensure(!isSubjectBlocked(documentNumber)) {
            log.warn("Issuance refused for a blocked subject {}", documentNumber.maskDocumentNumber())
            IssueCredentialError.AttestationDatasetNotFound
        }

        val licencia = findLicenciaByDocumentNumber(LicenciaDocumentNumber(documentNumber))
        ensureNotNull(licencia) { IssueCredentialError.AttestationDatasetNotFound }
        ensure(licencia.estado == EstadoLicencia.ACTIVA) {
            log.warn("Issuance refused: license of {} is {}", documentNumber.maskDocumentNumber(), licencia.estado)
            IssueCredentialError.AttestationDatasetNotFound
        }
        ensure(licencia.fotoReferencia != null) { IssueCredentialError.AttestationDatasetNotFound }

        val vigentes = licencia.categoriasVigentes(hoy()).filter { categoria == null || it.categoria == categoria }
        ensure(vigentes.isNotEmpty()) {
            log.warn("Issuance refused: {} has no valid categories", documentNumber.maskDocumentNumber())
            IssueCredentialError.AttestationDatasetNotFound
        }

        return mdl(licencia, vigentes)
    }

    private fun mdl(
        licencia: Licencia,
        vigentes: List<LicenciaCategoria>,
    ): MobileDrivingLicence {
        val foto = checkNotNull(licencia.fotoReferencia) { "fotoReferencia was checked non-null before calling mdl()" }
        val image =
            when (foto.contentType.lowercase()) {
                "image/jp2", "image/jpeg2000" -> Image.Jpeg2000(foto.bytes)
                else -> Image.Jpeg(foto.bytes)
            }
        val nacimiento = licencia.fechaNacimiento.toJavaLocalDate()
        val edad = Period.between(nacimiento, hoy()).years.coerceAtLeast(1)
        val cedula = Latin150(licencia.documentNumber.value)
        val pais = IsoAlpha2CountryCode(licencia.nacionalidad)

        return MobileDrivingLicence(
            driver =
                Driver(
                    familyName = Latin150AndUtf8(Latin150(licencia.apellidos)),
                    givenName = Latin150AndUtf8(Latin150(licencia.nombres)),
                    birthDate = nacimiento,
                    portrait = Portrait(image),
                    sex = if (licencia.sexo == Sexo.M) Sex.MALE else Sex.FEMALE,
                    height = licencia.estaturaCm?.toUInt()?.cm(),
                    birthPlace =
                        Latin150(listOfNotNull(licencia.lugarNacimientoCiudad, licencia.lugarNacimientoDepartamento).joinToString(", ")),
                    age = Age(edad.toUInt().natural(), nacimiento.year.toUInt().natural()),
                    nationality = pais,
                ),
            issueAndExpiry =
                IssueAndExpiry(
                    issuedAt = licencia.fechaExpedicion.toJavaLocalDate(),
                    expiresAt = vigentes.maxOf { it.fechaVencimiento }.toJavaLocalDate(),
                ),
            issuer =
                Issuer(
                    country = IssuingCountry(pais, DistinguishingSign("CO")),
                    authority = Latin150(licencia.organismoTransito),
                    jurisdiction = issuingJurisdiction,
                ),
            documentNumber = cedula,
            privileges = vigentes.map { privilegio(it, licencia.usaLentes) }.toSet(),
            administrativeNumber = cedula,
        )
    }

    private fun privilegio(
        categoria: LicenciaCategoria,
        usaLentes: Boolean,
    ): DrivingPrivilege {
        val restricciones: Set<Restriction> =
            buildSet {
                if (usaLentes) add(GenericRestriction.EYESIGHT_CORRECTION_OR_PROTECTION)
                if (categoria.categoria == CategoriaLicencia.A1) {
                    add(ParameterizedRestriction.VehicleCylinderCapacity(Sign.LessThanOrEqualTo(125u.cm3())))
                }
            }
        return DrivingPrivilege(
            vehicleCategory = categoria.categoria.toVehicleCategory(),
            issueAndExpiry =
                IssueAndExpiry(categoria.fechaExpedicion.toJavaLocalDate(), categoria.fechaVencimiento.toJavaLocalDate()),
            restrictions = restricciones.toNonEmptySetOrNull(),
        )
    }
}

private fun CategoriaLicencia.toVehicleCategory(): VehicleCategory =
    when (this) {
        CategoriaLicencia.A1 -> VehicleCategory.CO_A1
        CategoriaLicencia.A2 -> VehicleCategory.CO_A2
        CategoriaLicencia.B1 -> VehicleCategory.CO_B1
        CategoriaLicencia.B2 -> VehicleCategory.CO_B2
        CategoriaLicencia.B3 -> VehicleCategory.CO_B3
        CategoriaLicencia.C1 -> VehicleCategory.CO_C1
        CategoriaLicencia.C2 -> VehicleCategory.CO_C2
        CategoriaLicencia.C3 -> VehicleCategory.CO_C3
    }

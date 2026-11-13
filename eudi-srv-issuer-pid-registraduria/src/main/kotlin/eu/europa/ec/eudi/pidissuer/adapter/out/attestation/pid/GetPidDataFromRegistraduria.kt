/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md para el contexto completo (puntos 2, 4 y 9.3).
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import arrow.core.nonEmptyListOf
import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Cedula
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.Sexo
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(GetPidDataFromRegistraduria::class.java)

/**
 * Lee los datos del PID desde la tabla `cedulas` de este mismo emulador, en
 * vez de consultarlos en Keycloak (ver [GetPidDataFromKeyCloak]).
 *
 * El "sin login" ciudadano (Opción A, sección 5 de CONTEXTO-REGISTRADURIA.md)
 * sincroniza en Keycloak un usuario fantasma cuyo `username` es el número de
 * cédula; por eso aquí [AuthorizationContext.username] se interpreta
 * directamente como número de documento.
 */
class GetPidDataFromRegistraduria(
    private val issuerCountry: IsoCountry,
    private val issuingJurisdiction: IsoCountrySubdivision?,
    private val findCedulaByDocumentNumber: FindCedulaByDocumentNumber,
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
) : GetAttestationAttributes<PidAttributes> {
    init {
        issuingJurisdiction?.let {
            require(it.startsWith(issuerCountry.value)) {
                "Issuing Jurisdiction must be within the Issuing Country"
            }
        }
    }

    context(_: Raise<IssueCredentialError.AttestationDatasetNotFound>, authorizationContext: AuthorizationContext)
    override suspend fun invoke(): PidAttributes {
        val documentNumber = authorizationContext.username
        log.info("Trying to get PID Data from Registraduría for document {} ...", documentNumber.maskDocumentNumber())

        // Titular con la emisión bloqueada: no se le emite nada (sus datos no se tocan)
        ensure(!isSubjectBlocked(documentNumber)) {
            log.warn("Issuance refused for a blocked subject {}", documentNumber.maskDocumentNumber())
            IssueCredentialError.AttestationDatasetNotFound
        }

        val cedula = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))
        ensureNotNull(cedula) { IssueCredentialError.AttestationDatasetNotFound }

        // Sin foto de referencia no hay con qué comparar en la biometría de la
        // wallet (ver sección 2 de CONTEXTO-REGISTRADURIA.md) — se trata como
        // dataset incompleto en vez de emitir un PID sin retrato.
        ensure(cedula.fotoReferencia != null) { IssueCredentialError.AttestationDatasetNotFound }

        return pid(cedula)
    }

    private fun pid(cedula: Cedula): PidAttributes {
        val placeOfBirth =
            PlaceOfBirth(
                country = IsoCountry(cedula.nacionalidad),
                region = cedula.lugarNacimientoDepartamento?.let { State(it) },
                locality = City(cedula.lugarNacimientoCiudad),
            )

        val portrait =
            checkNotNull(cedula.fotoReferencia) { "fotoReferencia was checked non-null before calling pid()" }
        val portraitImage =
            when (portrait.contentType.lowercase()) {
                "image/jp2", "image/jpeg2000" -> PortraitImage.JPEG2000(portrait.bytes)
                else -> PortraitImage.JPEG(portrait.bytes)
            }

        val pid =
            Pid(
                familyName = FamilyName(cedula.apellidos),
                givenName = GivenName(cedula.nombres),
                birthDate = cedula.fechaNacimiento,
                placeOfBirth = placeOfBirth,
                nationalities = nonEmptyListOf(IsoCountry(cedula.nacionalidad)),
                portrait = portraitImage,
                sex = cedula.sexo.toIsoGender(),
                personalAdministrativeNumber = AdministrativeNumber(cedula.documentNumber.value),
                // Contacto del ciudadano (opcional): viaja en el PID si el panel de la Registraduría lo tiene.
                emailAddress = cedula.correoElectronico,
                mobilePhoneNumber = cedula.celular?.let { PhoneNumber(it) },
            )

        val pidMetaData =
            PidMetaData(
                expiryDate = null,
                issuingAuthority =
                    IssuingAuthority.AdministrativeAuthority(
                        "Registraduría Nacional del Estado Civil (${issuerCountry.value})",
                    ),
                issuingCountry = issuerCountry,
                documentNumber = DocumentNumber(cedula.documentNumber.value),
                issuingJurisdiction = issuingJurisdiction,
                issuanceDate = cedula.fechaExpedicion,
            )

        val domestic =
            PidDomesticAttributes(
                heightCm = cedula.estaturaCm,
                bloodType = cedula.grupoSanguineo?.etiqueta,
                documentIssueDate = cedula.fechaExpedicion,
                documentIssuePlace = cedula.lugarExpedicion,
            )

        return PidAttributes(pid, pidMetaData, domestic)
    }
}

/**
 * Conversión a ISO/IEC 5218 ("1" = masculino, "2" = femenino), que es lo que
 * espera [Pid.sex]. La cédula colombiana solo imprime M/F, así que no hay
 * "no known"/"not applicable" que mapear en origen.
 */
private fun Sexo.toIsoGender(): IsoGender =
    when (this) {
        Sexo.M -> IsoGender(1u)
        Sexo.F -> IsoGender(2u)
    }

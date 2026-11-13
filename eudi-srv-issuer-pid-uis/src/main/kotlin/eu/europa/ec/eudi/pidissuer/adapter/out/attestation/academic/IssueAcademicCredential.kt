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

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.toNonEmptyListOrNull
import arrow.fx.coroutines.parMap
import eu.europa.ec.eudi.pidissuer.adapter.out.IssuerSigningKey
import eu.europa.ec.eudi.pidissuer.adapter.out.format.AttestationAttributes
import eu.europa.ec.eudi.pidissuer.adapter.out.format.EncodeAttestationAttributes
import eu.europa.ec.eudi.pidissuer.adapter.out.format.sdjwtvc.SdJwtVcSerialization
import eu.europa.ec.eudi.pidissuer.adapter.out.format.sdjwtvc.encodeAttestationAttributesInSdJwtVc
import eu.europa.ec.eudi.pidissuer.adapter.out.signingAlgorithm
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.port.input.AuthorizationContext
import eu.europa.ec.eudi.pidissuer.port.input.IssueCredentialError
import eu.europa.ec.eudi.pidissuer.port.out.attestation.AttestationIssuer
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.port.out.attestation.keyAttestation
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GenerateNotificationId
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreIssuedCredential
import eu.europa.ec.eudi.pidissuer.port.out.status.AllocateStatus
import eu.europa.ec.eudi.pidissuer.port.out.proof.ValidateProof
import eu.europa.ec.eudi.sdjwt.HashAlgorithm
import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObjectBuilder
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration

private val log = LoggerFactory.getLogger(IssueAcademicCredential::class.java)

/**
 * Scope for academic credentials
 */
val AcademicCredentialScope: Scope = Scope("urn:eudi:academic:credential:1:dc+sd-jwt")

/**
 * VCT (Verifiable Credential Type) for academic credentials
 */
private const val ACADEMIC_CREDENTIAL_DOCTYPE = "urn:eudi:academic:credential:1"

/**
 * Service for issuing Academic Credentials in SD-JWT VC format
 */
class IssueAcademicCredential private constructor(
    override val configuration: SdJwtVcCredentialConfiguration,
    private val clock: Clock,
    private val getAttestationAttributes: GetAttestationAttributes<AcademicCredential>,
    private val encodeAttestationAttributes: EncodeAttestationAttributes<AcademicCredential>,
    private val validateProof: ValidateProof,
    private val generateNotificationId: GenerateNotificationId?,
    private val storeIssuedCredential: StoreIssuedCredential,
    private val allocateStatus: AllocateStatus?,
) : AttestationIssuer {
    context(_: Raise<IssueCredentialError>, authorizationContext: AuthorizationContext)
    override suspend fun invoke(request: AuthorizedCredentialRequest): CredentialResponse {
        log.info("Handling academic credential issuance request ...")
        val issuedAt = clock.now()
        val keyAttestation = context(validateProof) { keyAttestation(request, issuedAt) }
        val deviceKeys = keyAttestation.keys.value
        val attributes = getAttestationAttributes()
        val expiresAt = issuedAt + configuration.validity
        val notificationId = generateNotificationId?.invoke()
        val clientStatus = authorizationContext.clientStatus.status.statusList
        val keyStorageStatus = keyAttestation.keyStorageStatus.status.statusList
        val issuedCredentials =
            deviceKeys
                .parMap(Dispatchers.Default, 4) { deviceKey ->
                    // Con estado, la credencial se puede revocar (status list). `issuer.academic.sd_jwt_vc.revocable=false` lo desactiva.
                    val status = allocateStatus?.let { allocate -> either { allocate(configuration.type.value, expiresAt) }.getOrElse { throw it.value } }
                    val attestationAttributes =
                        AttestationAttributes(
                            attributes,
                            issuedAt,
                            expiresAt,
                            notBefore = issuedAt,
                            deviceKey,
                            status = status,
                        )
                    val attestation = encodeAttestationAttributes(attestationAttributes)

                    storeIssuedCredential(
                        IssuedCredential(
                            format = SD_JWT_VC_FORMAT,
                            type = configuration.type.value,
                            attestationAttributes.issuedAt,
                            attestationAttributes.expiresAt,
                            notificationId,
                            attestationAttributes.status,
                            clientStatus,
                            keyStorageStatus,
                            subject = authorizationContext.username,
                        ),
                    )

                    attestation
                }.toNonEmptyListOrNull()

        checkNotNull(issuedCredentials) {
            "Cannot happen"
        }

        return CredentialResponse
            .Issued(issuedCredentials, notificationId)
            .also { issued ->
                log.info("Issued Academic Credential {}", issued)
            }
    }

    companion object {
        operator fun invoke(
            sdJwtVcSerialization: SdJwtVcSerialization = SdJwtVcSerialization.Compact,
            clock: Clock,
            getAttestationAttributes: GetAttestationAttributes<AcademicCredential>,
            issuerSigningKey: IssuerSigningKey,
            digestsHashAlgorithm: HashAlgorithm,
            deviceBinding: DeviceBinding.Required,
            credentialReusePolicy: CredentialReusePolicy = CredentialReusePolicy.None,
            validity: Duration,
            validateProof: ValidateProof,
            generateNotificationId: GenerateNotificationId?,
            storeIssuedCredential: StoreIssuedCredential,
            allocateStatus: AllocateStatus? = null,
            display: CredentialDisplay? = null,
        ): IssueAcademicCredential {
            val credentialConfiguration = cfg(deviceBinding, credentialReusePolicy, validity, issuerSigningKey, display)
            return IssueAcademicCredential(
                credentialConfiguration,
                clock,
                getAttestationAttributes,
                encodeAttestationAttributesInSdJwtVc(
                    sdJwtVcSerialization,
                    digestsHashAlgorithm,
                    issuerSigningKey,
                    vct = credentialConfiguration.type,
                    build = { academicCredential(it) },
                ),
                validateProof,
                generateNotificationId,
                storeIssuedCredential,
                allocateStatus,
            )
        }

        private fun cfg(
            deviceBinding: DeviceBinding.Required,
            credentialReusePolicy: CredentialReusePolicy,
            validity: Duration,
            issuerSigningKey: IssuerSigningKey,
            display: CredentialDisplay?,
        ): SdJwtVcCredentialConfiguration =
            SdJwtVcCredentialConfiguration(
                CredentialConfigurationId(AcademicCredentialScope.value),
                Scope(AcademicCredentialScope.value),
                display =
                    nonEmptyListOf(
                        display ?: CredentialDisplay(
                            DisplayName.en("Academic Credential (SD-JWT VC Compact)"),
                        ),
                    ),
                claims = SdJwtVcAcademicCredentialClaims.all(),
                deviceBinding = deviceBinding,
                category = AttestationCategory.Eaa,
                reusePolicy = credentialReusePolicy,
                validity = validity,
                type = SdJwtVcType(ACADEMIC_CREDENTIAL_DOCTYPE),
                credentialSigningAlgorithmsSupported = nonEmptySetOf(issuerSigningKey.signingAlgorithm),
                publicKey = issuerSigningKey.key.toPublicJWK(),
            )
    }
}

/**
 * Extension function to build SD-JWT VC claims for academic credentials
 */
fun SdJwtObjectBuilder.academicCredential(credential: AcademicCredential) {
    // Minimizacion de datos: solo viajan siempre los datos de quien emite y las fechas de la credencial
    // (issuing_authority, issuing_country, issuing_institution_name, date_of_issuance, date_of_expiry).
    // TODO lo demas (nombre, documento, programa, nivel, estado, fechas academicas, contacto...) es
    // divulgable por separado: la wallet entrega solo lo que pide el verificador.
    with(credential) {
        // Institution info
        claim(SdJwtVcAcademicCredentialClaims.IssuingAuthority.name, institution.name.value)
        claim(SdJwtVcAcademicCredentialClaims.IssuingCountry.name, institution.country.value)
        claim(SdJwtVcAcademicCredentialClaims.IssuingInstitutionName.name, institution.name.value)

        // Student info
        sdClaim(SdJwtVcAcademicCredentialClaims.FamilyName.name, familyName.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.GivenName.name, givenName.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.StudentId.name, username.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.IdentificationType.name, studentId.type.value)
        email?.let { sdClaim(SdJwtVcAcademicCredentialClaims.Email.name, it) }
        phoneNumber?.let { sdClaim(SdJwtVcAcademicCredentialClaims.PhoneNumber.name, it) }

        // Program info
        sdClaim(SdJwtVcAcademicCredentialClaims.ProgramCode.name, program.code.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.ProgramName.name, program.name.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.AcademicLevel.name, program.level.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.AwardedTitle.name, program.awardedTitle)
        program.faculty?.let { sdClaim(SdJwtVcAcademicCredentialClaims.Faculty.name, it) }
        program.academicUnit?.let { sdClaim(SdJwtVcAcademicCredentialClaims.AcademicUnit.name, it) }
        sdClaim(SdJwtVcAcademicCredentialClaims.ProgramModality.name, program.modality.value)
        sdClaim(SdJwtVcAcademicCredentialClaims.DurationSemesters.name, program.durationSemesters)
        sdClaim(SdJwtVcAcademicCredentialClaims.TotalCredits.name, program.totalCredits)

        // Academic record
        academicRecord.enrollmentDate?.let {
            sdClaim(SdJwtVcAcademicCredentialClaims.EnrollmentDate.name, it.toString())
        }
        academicRecord.graduationDate?.let {
            sdClaim(SdJwtVcAcademicCredentialClaims.GraduationDate.name, it.toString())
        }
        sdClaim(SdJwtVcAcademicCredentialClaims.ApprovedCredits.name, academicRecord.approvedCredits)
        sdClaim(SdJwtVcAcademicCredentialClaims.SemestersCompleted.name, academicRecord.semestersCompleted)
        sdClaim(SdJwtVcAcademicCredentialClaims.AcademicStatus.name, academicRecord.status.value)
        academicRecord.accumulatedAverage?.let {
            sdClaim(SdJwtVcAcademicCredentialClaims.AccumulatedAverage.name, it)
        }

        // Dates
        claim(SdJwtVcAcademicCredentialClaims.DateOfIssuance.name, dateOfIssuance.toString())
        dateOfExpiry?.let { claim(SdJwtVcAcademicCredentialClaims.DateOfExpiry.name, it.toString()) }
        documentNumber?.let { sdClaim(SdJwtVcAcademicCredentialClaims.DocumentNumber.name, it) }
    }
}

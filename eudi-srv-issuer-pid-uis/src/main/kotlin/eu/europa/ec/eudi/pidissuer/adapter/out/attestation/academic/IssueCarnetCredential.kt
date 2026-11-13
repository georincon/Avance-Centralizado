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
import com.eygraber.uri.Uri
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
import eu.europa.ec.eudi.pidissuer.port.out.proof.ValidateProof
import eu.europa.ec.eudi.pidissuer.port.out.status.AllocateStatus
import eu.europa.ec.eudi.sdjwt.HashAlgorithm
import eu.europa.ec.eudi.sdjwt.dsl.values.SdJwtObjectBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private val log = LoggerFactory.getLogger(IssueCarnetCredential::class.java)

/** Scope del carnet estudiantil (debe existir como client scope en el Keycloak de la UIS). */
val CarnetCredentialScope: Scope = Scope("urn:co:uis:carnet-estudiantil:1:dc+sd-jwt")

private const val CARNET_DOCTYPE = "urn:co:uis:carnet-estudiantil:1"

/** Fin del periodo académico en curso (simplificación: el 1 termina el 20 de junio, el 2 el 18 de diciembre). */
internal fun finDelPeriodo(ahora: Instant): java.time.LocalDate {
    val hoy = java.time.Instant.ofEpochMilli(ahora.toEpochMilliseconds()).atZone(ZoneOffset.UTC).toLocalDate()
    val p1 = java.time.LocalDate.of(hoy.year, 6, 20)
    val p2 = java.time.LocalDate.of(hoy.year, 12, 18)
    return when {
        !hoy.isAfter(p1) -> p1
        !hoy.isAfter(p2) -> p2
        else -> java.time.LocalDate.of(hoy.year + 1, 6, 20)
    }
}

/**
 * Carnet estudiantil de la UIS (SD-JWT VC): solo para estudiantes con el programa ACTIVO. Sale del mismo issuer y de los
 * mismos datos que la credencial académica (la selección que hace el estudiante en la página de programas), vale hasta
 * el fin del periodo y lleva status list, así que se puede revocar.
 */
class IssueCarnetCredential private constructor(
    override val configuration: SdJwtVcCredentialConfiguration,
    private val clock: Clock,
    private val getAttestationAttributes: GetAttestationAttributes<AcademicCredential>,
    private val encodeAttestationAttributes: EncodeAttestationAttributes<AcademicCredential>,
    private val validateProof: ValidateProof,
    private val generateNotificationId: GenerateNotificationId?,
    private val storeIssuedCredential: StoreIssuedCredential,
    private val allocateStatus: AllocateStatus,
) : AttestationIssuer {
    context(_: Raise<IssueCredentialError>, authorizationContext: AuthorizationContext)
    override suspend fun invoke(request: AuthorizedCredentialRequest): CredentialResponse {
        log.info("Handling carnet issuance request ...")
        val issuedAt = clock.now()
        val keyAttestation = context(validateProof) { keyAttestation(request, issuedAt) }
        val deviceKeys = keyAttestation.keys.value
        val data = getAttestationAttributes()
        check(data.academicRecord.status == AcademicStatus.ACTIVO) {
            "El carnet estudiantil es solo para estudiantes activos"
        }
        val fin = finDelPeriodo(issuedAt)
        val attributes = data.copy(dateOfExpiry = LocalDate(fin.year, fin.monthValue, fin.dayOfMonth))
        val expiresAt = Instant.fromEpochMilliseconds(fin.atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli())
        val notificationId = generateNotificationId?.invoke()
        val clientStatus = authorizationContext.clientStatus.status.statusList
        val keyStorageStatus = keyAttestation.keyStorageStatus.status.statusList
        val issuedCredentials =
            deviceKeys
                .parMap(Dispatchers.Default, 4) { deviceKey ->
                    val status = either { allocateStatus(configuration.type.value, expiresAt) }.getOrElse { throw it.value }
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

        checkNotNull(issuedCredentials) { "Cannot happen" }

        return CredentialResponse
            .Issued(issuedCredentials, notificationId)
            .also { issued -> log.info("Issued Carnet Estudiantil {}", issued) }
    }

    companion object {
        operator fun invoke(
            sdJwtVcSerialization: SdJwtVcSerialization = SdJwtVcSerialization.Compact,
            clock: Clock,
            getAttestationAttributes: GetAttestationAttributes<AcademicCredential>,
            issuerSigningKey: IssuerSigningKey,
            digestsHashAlgorithm: HashAlgorithm,
            deviceBinding: DeviceBinding.Required,
            validity: Duration,
            validateProof: ValidateProof,
            generateNotificationId: GenerateNotificationId?,
            storeIssuedCredential: StoreIssuedCredential,
            allocateStatus: AllocateStatus,
            display: CredentialDisplay,
        ): IssueCarnetCredential {
            val credentialConfiguration = cfg(deviceBinding, validity, issuerSigningKey, display)
            return IssueCarnetCredential(
                credentialConfiguration,
                clock,
                getAttestationAttributes,
                encodeAttestationAttributesInSdJwtVc(
                    sdJwtVcSerialization,
                    digestsHashAlgorithm,
                    issuerSigningKey,
                    vct = credentialConfiguration.type,
                    build = { carnetCredential(it) },
                ),
                validateProof,
                generateNotificationId,
                storeIssuedCredential,
                allocateStatus,
            )
        }

        private fun cfg(
            deviceBinding: DeviceBinding.Required,
            validity: Duration,
            issuerSigningKey: IssuerSigningKey,
            display: CredentialDisplay,
        ): SdJwtVcCredentialConfiguration =
            SdJwtVcCredentialConfiguration(
                CredentialConfigurationId(CarnetCredentialScope.value),
                Scope(CarnetCredentialScope.value),
                display = nonEmptyListOf(display),
                claims = SdJwtVcCarnetClaims.all(),
                deviceBinding = deviceBinding,
                category = AttestationCategory.Eaa,
                reusePolicy = CredentialReusePolicy.None,
                validity = validity,
                type = SdJwtVcType(CARNET_DOCTYPE),
                credentialSigningAlgorithmsSupported = nonEmptySetOf(issuerSigningKey.signingAlgorithm),
                publicKey = issuerSigningKey.key.toPublicJWK(),
            )

        /** La presentación del carnet en la wallet: nombre, colores, escudo y fondo de la UIS. */
        fun display(publicUrl: String): CredentialDisplay =
            CredentialDisplay(
                name = DisplayName("Carnet Estudiantil", Locale.forLanguageTag("es")),
                logo = ImageUri(Uri.parse("$publicUrl/public/img/uis-credential-blanco.png"), "Escudo de la UIS"),
                description = "Carnet de estudiante activo de la Universidad Industrial de Santander",
                backgroundColor = "#14452b",
                backgroundImage = BackgroundImage(Uri.parse("$publicUrl/public/img/carnet-fondo.jpg")),
                textColor = "#FFFFFF",
            )
    }
}

object SdJwtVcCarnetClaims {
    private fun def(name: String, es: String, mandatory: Boolean = false) =
        ClaimDefinition(path = ClaimPath.claim(name), mandatory = mandatory, display = mapOf(Locale.forLanguageTag("es") to es))

    // Solo los datos básicos que pide una beca (mismos nombres que la credencial académica, para que un verifier
    // como el de inscripción y becas los lea igual): nombre, código, programa, nivel, estado, título y promedio.
    fun all() =
        nonEmptyListOf(
            def("issuing_authority", "Autoridad emisora", true),
            def("issuing_country", "País emisor", true),
            def("issuing_institution_name", "Institución", true),
            def("date_of_issuance", "Fecha de emisión", true),
            def("date_of_expiry", "Válido hasta", true),
            def("given_name", "Nombres", true),
            def("family_name", "Apellidos", true),
            def("student_id", "Código estudiantil", true),
            def("program_name", "Programa"),
            def("academic_level", "Nivel"),
            def("awarded_title", "Título que otorga"),
            def("accumulated_average", "Promedio acumulado"),
            def("academic_status", "Estado", true),
        )
}

/** Claims del carnet: lo que identifica a quien emite y la vigencia va siempre; lo demás se divulga por separado. */
fun SdJwtObjectBuilder.carnetCredential(credential: AcademicCredential) {
    with(credential) {
        claim("issuing_authority", institution.name.value)
        claim("issuing_country", institution.country.value)
        claim("issuing_institution_name", institution.name.value)
        claim("date_of_issuance", dateOfIssuance.toString())
        dateOfExpiry?.let { claim("date_of_expiry", it.toString()) }
        claim("academic_status", academicRecord.status.value)
        sdClaim("given_name", givenName.value)
        sdClaim("family_name", familyName.value)
        sdClaim("student_id", username.value)
        sdClaim("program_name", program.name.value)
        sdClaim("academic_level", program.level.value)
        sdClaim("awarded_title", program.awardedTitle)
        academicRecord.accumulatedAverage?.let { sdClaim("accumulated_average", it) }
    }
}

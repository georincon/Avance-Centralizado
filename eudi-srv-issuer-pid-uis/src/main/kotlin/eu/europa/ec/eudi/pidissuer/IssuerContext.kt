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
package eu.europa.ec.eudi.pidissuer

import arrow.core.toNonEmptyListOrNull
import arrow.core.toNonEmptySetOrThrow
import com.eygraber.uri.Uri
import com.eygraber.uri.Url
import eu.europa.ec.eudi.pidissuer.adapter.input.scheduler.CredentialRevocationJob
import eu.europa.ec.eudi.pidissuer.adapter.input.web.*
import eu.europa.ec.eudi.pidissuer.adapter.input.web.AuditoriaEmisionWebFilter
import eu.europa.ec.eudi.pidissuer.adapter.input.web.csrf.CsrfTokenSubscriberWebFilter
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.*
import eu.europa.ec.eudi.pidissuer.adapter.out.jose.EncryptCredentialResponseNimbus
import eu.europa.ec.eudi.pidissuer.adapter.out.jose.EncryptDeferredResponseNimbus
import eu.europa.ec.eudi.pidissuer.adapter.out.jose.GenerateSignedMetadataWithNimbus
import eu.europa.ec.eudi.pidissuer.adapter.out.nonce.DecryptNonceWithNimbusAndVerify
import eu.europa.ec.eudi.pidissuer.adapter.out.nonce.GenerateNonceAndEncryptWithNimbus
import eu.europa.ec.eudi.pidissuer.adapter.out.persistence.InMemoryDeferredCredentialRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.notification.WebhookDeliverRevocationNotice
import eu.europa.ec.eudi.pidissuer.adapter.out.persistence.R2dbcRevocationNoticeRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.persistence.R2dbcSubjectBlockRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.persistence.R2dbcIssuedCredentialRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.qr.DefaultGenerateQrCode
import eu.europa.ec.eudi.pidissuer.adapter.out.status.GenerateStatusListTokenWithExternalService
import eu.europa.ec.eudi.pidissuer.adapter.out.status.GetStatusListTokenWithStatium
import eu.europa.ec.eudi.pidissuer.adapter.out.status.MarkStatusAsRevokedWithExternalService
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.AcademicDb
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.InMemoryActivationCodes
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidIngresos
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidVerifierClient
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.InMemoryOfferTickets
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.SyncEstudianteToKeycloak
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.UisAcademicStore
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.UisAdminUsers
import eu.europa.ec.eudi.pidissuer.port.out.uis.ActivationCodes
import eu.europa.ec.eudi.pidissuer.port.out.uis.OfferTickets
import eu.europa.ec.eudi.pidissuer.adapter.out.webclient.KtorHttpClients
import eu.europa.ec.eudi.pidissuer.adapter.out.webclient.WebClients
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.port.input.*
import eu.europa.ec.eudi.pidissuer.port.out.attestation.asDeferred
import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliverRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.notification.GetSubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GenerateNotificationId
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GenerateTransactionId
import eu.europa.ec.eudi.pidissuer.port.out.status.AllocateStatus
import eu.europa.ec.eudi.pidissuer.port.out.status.GetStatusListTokenStatus
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import eu.europa.ec.eudi.sdjwt.vc.Vct
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.core.Ordered
import org.springframework.core.env.getRequiredProperty
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.scheduling.annotation.SchedulingConfigurer
import java.security.KeyStore
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

val log = LoggerFactory.getLogger(PidIssuerApplication::class.java)

internal class AppBeans :
    BeanRegistrarDsl({
        registerBean { Clock.System }
        registerBean { TimeZone.currentSystemDefault() }
        val issuerPublicUrl = env.readRequiredUrl("issuer.publicUrl", removeTrailingSlash = true)
        val issuerKeystore: KeyStore by lazy { keystore(env) }
        val getIssuerSigningKey = loadIssuerSigningKey(env) { issuerKeystore }

        registerBean { env.dPoPConfigurationProperties() }
        registerBean(lazyInit = true) { loadAccessCertificate(env) { issuerKeystore } }
        registerBean { env.httpProxy() }
        registerBean { WebClients(bean(), secure = "insecure" !in env.activeProfiles) }
        registerBean { trustValidatorService(env, bean()) }
        registerBean { DefaultGenerateQrCode() }
        registerBean { HandleNotificationRequest(bean()) }
        registerBean {
            val cNonceExpiresIn = env.duration("issuer.cnonce.expiration") ?: 5.minutes
            HandleNonceRequest(bean(), cNonceExpiresIn, bean())
        }
        registerBean(lazyInit = true) {
            val signedMetadataIssuer =
                env
                    .getProperty("issuer.signed-metadata.issuer")
                    ?.takeIf { it.isNotBlank() }
                    ?.trim()
                    ?.let { requireNotNull(HttpsUrl.of(it)) { "'issuer.signed-metadata.issuer' is not a valid HttpsUrl" } }
                    ?: issuerPublicUrl

            GenerateSignedMetadataWithNimbus(
                clock = bean(),
                signedMetadataIssuer = signedMetadataIssuer,
                credentialIssuerId = bean<CredentialIssuerMetaData>().id,
                accessCertificate = bean(),
            )
        }
        registerBean<GetStatusListTokenStatus> {
            val httpClient = KtorHttpClients(bean(), secure = "insecure" !in env.activeProfiles)
            GetStatusListTokenWithStatium(
                httpClient,
                clock = bean(),
                allowedClockSkew = 5.seconds,
                bean(),
            )
        }
        registerBean {
            RevokeCredentialsWithRevokedStatus(
                clock = bean(),
                deleteExpiredIssuedCredentials = bean(),
                getNonExpiredIssuedCredentials = bean(),
                getStatusListTokenStatus = bean(),
                markStatusAsRevoked = bean(),
                markIssuedCredentialRevoked = bean(),
                noticeService = bean(),
            )
        }
        registerBean { CredentialRevocationJob(bean()) }
        registerBean {
            RevokeCredentialsOfSubject(
                clock = bean(),
                getActiveIssuedCredentialsBySubject = bean(),
                markStatusAsRevoked = bean(),
                markIssuedCredentialRevoked = bean(),
                noticeService = bean(),
            )
        }
        registerBean {
            val cron = env.getProperty("issuer.revocationJob.cron", "0 0 */8 * * *")
            SchedulingConfigurer { taskRegistrar ->
                taskRegistrar.addCronTask({
                    runBlocking {
                        bean<CredentialRevocationJob>().run()
                    }
                }, cron)
            }
        }

        registerBean<AllocateStatus> {
            val serviceUrl = Url.parse(env.getRequiredProperty("issuer.statusList.service.generate-uri"))
            GenerateStatusListTokenWithExternalService(
                webClient = bean(),
                serviceUrl = serviceUrl,
                apiKey = env.getRequiredProperty("issuer.statusList.service.apiKey"),
                timeZone = bean(),
            )
        }

        registerBean<MarkStatusAsRevoked> {
            val serviceUrl = Url.parse(env.getRequiredProperty("issuer.statusList.service.revoke-uri"))
            MarkStatusAsRevokedWithExternalService(
                webClient = bean(),
                serviceUrl = serviceUrl,
                apiKey = env.getRequiredProperty("issuer.statusList.service.apiKey"),
            )
        }

        //
        // Encryption of credential response
        //
        registerBean(lazyInit = true) {
            EncryptDeferredResponseNimbus(bean<CredentialIssuerMetaData>().id, bean())
        }
        registerBean(lazyInit = true) {
            EncryptCredentialResponseNimbus(bean<CredentialIssuerMetaData>().id, bean())
        }

        //
        // Nonce
        //
        registerBean { loadNonceEncryptionKey(env) { issuerKeystore } }
        registerBean { GenerateNonceAndEncryptWithNimbus(issuerPublicUrl, bean()) }
        registerBean { DecryptNonceWithNimbusAndVerify(issuerPublicUrl, bean()) }

        //
        // Credentials
        //
        registerBean { GenerateNotificationId.Random }
        registerBean { R2dbcIssuedCredentialRepository(bean()) }
        registerBean { bean<R2dbcIssuedCredentialRepository>().storeIssuedCredential }
        registerBean { bean<R2dbcIssuedCredentialRepository>().loadIssuedCredentialsByNotificationId }
        registerBean { bean<R2dbcIssuedCredentialRepository>().getNonExpiredIssuedCredentials }
        registerBean { bean<R2dbcIssuedCredentialRepository>().deleteExpiredIssuedCredentials }
        registerBean { bean<R2dbcIssuedCredentialRepository>().markIssuedCredentialRevoked }

        //
        // Bloqueo de emisión por titular y avisos de revocación (tablas propias: no tocan los datos de los estudiantes)
        //
        registerBean { R2dbcSubjectBlockRepository(bean()) }
        registerBean { bean<R2dbcSubjectBlockRepository>().isSubjectBlocked }
        registerBean { bean<R2dbcSubjectBlockRepository>().blockSubject }
        registerBean { bean<R2dbcSubjectBlockRepository>().unblockSubject }
        registerBean { bean<R2dbcSubjectBlockRepository>().listBlockedSubjects }
        registerBean { R2dbcRevocationNoticeRepository(bean()) }
        registerBean { bean<R2dbcRevocationNoticeRepository>().storeRevocationNotice }
        registerBean { bean<R2dbcRevocationNoticeRepository>().listRevocationNotices }
        registerBean { bean<R2dbcRevocationNoticeRepository>().updateRevocationNotice }
        // Datos de contacto del estudiante (solo lectura) para avisarle de la revocación
        registerBean { GetSubjectContact { subject -> bean<GetAcademicDataFromDatabase>().contactOf(subject) } }
        registerBean<DeliverRevocationNotice> {
            WebhookDeliverRevocationNotice(
                webClient = bean(),
                url = env.getProperty("issuer.revocation.notification.webhook-url"),
                apiKey = env.getProperty("issuer.revocation.notification.api-key"),
            )
        }
        registerBean {
            RevocationNoticeService(
                clock = bean(),
                store = bean(),
                update = bean(),
                deliver = bean(),
                getContact = bean(),
                isSubjectBlocked = bean(),
                issuerName = env.getProperty("issuer.revocation.notification.issuer-name", "la Universidad Industrial de Santander (UIS)"),
            )
        }
        registerBean {
            SubjectControls(
                isBlocked = bean(),
                block = bean(),
                unblock = bean(),
                listBlocked = bean(),
                listNotices = bean(),
                updateNotice = bean(),
            )
        }
        registerBean { bean<R2dbcIssuedCredentialRepository>().getActiveIssuedCredentialsBySubject }

        //
        // Identidad del estudiante: tickets, códigos de activación, panel admin y usuarios de Keycloak
        //
        registerBean<OfferTickets> { InMemoryOfferTickets(bean()) }
        registerBean<ActivationCodes> { InMemoryActivationCodes(bean()) }
        registerBean { academicDb(env) }
        registerBean { UisAcademicStore(bean<AcademicDb>()) }
        registerBean {
            SyncEstudianteToKeycloak(
                webClient = bean(),
                serverUrl = env.getProperty("issuer.keycloak.server-url", "http://keycloak-uis:8080/idp-uis"),
                adminRealm = env.getProperty("issuer.keycloak.authentication-realm", "master"),
                adminClientId = env.getProperty("issuer.keycloak.client-id", "admin-cli"),
                adminUser = env.getProperty("issuer.keycloak.username", "admin"),
                adminPassword = env.getProperty("issuer.keycloak.password", "password"),
                userRealm = env.getProperty("issuer.keycloak.user-realm", "uis-issuer-realm"),
            )
        }
        registerBean { PidIngresos() }
        registerBean {
            PidVerifierClient(
                webClient = bean(),
                apiBase = env.getProperty("issuer.uis.verifier.api", "http://verifier-uis/verifier-uis"),
            )
        }
        registerBean { UisAdminUsers(bean<org.springframework.r2dbc.core.DatabaseClient>()) }

        //
        // Deferred Credentials
        //
        registerBean { InMemoryDeferredCredentialRepository(mutableMapOf(), bean()) }
        registerBean { GenerateTransactionId.Random }
        registerBean { bean<InMemoryDeferredCredentialRepository>().storeDeferredCredential }
        registerBean { bean<InMemoryDeferredCredentialRepository>().loadDeferredCredentialByTransactionId }

        //
        // Specific Issuers
        //

        registerBean {
            getPidDataFromKeyCloak(bean(), bean(), env, webClient = bean())
        }
        registerBean {
            getAcademicDataFromDatabase(env, bean())
        }
        registerBean {
            val ctx =
                IssuerFactory.Ctx(
                    bean(),
                    bean(),
                    env,
                    credentialIssuerId = issuerPublicUrl,
                    validateProof =
                        validateProof(
                            issuerPublicUrl,
                            bean(),
                            bean(),
                            bean(),
                        ),
                    storeIssuedCredential = bean(),
                    allocateStatus = bean(),
                    generateNotificationId = bean(),
                )
            val attestationIssuers =
                context(ctx) {
                    buildList {
                        if (env.getBoolean("issuer.pid.mso_mdoc.enabled") ?: true) {
                            val issueMsoMdocPid =
                                IssuerFactory.pidInMdoc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.pid.mso_mdoc.signing-key"),
                                    getAttestationAttributes = bean(),
                                )
                            add(issueMsoMdocPid)
                            add(issueMsoMdocPid.asDeferred(bean(), bean(), bean()))
                        }

                        if (env.getBoolean("issuer.pid.sd_jwt_vc.enabled") ?: true) {
                            val issueSdJwtVcPid =
                                IssuerFactory.pidInSdJwtVc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.pid.sd_jwt_vc.signing-key"),
                                    getAttestationAttributes = bean(),
                                )
                            add(issueSdJwtVcPid)
                            add(issueSdJwtVcPid.asDeferred(bean(), bean(), bean()))
                        }

                        if (env.getBoolean("issuer.mdl.enabled", true)) {
                            val mdlIssuer = IssuerFactory.mdl(getIssuerSigningKey("issuer.mdl.signing-key"))
                            add(mdlIssuer)
                            add(mdlIssuer.asDeferred(bean(), bean(), bean()))
                        }
                        if (env.getBoolean("issuer.learningCredential.enabled") ?: true) {
                            val issueLearningCredential =
                                IssuerFactory.learningCredentialInSdJwtVc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.learningCredential.signing-key"),
                                    getPidData = bean(),
                                )
                            add(issueLearningCredential)
                            add(issueLearningCredential.asDeferred(bean(), bean(), bean()))
                        }

                        if (env.getBoolean("issuer.academic.enabled") ?: false) {
                            val issueAcademicCredential =
                                IssuerFactory.academicCredentialInSdJwtVc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.academic.sd_jwt_vc.signing-key"),
                                    getAcademicData = bean(),
                                )
                            add(issueAcademicCredential)
                            add(issueAcademicCredential.asDeferred(bean(), bean(), bean()))

                            // Carnet estudiantil: otra credencial del mismo issuer, con los mismos datos y revocable.
                            val issueCarnetCredential =
                                IssuerFactory.carnetCredentialInSdJwtVc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.academic.sd_jwt_vc.signing-key"),
                                    getAcademicData = bean(),
                                )
                            add(issueCarnetCredential)
                            add(issueCarnetCredential.asDeferred(bean(), bean(), bean()))
                        }
                    }.toNonEmptyListOrNull()
                }
            checkNotNull(attestationIssuers) { "At least one credential issuer must be configured" }

            val preferredClientStatusPeriod =
                bean<IssuerMetadataProperties>().preferredClientStatusPeriod.toKotlinDuration()

            val registrationCertificate = Wrprc.tryParse(env.getRequiredProperty("issuer.wrprc"))

            CredentialIssuerMetaData(
                id = issuerPublicUrl,
                credentialEndPoint = issuerPublicUrl.appendPath(WalletApi.CREDENTIAL_ENDPOINT),
                deferredCredentialEndpoint = issuerPublicUrl.appendPath(WalletApi.DEFERRED_ENDPOINT),
                notificationEndpoint = issuerPublicUrl.appendPath(WalletApi.NOTIFICATION_ENDPOINT),
                nonceEndpoint = issuerPublicUrl.appendPath(WalletApi.NONCE_ENDPOINT),
                authorizationServers = listOf(env.readRequiredUrl("issuer.authorizationServer.publicUrl")),
                credentialRequestEncryption = env.credentialRequestEncryption { issuerKeystore },
                credentialResponseEncryption = env.credentialResponseEncryption(),
                attestationIssuers = attestationIssuers,
                batchCredentialIssuance = env.batchCredentialIssuance(),
                display =
                    bean<IssuerMetadataProperties>()
                        .display
                        .map { display ->
                            CredentialIssuerDisplay(
                                name = display.name,
                                locale = display.locale?.let { Locale.forLanguageTag(it) },
                                logo = display.logo?.let { ImageUri(Uri.parse(it.uri), it.alternativeText) },
                            )
                        },
                preferredClientStatusPeriod = PreferredClientStatusPeriod(preferredClientStatusPeriod),
                registrationCertificate = registrationCertificate,
            )
        }

        //
        // In Ports (use cases)
        //
        registerBean {
            GetCredentialIssuerMetaData(bean(), bean())
        }
        registerBean {
            IssueCredential(
                credentialIssuerMetadata = bean(),
                encryptCredentialResponse = bean(),
                clock = bean(),
                getStatusListTokenStatus = bean(),
            )
        }
        registerBean {
            GetDeferredCredential(bean(), bean(), bean())
        }
        registerBean {
            val defaultCredentialOfferUri = Uri.parse(env.getRequiredProperty("issuer.credentialOffer.defaultUri"))
            val allowedSchemes =
                env
                    .getRequiredProperty<Set<String>>("issuer.credentialOffer.allowedSchemes")
                    .map { SupportedCredentialOfferUriScheme.of(it) }
                    .toNonEmptySetOrThrow()
            CreateCredentialsOffer(bean(), defaultCredentialOfferUri, allowedSchemes)
        }

        registerBean {
            GetProtectedResourceMetadata(
                credentialIssuerMetadata = bean(),
                dPoPConfigurationProperties = bean(),
            )
        }

        //
        // Routes
        //
        registerBean {
            val typeMetadata =
                bean<SdJwtVcProperties>()
                    .typeMetadata
                    .associateBy { Vct(it.vct) }
                    .mapValues { it.value.resource }
            val metaDataApi = MetaDataApi(bean(), bean(), typeMetadata, bean())
            val walletApi = WalletApi(bean(), bean(), bean(), bean())
            val issuerUi = IssuerUi(bean(), bean(), bean(), bean(), LoginRateLimit.fromEnvironment(env, bean()), bean(), bean(), bean(), bean())
            val issuerApi = IssuerApi(bean())
            val revocationApi = RevocationApi(bean(), env.getProperty("issuer.revocation.api-key"), bean())
            val ticketsApi = TicketsApi(bean(), bean(), env.getProperty("issuer.ticket.api-key"))
            val uisAdminUi = UisAdminUi(bean(), bean(), bean(), bean(), bean())
            val uisIngresoApi = UisIngresoApi(bean(), bean(), bean(), bean(), bean())
            metaDataApi.route
                .and(walletApi.route)
                .and(ticketsApi.router)
                .and(uisAdminUi.router)
                .and(HistorialEmisionesUi(bean()).router)
                .and(uisIngresoApi.router)
                .and(issuerUi.router)
                .and(issuerApi.router)
                .and(revocationApi.router)
        }

        //
        // Security
        //

        // UI Security
        registerBean(order = Ordered.HIGHEST_PRECEDENCE) {
            configureUiSecurity(env, bean())
        }
        registerBean<CsrfTokenSubscriberWebFilter>()
        registerBean { AuditoriaEmisionWebFilter(env.getProperty("issuer.publicUrl") ?: "", "UIS (cédula estudiantil)", "eudi-srv-issuer-pid-uis") } // auditoría (solo monitoreo)

        // Panel de estudiantes (login propio por formulario; NO Keycloak)
        registerBean(order = Ordered.HIGHEST_PRECEDENCE + 5) {
            val users = bean<UisAdminUsers>()
            val maxFailures = env.getProperty("issuer.rateLimit.adminLogin.maxFailuresPerUser", Int::class.javaObjectType, 5)
            val window = env.duration("issuer.rateLimit.adminLogin.window") ?: AttemptLimiter.DEFAULT_WINDOW
            val username = env.getProperty("issuer.uis.admin.username")
            val password = env.getProperty("issuer.uis.admin.password")
            runBlocking {
                users.ensureTable()
                if (username.isNullOrBlank() || password.isNullOrBlank()) {
                    log.warn("issuer.uis.admin.username/password no definidos: no se crea usuario del panel de estudiantes")
                } else {
                    users.upsert(username, org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password)!!)
                    log.info("Usuario '{}' del panel de estudiantes listo", username)
                }
            }
            configureUisAdminSecurity(http = bean(), userDetailsService = users, loginLimiter = AttemptLimiter(maxFailures, window, bean()))
        }

        // API Security
        registerBean(order = Ordered.HIGHEST_PRECEDENCE + 10) {
            configureApiSecurity(
                http = bean(),
                metadata = bean(),
                oAuth2ResourceServerProperties = bean(),
                dPoPConfigurationProperties = bean(),
                webClient = bean(),
                env = env,
                clock = bean(),
                verifyNonce = bean(),
                generateNonce = bean(),
            )
        }

        //
        // Other
        //
        registerBean {
            CodecCustomizer {
                val json =
                    Json {
                        explicitNulls = false
                        ignoreUnknownKeys = true
                    }
                it.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
                it.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
                it.defaultCodecs().enableLoggingRequestDetails(true)
            }
        }
    })

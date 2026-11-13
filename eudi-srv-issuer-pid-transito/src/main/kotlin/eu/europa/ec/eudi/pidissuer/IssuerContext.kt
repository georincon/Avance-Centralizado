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

import eu.europa.ec.eudi.pidissuer.adapter.out.transito.CategoriaLicencia
import arrow.core.toNonEmptyListOrNull
import arrow.core.toNonEmptyListOrThrow
import arrow.core.toNonEmptySetOrThrow
import com.eygraber.uri.Uri
import com.eygraber.uri.Url
import com.eygraber.uri.toURI
import com.eygraber.uri.toUrl
import eu.europa.ec.eudi.pidissuer.adapter.input.scheduler.CredentialRevocationJob
import eu.europa.ec.eudi.pidissuer.adapter.input.web.*
import eu.europa.ec.eudi.pidissuer.adapter.input.web.AuditoriaEmisionWebFilter
import eu.europa.ec.eudi.pidissuer.adapter.input.web.csrf.CsrfTokenSubscriberWebFilter
import eu.europa.ec.eudi.pidissuer.adapter.input.web.security.TransitoAdminUserDetailsService
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
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.AdminUsuarioR2dbcRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.MobileDrivingLicence
import eu.europa.ec.eudi.pidissuer.port.out.attestation.GetAttestationAttributes
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.R2dbcLicenciaRepository
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.upsert
import eu.europa.ec.eudi.pidissuer.adapter.input.web.OnboardingOffersApi
import eu.europa.ec.eudi.pidissuer.adapter.out.onboarding.HttpCompareOnboardingSelfie
import eu.europa.ec.eudi.pidissuer.adapter.out.onboarding.HttpFaceMatcher
import eu.europa.ec.eudi.pidissuer.adapter.out.onboarding.HttpGetOnboardingSession
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.InMemoryOfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.transito.CompareOnboardingSelfie
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatcher
import eu.europa.ec.eudi.pidissuer.port.out.transito.GetOnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.transito.OfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.transito.SaveLicencia
import eu.europa.ec.eudi.pidissuer.port.out.transito.SyncLicenciaToKeycloak
import eu.europa.ec.eudi.pidissuer.adapter.out.status.GenerateStatusListTokenWithExternalService
import eu.europa.ec.eudi.pidissuer.adapter.out.status.GetStatusListTokenWithStatium
import eu.europa.ec.eudi.pidissuer.adapter.out.status.MarkStatusAsRevokedWithExternalService
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.webclient.KtorHttpClients
import eu.europa.ec.eudi.pidissuer.adapter.out.webclient.WebClients
import eu.europa.ec.eudi.pidissuer.domain.*
import eu.europa.ec.eudi.pidissuer.port.input.*
import eu.europa.ec.eudi.pidissuer.port.out.attestation.asDeferred
import eu.europa.ec.eudi.pidissuer.port.out.notification.DeliverRevocationNotice
import eu.europa.ec.eudi.pidissuer.port.out.notification.GetSubjectContact
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GenerateNotificationId
import eu.europa.ec.eudi.pidissuer.port.out.persistence.GenerateTransactionId
import eu.europa.ec.eudi.pidissuer.port.out.persistence.StoreIssuedCredential
import eu.europa.ec.eudi.pidissuer.port.out.status.AllocateStatus
import eu.europa.ec.eudi.pidissuer.port.out.status.GetStatusListTokenStatus
import eu.europa.ec.eudi.pidissuer.port.out.status.MarkStatusAsRevoked
import eu.europa.ec.eudi.pidissuer.adapter.out.status.RevokeWalletInstancesWithWalletProvider
import eu.europa.ec.eudi.sdjwt.vc.Vct
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.http.codec.CodecCustomizer
import org.springframework.core.Ordered
import org.springframework.core.env.getRequiredProperty
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
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
            // Pérdida o robo: revocar también la wallet (WURevocation_07). Sin URL y clave configuradas, no se intenta.
            val url = env.getProperty("issuer.wallet-revocation.url")
            val key = env.getProperty("issuer.wallet-revocation.api-key")
            RevokeWalletOfSubject(
                clock = bean(),
                getActiveIssuedCredentialsBySubject = bean(),
                revokeWalletInstances =
                    if (!url.isNullOrBlank() && !key.isNullOrBlank()) RevokeWalletInstancesWithWalletProvider(bean(), url, key) else null,
            )
        }
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
        registerBean<StoreIssuedCredential> {
            val store = bean<R2dbcIssuedCredentialRepository>().storeIssuedCredential
            // Reinstalación o cambio de celular: al emitir a una wallet nueva se revoca lo de la anterior (apagado por defecto)
            if (env.getProperty("issuer.reissue.revoke-previous-wallet", Boolean::class.java, false)) {
                RevokePreviousWalletOnIssuance(
                    delegate = store,
                    clock = bean(),
                    getActiveIssuedCredentialsBySubject = bean(),
                    markStatusAsRevoked = bean(),
                    markIssuedCredentialRevoked = bean(),
                    noticeService = bean(),
                )
            } else {
                store
            }
        }
        registerBean { bean<R2dbcIssuedCredentialRepository>().loadIssuedCredentialsByNotificationId }
        registerBean { bean<R2dbcIssuedCredentialRepository>().getNonExpiredIssuedCredentials }
        registerBean { bean<R2dbcIssuedCredentialRepository>().deleteExpiredIssuedCredentials }
        registerBean { bean<R2dbcIssuedCredentialRepository>().markIssuedCredentialRevoked }

        //
        // Bloqueo de emisión por titular y avisos de revocación (tablas propias: no tocan los datos de los ciudadanos)
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
        // La Tránsito no guarda datos de contacto de los ciudadanos: el aviso queda pendiente para hacerlo a mano
        registerBean { GetSubjectContact { null } }
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
                issuerName = env.getProperty("issuer.revocation.notification.issuer-name", "la Dirección de Tránsito de Bucaramanga"),
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
        // Tránsito: cédulas (ver README.md)
        //
        registerBean { R2dbcLicenciaRepository(bean(), bean()) }
        registerBean { bean<R2dbcLicenciaRepository>().findLicenciaByDocumentNumber }
        registerBean { bean<R2dbcLicenciaRepository>().listLicencias }
        registerBean { bean<R2dbcLicenciaRepository>().deleteLicencia }
        registerBean { syncLicenciaToKeycloak(env, webClient = bean()) }

        // Puente onboarding -> emisión: sesión de onboarding (server-to-server) y tickets de un solo uso
        registerBean<GetOnboardingSession> {
            HttpGetOnboardingSession(
                webClient = bean(),
                baseUrl = env.getProperty("issuer.onboarding.backend-url", "http://192.168.0.240:8095"),
            )
        }
        registerBean<OfferTickets> { InMemoryOfferTickets(bean()) }
        registerBean<CompareOnboardingSelfie> {
            HttpCompareOnboardingSelfie(
                webClient = bean(),
                baseUrl = env.getProperty("issuer.onboarding.backend-url", "http://192.168.0.240:8095"),
                apiKey = env.getProperty("issuer.onboarding.internal-api-key"),
            )
        }
        registerBean<FaceMatcher> {
            HttpFaceMatcher(
                webClient = bean(),
                baseUrl = env.getProperty("issuer.biometrics.url", "http://onboarding-biometrics:8100"),
            )
        }
        registerBean {
            // Al guardar un ciudadano, además de persistirlo, se sincroniza su
            // usuario fantasma en Keycloak (Opción A, sección 5 de
            // README.md) — solo si el emulador está activo;
            // si falla la sincronización, se guarda igual y solo se advierte,
            // para no bloquear la carga de datos por un problema de Keycloak.
            val persistLicencia = bean<R2dbcLicenciaRepository>().saveLicencia
            val syncToKeycloak = bean<SyncLicenciaToKeycloak>()
            val syncKeycloak = env.getProperty("issuer.transito.syncKeycloak", Boolean::class.java, true)
            SaveLicencia { licencia ->
                val saved = persistLicencia(licencia)
                if (syncKeycloak) {
                    runCatching { syncToKeycloak(saved) }
                        .onFailure { log.error("No se pudo sincronizar '{}' con Keycloak", saved.documentNumber.value.maskDocumentNumber(), it) }
                }
                saved
            }
        }

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
            getPidData(env, webClient = bean())
        }
        registerBean<GetAttestationAttributes<MobileDrivingLicence>> {
            getMdlDataFromTransito(env, findLicenciaByDocumentNumber = bean(), isSubjectBlocked = bean())
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
            val buildX5u: (SdJwtVcType) -> Url = { sdJwtVcType ->
                val pathSegments =
                    MetaDataApi.SDJWTVC_SIGNING_CERTIFICATES
                        .split("/")
                        .filter { it.isNotEmpty() }
                        .map { part ->
                            if (part == "{vct}") sdJwtVcType.value else part
                        }

                Url
                    .parse(ctx.env.getRequiredProperty("issuer.public-url"))
                    .buildUpon()
                    .apply {
                        pathSegments.forEach(::appendPath)
                    }.build()
                    .toURI()
                    .toUrl()
            }
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
                                    buildX5u = buildX5u,
                                    getAttestationAttributes = bean(),
                                )
                            add(issueSdJwtVcPid)
                            add(issueSdJwtVcPid.asDeferred(bean(), bean(), bean()))
                        }

                        if (env.getBoolean("issuer.mdl.enabled", true)) {
                            // Una credencial (configuración) por categoría de licencia: A1, A2, B1, ...
                            val mdlKey = getIssuerSigningKey("issuer.mdl.signing-key")
                            CategoriaLicencia.entries.forEach { categoria ->
                                val mdlIssuer =
                                    IssuerFactory.mdl(
                                        mdlKey,
                                        getMdlDataFromTransito(env, findLicenciaByDocumentNumber = bean(), isSubjectBlocked = bean(), categoria = categoria),
                                        categoria,
                                    )
                                add(mdlIssuer)
                                add(mdlIssuer.asDeferred(bean(), bean(), bean()))
                            }
                        }
                        if (env.getBoolean("issuer.learningCredential.enabled") ?: true) {
                            val issueLearningCredential =
                                IssuerFactory.learningCredentialInSdJwtVc(
                                    issuerSigningKey = getIssuerSigningKey("issuer.learningCredential.signing-key"),
                                    buildX5u = buildX5u,
                                    getPidData = bean(),
                                )
                            add(issueLearningCredential)
                            add(issueLearningCredential.asDeferred(bean(), bean(), bean()))
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
        registerBean {
            GetSigningCertificates(
                signingKeys =
                    bean<CredentialIssuerMetaData>()
                        .attestationIssuers
                        .map { it.configuration }
                        .filterIsInstance<SdJwtVcCredentialConfiguration>()
                        .associate { configuration ->
                            configuration.type to configuration.publicKey.parsedX509CertChain.toNonEmptyListOrThrow()
                        },
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
            val metaDataApi = MetaDataApi(bean(), bean(), typeMetadata, bean(), bean())
            val walletApi = WalletApi(bean(), bean(), bean(), bean())
            val issuerUi = IssuerUi(bean(), bean(), bean())
            val issuerApi = IssuerApi(bean())
            val onboardingOffersApi =
                OnboardingOffersApi(
                    createCredentialsOffer = bean(),
                    getOnboardingSession = bean(),
                    findLicenciaByDocumentNumber = bean(),
                    tickets = bean(),
                    ticketApiKey = env.getProperty("issuer.ticket.api-key"),
                    rateLimit = IpRateLimit.fromEnvironment(env, bean(), name = "onboardingOffers", defaultMaxAttempts = 0),
                    isSubjectBlocked = bean(),
                    compareSelfie = bean(),
                )
            val transitoAdminUi = TransitoAdminUi(bean(), bean(), bean(), bean(), bean(), bean(), bean())
            val revocationApi = RevocationApi(bean(), env.getProperty("issuer.revocation.api-key"), bean(), bean())
            val routes =
                metaDataApi.route
                    .and(walletApi.route)
                    .and(issuerUi.router)
                    .and(issuerApi.router)
                    .and(onboardingOffersApi.router)
                    .and(transitoAdminUi.router)
                    .and(HistorialEmisionesUi(bean()).router)
                    .and(revocationApi.router)
            // La pantalla ciudadana "cédula -> QR" no pide autenticación: se puede apagar con una propiedad.
            if (env.getBoolean(TransitoSolicitarUi.ENABLED_PROPERTY, true)) {
                val solicitarRateLimit = IpRateLimit.fromEnvironment(env, bean(), name = "solicitar", defaultMaxAttempts = 0)
                routes.and(TransitoSolicitarUi(bean(), bean(), bean(), solicitarRateLimit, bean(), bean(), bean()).router)
            } else {
                log.warn("{}=false: {} no está disponible", TransitoSolicitarUi.ENABLED_PROPERTY, TransitoSolicitarUi.SOLICITAR)
                routes
            }
        }

        //
        // Security
        //

        // UI Security
        registerBean(order = Ordered.HIGHEST_PRECEDENCE) {
            configureUiSecurity(env, bean())
        }
        registerBean<CsrfTokenSubscriberWebFilter>()
        registerBean { AuditoriaEmisionWebFilter(env.getProperty("issuer.publicUrl") ?: "", "Tránsito (licencias)", "eudi-srv-issuer-pid-transito") } // auditoría (solo monitoreo)

        // Tránsito admin panel Security (ver README.md, sección 5)
        registerBean { TransitoAdminUserDetailsService(bean()) }
        registerBean(order = Ordered.HIGHEST_PRECEDENCE + 5) {
            val adminMaxFailures = env.getProperty("issuer.rateLimit.adminLogin.maxFailuresPerUser", Int::class.javaObjectType, 5)
            val adminWindow = env.duration("issuer.rateLimit.adminLogin.window") ?: AttemptLimiter.DEFAULT_WINDOW
            configureTransitoAdminSecurity(
                http = bean(),
                userDetailsService = bean(),
                adminLoginLimiter = AttemptLimiter(adminMaxFailures, adminWindow, bean()),
            )
        }
        registerBean {
            // Crea/actualiza el admin inicial del panel a partir de propiedades, para
            // no depender de insertar el primer usuario a mano en la base de datos.
            ApplicationRunner {
                val username = env.getProperty("issuer.transito.admin.username")
                val password = env.getProperty("issuer.transito.admin.password")
                if (username == null || password == null) {
                    log.warn(
                        "issuer.transito.admin.username/password not set — " +
                            "no admin user will be created/updated at startup",
                    )
                } else {
                    val adminUsername: String = username
                    val adminPassword: String = password
                    runBlocking {
                        bean<AdminUsuarioR2dbcRepository>()
                            .upsert(adminUsername, BCryptPasswordEncoder().encode(adminPassword)!!)
                    }
                    log.info("Tránsito admin user '{}' ensured", adminUsername)
                }
            }
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

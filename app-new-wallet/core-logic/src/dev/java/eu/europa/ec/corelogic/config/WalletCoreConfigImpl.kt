/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.config

import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.auth.HeadlessAuthorizationHandler
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.provider.RegistrationCheckProvider
import eu.europa.ec.eudi.etsi119602.datamodel.Uri
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationClassifications
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationIdentifier
import eu.europa.ec.eudi.etsi1196x2.consultation.AttestationIdentifierPredicate
import eu.europa.ec.eudi.etsi1196x2.consultation.SupportedLists
import eu.europa.ec.eudi.iso18013.transfer.response.ReaderAuthPolicy
import eu.europa.ec.eudi.openid4vci.CredentialReusePolicies
import eu.europa.ec.eudi.openid4vci.EudiReusePolicyType
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.dcapi.DCAPIProtocol
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import eu.europa.ec.eudi.wallet.registration.issuer.IssuerRegistrationPolicy
import eu.europa.ec.eudi.wallet.registration.relyingparty.WrpRegistrationPolicy
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.eudi.wallet.trust.TrustPolicy
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class WalletCoreConfigImpl(
    private val registrationCheckProvider: RegistrationCheckProvider,
) : WalletCoreConfig {

    private var _config: EudiWalletConfig? = null

    override val isRegistrationCheckEnabled: Boolean by lazy {
        runBlocking { registrationCheckProvider.isEnabled() }
    }

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = false,
                        userAuthenticationTimeout = 30.seconds,
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withClientIdSchemes(
                            listOf(
                                ClientIdScheme.X509SanDns,
                                ClientIdScheme.X509Hash
                            )
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME,
                                BuildConfig.HAIP_OPENID4VP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc.ES256, Format.SdJwtVc.ES256
                        )
                    }

                    configureDCAPI {
                        withEnabled(true)
                        withSupportedProtocols(
                            DCAPIProtocol.ISO_MDOC,
                            DCAPIProtocol.OPENID4VP_V1_SIGNED,
                        )
                    }

                    configureEtsiTrust {
                        loteLocations(
                            SupportedLists(
                                pidProviders = Uri(BuildConfig.NW_TL_PID),
                                // El ancla de las partes usuarias esta en TL/RPREG: es la lista
                                // que publica el tipo de servicio CA/RPaccess.
                                wrpacProviders = Uri(BuildConfig.NW_TL_WRPAC),
                                // Sin lista de certificados de REGISTRO publicada: null, para que
                                // el SDK no la busque.
                                wrprcProviders = BuildConfig.NW_TL_WRPRC?.let { Uri(it) },
                                pubEaaProviders = Uri(BuildConfig.NW_TL_PUBEAA),
                                walletProviders = Uri(BuildConfig.NW_TL_WP),
                            )
                        )

                        classifications(
                            AttestationClassifications(
                                pids = AttestationIdentifierPredicate.any(
                                    identifiers = setOf(
                                        AttestationIdentifier.MDoc(
                                            docType = DocumentIdentifier.MdocPid.formatType
                                        ),
                                        AttestationIdentifier.SDJwtVc(
                                            vct = DocumentIdentifier.SdJwtPid.formatType
                                        ),
                                    )
                                )
                            )
                        )

                        relaxCertificateProfiles()
                        relaxPkixRevocation()
                    }

                    configureIssuerTrust {
                        // Local testing: do not block when the issuer chain is not on a LoTE
                        policy { default(TrustPolicy.Action.INFORM) }
                        ignoreSignedMetadata()
                        // La politica del Core debe seguir EXACTAMENTE el mismo interruptor
                        // que usa la app para validar (WalletCoreConfig.isRegistrationCheckEnabled).
                        // Si el Core evaluase siempre "Disabled" y la app exigiera el
                        // certificado, el resultado seria siempre "NotEvaluated" y la app lo
                        // leeria como rechazo: un falso bloqueo que no tiene nada que ver con
                        // la confianza real del emisor.
                        configureIssuerRegistrationPolicy(
                            if (isRegistrationCheckEnabled) {
                                IssuerRegistrationPolicy.Enabled
                            } else {
                                IssuerRegistrationPolicy.Disabled
                            }
                        )
                    }

                    configureDocumentStatusResolver {
                        configureTrust {
                            policy {
                                default(TrustPolicy.Action.INFORM)
                            }
                        }
                    }

                    configureReaderTrustStore {
                        readerAuthPolicy(ReaderAuthPolicy.EnforceIfPresent)
                    }

                    configureWrpRegistrationPolicy(
                        if (isRegistrationCheckEnabled) {
                            WrpRegistrationPolicy.Enabled
                        } else {
                            WrpRegistrationPolicy.Disabled
                        }
                    )
                }
            }
            return _config!!
        }

    override val issuersConfig: List<VciConfig>
        get() = listOf(
            VciConfig(
                issuerUrl = BuildConfig.NW_ISSUER_URL,
                config = OpenId4VciManager.Config.Builder()
                    .withClientAuthenticationType(
                        OpenId4VciManager.ClientAuthenticationType.AttestationBased(clientId = BuildConfig.NW_CLIENT_ID)
                    )
                    .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                    // Registraduria: la autorizacion se resuelve sin abrir el navegador; el ticket (issuer_state) de la oferta
                    // identifica al titular, no hay usuario ni contrasena.
                    .withAuthorizationHandler(
                        HeadlessAuthorizationHandler(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK.substringBefore("://"))
                    )
                    .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                    .withDPopConfig(DPopConfig.Default)
                    .withSupportedCredentialReusePolicies(
                        CredentialReusePolicies.Supported(
                            policyTypes = setOf(
                                EudiReusePolicyType.RotatingBatch,
                                EudiReusePolicyType.OnceOnly,
                                EudiReusePolicyType.LimitedTime,
                            )
                        )
                    )
                    .build(),
                order = 0
            ),
            VciConfig(
                // Issuer de la UIS: credenciales academicas (PuB-EAA). Se sirve por el
                // proxy unico bajo /uis-issuer y su autorizacion vive en /idp-uis
                // (cada Keycloak tiene su prefijo: /idp-reg el de la Registraduria,
                // /idp-uis el de la UIS).
                issuerUrl = "https://civica-desarrollo.avance.org.co/uis-issuer",
                config = OpenId4VciManager.Config.Builder()
                    .withClientAuthenticationType(
                        OpenId4VciManager.ClientAuthenticationType.AttestationBased(
                            // El client_id tiene que ser el del Wallet Provider que emite
                            // la WIA: el `sub` de la atestacion es el que Keycloak usa
                            // para identificar al cliente. Con uno distinto, el token se
                            // emite para ese otro cliente y se queda sin el mapper que
                            // anade el claim `client_status`, que el emisor exige.
                            clientId = BuildConfig.NW_CLIENT_ID
                        )
                    )
                    .withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
                    // UIS: igual que la Registraduria, sin navegador; el ticket de la oferta (issuer_state) identifica al estudiante.
                    .withAuthorizationHandler(
                        HeadlessAuthorizationHandler(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK.substringBefore("://"))
                    )
                    .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                    .withDPopConfig(DPopConfig.Default)
                    .withSupportedCredentialReusePolicies(
                        CredentialReusePolicies.Supported(
                            policyTypes = setOf(
                                EudiReusePolicyType.RotatingBatch,
                                EudiReusePolicyType.OnceOnly,
                                EudiReusePolicyType.LimitedTime,
                            )
                        )
                    )
                    .build(),
                order = 1
            ),

        )

    override val documentIssuanceConfig: DocumentIssuanceConfig
        get() = DocumentIssuanceConfig(
            defaultPolicy = CredentialPolicy.RotatingBatch(
                numberOfCredentials = 1,
                reissueTriggerLifetimeLeft = 24.hours
            ),
            documentSpecificPolicies = mapOf(
                // Copias de un solo uso del PID: 50 es el tope que concede el emisor de la Registraduría (batchSize); con las
                // 10-20 de antes se agotaban en una tarde de pruebas y la renovación en segundo plano pide autorización del usuario.
                DocumentIdentifier.MdocPid to CredentialPolicy.OnceOnly(
                    numberOfCredentials = 50,
                    reissueTriggerUnused = 5
                ),
                DocumentIdentifier.SdJwtPid to CredentialPolicy.OnceOnly(
                    numberOfCredentials = 50,
                    reissueTriggerUnused = 5
                ),
                // Tiquete de la aerolínea de ejemplo: una sola copia de UN SOLO USO. Al presentarlo en el embarque se gasta (el contador pasa a
                // 0/1) y no se renueva solo: -1 hace que la renovación en segundo plano nunca lo dé por «pendiente» (un tiquete usado no se reexpide).
                DocumentIdentifier.OTHER(formatType = "urn:co:aerolinea:tiquete:1") to CredentialPolicy.OnceOnly(
                    numberOfCredentials = 1,
                    reissueTriggerUnused = -1
                ),
            ),
            reissuanceRule = ReIssuanceRule(
                backgroundInterval = 15.minutes
            )
        )

    override val walletProviderHost: String
        get() = BuildConfig.NW_WALLET_PROVIDER_URL
}
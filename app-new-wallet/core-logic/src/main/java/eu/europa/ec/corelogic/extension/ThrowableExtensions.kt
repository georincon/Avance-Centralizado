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

package eu.europa.ec.corelogic.extension

import eu.europa.ec.corelogic.model.UntrustedIssuerReasonDomain
import eu.europa.ec.eudi.openid4vci.AuthorizationPolicyValidationError
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadataError
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestError
import eu.europa.ec.eudi.openid4vci.CredentialOfferRequestException
import eu.europa.ec.eudi.wallet.trust.IssuerNotTrustedException

/**
 * Which trust layer refused the issuer behind this failure, or null when the failure is not about
 * trust and the caller should surface it as an ordinary error. An untrusted issuer chain or
 * missing/invalid signed metadata reads as [UntrustedIssuerReasonDomain.ACCESS_CERTIFICATE]; an
 * absent registration certificate, or one that fails policy validation, reads as
 * [UntrustedIssuerReasonDomain.REGISTRATION_CERTIFICATE].
 */
fun Throwable.toUntrustedIssuerReasonOrNull(): UntrustedIssuerReasonDomain? {
    var throwable: Throwable? = this
    while (throwable != null) {
        if (throwable is IssuerNotTrustedException ||
            throwable is CredentialIssuerMetadataError.InvalidSignedMetadata ||
            throwable is CredentialIssuerMetadataError.MissingSignedMetadata
        ) {
            return UntrustedIssuerReasonDomain.ACCESS_CERTIFICATE
        }

        if (throwable is AuthorizationPolicyValidationError) {
            return UntrustedIssuerReasonDomain.REGISTRATION_CERTIFICATE
        }

        // CredentialOfferRequestException carries its error in a property, not in `cause`
        val offerError = (throwable as? CredentialOfferRequestException)?.error
        throwable =
            if (offerError is CredentialOfferRequestError.UnableToResolveCredentialIssuerMetadata) {
                offerError.reason
            } else {
                throwable.cause
            }
    }
    return null
}

/**
 * Flattens a failure into a single readable line: the type of every exception in the cause chain
 * plus its message, separated by " <- ".
 *
 * Issuance failures reach the app wrapped in several layers (wallet core event -> openid4vci
 * exception -> HTTP error). Without this, the log only kept the outermost message, which is why a
 * rejected client attestation and a DNS failure looked exactly the same on the device.
 */
fun Throwable.describeCause(): String {
    val chain = mutableListOf<String>()
    var throwable: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (throwable != null && seen.add(throwable)) {
        val message = throwable.message?.takeIf { it.isNotBlank() }
        chain += if (message != null) {
            "${throwable::class.simpleName}: $message"
        } else {
            throwable::class.simpleName ?: "Throwable"
        }

        val offerError = (throwable as? CredentialOfferRequestException)?.error
        throwable =
            if (offerError is CredentialOfferRequestError.UnableToResolveCredentialIssuerMetadata) {
                offerError.reason
            } else {
                throwable.cause
            }
    }
    return chain.joinToString(" <- ")
}
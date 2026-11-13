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

import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentedClaim
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentedDocument

/**
 * Claims that an SD-JWT VC carries as part of the token itself (RFC 7519 registered claims, the
 * holder key in `cnf`, the status reference, the credential type and the selective-disclosure
 * bookkeeping). They are always present in a presented credential and none of them is data the
 * relying party asked for.
 */
private val SD_JWT_VC_TECHNICAL_CLAIMS = setOf(
    "iss", "sub", "aud", "iat", "nbf", "exp", "jti", "nonce",
    "cnf", "status", "vct", "vct#integrity",
    "_sd", "_sd_alg",
)

/**
 * The claims of a presented document that the user actually shared.
 *
 * The transaction log of the Core stores the whole decoded SD-JWT, so besides the claims the
 * verifier requested it also holds the technical ones (`iss`, `exp`, `cnf.jwk.x`...). Showing or
 * counting them tells the user that "10 data" were shared when only one (e.g. "Over 18") was.
 * An mdoc only ever contains the requested data elements, so it is returned untouched.
 */
fun PresentedDocument.sharedClaims(): List<PresentedClaim> = when (format) {
    is SdJwtVcFormat -> claims.filterNot { claim ->
        claim.path.firstOrNull() in SD_JWT_VC_TECHNICAL_CLAIMS
    }

    else -> claims
}

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

import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager

/**
 * Pairs an OpenID4VCI issuer's [config] with the [issuerUrl] identifying it and its display
 * [order] on the Add Document screen.
 *
 * @property issuerUrl Credential issuer identifier; the key used to fetch the issuer's metadata
 * and to match an incoming credential offer to this config.
 * @property config OpenID4VCI configuration for this issuer.
 * @property order Display order on the Add Document screen; lower comes first.
 */
data class VciConfig(
    val issuerUrl: String,
    val config: OpenId4VciManager.Config,
    val order: Int,
)
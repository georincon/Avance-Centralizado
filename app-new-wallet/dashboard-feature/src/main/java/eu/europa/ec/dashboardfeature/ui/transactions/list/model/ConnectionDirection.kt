/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the 'Licence'); You may not use this work
 * except in compliance with the Licence.
 *
 * You may have received a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * Licence is distributed on an 'AS IS' basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the Licence for the specific language governing permissions
 * and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.transactions.list.model

/**
 * Which way the data went in a connection.
 *
 * [SHARED] is a presentation: the wallet handed attributes to a verifier.
 * [RECEIVED] is an issuance: an issuer created a credential for the wallet.
 */
enum class ConnectionDirection {
    SHARED,
    RECEIVED,
}

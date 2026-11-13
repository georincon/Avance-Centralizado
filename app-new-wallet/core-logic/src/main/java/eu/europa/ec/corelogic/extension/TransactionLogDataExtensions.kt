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

import eu.europa.ec.businesslogic.util.toLocalDate
import eu.europa.ec.businesslogic.util.toLocalDateTime
import eu.europa.ec.corelogic.model.TransactionLogDataDomain
import eu.europa.ec.corelogic.model.TransactionLogDataDomain.IssuanceLog
import eu.europa.ec.corelogic.model.TransactionLogDataDomain.PresentationLog
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.transactionLogging.TransactionLog
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentationTransactionLog
import java.net.URI
import java.util.Locale

// TODO RETURN PROPER OBJECTS ONCE READY FROM CORE ISSUANCE,SIGNING
@Throws(IllegalArgumentException::class)
internal fun Any.toTransactionLogData(id: String): TransactionLogDataDomain = when (this) {
    is PresentationTransactionLog -> PresentationLog(
        id = id,
        name = relyingParty.name,
        status = status,
        creationLocalDateTime = timestamp.toLocalDateTime(),
        creationLocalDate = timestamp.toLocalDate(),
        relyingParty = relyingParty,
        documents = documents,
    )

    else -> throw IllegalArgumentException("Unknown transaction log type")
}

/**
 * Turns an issued document into the "received" side of the activity history.
 *
 * The Core does not log issuances (only presentations), so the credential the wallet holds is
 * the source of truth for what it received: the issuer that created it, when it was issued and
 * how many attributes it carries.
 */
internal fun IssuedDocument.toIssuanceLogData(userLocale: Locale): IssuanceLog {
    val issuer = localizedIssuerMetadata(userLocale)
    return IssuanceLog(
        id = id,
        name = name,
        status = TransactionLog.Status.Completed,
        creationLocalDateTime = issuedAt.toLocalDateTime(),
        creationLocalDate = issuedAt.toLocalDate(),
        issuerName = issuer?.name
            ?: issuerMetadata?.credentialIssuerIdentifier?.let { identifier ->
                runCatching { URI(identifier).host }.getOrNull()
            }
            ?: "",
        attributesCount = data.claims.size,
    )
}
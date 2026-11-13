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

package eu.europa.ec.corelogic.model

import eu.europa.ec.corelogic.extension.getLocalizedDocumentName
import eu.europa.ec.eudi.wallet.transactionLogging.TransactionLog
import eu.europa.ec.eudi.wallet.transactionLogging.presentation.PresentedDocument
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

sealed interface TransactionLogDataDomain {

    val id: String
    val name: String
    val status: TransactionLog.Status
    val creationLocalDateTime: LocalDateTime
    val creationLocalDate: LocalDate

    data class PresentationLog(
        override val id: String,
        override val name: String,
        override val status: TransactionLog.Status,
        override val creationLocalDateTime: LocalDateTime,
        override val creationLocalDate: LocalDate,
        val relyingParty: TransactionLog.RelyingParty,
        val documents: List<PresentedDocument>,
    ) : TransactionLogDataDomain

    /**
     * A credential the wallet received from an issuer.
     *
     * The Core only logs *presentations* (see `TransactionsListener`), so this is not read from
     * the transaction log: it is derived from the issued documents the wallet holds. The id is
     * the document id, which is what makes it addressable from the detail screen.
     */
    data class IssuanceLog(
        override val id: String,
        override val name: String,
        override val status: TransactionLog.Status,
        override val creationLocalDateTime: LocalDateTime,
        override val creationLocalDate: LocalDate,
        val issuerName: String = "",
        val attributesCount: Int? = null,
    ) : TransactionLogDataDomain

    data class SigningLog(
        override val id: String,
        override val name: String,
        override val status: TransactionLog.Status,
        override val creationLocalDateTime: LocalDateTime,
        override val creationLocalDate: LocalDate,
    ) : TransactionLogDataDomain

    companion object {
        fun TransactionLogDataDomain.getTransactionTypeLabel(resourceProvider: ResourceProvider): String {
            return when (this) {
                is PresentationLog -> resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.transactions_screen_filters_filter_by_transaction_type_presentation)
                is IssuanceLog -> resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.transactions_screen_filters_filter_by_transaction_type_issuance)
                is SigningLog -> resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.transactions_screen_filters_filter_by_transaction_type_signing)
            }
        }

        /**
         * The counterparty of the transaction: the verifier the wallet presented to, or the issuer
         * the wallet received a credential from. Null when the counterparty is unknown.
         */
        val TransactionLogDataDomain.counterpartyName: String?
            get() = when (this) {
                is PresentationLog -> relyingParty.name
                is IssuanceLog -> issuerName.takeIf { it.isNotBlank() }
                is SigningLog -> null
            }

        fun TransactionLogDataDomain.getTransactionDocumentNames(userLocale: Locale): List<String> {
            return when (this) {
                is IssuanceLog -> {
                    // The credential itself is the "document" of an issuance: there is no
                    // presented document to name.
                    listOf(name).filter { it.isNotBlank() }
                }

                is PresentationLog -> {
                    this.documents.mapNotNull { document ->
                        document.metadata.getLocalizedDocumentName(
                            userLocale = userLocale,
                            fallback = ""
                        ).takeIf { it.isNotBlank() }
                    }.flatMap { documentName ->
                        listOf(
                            documentName,
                            documentName.replace(regex = "\\s".toRegex(), replacement = "")
                        )
                    }
                }

                is SigningLog -> {
                    //TODO change this once Core supports more transaction types
                    emptyList()
                }
            }
        }
    }
}
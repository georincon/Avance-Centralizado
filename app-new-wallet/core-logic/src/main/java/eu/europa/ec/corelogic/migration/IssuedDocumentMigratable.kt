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

package eu.europa.ec.corelogic.migration

import eu.europa.ec.businesslogic.migration.MigratableDocument
import eu.europa.ec.corelogic.extension.localizedDocumentDisplay
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import java.util.Locale

/**
 * Adaptador [IssuedDocument] -> [MigratableDocument] (Objeto de Migración, ARF Topic 34).
 *
 * Lee SOLO metadatos de emisión (id local, formato/tipo, emisor, configuración de credencial,
 * nombres visibles). NUNCA lee claims/atributos ni claves.
 */
internal class IssuedDocumentMigratable(
    override val id: String,
    override val attestationType: String,
    override val format: String,
    override val isPid: Boolean,
    override val issuerIdentifier: String,
    override val credentialConfigurationId: String?,
    override val issuerDisplayName: String?,
    override val documentDisplayName: String?,
) : MigratableDocument

/**
 * @return la vista migrable del documento, o null si no tiene metadatos de emisor (no se
 * podría volver a pedir).
 */
fun IssuedDocument.toMigratableDocument(locale: Locale): MigratableDocument? {
    val issuerId = issuerMetadata?.credentialIssuerIdentifier?.takeIf { it.isNotBlank() }
        ?: return null

    val (type, formatName) = when (val f = format) {
        is MsoMdocFormat -> f.docType to "mso_mdoc"
        is SdJwtVcFormat -> f.vct to "dc+sd-jwt"
    }
    val identifier = toDocumentIdentifier()

    return IssuedDocumentMigratable(
        id = id,
        attestationType = type,
        format = formatName,
        isPid = identifier == DocumentIdentifier.MdocPid || identifier == DocumentIdentifier.SdJwtPid,
        issuerIdentifier = issuerId,
        credentialConfigurationId = issuerMetadata?.documentConfigurationIdentifier,
        issuerDisplayName = localizedIssuerMetadata(locale)?.name,
        documentDisplayName = localizedDocumentDisplay(locale)?.name ?: name,
    )
}

fun List<IssuedDocument>.toMigratableDocuments(locale: Locale): List<MigratableDocument> =
    mapNotNull { it.toMigratableDocument(locale) }

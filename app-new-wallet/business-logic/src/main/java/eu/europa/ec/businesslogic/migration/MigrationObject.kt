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

package eu.europa.ec.businesslogic.migration

import com.google.gson.annotations.SerializedName

/**
 * Objeto de Migración (ARF Topic 34, Mig_01..Mig_07a).
 *
 * Lista que la wallet mantiene al día con, por cada PID/atestación presente, lo necesario para
 * volver a pedirla a su emisor (Mig_03): tipo, emisor y direcciones de servicio.
 *
 * NO contiene valores ni identificadores de atributos, ni claves privadas (Mig_03). Los campos
 * son solo metadatos de emisión; no hay ningún campo que pueda alojar claims.
 *
 * Nota: el registro de transacciones (Mig_01/Mig_07b) aún no forma parte de este objeto.
 */
data class MigrationObject(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("entries") val entries: List<MigrationEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun empty(now: Long): MigrationObject = MigrationObject(createdAt = now, updatedAt = now)
    }

    /**
     * Sincroniza el objeto con los documentos presentes (Mig_01, Mig_03, Mig_03b): añade las
     * entradas nuevas y elimina las de documentos que ya no existen. Conserva `createdAt`.
     */
    fun syncWith(documents: List<MigratableDocument>, now: Long): MigrationObject {
        val current = documents.map { MigrationEntry.from(it) }
        return copy(
            entries = current,
            updatedAt = if (current == entries) updatedAt else now,
        )
    }

    /** Mig_03b: elimina la entrada del documento borrado por la persona. */
    fun withoutDocument(documentId: String, now: Long): MigrationObject {
        val remaining = entries.filterNot { it.documentId == documentId }
        return if (remaining.size == entries.size) this else copy(entries = remaining, updatedAt = now)
    }

    /**
     * Mig_07/Mig_07a: entradas elegidas por la persona, ordenadas para pedirlas: el PID primero
     * (para que otros emisores puedan autenticar con él) y luego el resto en su orden original.
     */
    fun restorePlan(selectedDocumentIds: Set<String>): List<MigrationEntry> {
        val selected = entries.filter { it.documentId in selectedDocumentIds }
        return selected.filter { it.isPid } + selected.filterNot { it.isPid }
    }
}

/**
 * Una entrada: datos para solicitar de nuevo un PID o atestación. `documentId` es un
 * identificador local opaco, solo sirve para sincronizar/borrar (Mig_03b) y para que la persona
 * seleccione al importar.
 */
data class MigrationEntry(
    @SerializedName("documentId") val documentId: String,
    /** docType (mso_mdoc) o vct (SD-JWT VC). */
    @SerializedName("attestationType") val attestationType: String,
    /** Formato de credencial, p. ej. "mso_mdoc" o "dc+sd-jwt". */
    @SerializedName("format") val format: String,
    @SerializedName("isPid") val isPid: Boolean,
    /** Si la atestación está ligada al dispositivo (Mig_03) o no (Mig_03a). */
    @SerializedName("deviceBound") val deviceBound: Boolean,
    @SerializedName("issuerIdentifier") val issuerIdentifier: String,
    /** URL base del Credential Issuer (OpenID4VCI): de ahí salen metadatos y endpoints. */
    @SerializedName("credentialIssuerUrl") val credentialIssuerUrl: String,
    @SerializedName("credentialConfigurationId") val credentialConfigurationId: String?,
    @SerializedName("issuerDisplayName") val issuerDisplayName: String?,
    @SerializedName("documentDisplayName") val documentDisplayName: String?,
) {
    companion object {
        fun from(document: MigratableDocument) = MigrationEntry(
            documentId = document.id,
            attestationType = document.attestationType,
            format = document.format,
            isPid = document.isPid,
            deviceBound = document.deviceBound,
            issuerIdentifier = document.issuerIdentifier,
            credentialIssuerUrl = document.credentialIssuerUrl ?: document.issuerIdentifier,
            credentialConfigurationId = document.credentialConfigurationId,
            issuerDisplayName = document.issuerDisplayName,
            documentDisplayName = document.documentDisplayName,
        )
    }
}

/**
 * Vista mínima de un documento emitido, para no acoplar este paquete a la librería de wallet-core.
 * Se implementa con un adaptador sobre `IssuedDocument` (ver docs/objeto-de-migracion.md).
 * Deliberadamente no expone atributos ni claves.
 */
interface MigratableDocument {
    val id: String
    val attestationType: String
    val format: String
    val isPid: Boolean
    val deviceBound: Boolean get() = true
    val issuerIdentifier: String
    val credentialIssuerUrl: String? get() = null
    val credentialConfigurationId: String?
    val issuerDisplayName: String? get() = null
    val documentDisplayName: String? get() = null
}

package com.onboarding.storage

data class StoredFile(
    val key: String,
    val size: Long,
)

/** Contrato de almacenamiento de archivos (imagenes de documentos y selfies). */
interface FileStorage {
    val providerName: String
    fun store(key: String, bytes: ByteArray, contentType: String?): StoredFile
    fun load(key: String): ByteArray?
    fun delete(key: String)
}

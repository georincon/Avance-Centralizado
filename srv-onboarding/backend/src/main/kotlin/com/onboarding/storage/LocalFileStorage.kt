package com.onboarding.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Almacenamiento en disco local. Ideal para desarrollo. Para produccion usar
 * el adaptador MinIO/S3 (misma interfaz FileStorage).
 */
@Component
@ConditionalOnProperty(name = ["onboarding.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalFileStorage(
    @Value("\${onboarding.storage.local-path}") localPath: String,
) : FileStorage {
    private val log = LoggerFactory.getLogger(javaClass)
    private val baseDir: Path = Paths.get(localPath).toAbsolutePath().normalize()

    init {
        Files.createDirectories(baseDir)
        log.info("LocalFileStorage inicializado en {}", baseDir)
    }

    override val providerName = "local"

    override fun store(key: String, bytes: ByteArray, contentType: String?): StoredFile {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        log.debug("Archivo guardado {} ({} bytes)", target, bytes.size)
        return StoredFile(key, bytes.size.toLong())
    }

    override fun load(key: String): ByteArray? {
        val target = resolve(key)
        return if (Files.exists(target)) Files.readAllBytes(target) else null
    }

    override fun delete(key: String) {
        Files.deleteIfExists(resolve(key))
    }

    private fun resolve(key: String): Path {
        val target = baseDir.resolve(key).normalize()
        require(target.startsWith(baseDir)) { "Ruta de almacenamiento invalida" }
        return target
    }
}

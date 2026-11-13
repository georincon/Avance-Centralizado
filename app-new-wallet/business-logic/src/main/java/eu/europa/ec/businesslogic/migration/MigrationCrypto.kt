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

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MigrationFormatException(message: String) : Exception(message)

/** Contraseña incorrecta o archivo alterado: GCM no distingue ambos casos. */
class MigrationDecryptionException(cause: Throwable? = null) :
    Exception("Contraseña incorrecta o archivo alterado", cause)

/**
 * Cifrado del Objeto de Migración con contraseña (Mig_05): PBKDF2-HMAC-SHA256 + AES-256-GCM.
 *
 * Formato binario (versión 1), big-endian:
 *  - magic "NWMO" (4) | versión (1) | iteraciones PBKDF2 (4) | sal (16) | nonce (12) | texto cifrado + tag (16)
 * La cabecera completa (hasta el nonce) se autentica como AAD: alterarla invalida el archivo.
 */
object MigrationCrypto {
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'W'.code.toByte(), 'M'.code.toByte(), 'O'.code.toByte())
    const val FORMAT_VERSION = 1
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + NONCE_LEN
    private val random = SecureRandom()

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        require(password.isNotEmpty()) { "La contraseña no puede estar vacía" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Iteraciones fuera de rango" }
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC).put(FORMAT_VERSION.toByte()).putInt(iterations).put(salt).put(nonce)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < HEADER_LEN + TAG_BITS / 8) throw MigrationFormatException("Archivo demasiado corto")
        val buf = ByteBuffer.wrap(data)
        val magic = ByteArray(4).also(buf::get)
        if (!magic.contentEquals(MAGIC)) throw MigrationFormatException("No es una copia de migración")
        val version = buf.get().toInt()
        if (version != FORMAT_VERSION) throw MigrationFormatException("Versión de formato no soportada: $version")
        val iterations = buf.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw MigrationFormatException("Parámetros KDF inválidos")
        val salt = ByteArray(SALT_LEN).also(buf::get)
        val nonce = ByteArray(NONCE_LEN).also(buf::get)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(data, 0, HEADER_LEN)
            cipher.doFinal(data, HEADER_LEN, data.size - HEADER_LEN)
        } catch (e: GeneralSecurityException) {
            throw MigrationDecryptionException(e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

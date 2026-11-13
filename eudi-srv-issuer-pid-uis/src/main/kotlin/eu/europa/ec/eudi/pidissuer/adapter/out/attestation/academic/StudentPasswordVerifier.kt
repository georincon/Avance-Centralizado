/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest

/**
 * Resultado de comprobar la clave de un estudiante.
 */
enum class PasswordCheck {
    /** La clave coincide con el hash guardado. */
    VALID,

    /** La clave coincide con la clave en texto plano heredada (el estudiante aún no tiene hash). */
    VALID_LEGACY,

    INVALID,
}

/**
 * Comprueba la clave de un estudiante contra `clave_hash` (bcrypt).
 *
 * Mientras la migración no haya terminado, un estudiante sin hash se valida contra la clave en texto plano
 * heredada (`clave_autenticacion`), comparada en tiempo constante. Si hay hash, este manda siempre: una clave
 * heredada distinta nunca da acceso.
 *
 * bcrypt es costoso a propósito: quien llame debe ejecutarlo fuera del hilo del event loop.
 */
class StudentPasswordVerifier(
    private val encoder: PasswordEncoder = BCryptPasswordEncoder(),
) {
    // Para que un usuario inexistente tarde lo mismo que uno existente (evita enumerar usuarios por tiempo).
    private val unknownUserHash: String by lazy { requireNotNull(encoder.encode("usuario-inexistente")) }

    fun verify(
        rawPassword: String,
        storedHash: String?,
        legacyPlainPassword: String?,
    ): PasswordCheck =
        when {
            storedHash != null ->
                if (encoder.matches(rawPassword, storedHash)) PasswordCheck.VALID else PasswordCheck.INVALID

            legacyPlainPassword != null ->
                if (constantTimeEquals(rawPassword, legacyPlainPassword)) PasswordCheck.VALID_LEGACY else PasswordCheck.INVALID

            else -> PasswordCheck.INVALID
        }

    /** Gasta el mismo tiempo que una verificación real, para un usuario que no existe. */
    fun verifyUnknownUser(rawPassword: String): PasswordCheck {
        encoder.matches(rawPassword, unknownUserHash)
        return PasswordCheck.INVALID
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

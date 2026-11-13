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
package eu.europa.ec.eudi.pidissuer.port.out.persistence

/**
 * Marca de "emisión bloqueada" para un titular (el `username` con el que se autentica: la cédula o el usuario del
 * estudiante). Vive en una tabla propia (`blocked_subject`): NO toca ni modifica los datos del titular.
 * Un titular bloqueado conserva sus datos, pero no se le emiten credenciales nuevas.
 */
fun interface IsSubjectBlocked {
    suspend operator fun invoke(subject: String): Boolean
}

/** Bloquea la emisión al titular. Devuelve `true` si lo bloqueó ahora, `false` si ya estaba bloqueado. */
fun interface BlockSubject {
    suspend operator fun invoke(
        subject: String,
        reason: String,
    ): Boolean
}

/** Levanta el bloqueo (queda anotada la fecha de liberación, no se borra el historial). `false` si no estaba bloqueado. */
fun interface UnblockSubject {
    suspend operator fun invoke(subject: String): Boolean
}

fun interface ListBlockedSubjects {
    suspend operator fun invoke(): Set<String>
}

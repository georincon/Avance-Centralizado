/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSession
import java.text.Normalizer

/**
 * Homologación entre la evidencia del onboarding (lectura de la cédula física) y el registro de la
 * Registraduría (tabla `cedulas`).
 *
 * Regla (todas deben cumplirse; el número de documento es la clave de búsqueda y ya coincide):
 *  1. La fecha de nacimiento es exactamente igual.
 *  2. Nombres y apellidos, normalizados (sin tildes, mayúsculas, solo letras y un espacio entre palabras),
 *     son iguales o tienen similitud >= [MIN_NAME_SIMILARITY] (1 - distancia de Levenshtein / longitud mayor).
 */
object CedulaHomologation {
    const val MIN_NAME_SIMILARITY: Double = 0.90

    data class Result(
        val ok: Boolean,
        val reasons: List<String>,
    )

    fun check(
        session: OnboardingSession,
        cedula: Cedula,
    ): Result {
        val reasons = buildList {
            if (session.birthDate != cedula.fechaNacimiento) add("BIRTH_DATE_MISMATCH")
            if (similarity(session.firstNames, cedula.nombres) < MIN_NAME_SIMILARITY) add("GIVEN_NAMES_MISMATCH")
            if (similarity(session.lastNames, cedula.apellidos) < MIN_NAME_SIMILARITY) add("FAMILY_NAMES_MISMATCH")
        }
        return Result(reasons.isEmpty(), reasons)
    }

    fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .uppercase()
            .replace("[^A-Z ]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")

    fun similarity(
        a: String,
        b: String,
    ): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        return 1.0 - levenshtein(x, y).toDouble() / maxOf(x.length, y.length)
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}

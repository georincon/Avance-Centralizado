package com.onboarding.service

import com.onboarding.domain.OnboardingSession
import java.text.Normalizer

/** Resultado de comparar lo que escribio el usuario con lo leido de la cedula. */
data class DataMatchResult(
    val documentNumberMatch: Boolean,
    val birthDateMatch: Boolean,
    val firstNamesSimilarity: Double,
    val lastNamesSimilarity: Double,
) {
    val namesMatch: Boolean get() = firstNamesSimilarity >= NAME_THRESHOLD && lastNamesSimilarity >= NAME_THRESHOLD

    companion object {
        const val NAME_THRESHOLD = 0.75
    }
}

/**
 * Compara datos declarados vs datos leidos del PDF417. Los nombres se normalizan
 * (mayusculas, sin tildes, sin orden) y toleran una letra de diferencia por palabra.
 */
object IdentityComparator {

    /** Devuelve null si faltan los datos declarados o los leidos. */
    fun compare(s: OnboardingSession): DataMatchResult? {
        val ocrNumber = s.documentNumber ?: return null
        val declNumber = s.declaredDocumentNumber ?: return null
        val ocrBirth = s.birthDate ?: return null
        val declBirth = s.declaredBirthDate ?: return null
        return DataMatchResult(
            documentNumberMatch = digits(ocrNumber) == digits(declNumber),
            birthDateMatch = ocrBirth == declBirth,
            firstNamesSimilarity = similarity(
                listOfNotNull(s.firstName, s.middleName).joinToString(" "),
                s.declaredFirstNames.orEmpty(),
            ),
            lastNamesSimilarity = similarity(
                listOfNotNull(s.lastName, s.secondLastName).joinToString(" "),
                s.declaredLastNames.orEmpty(),
            ),
        )
    }

    private fun digits(v: String) = v.filter { it.isDigit() }.trimStart('0')

    private fun tokens(v: String): List<String> =
        Normalizer.normalize(v, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase()
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /** Fraccion de palabras en comun sobre la lista mas larga (0.0 a 1.0). */
    fun similarity(read: String, declared: String): Double {
        val a = tokens(read)
        val b = tokens(declared).toMutableList()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var matched = 0
        for (t in a) {
            val idx = b.indexOfFirst { close(t, it) }
            if (idx >= 0) {
                matched++
                b.removeAt(idx)
            }
        }
        return matched.toDouble() / maxOf(a.size, tokens(declared).size)
    }

    private fun close(x: String, y: String): Boolean =
        x == y || (x.length >= 5 && y.length >= 5 && levenshtein(x, y) <= 1)

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}

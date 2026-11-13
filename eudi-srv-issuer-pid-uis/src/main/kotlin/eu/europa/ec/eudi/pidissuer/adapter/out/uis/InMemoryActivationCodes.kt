/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import eu.europa.ec.eudi.pidissuer.port.out.uis.ActivationCodes
import eu.europa.ec.eudi.pidissuer.port.out.uis.IssuedActivationCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Códigos de activación en memoria (solo se guarda su huella SHA-256): vencen (24 h por defecto), sirven una vez y se
 * invalidan tras [maxAttempts] intentos fallidos. Un reinicio del issuer los borra: el funcionario genera otro.
 */
class InMemoryActivationCodes(
    private val clock: Clock,
    private val ttl: Duration = 24.hours,
    private val maxAttempts: Int = 5,
) : ActivationCodes {
    private class Entry(val hash: ByteArray, val expiresAt: Instant) {
        var failures = 0
    }

    private val codes = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    override fun issue(documentNumber: String): IssuedActivationCode {
        val now = clock.now()
        codes.entries.removeIf { it.value.expiresAt < now }
        // 8 dígitos (NNNN-NNNN al mostrarlos): fáciles de dictar en ventanilla.
        val code = "%08d".format(random.nextInt(100_000_000))
        val expiresAt = now + ttl
        codes[documentNumber] = Entry(hash(code), expiresAt)
        return IssuedActivationCode(code, expiresAt)
    }

    override fun verifyAndConsume(documentNumber: String, code: String): Boolean {
        val entry = codes[documentNumber] ?: return false
        synchronized(entry) {
            if (clock.now() > entry.expiresAt) {
                codes.remove(documentNumber, entry)
                return false
            }
            val ok = MessageDigest.isEqual(entry.hash, hash(code.filter(Char::isDigit)))
            if (ok) {
                codes.remove(documentNumber, entry)
                return true
            }
            if (++entry.failures >= maxAttempts) codes.remove(documentNumber, entry)
            return false
        }
    }

    private fun hash(code: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
}

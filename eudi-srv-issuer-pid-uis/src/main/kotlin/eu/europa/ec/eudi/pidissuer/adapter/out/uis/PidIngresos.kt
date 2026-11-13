/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Un ingreso con PID en curso: la transacción del verifier y, cuando el PID se verificó, la cédula del estudiante. */
class PidIngreso(val id: String, val transactionId: String, val requestUri: String, val creado: Long) {
    @Volatile var documentNumber: String? = null
}

/** Ingresos con PID en memoria (vencen a los [vigenciaMs]); el `id` es aleatorio e inadivinable. */
class PidIngresos(private val vigenciaMs: Long = 5 * 60 * 1000L) {
    private val map = ConcurrentHashMap<String, PidIngreso>()
    private val random = SecureRandom()

    fun crear(transactionId: String, requestUri: String): PidIngreso {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.creado > vigenciaMs * 2 }
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(random::nextBytes))
        return PidIngreso(id, transactionId, requestUri, now).also { map[id] = it }
    }

    fun obtener(id: String): PidIngreso? = map[id]?.takeIf { System.currentTimeMillis() - it.creado <= vigenciaMs }

    fun cerrar(id: String) {
        map.remove(id)
    }
}

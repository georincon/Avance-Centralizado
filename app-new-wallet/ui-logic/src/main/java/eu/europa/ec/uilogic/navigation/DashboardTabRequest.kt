package eu.europa.ec.uilogic.navigation

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aviso de un solo uso: "la proxima vez que se muestre el tablero, abre la pestana de Credenciales".
 *
 * Lo activa la pantalla de exito de una EMISION (acaba de llegar una credencial nueva, que es lo que
 * la persona quiere ver) y lo consume el tablero al volver a primer plano. Las presentaciones y
 * verificaciones no lo activan, asi que siguen volviendo a Inicio.
 */
object DashboardTabRequest {
    private val documentsPending = AtomicBoolean(false)

    fun openDocumentsOnNextVisit() = documentsPending.set(true)

    /** true una sola vez por cada aviso. */
    fun consumeDocuments(): Boolean = documentsPending.getAndSet(false)
}

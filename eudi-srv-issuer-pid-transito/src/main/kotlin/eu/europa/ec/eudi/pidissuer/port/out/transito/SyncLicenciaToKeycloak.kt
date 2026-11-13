/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md, sección 5 (Opción A) y 9.4.
 */
package eu.europa.ec.eudi.pidissuer.port.out.transito

import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia

/**
 * Sincroniza en Keycloak un usuario "fantasma" (username = número de cédula,
 * sin contraseña) cada vez que el panel admin crea o actualiza un ciudadano,
 * para que el flujo OAuth2/OpenID4VCI existente (Opción A, sección 5 de
 * README.md) pueda autenticarlo sin pedirle clave — el
 * *authentication flow* del realm debe configurarse aparte para no exigir
 * contraseña (ver sección 6.1, pendiente de hacerse en la consola de Keycloak).
 *
 * Solo se usa cuando `issuer.pid.dataSource=transito`; los datos del PID
 * siguen viniendo de la tabla `licencias` (ver [eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.GetPidDataFromTransito]),
 * no de los atributos de este usuario de Keycloak.
 */
fun interface SyncLicenciaToKeycloak {
    suspend operator fun invoke(licencia: Licencia)
}

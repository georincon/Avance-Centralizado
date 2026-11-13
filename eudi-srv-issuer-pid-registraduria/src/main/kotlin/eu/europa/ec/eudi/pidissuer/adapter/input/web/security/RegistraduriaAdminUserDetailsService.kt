/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web.security

import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.AdminUsuarioR2dbcRepository
import kotlinx.coroutines.reactor.mono
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import reactor.core.publisher.Mono

/**
 * [ReactiveUserDetailsService] para el login del panel admin de la
 * Registraduría, respaldado por la tabla `admin_usuarios` (ver
 * CONTEXTO-REGISTRADURIA.md, sección 5). Todo admin tiene el mismo rol
 * `ADMIN`; no hay niveles de permisos finos en este MVP.
 */
class RegistraduriaAdminUserDetailsService(
    private val repository: AdminUsuarioR2dbcRepository,
) : ReactiveUserDetailsService {
    override fun findByUsername(username: String): Mono<UserDetails> =
        mono {
            val admin =
                repository.findByUsername(username)
                    ?: throw UsernameNotFoundException("No admin user with username '$username'")
            User
                .withUsername(admin.username)
                .password(admin.passwordHash)
                .roles("ADMIN")
                .build()
        }
}

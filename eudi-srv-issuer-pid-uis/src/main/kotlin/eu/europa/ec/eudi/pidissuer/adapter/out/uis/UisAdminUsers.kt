/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import reactor.core.publisher.Mono

/** Usuarios del panel admin de la UIS (tabla `admin_usuarios` del issuer, con la clave en bcrypt). */
class UisAdminUsers(private val db: DatabaseClient) : ReactiveUserDetailsService {
    suspend fun ensureTable() {
        db.sql(
            """CREATE TABLE IF NOT EXISTS admin_usuarios (
                 id BIGSERIAL PRIMARY KEY, username VARCHAR(100) NOT NULL, password_hash VARCHAR(255) NOT NULL,
                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now())""",
        ).fetch().rowsUpdated().awaitSingle()
        db.sql("CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_usuarios_username ON admin_usuarios (username)").fetch().rowsUpdated().awaitSingle()
    }

    suspend fun upsert(username: String, passwordHash: String) {
        db.sql(
            """INSERT INTO admin_usuarios (username, password_hash) VALUES (:u, :p)
               ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash""",
        ).bind("u", username).bind("p", passwordHash).fetch().rowsUpdated().awaitSingle()
    }

    override fun findByUsername(username: String): Mono<UserDetails> =
        mono {
            val hash =
                db.sql("SELECT password_hash FROM admin_usuarios WHERE username = :u").bind("u", username)
                    .map { row, _ -> row.get("password_hash") as String }.one().awaitFirstOrNull()
                    ?: throw UsernameNotFoundException("No admin user '$username'")
            User.withUsername(username).password(hash).roles("ADMIN").build() as UserDetails
        }
}
